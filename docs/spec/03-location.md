# 03 — Location Tracking (ANDLOC)

**Any parent device may report location** (design decision, revised by the tracker rework —
epic #53): a foreground service samples GPS every `ping_interval_s` (from server config,
default 300 s) and enqueues `location.ping` events into the outbox — so tracking works
offline and backfills on reconnect. Kid profiles never see the tracker, and the app never
requests location permissions outside the parent enable flow.

## Behavior

- Tracking is opt-in per device: a parent enables "This device is a trip tracker" in
  settings; the toggle exists for **parent profiles on any device class** (phones and
  tablets) and never for kid profiles.
- Enabling records the **enabling parent's profile id**. The tracker service attributes
  every `location.ping` to that parent regardless of which profile is signed in later:
  ping sync batches are uploaded under that parent's `X-Profile-Id`, so backend
  attribution (EVT-005) is untouched.
- **Multi-device merge is accepted behavior**: several parent devices may track
  simultaneously; the server merges their pings into the one family breadcrumb ordered by
  client_ts. No client-side de-duplication is attempted.
- The service runs as a `foregroundServiceType="location"` service with a persistent
  notification, samples once per interval, and survives Doze via `setExactAndAllowWhileIdle`
  scheduling.
- Each sample becomes an outbox event immediately (offline-tolerant); flush follows ANDSYNC
  rules. The tracker does not run between trips (`09-trips.md`).

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDLOC-001 | Each location sample enqueues a `location.ping` outbox event with lat, lon, accuracy, and the sample's timestamp as client_ts. | auto |
| ANDLOC-002 | The scheduler requests the next sample `ping_interval_s` after the previous one and follows server config changes on the next cycle. | auto |
| ANDLOC-003 | The tracker toggle is available to parent profiles on any device class (phones and tablets) and never to kid profiles; enabling requires granted fine-location (+ background/foreground-service) permission, walked through an explanatory flow, and records the enabling parent's profile id. | auto |
| ANDLOC-004 | Samples taken while offline queue up and sync later without loss or duplication (with ANDSYNC-002/003). | auto |
| ANDLOC-005 | Kid profiles: the app never requests any location permission (manifest-level: permissions exist, but the runtime request flow is unreachable outside the parent tracker enable flow). | auto |
| ANDLOC-006 | The foreground service shows a persistent "Trip tracking active" notification and stops cleanly from it or the settings toggle. | manual |
| ANDLOC-007 | A GPS sample failure (timeout/no fix) skips that cycle without crashing and retries next cycle; three consecutive failures surface a quiet in-app warning to parents. | auto |
| ANDLOC-008 | `location.ping` events are attributed to the enabling parent regardless of the signed-in profile: outbox entries carry that parent's profile id, and sync batches containing them are sent under it (per-batch `X-Profile-Id`); entries with different actors never share a batch. | auto |
