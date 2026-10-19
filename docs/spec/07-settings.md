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

## Naming a checklist stop

A **checklist stop** is a server-detected stop row in the Checklist's Stops section
(`ChecklistStopEntry` → `StopRow`). It is not a map destination pin and not a leg; the code
keeps those types separate and the UI copy says "stop", never "destination".

A parent can press-and-hold a stop row to give it a custom name (issue #160, backend
roadtrip-backend#104, LOC-014..017 / LIST-003). The name travels as the client-originated
`location.stop.labeled {stop_id, label}` event through the offline outbox (ANDSYNC-001/002),
so naming works offline exactly like a journal post; the server folds the latest label per
stop into `GET /api/checklist` (last-write-wins by `client_ts`).

- **Invariant:** a stop is identified only by the backend's stable `stop_id` — never by its
  lat/lon or time.
- **Invariant:** kids never get the naming affordance: the long-press is a no-op for them and
  the dialog cannot open. (The resulting name is part of the stop's row like `place`, so it
  shows to every profile.)
- **Invariant:** a name can be changed but never cleared — a name that is blank after
  trimming is not saved and nothing is enqueued.
- The client does not read `stop_label_max_chars` (backend CFG-008) from `GET /api/config`;
  it trims and leaves the upper bound to the server, which rejects an over-long label
  (`label_too_long`) into the outbox quarantine (ANDSYNC-004).

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDSET-001 | The tuning screen loads current values from `GET /api/config`, validates edits against the documented bounds client-side, and saves via `PUT /api/config`; save is parent-only. | auto |
| ANDSET-002 | Config save failures (offline, out-of-bounds from server) restore the last known server values and show the reason. | auto |
| ANDSET-003 | The checklist screen renders from `GET /api/checklist`, cached for offline (AND-005): a single **States** section in which each state row (with its first-entered time) is a collapsible group holding that state's cities (ANDSET-009–012), followed by a **Stops** section. Each stop carries the backend's stable `stop_id` and, when it has been named, its custom `label` alongside the auto-detected `place` (backend LIST-003). There is no separate Cities section. States light up as crossings arrive on the event feed. | auto |
| ANDSET-004 | The trip screen shows per-leg summaries and the whole-trip summary (miles, total/moving duration in `h:mm` per ANDTRIP-010, states, stops, and the aggregate games-played count). No per-person wins or journal-post breakdowns are shown. | auto |
| ANDSET-005 | Parent profile administration: create/edit profiles (name, avatar, role) per PRO-002; hidden from kids. | manual |
| ANDSET-006 | The parent profiles section has an "Anyone can add family members" switch bound to the `open_profile_creation` config key (backend CFG-006/PRO-009), applied via `PUT /api/config`; a failed apply reverts the switch to the last known server value and shows the reason (ANDSET-002 semantics). | auto |
| ANDSET-007 | Settings shows a **Theme** selector to every profile, parent and kid (`FeatureVisibility.themeSetting` is true for both roles), as AND-013 single-choice chips over the core `ThemeChoices` set — Light, Dark, System, in that order (`ThemeMode.LIGHT`, `DARK`, `SYSTEM`). It is ONE preference per device, not per profile: switching profile does not change it. A never-set preference is System (`ThemePreference.DEFAULT`). It is persisted locally in the device settings (DataStore key `theme_mode`) like the other client-side prefs, through the pure `ThemePreference` codec: `encode` writes a stable token and `decode` reads it back to the same mode, while a missing, unknown or garbled stored value decodes to System — never an error. Choosing an option applies at once: the root theme (AND-019) re-reads the preference and the whole app re-themes immediately, without a restart. Client-side only; nothing is sent to the server. | auto |
| ANDSET-008 | The effective theme is decided by one pure resolver, `ThemeResolver.resolve(mode, systemDark)` → `EffectiveTheme.LIGHT` / `DARK`: System follows the system dark-mode flag (dark exactly when `systemDark` is true), Light is always light and Dark is always dark, whatever the flag. AND-019 calls it at the root; nothing else decides light vs dark. | auto |
| ANDSET-009 | Cities are grouped under the state row with the same `stateCode` by a pure core function (`ChecklistScreenState.stateGroups`), not in the composable. Groups follow the state order of the read model; cities inside a group keep their read-model order. Each group exposes its `cityCount`, shown on the state row. **Invariant:** grouping happens in plain Kotlin over the cached read model; the composable only renders the groups it is handed. | auto |
| ANDSET-010 | A city whose `stateCode` has no state row gets a synthesized state group: not entered (no first-entered time), display name falling back to the state code (as `applyEvent` already does for a nameless crossing). Synthesized groups follow the real states, in order of their first city. When the matching `location.crossing.state` event later arrives, that group becomes the real, entered state row with its cities still nested under it — never a duplicate. | auto |
| ANDSET-011 | State groups are collapsed by default. Arriving via a state-crossing deep link (ANDJRNL-004) auto-expands the highlighted state's group only; every other group stays collapsed. Tapping a state row toggles its group. Expansion is view-local and not persisted; the initial set and the toggle are pure core functions (`ChecklistExpansion`). | auto |
| ANDSET-012 | Grouping keeps every existing affordance: the entered/not-entered icon, the state's first-entered time, the deep-link highlight on the state row, and each nested city's first-visited time (`CityRow.firstAt`). Nested cities are indented under their state. | manual |
| ANDSET-013 | A stop row's display name is decided in core (`StopRow.displayName`), never in the composable: the custom `label` if present, else `place`, else the lat/lon formatted to four decimals (`41.6000, -93.6000`). The custom name replaces `place` in the row; it is not shown as an extra line. | auto |
| ANDSET-014 | Long-pressing a checklist stop row as a **parent** opens a "Name this stop" dialog pre-filled with the row's current `label`, else its `place`, else empty; as a **kid** the long-press does nothing. The open/no-op decision and the pre-fill are the pure core `StopNaming.open(row, role)`. | auto |
| ANDSET-015 | Saving the dialog trims the name; a name that is blank after trimming cannot be saved (the dialog's Save is disabled and `StopNaming.save` rejects it) and nothing is enqueued; otherwise exactly one `location.stop.labeled {stop_id, label}` event is enqueued through the outbox (ANDSYNC-001) with the trimmed label and the save time as `client_ts`, regardless of connectivity, followed by a post-write sync (ANDSYNC-007). | auto |
| ANDSET-016 | A saved name shows in the row immediately, online or offline: the checklist reducer overlays pending `location.stop.labeled` outbox entries on the cached read model (latest `client_ts` per `stop_id` wins; entries for a `stop_id` not in the cache are ignored). After the event syncs, the name survives: `location.stop.labeled` events on the event feed update the cached stop's `label` (ANDSYNC-005 seq-guarded applier), and the next `GET /api/checklist` refresh carries it from the server. | auto |
