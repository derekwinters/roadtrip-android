# roadtrip-android

Android app (phones + tablets) for the Family Road Trip project. Kotlin + Jetpack Compose,
Material 3 adaptive layouts, Room offline queue, WorkManager sync, osmdroid map. Talks to
`roadtrip-backend` over the car-hotspot VPN; must stay fully usable offline.

## Development methodology (mandatory)

Spec-driven + test-driven, via the `roadtrip-dev` agent workflow in
`.claude/agents/roadtrip-dev.md`:

1. **Spec first** — update `docs/spec/*.md`. Client behavior references backend requirement IDs
   where relevant; Android-specific requirements use the `AND-*` areas.
2. **Tests second** — failing JVM/Robolectric tests referencing requirement IDs.
3. **Implement** — keep logic in plain Kotlin (view models/use cases/repositories) so it tests
   on the JVM; Compose UI stays thin.
4. **Validate** — `./scripts/validate-specs.sh` and `./gradlew test` must pass.

## Commands

- `./gradlew -p core test` — core JVM unit tests (local TDD loop); `./gradlew :app:testDebugUnitTest` — app Robolectric tests
- `./gradlew :app:assembleDebug` / `:app:assembleRelease` — build APKs
- `./gradlew lint` — Android lint
- `./scripts/validate-specs.sh` — spec/requirement/docs validation (also runs in CI)

## Conventions

- Conventional Commits (release-please manages versioning; `versionName` comes from
  `version.txt`, `versionCode` is derived from it).
- One APK serves phone and tablet — layouts adapt via `WindowSizeClass`.
- Location tracking runs **only** when the signed-in profile is a parent on a phone.
- Requirement areas: AND (app-wide), ANDSYNC, ANDLOC, ANDMAP, ANDJRNL, ANDGAME, ANDNOTIF, ANDSET.
