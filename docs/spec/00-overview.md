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
| Parent phone | parents | optional location tracking (foreground service), full admin |
| Kid tablet | kids | no location permission requested, kid map view |
| Parent tablet | parents | full admin, optional location tracking (same parent toggle as phones) |

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
| AND-007 | When `GET /api/profiles` returns an empty list, the profile picker shows a first-run "Set up your family" flow that creates the first profile (parent role enforced by the flow) and signs in as them; with one or more profiles the picker is the avatar grid — selection plus the AND-010 add-member affordance, nothing else. The flow collects only a name — there is no avatar input; the create request carries no avatar and the server assigns its default. | auto |
| AND-008 | The server address is editable before sign-in: the profile picker exposes a server-settings affordance in every state (header) and prominently in the unreachable state; an unreachable server with no usable cached profile list is a distinct unreachable state — never the first-run setup, which appears only after the server confirmed zero profiles — showing the current address, an explicit Retry, and the address editor (prefilled, with an emulator-host hint); saving an address (even unchanged), tapping Retry, or connectivity returning re-probes `GET /api/profiles` immediately. | auto |
| AND-009 | A failed first-run create never dead-ends the wizard on a raw server error. A 401 `unauthenticated` refusal means the server's bootstrap is closed, so the flow re-probes `GET /api/profiles` immediately: profiles present leaves setup for the grid (setup finished elsewhere — nothing is auto-selected); a still-empty list shows an actionable message that the trip server doesn't support first-run setup yet (older server version — update it, then retry). Transport failures (create or re-probe) show a human offline message. Every failure leaves the wizard intact and retryable. | auto |
| AND-010 | The picker grid offers "Add family member": a name (no avatar — the server assigns its default) and a role choice, created via `POST /api/profiles` before sign-in — the server's `open_profile_creation` flag (default on, backend PRO-009) governs whether that succeeds — signing in as the new profile on success. Failures keep the dialog retryable and human-readable: server rejections surface the server's own message (which explains when a parent has turned creation off); transport failures show an offline message. | auto |
| AND-011 | In-app navigation uses Material motion, not the navigation-compose default crossfade: switching between the five top-level destinations (AND-004) uses **fade-through** (fade + slight scale), while drilling into a hierarchical/detail route (Games → Board/Replay/Bingo, Trip → history, Settings) and popping back use **shared-axis X** (incoming slides in from the trailing edge, outgoing slides out and dims). Durations are ~250–300 ms, not the 700 ms default. The route-change → motion decision is a pure classifier (`NavMotionClassifier`): a change is fade-through only when both the origin and destination routes are top-level tabs, otherwise shared-axis X. | auto |
| AND-012 | The trip-shell state the app bar and TripStrip read (active-trip name, between-trips banner, planner card) is produced off the composition path so navigation never blocks a frame on Room I/O: `AppContainer` exposes `tripHomeFlow(profile)`/`plannerFlow(profile)` `StateFlow`s that read the trip caches and run the pure trip reducers on `Dispatchers.IO`, driven by the sync `refreshTick` and connectivity and memoized per profile; the shell observes them via `collectAsState` with an empty/loading initial state, so first composition and every navigation read already-loaded state instead of a synchronous cache read + JSON decode. The planner's "resolve the planned trip's staged itinerary, then reduce" glue is a pure JVM-tested function (`TripPlannerReducer.reduce(trips, stagedFor, role, online)`) so the flow/threading wiring stays thin. | auto |
| AND-013 | Single-choice selectors present the selected option unmistakably — a filled container plus a leading check icon (Material 3 `SingleChoiceSegmentedButtonRow`/`SegmentedButton`) — with unselected options clearly reading as inactive, applied consistently across the game type and mode pickers (new-game dialog, ANDGAME-002) and the Kid/Parent role pickers (profile picker, profile admin). The role options (ordered Kid, Parent with their labels) are a shared core model (`RoleChoices`) so both role pickers render the same set. The invited-profile `RadioButton` picker is unaffected. | auto |

## Spec index

| File | Contents |
|------|----------|
| `01-sync.md` | Offline queue, sync engine, cursors (ANDSYNC) |
| `02-map.md` | Map & progress view incl. kid view rules (ANDMAP) |
| `03-location.md` | Parent-device location tracking service (ANDLOC) |
| `04-journal.md` | Journal feed, composer, deep links (ANDJRNL) |
| `05-games.md` | Lobby, boards, challenge/spectate/replay UI (ANDGAME) |
| `06-notifications.md` | Local notifications on phones and tablets (ANDNOTIF) |
| `07-settings.md` | Parent settings (radii), checklist & summaries screens (ANDSET) |
| `08-testing.md` | Test strategy, CI, release engineering |
| `09-trips.md` | Multiple road trips: parent-only start/end, per-trip history, itinerary planner (ANDTRIP) |
| `10-bingo.md` | License plate bingo card, offline spots, per-trip log (ANDBNG) |
