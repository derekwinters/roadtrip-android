# 09 — Trips (ANDTRIP)

Client side of backend spec `roadtrip-backend/docs/spec/12-trips.md`: a parent taps
**"Road trip starts now"**, everything the family does is associated with that trip, and past
trips remain browsable read-only. **Starting/ending/renaming a trip is parent-only.**

## Interface decisions

- **Between trips** (no active trip, at least one ended trip): the app opens on the most
  recently ended trip **read-only**, under a persistent, single-line **"No active road
  trip."** strip. The strip carries only that banner line plus a right-aligned **"New
  trip"** text link that navigates to **Settings › Road trip** (where a trip is
  created/started); it no longer hosts the start/plan/history buttons or a read-only
  "Browsing …" subtitle. The **"New trip"** link follows the parent-only start gating
  (`startAction.visible`) — kids see the banner without the link and never see start/end
  anywhere. Start/end live in **Settings › Road trip**, trip history lives in the **Trip
  tab**, and an existing plan keeps its own actions on the **planned-trip card** below the
  strip.
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
- **Itinerary planner** (backend planner contract, issue #60): parents can plan **one**
  "next trip" ahead of time — `POST /api/trips {status:"planned", name?, planned_start_at?}`
  (the server answers a second planned trip with 409 `conflict`), rename/re-date it with
  `PATCH /api/trips/{id} {name?, planned_start_at?}`, and delete it (`DELETE`, planned
  only). `planned_start_at` is an **approximate** start entered as free text and displayed
  verbatim ("~ early August") — the client never parses it. Planned trips have no
  `started_at` yet; `GET /api/trips` lists them alongside active/ended ones.
- The **planned-trip card** (name, ~start, staged itinerary preview) renders wherever the
  "No active road trip" banner rules apply: between trips and on the first-launch welcome.
  Kids see the card **read-only**; all planner actions (plan/rename/delete/start) are
  parent-only and online-only like the rest of the trip lifecycle. The card's
  **"Road trip starts now"** button activates the plan via `POST /api/trips/{id}/start`
  (confirm dialog; 409 `conflict` while another trip is active).
- **Staging**: destination writes and the destination list accept `?trip=<plannedId>`, so
  parents stage the planned trip's itinerary from the map screen exactly like during a
  trip (parent-only, online-only). The staged list previews on the planned-trip card and
  in the map destination panel while no trip is active. Activation **adopts** the staged
  itinerary server-side — the client just calls `/start`, re-pulls trips, and its
  trip-scoped caches switch to the new active trip (fresh keys, ANDTRIP-002).
- Planned trips never appear in the trip **history** browser's past-trip list (they have
  no history); the card is their home until they start or are deleted.

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDTRIP-001 | "Start road trip" / "End road trip" actions live in **Settings › Road trip** (reached from the no-active-trip strip's "New trip" link) and are visible **only to parent profiles** (role attribute, per AND-003); kids never see them anywhere in the UI and get history browsing only. | auto |
| ANDTRIP-002 | An active-trip indicator shows the current trip's name; with no active trip the app opens on the most recently ended trip read-only under a persistent, **single-line "No active road trip." strip** (`TripStateReducer.noActiveTripStrip`): one banner line (ellipsized if ever needed) plus a right-aligned **"New trip"** link that navigates to Settings › Road trip. The strip no longer hosts the start/plan/history actions or the read-only "Browsing …" subtitle (those live in Settings, the Trip-tab history, and the planned-trip card); the "New trip" link follows the parent-only start gating (`startAction.visible`), so kids see the banner without it. First-ever launch is the welcome/empty state; the tracker never runs between trips. Journal, map, checklist, and trip caches are scoped per trip so switching/ending never mixes histories. | auto |
| ANDTRIP-003 | A trip history browser lists past trips and opens read-only journal, checklist, and summary views for each. | auto |
| ANDTRIP-004 | Start/end are online-only actions with a confirm dialog (the server arbitrates the single active trip); offline, the actions are disabled with an explanation, consistent with AND-005. | auto |
| ANDTRIP-005 | Trip start/end flows verified end-to-end during the pre-trip dry run. | manual |
| ANDTRIP-006 | Parents can create, rename, and delete exactly **one** planned "next trip" (server 409s a second plan) with an optional approximate free-text start; the no-active-trip screen and the first-launch welcome show the planned-trip card (name, ~start, itinerary preview) with a **"Road trip starts now"** action that activates it; kids see the card read-only with no planner actions. | auto |
| ANDTRIP-007 | Destinations can be staged against the planned trip from the map screen exactly like during a trip — destination writes/list pass `?trip=<plannedId>`; staging is parent-only and online-only; the staged list shows on the planned-trip card and in the map destination panel while viewing the planned trip. | auto |
| ANDTRIP-008 | Activating the planned trip adopts the staged itinerary: the client calls `POST /api/trips/{id}/start` (409 while another trip is active), re-syncs, and the app switches to the new active trip — the trip-scoped caches land under the new trip's keys and the staged destinations become the active list. | auto |
| ANDTRIP-009 | While a trip is active, its name is surfaced as **persistent context in the top app bar** — a compact overline above the per-screen title — rather than as a dismissable notification-style band, and it ellipsizes when long. The standalone TripStrip active-trip-name band is removed; between trips the TripStrip is a single-line "No active road trip." banner with the parent-only "New trip → Settings" link only (ANDTRIP-002) — it no longer carries the read-only "Browsing …" subtitle or the start/plan/history actions. The app bar shows the active name only while a trip runs (`TripStateReducer.activeTripBarLabel` returns the active trip's name and null otherwise). | auto |
