# Road Trip Android — Specification Overview

Client spec for the Family Road Trip app. The backend specs in
`roadtrip-backend/docs/spec/` are the system of record for shared behavior (event model, API
contract, detection rules); this directory specifies the Android client. Requirement IDs here
use `AND*` areas; the same `auto`/`manual` verification rules and validator apply
(`./scripts/validate-specs.sh`).

## Device matrix

One APK serves everything:

| Device | Users | Special behavior |
|--------|-------|------------------|
| Parent phone | parents | location tracking (foreground service), full admin |
| Kid tablet | kids | no location permission requested, kid map view |
| Parent tablet | parents | admin without location tracking |

## Architecture

- **Kotlin + Jetpack Compose + Material 3**, single-activity, Navigation Compose.
- **`core` module (pure JVM)** — all business logic: API client + DTOs, offline queue logic,
  sync engine, notification derivation, game state rendering, view-model state reducers.
  Fully unit-testable without an emulator; this is where TDD happens.
- **`app` module (Android)** — Compose UI, Room storage, WorkManager sync scheduling,
  foreground location service, notification channels. Thin: framework glue only.
- **Adaptive layouts** via `WindowSizeClass`: compact width = phone (bottom navigation),
  medium/expanded = tablet (navigation rail + two-pane where specified).
- Offline-first: reads render from the local Room cache; writes queue locally and sync.

## App-wide requirements

| ID | Requirement | Verify |
|----|-------------|--------|
| AND-001 | Launch shows the profile picker (avatars from `GET /api/profiles`); tapping one selects it — no passwords anywhere. | auto |
| AND-002 | The selected profile persists across restarts and is attached to every API call as `X-Profile-Id`. | auto |
| AND-003 | Parent-only UI (destination editing, settings, profile admin) is hidden for kid profiles; role comes from the profile record, never from names. | auto |
| AND-004 | Navigation adapts by window size class: bottom bar on compact width, navigation rail on medium/expanded — same destinations (Journal, Map, Games, Checklist, Trip). | manual |
| AND-005 | The app is fully navigable offline: cached journal, map (last known state), checklist, and summaries render without connectivity; online-only actions (game moves) show a clear offline state instead of failing silently. | auto |
| AND-006 | An online/offline indicator reflects reachability of `GET /api/health`, re-checked on connectivity changes and sync attempts. | auto |

## Spec index

| File | Contents |
|------|----------|
| `01-sync.md` | Offline queue, sync engine, cursors (ANDSYNC) |
| `02-map.md` | Map & progress view incl. kid view rules (ANDMAP) |
| `03-location.md` | Parent-phone location tracking service (ANDLOC) |
| `04-journal.md` | Journal feed, composer, deep links (ANDJRNL) |
| `05-games.md` | Lobby, boards, challenge/spectate/replay UI (ANDGAME) |
| `06-notifications.md` | Local notifications on phones and tablets (ANDNOTIF) |
| `07-settings.md` | Parent settings (radii), checklist & summaries screens (ANDSET) |
| `08-testing.md` | Test strategy, CI, release engineering |
| `09-trips.md` | Multiple road trips: parent-only start/end, per-trip history (planned) |
