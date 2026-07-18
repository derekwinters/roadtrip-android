# 07 — Settings, Checklist & Summaries (ANDSET)

## Parent settings

Parents get a settings screen with: detection tuning (the parent-configurable radii —
`stop_radius_m`, `min_stop_duration_min`, `arrival_radius_m`, `ping_interval_s`,
`city_radius_km`), the phone tracker toggle (ANDLOC), profile administration, and server
address. Kids get only: server address display and app version.

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDSET-001 | The tuning screen loads current values from `GET /api/config`, validates edits against the documented bounds client-side, and saves via `PUT /api/config`; save is parent-only. | auto |
| ANDSET-002 | Config save failures (offline, out-of-bounds from server) restore the last known server values and show the reason. | auto |
| ANDSET-003 | The checklist screen renders states (with first-entered time), cities, and stops from `GET /api/checklist`, cached for offline; states light up as crossings arrive on the event feed. | auto |
| ANDSET-004 | The trip screen shows per-leg summaries and the whole-trip summary (miles, hours wall/moving, states, stops, games won per person, journal counts per person). | auto |
| ANDSET-005 | Parent profile administration: create/edit profiles (name, avatar, role) per PRO-002; hidden from kids. | manual |
