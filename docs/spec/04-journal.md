# 04 — Journal (ANDJRNL)

The home screen: the shared family feed. Renders the backend journal read model
(`GET /api/journal` + live updates from the event feed), cached in Room for offline reading.

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDJRNL-001 | The feed renders all journal entry kinds with distinct presentations: manual posts (author avatar + text), state crossings, journal-worthy stops (with place when present), game results, leg arrivals. | auto |
| ANDJRNL-002 | The composer posts free text (1–2000 chars) from any profile; offline posts queue and appear in the feed immediately from the local outbox, marked "syncing" until accepted. | auto |
| ANDJRNL-003 | Feed ordering is by client_ts (the backend's order), so offline posts land at their written time after sync — the local render matches the server's ordering rules. | auto |
| ANDJRNL-004 | Deep links navigate per entry kind: game result → replay screen; stop → map centered on pin; state crossing → checklist; leg arrival → leg summary. | auto |
| ANDJRNL-005 | Infinite scroll pages backward with the `before` cursor; pull-to-refresh syncs forward. The load-older cursor is the `seq` of the **oldest currently-cached entry by the feed's sort key `(client_ts, seq)`** — matching the server's `(client_ts, seq)` `before` comparison (backend JRNL-002) — **not** the global minimum `seq`. Because backdated entries sync late with a high `seq` but an old `client_ts` (ANDJRNL-003, ANDSYNC-006; auto stops backdated per backend LOC-006), `seq` order diverges from `client_ts` order, so paging from `min(seq)` points the cursor above the true client_ts fold: the server returns already-cached rows, the dedup keeps the cache (and cursor) unchanged, and load-older deadlocks while genuinely older entries fall past the row limit and are never fetched. Paging from the client_ts-oldest cached entry keeps the cursor advancing and retrieves entries created before a device connected. | auto |
| ANDJRNL-006 | Kid and parent profiles have identical journal capabilities (posting is never moderated or restricted). | auto |
| ANDJRNL-007 | Author attribution (avatar + name) for actor-attributed entry kinds resolves from the current profiles cache by actor id at render time, so a profile changing its avatar/name updates every one of that author's rows; the entry's embedded `actor` snapshot is used only as a fallback when the profile is not cached (offline for an unknown profile). | auto |
| ANDJRNL-008 | A journal row's visual emphasis is independent of deep-link availability: **only click-navigability** is gated by the row's resolvable link (ANDJRNL-004). A non-linkable row — e.g. a manual post, which never carries a deep link — renders at **full emphasis**, never as a disabled/greyed card, and is not exposed as a disabled clickable element (an accessibility oddity). The navigability/emphasis decision is a pure seam over the feed item so the composable stays thin. | auto |
