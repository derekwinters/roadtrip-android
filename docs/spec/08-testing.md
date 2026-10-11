# 08 — Testing Strategy & Release Engineering

## Test layers

1. **core module JVM tests** (the TDD center): sync engine (outbox/inbox/cursors), state
   reducers (map, lobby, journal, notifications), client-side game replay, DTO
   serialization against recorded backend fixtures, validation logic. Run with
   `./gradlew -p core test` — no Android SDK required (core is a standalone composite build).
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
attached to the notes. No store publishing. RC prereleases are published under opaque
`rc-<run>` git tags that release-please cannot mistake for shipped versions; the
human-readable `v<next>-rc.<run>` appears only in release titles and APK filenames, and
final releases are the only SemVer tags in the repo.

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDREL-001 | `versionCode = major*10000 + minor*100 + patch` derived from `version.txt`, which must be a plain `MAJOR.MINOR.PATCH` triple; SemVer prerelease/build suffixes (e.g. `1.2.1-rc.4`) are rejected at build time with an actionable error. Monotonically increases for every release-please version bump. | auto |
| ANDREL-002 | CI (PR): `:core:test`, `:app:testDebugUnitTest`, lint, spec validator, `assembleDebug` + `assembleRelease`, APKs uploaded as artifacts. | manual |
| ANDREL-003 | RC prereleases and final releases attach APKs to GitHub releases per the backend REL spec. | manual |
| ANDREL-004 | RC prereleases use git tags release-please cannot parse as release versions (opaque `rc-<run_number>`), isolating its version stream from RC publishing; the human-readable `v<version>-rc.<run>` appears only in the release title and APK filenames; final releases remain the only SemVer tags; on a final release, stale `rc-*` prereleases and their tags are pruned. | manual |
