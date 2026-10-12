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
`// covers:` comment) — enforced by `./scripts/validate-specs.sh` in CI. Release-pipeline
requirements are covered by the stdlib Python tests under `.github/scripts/tests/` (run with
`python3 -m unittest discover -s .github/scripts/tests`), which the validator counts too.

## Release engineering

Mirrors backend spec `11-release-engineering.md`: release-please (`simple` strategy,
`version.txt` → `versionName`, derived `versionCode`); PR CI uploads debug+release APKs as
artifacts; open-release-PR builds publish `-rc` prerelease APKs; releases get final APKs
attached to the notes. No store publishing. RC prereleases are published under opaque
`rc-<run>` git tags that release-please cannot mistake for shipped versions; the
human-readable `v<next>-rc.<run>` appears only in release titles and APK filenames, and
final releases are the only SemVer tags in the repo.

### Release signing and in-place upgrades

Android refuses to install an APK over an installed app whose signing certificate differs
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), and the only way past that is uninstalling — which
deletes the Room database, **including the unsynced outbox**. So every published release APK
(RC prereleases and final releases alike) is signed with one stable, owned release key.

**Invariant — a published release APK is signed by the pinned release certificate, and by no
other key.** The dangerous failure is a quiet success: a mis-wired keystore makes Gradle fall
back to debug signing and emit a perfectly valid APK that installs once and then blocks every
future upgrade. Three things enforce the invariant in `.github/workflows/release.yml` (which
runs only on `push` to `main`):

1. **Fail closed on missing secrets.** A "Check release keystore secrets" step fails the job
   (never skips) unless all five repository secrets are set: `ANDROID_KEYSTORE_BASE64`,
   `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEYSTORE_SHA256`, `ANDROID_KEY_ALIAS`,
   `ANDROID_KEY_ALIAS_PASSWORD`. The build step also sets `ROADTRIP_REQUIRE_RELEASE_SIGNING=true`,
   which makes Gradle itself refuse to configure without the release signing inputs.
2. **Signing inputs.** The keystore is decoded from `ANDROID_KEYSTORE_BASE64` into a runner temp
   file; `app/build.gradle.kts` reads `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
   `ANDROID_KEY_ALIAS` and `ANDROID_KEY_ALIAS_PASSWORD` from the environment (CI) or from a
   git-ignored `keystore.properties` at the repo root (local: `storeFile`, `storePassword`,
   `keyAlias`, `keyPassword`). The decoded keystore is deleted in an `if: always()` step.
3. **Pinned-certificate gate.** Before `gh release create`/`gh release upload`,
   `.github/scripts/verify_release_signature.py` runs `apksigner verify --print-certs --verbose`
   on the release APK and fails unless its only signer's SHA-256 equals `ANDROID_KEYSTORE_SHA256`
   (case and colons normalized, so keytool's `AB:CD:…` and apksigner's bare hex both work). A
   debug-key fallback is named explicitly in the error. The parser is tested against output
   captured from a real `apksigner` run with those exact flags — a gate that parses another
   tool's output is only as trustworthy as the realness of its fixtures.
4. **The parser meets the runner's real apksigner on every PR.** Fixtures go stale when the
   runner's build-tools move on (v1.2.0 shipped no APKs because the CI apksigner's signer lines
   no longer matched the 31.0.2 captures, #193). So PR CI runs the gate in `--parse-only` mode
   against the debug-signed `app-release.apk` it already builds: it prints the raw
   `apksigner verify --print-certs --verbose` output to the log and fails when the parser cannot
   read it (no `Number of signers:` header, or a declared count that differs from the signers
   parsed). Parse-only mode needs no secrets and checks no fingerprint. **Invariant:** the
   signer-count cross-check is never loosened to accept a new format — the parser learns the
   format from a verbatim capture of the runner's output, kept as a fixture beside the old ones.
   The runner's apksigner labels signers by scheme (`V2 Signer: certificate DN: …`, no `#N`), so
   the parser counts one signer per distinct certificate across the labelled blocks: one key
   listed under several schemes is one signer, and any second key is a second signer.
   Whenever the gate cannot parse apksigner's output (PR or release), it prints that raw output
   (public certificate data only) so the drift is diagnosable from the job log.

Without signing inputs (and without `ROADTRIP_REQUIRE_RELEASE_SIGNING`), `assembleRelease`
falls back to debug signing with a warning, so PR CI (ANDREL-002) and local builds keep
working; `pr.yml` never references the keystore secrets. The debug build type carries
`applicationIdSuffix = ".debug"` (and `versionNameSuffix = "-debug"`, label "Road Trip
(debug)"), so the randomly-signed debug APK attached to releases installs side-by-side with
the real app and can never collide with it.

**Workflow scripts never inline untrusted text** (ANDREL-009). Actions substitutes `${{ }}` into a `run:` script before bash parses it, so a release-please PR whose body quotes a commit title with an apostrophe broke "Determine version" (and `$(...)` there would have run beside the signing secrets, #193). **Invariant:** release-please outputs and PR/issue/comment/commit text are passed through a step's `env:` and read as quoted shell variables; only values the workflow controls (`github.run_number`, `github.sha`, its own computed step outputs) may be inlined. `.github/scripts/tests/test_release_workflow_injection.py` scans every workflow for violations and runs the version step under Actions' own bash flags against a hostile PR body.

**One-time transition.** The first stable-signed build is itself a signature change from the
earlier debug-signed installs, so each device needs one last uninstall/reinstall — make sure it
has fully synced (outbox empty) first. Every upgrade after that is in place. **The keystore has
no recovery:** for a sideloaded APK, losing it (or its passwords) means no future version can
ever install over the current one again, so keep an offline backup outside GitHub.

| ID | Requirement | Verify |
|----|-------------|--------|
| ANDREL-001 | `versionCode = major*10000 + minor*100 + patch` derived from `version.txt`, which must be a plain `MAJOR.MINOR.PATCH` triple; SemVer prerelease/build suffixes (e.g. `1.2.1-rc.4`) are rejected at build time with an actionable error. Monotonically increases for every release-please version bump. | auto |
| ANDREL-002 | CI (PR): `:core:test`, `:app:testDebugUnitTest`, lint, spec validator, `assembleDebug` + `assembleRelease`, APKs uploaded as artifacts. | manual |
| ANDREL-003 | RC prereleases and final releases attach APKs to GitHub releases per the backend REL spec. | manual |
| ANDREL-004 | RC prereleases use git tags release-please cannot parse as release versions (opaque `rc-<run_number>`), isolating its version stream from RC publishing; the human-readable `v<version>-rc.<run>` appears only in the release title and APK filenames; final releases remain the only SemVer tags; on a final release, stale `rc-*` prereleases and their tags are pruned. | manual |
| ANDREL-005 | Every APK published to an RC prerelease or final release is signed by the release certificate pinned in the `ANDROID_KEYSTORE_SHA256` secret and by no other key: the release job fails before any upload when a keystore secret is missing or the APK's signer fingerprint (normalized for case/colons) differs, naming a debug-key fallback explicitly; the decoded keystore is deleted afterwards. | auto |
| ANDREL-006 | PR builds never reach the release key: `pr.yml` references none of the keystore secrets, and `assembleRelease` without signing inputs falls back to debug signing unless `ROADTRIP_REQUIRE_RELEASE_SIGNING=true`, in which case the build fails. | auto |
| ANDREL-007 | The debug build installs side-by-side with release builds: debug `applicationId` is `com.roadtrip.app.debug` (suffix `.debug`). | auto |
| ANDREL-008 | PR CI runs the release-signature gate's parser (`--parse-only`: no secrets, no pinned fingerprint) against the runner's real `apksigner verify --print-certs --verbose` output for the debug-signed `app-release.apk`, printing that raw output and failing when the declared signer count differs from the signers parsed; the parser reads every captured apksigner format under `.github/scripts/tests/fixtures/`, and on any parse failure the gate prints the raw apksigner output. | auto |
| ANDREL-009 | No workflow `run:` script interpolates PR/commit-controlled text with `${{ }}`: release-please's outputs (`pr`, `tag_name`, `release_created`) and any `github.event` PR/issue/comment/commit text reach the shell only through the step's `env:` and are read as quoted variables, so quotes or `$(...)` in a release PR body neither break nor execute in the release job; an RC whose release-PR title carries no `MAJOR.MINOR.PATCH` fails instead of publishing an unversioned build. | auto |
