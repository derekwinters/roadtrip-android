# 09 — Trips (ANDTRIP)

Client side of backend spec `roadtrip-backend/docs/spec/12-trips.md`: a parent taps
**"Road trip starts now"**, everything the family does is associated with that trip, and past
trips remain browsable read-only. **Starting/ending/renaming a trip is parent-only.**

## Interface decisions

- **Between trips** (no active trip, at least one ended trip): the app opens on the most
  recently ended trip **read-only**, under a persistent **"No active road trip"** banner.
  Parents get a **"Road trip starts now"** action in the banner and in settings; kids see
  history only and never see start/end anywhere.
- **First-ever launch** (no trips at all): a welcome/empty state with the parent start
  action (kids get the welcome text without the action).
- The location tracker **does not run between trips** — no active trip, no pings
  (see `03-location.md`).
- Games and the journal composer stay usable between trips; their events simply belong to
  no trip (backend TRIP-010 keeps unassociated activity readable in unscoped views).
- Start and end are **confirm-dialog, online-only** actions: the server arbitrates the
  single active trip (TRIP-001/002) and answers a second concurrent start with
  409 `conflict`.
- Read scoping uses the `?trip=<id>` parameter on the journal/checklist/legs/map read
  models (TRIP-007) plus `GET /api/trips/{id}/summary` (TRIP-008). The client's Room
  caches are **keyed per trip** (`journal_<tripId>`, `checklist_<tripId>`, ...) so
  switching or ending a trip never mixes histories.
- `trip.started` / `trip.ended` are journal-worthy (TRIP-009): they render as distinct
  journal entry kinds and deep-link to the trip summary via the new `trip_summary` deep
  link kind (`{trip_id}`).

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDTRIP-001 | "Start road trip" / "End road trip" actions exist (banner + settings) and are visible **only to parent profiles** (role attribute, per AND-003); kids never see them anywhere in the UI and get history browsing only. | auto |
| ANDTRIP-002 | An active-trip indicator shows the current trip's name; with no active trip the app opens on the most recently ended trip read-only under a persistent "No active road trip" banner (first-ever launch: welcome/empty state; the tracker never runs between trips). Journal, map, checklist, and trip caches are scoped per trip so switching/ending never mixes histories. | auto |
| ANDTRIP-003 | A trip history browser lists past trips and opens read-only journal, checklist, and summary views for each. | auto |
| ANDTRIP-004 | Start/end are online-only actions with a confirm dialog (the server arbitrates the single active trip); offline, the actions are disabled with an explanation, consistent with AND-005. | auto |
| ANDTRIP-005 | Trip start/end flows verified end-to-end during the pre-trip dry run. | manual |
