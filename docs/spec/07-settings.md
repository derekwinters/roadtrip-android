# 07 — Settings, Checklist & Summaries (ANDSET)

## Parent settings

Parents get a settings screen with: detection tuning (the parent-configurable radii —
`stop_radius_m`, `min_stop_duration_min`, `arrival_radius_m`, `ping_interval_s`,
`city_radius_km`), the trip tracker toggle (ANDLOC — any device class), the road trip
start/end actions (ANDTRIP), profile administration, server address, and the theme setting.
Kids get only: server address display, app version, and the theme setting.

The theme setting (ANDSET-007) is a per-device display preference, not a per-profile one:
a kid on a shared tablet needs it as much as a parent does, so it is shown to every profile
and whoever changes it changes it for the device.

The server address is first configured on the first-run setup gate (AND-014), not defaulted
to a baked-in host; every editor here and pre-sign-in (AND-008) persists through the same
validation seam (AND-015), so a malformed address can never be saved.

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDSET-001 | The tuning screen loads current values from `GET /api/config`, validates edits against the documented bounds client-side, and saves via `PUT /api/config`; save is parent-only. | auto |
| ANDSET-002 | Config save failures (offline, out-of-bounds from server) restore the last known server values and show the reason. | auto |
| ANDSET-003 | The checklist screen renders states (with first-entered time), cities, and stops from `GET /api/checklist`, cached for offline; states light up as crossings arrive on the event feed. | auto |
| ANDSET-004 | The trip screen shows per-leg summaries and the whole-trip summary (miles, total/moving duration in `h:mm` per ANDTRIP-010, states, stops, and the aggregate games-played count). No per-person wins or journal-post breakdowns are shown. | auto |
| ANDSET-005 | Parent profile administration: create/edit profiles (name, avatar, role) per PRO-002; hidden from kids. | manual |
| ANDSET-006 | The parent profiles section has an "Anyone can add family members" switch bound to the `open_profile_creation` config key (backend CFG-006/PRO-009), applied via `PUT /api/config`; a failed apply reverts the switch to the last known server value and shows the reason (ANDSET-002 semantics). | auto |
| ANDSET-007 | Settings shows a **Theme** selector to every profile, parent and kid (`FeatureVisibility.themeSetting` is true for both roles), as AND-013 single-choice chips over the core `ThemeChoices` set — Light, Dark, System, in that order (`ThemeMode.LIGHT`, `DARK`, `SYSTEM`). It is ONE preference per device, not per profile: switching profile does not change it. A never-set preference is System (`ThemePreference.DEFAULT`). It is persisted locally in the device settings (DataStore key `theme_mode`) like the other client-side prefs, through the pure `ThemePreference` codec: `encode` writes a stable token and `decode` reads it back to the same mode, while a missing, unknown or garbled stored value decodes to System — never an error. Choosing an option applies at once: the root theme (AND-019) re-reads the preference and the whole app re-themes immediately, without a restart. Client-side only; nothing is sent to the server. | auto |
| ANDSET-008 | The effective theme is decided by one pure resolver, `ThemeResolver.resolve(mode, systemDark)` → `EffectiveTheme.LIGHT` / `DARK`: System follows the system dark-mode flag (dark exactly when `systemDark` is true), Light is always light and Dark is always dark, whatever the flag. AND-019 calls it at the root; nothing else decides light vs dark. | auto |
