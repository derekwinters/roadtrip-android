# 09 — Trips (ANDTRIP) — *planned*

Client side of backend spec `roadtrip-backend/docs/spec/12-trips.md`: a parent taps
**"Road trip starts now"**, everything the family does is associated with that trip, and past
trips remain browsable read-only. **Starting/ending/renaming a trip is parent-only.**

Status: **queued for implementation** — rows are tagged `planned` (documented and
issue-tracked; flip to `auto` when implementing).

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDTRIP-001 | "Start road trip" / "End road trip" actions exist and are visible **only to parent profiles** (role attribute, per AND-003); kids never see them anywhere in the UI. | planned |
| ANDTRIP-002 | An active-trip indicator shows the current trip's name; journal, map, checklist, and trip caches are scoped per trip so switching/ending never mixes histories. | planned |
| ANDTRIP-003 | A trip history browser lists past trips and opens read-only journal, checklist, and summary views for each. | planned |
| ANDTRIP-004 | Start/end are online-only actions with a confirm dialog (the server arbitrates the single active trip); offline, the actions are disabled with an explanation, consistent with AND-005. | planned |
| ANDTRIP-005 | Trip start/end flows verified end-to-end during the pre-trip dry run. | manual |
