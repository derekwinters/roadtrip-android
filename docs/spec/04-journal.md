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
| ANDJRNL-005 | Infinite scroll pages backward with the `before` cursor; pull-to-refresh syncs forward. | manual |
| ANDJRNL-006 | Kid and parent profiles have identical journal capabilities (posting is never moderated or restricted). | auto |
| ANDJRNL-007 | Author attribution (avatar + name) for actor-attributed entry kinds resolves from the current profiles cache by actor id at render time, so a profile changing its avatar/name updates every one of that author's rows; the entry's embedded `actor` snapshot is used only as a fallback when the profile is not cached (offline for an unknown profile). | auto |
