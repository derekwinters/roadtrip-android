# 01 — Offline Sync Engine (ANDSYNC)

Implements the client side of backend spec `09-sync-notifications.md`.

## Design

- **Outbox**: every locally-created event (`journal.post` from anyone, `location.ping` from
  parent-enabled tracker devices) is written to a Room `outbox` table with a client-generated
  UUID `event_id` and `client_ts` at creation time, regardless of connectivity. Entries may
  carry an `actor_profile_id` attribution override (ANDLOC-008).
- **Flush**: a sync pass sends outbox rows in `client_ts` order via `POST /api/sync/batch`
  (chunks ≤ 500), removing rows on `accepted`/`duplicate` and quarantining rows on
  `rejected` (never retried, surfaced in a debug screen). Consecutive runs of rows sharing
  an actor override upload as their own batch under that profile's `X-Profile-Id`
  (ANDLOC-008).
- **Inbox**: the client stores the last seen event `seq` and pulls
  `GET /api/events?after=<cursor>` to update local caches (journal, map, games, checklist,
  notifications). Foreground: long-poll loop. Background: periodic WorkManager sync.
- Sync triggers: app foreground, connectivity regained, WorkManager period, post-write.

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDSYNC-001 | Events created offline are queued in the outbox with client timestamps and a UUID event_id generated exactly once (stable across retries). | auto |
| ANDSYNC-002 | Flushing sends outbox entries in client_ts order and removes them only on `accepted` or `duplicate`; network failure keeps them queued for the next pass. | auto |
| ANDSYNC-003 | A flush interrupted after the server processed the batch but before the response arrived does not duplicate events on retry (relies on event_id idempotency; verified with a simulated lost response). | auto |
| ANDSYNC-004 | `rejected` events are quarantined (removed from the retry loop) with their reason retained. | auto |
| ANDSYNC-005 | The inbox cursor only advances after the pulled page is durably applied to the local cache, so a crash mid-apply re-pulls rather than skips. | auto |
| ANDSYNC-006 | The journal cache renders correctly interleaved entries after syncing events that were created offline on other devices (mixed-device ordering by client_ts). | auto |
| ANDSYNC-007 | Sync runs on: foreground entry, connectivity regained, a periodic background schedule (15 min default), and after each local write; passes are serialized (no concurrent flushes). | auto |
