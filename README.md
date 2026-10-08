# roadtrip-android

Android client for the **Family Road Trip app** — one APK for parent phones and kid tablets.
Talks to [roadtrip-backend](https://github.com/derekwinters/roadtrip-backend) over the
car-hotspot VPN and stays fully usable offline.

- **Profiles** — passwordless avatar picker; the first launch walks through family setup,
  starting with a parent profile. The server address is fixable right from the picker —
  before any sign-in — with retry and automatic re-probe when connectivity returns.
- **Journal** — the shared family feed: manual posts from anyone plus automatic entries
  (stops, state crossings, game results, arrivals) with deep links.
- **Map** — "you are here" with breadcrumb trail and progress; kids see start, current
  position, and the next destination; parents manage the full destination list (long-press
  pin, coordinates, or address search via the backend geocode proxy).
- **Games** — chess, checkers, tic-tac-toe, ultimate tic-tac-toe, hangman: lobby,
  challenges, live spectating, and replays — plus **license plate bingo**: one shared
  50-states-+-DC card per trip that anyone can fill, offline-friendly, with a history log
  and per-spotter standings.
- **Trips & planner** — parents start/end named road trips; between trips the family can
  plan the **next trip** (name, approximate start, staged itinerary from the map) and
  launch it with one tap — the staged stops become the trip's destination list.
- **Location tracking** — only the parent phone runs the foreground tracker (5-minute pings,
  offline-queued); tablets never request location permissions.
- **Notifications** — local notifications on phones and tablets for received challenges and
  journal activity.
- **Checklist & summaries** — states/cities collected, per-leg and whole-trip stats.

## Building

```bash
./gradlew -p core test            # pure-JVM business logic (no Android SDK needed)
./gradlew :app:testDebugUnitTest  # Robolectric tests
./gradlew :app:assembleRelease    # release APK (debug-signed fallback without signing inputs)
./scripts/validate-specs.sh       # spec/documentation validation
```

The `core` module is a standalone composite build containing all business logic (sync engine,
reducers, API client, game replay) so TDD runs anywhere; the `app` module is thin Compose UI
and framework glue. Specs live in [docs/spec/](docs/spec/00-overview.md) with requirement IDs
enforced by CI.

## Releases

Conventional commits + release-please (`version.txt` drives `versionName`/`versionCode`):
PR builds upload APK artifacts, `main` builds publish RC prereleases while a release PR is
open, and versioned releases get final APKs attached to the release notes. No app stores.

### Release signing (upgrade in place)

Release and RC APKs are signed with one stable release key, so each release installs over the
previous one without uninstalling (which would wipe the local database and any unsynced
outbox). The keystore lives only in repository Actions secrets (`ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEYSTORE_SHA256`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_ALIAS_PASSWORD`); the release workflow fails if any is missing and verifies every
release APK against the pinned certificate before publishing. PR builds never see the key and
stay debug-signed. The `-debug` APK uses the `com.roadtrip.app.debug` application ID, so it
installs alongside the real app.

- **One-time transition:** the first stable-signed release differs from the old debug-signed
  installs, so each device needs one final uninstall/reinstall — sync first (empty outbox).
  Every upgrade after that is in place.
- **No recovery:** if the keystore or its passwords are lost, no future version can install
  over the current one. Keep an offline backup outside GitHub.
- **Local release signing (optional):** create a git-ignored `keystore.properties` at the repo
  root with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

Details: [docs/spec/08-testing.md](docs/spec/08-testing.md#release-signing-and-in-place-upgrades).
