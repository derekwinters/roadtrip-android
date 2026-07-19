# 07 — Settings, Checklist & Summaries (ANDSET)

## Parent settings

Parents get a settings screen with: detection tuning (the parent-configurable radii —
`stop_radius_m`, `min_stop_duration_min`, `arrival_radius_m`, `ping_interval_s`,
`city_radius_km`), the trip tracker toggle (ANDLOC — any device class), the road trip
start/end actions (ANDTRIP), profile administration, and server address. Kids get only:
server address display and app version.

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDSET-001 | The tuning screen loads current values from `GET /api/config`, validates edits against the documented bounds client-side, and saves via `PUT /api/config`; save is parent-only. | auto |
| ANDSET-002 | Config save failures (offline, out-of-bounds from server) restore the last known server values and show the reason. | auto |
| ANDSET-003 | The checklist screen renders states (with first-entered time), cities, and stops from `GET /api/checklist`, cached for offline; states light up as crossings arrive on the event feed. | auto |
| ANDSET-004 | The trip screen shows per-leg summaries and the whole-trip summary (miles, hours wall/moving, states, stops, and the aggregate games-played count). No per-person wins or journal-post breakdowns are shown. | auto |
| ANDSET-005 | Parent profile administration: create/edit profiles (name, avatar, role) per PRO-002; hidden from kids. | manual |
| ANDSET-006 | The parent profiles section has an "Anyone can add family members" switch bound to the `open_profile_creation` config key (backend CFG-006/PRO-009), applied via `PUT /api/config`; a failed apply reverts the switch to the last known server value and shows the reason (ANDSET-002 semantics). | auto |
