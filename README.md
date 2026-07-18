# roadtrip-android

Android client for the **Family Road Trip app** — one APK for parent phones and kid tablets.
Talks to [roadtrip-backend](https://github.com/derekwinters/roadtrip-backend) over the
car-hotspot VPN and stays fully usable offline.

- **Journal** — the shared family feed: manual posts from anyone plus automatic entries
  (stops, state crossings, game results, arrivals) with deep links.
- **Map** — "you are here" with breadcrumb trail and progress; kids see start, current
  position, and the next destination; parents manage the full destination list.
- **Games** — chess, checkers, tic-tac-toe, ultimate tic-tac-toe, hangman: lobby,
  challenges, live spectating, and replays.
- **Location tracking** — only the parent phone runs the foreground tracker (5-minute pings,
  offline-queued); tablets never request location permissions.
- **Notifications** — local notifications on phones and tablets for received challenges and
  journal activity.
- **Checklist & summaries** — states/cities collected, per-leg and whole-trip stats.

## Building

```bash
./gradlew -p core test            # pure-JVM business logic (no Android SDK needed)
./gradlew :app:testDebugUnitTest  # Robolectric tests
./gradlew :app:assembleRelease    # APK (debug-signed; distribution is GitHub releases only)
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
