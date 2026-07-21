# 10 — License Plate Bingo (ANDBNG)

Family license-plate bingo card (backend contract, issue #58): one shared 50-states-plus-DC
card per trip. Anyone who spots a plate taps its state; the family fills the card together.
Spots are **client sync events** (`plate.spotted` / `plate.unspotted` with `{state_code}`),
so — unlike server-arbitrated game moves — they **queue offline** exactly like journal posts
(ANDSYNC-001) and reconcile on sync.

## Interface decisions

- The bingo screen lives in the **Games** section (it is a family game, not a checklist):
  a grid of the 50 states + DC from a static in-app list — cells light up as plates are
  spotted, showing state, spotter, and time.
- **Any profile** (kid or parent) can spot a plate — spotting is never parent-gated, and
  spots are attributed to the signed-in profile through the normal sync path (no actor
  override).
- **Removals mirror the backend rule**: only the original spotter or a parent can remove a
  spot (mis-taps, disputed sightings). Everyone else gets details only.
- Pending offline spots/removals render immediately with a **pending** marker and their
  real tap timestamps (`client_ts`), matching the journal's offline pattern.
- The card reads from `GET /api/bingo?trip=` (`cells` + chronological `log` + per-profile
  `counts`); live `plate.*` events on the event feed fold into the cached card between
  refreshes, like pings extend the map breadcrumb.
- Unlike the other Games-section screens, the bingo card's read model is fed by **locally-queued
  offline writes**, so its foreground live-refresh runs a **full serialized sync pass** (flush +
  pull), not the lobby's pull-only re-pull. It therefore reports its own `Screen.BINGO` visible
  context so a device parked on the card pushes its queued spots and pulls other devices' spots
  on the foreground cadence, rather than drifting until the next background sync (ANDSYNC-009).
- Bingo is deliberately **quiet**: the backend emits no notifications and no journal
  entries for plate spots, and the client posts no local notifications for them — the
  fun is watching the card fill, not being pinged about it.
- The card is **per-trip** (trip-scoped cache like journal/checklist); past trips' cards
  open read-only from the trip history browser.

## Requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDBNG-001 | The bingo screen in the Games section shows the 50-state + DC grid from a static in-app state list; filled cells show state, spotter, and spot time; any profile can spot an empty cell via tap + confirm; spots queue offline as `plate.spotted` events with a pending marker and real tap timestamps. | auto |
| ANDBNG-002 | Tapping a filled cell opens a detail sheet; a "Remove" action is available only to the original spotter or a parent, queues `plate.unspotted` offline, and clears the cell with a pending marker; every other profile sees details only. | auto |
| ANDBNG-003 | A log section renders the backend log as chronological history — spots and removals with resolved profile names and timestamps. | auto |
| ANDBNG-004 | Live updates: `plate.*` events arriving on the event feed fill/clear cells (honoring the spotter-or-parent removal rule) without a reload; a leaderboard row shows standing counts per profile; the card is per-trip via the trip-scoped cache, and past trips' cards are read-only in the history browser. | auto |
| ANDBNG-005 | Bingo posts no local notifications and creates no journal entries: the notification pipeline and the journal cache applier ignore `plate.*` / bingo activity. | auto |
| ANDBNG-006 | The bingo card reports its own `Screen.BINGO` visible context, and that screen's foreground live-refresh runs a full sync pass (ANDSYNC-009) rather than a pull-only re-pull: queued offline spots/removals are flushed and the remote card is pulled in place, so two devices parked on the card reconverge within the foreground cadence — a device's offline marks reach the other without either navigating away or waiting for the periodic background sync. | auto |
