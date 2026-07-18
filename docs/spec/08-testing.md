# 08 — Testing Strategy & Release Engineering

## Test layers

1. **core module JVM tests** (the TDD center): sync engine (outbox/inbox/cursors), state
   reducers (map, lobby, journal, notifications), client-side game replay, DTO
   serialization against recorded backend fixtures, validation logic. Run with
   `./gradlew :core:test` — no Android SDK required.
2. **app module unit tests** (Robolectric): view models wired to fakes, Room DAO tests,
   notification mapping, deep-link routing. `./gradlew :app:testDebugUnitTest`.
3. **Contract fixtures**: JSON fixtures under `core/src/test/resources/fixtures/` mirror
   backend responses; regenerated from the backend's seed/demo data so client and server
   cannot drift silently.
4. **Offline/sync simulation**: fake clock + fake server harness in `core` reproduces the
   design doc's offline scenarios (queue while offline, flush, duplicate-free retry,
   mixed-device interleaving).
5. **Dry run** (manual): pre-trip drive with phone tracking through hotspot+VPN, tablets
   following, one game played en route.

Every `auto` requirement must be referenced by a test (`[ANDSYNC-003]` in the test name or a
`// covers:` comment) — enforced by `./scripts/validate-specs.sh` in CI.

## Release engineering

Mirrors backend spec `11-release-engineering.md`: release-please (`simple` strategy,
`version.txt` → `versionName`, derived `versionCode`); PR CI uploads debug+release APKs as
artifacts; open-release-PR builds publish `-rc` prerelease APKs; releases get final APKs
attached to the notes. No store publishing.

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDREL-001 | `versionCode = major*10000 + minor*100 + patch` derived from `version.txt`; monotonically increases for every release-please version bump. | auto |
| ANDREL-002 | CI (PR): `:core:test`, `:app:testDebugUnitTest`, lint, spec validator, `assembleDebug` + `assembleRelease`, APKs uploaded as artifacts. | manual |
| ANDREL-003 | RC prereleases and final releases attach APKs to GitHub releases per the backend REL spec. | manual |
