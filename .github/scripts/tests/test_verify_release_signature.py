"""Unit tests for the release-signature gate (#185).

covers: ANDREL-005 (a published release APK is signed by the pinned release
certificate and no other key; the release job fails closed before any upload)
covers: ANDREL-006 (PR builds never reach the release key; Gradle falls back to
debug signing only when release signing is not required)

Every roadtrip-android release up to #185 was signed with a fresh random debug
key generated on the CI runner, so no release could be installed over the
previous one. Android's only remedy is uninstall-then-reinstall, which wipes the
Room database — including the unsynced outbox. `verify_release_signature.py` is
the gate that makes publishing such an APK impossible: the release certificate's
SHA-256 is pinned in the `ANDROID_KEYSTORE_SHA256` Actions secret, and any
release APK signed by a different key — including a silent fallback to the debug
key, which is exactly what mis-wired signing inputs produce — fails the release
job before anything is uploaded.

These tests pin the pure pieces (`normalize_fingerprint`,
`parse_apksigner_certs`, `assess`, `expected_fingerprint_from_env`) and the
*wiring*, because the gate is worthless if the keystore never reaches Gradle:
`release.yml` must check the secrets, hand them to the build, verify before
publishing, and delete the decoded keystore; `pr.yml` must never reference the
keystore secrets at all; and `app/build.gradle.kts` must fail rather than fall
back when CI requires release signing.

The apksigner transcripts under `fixtures/` are **captured**, not authored: the
verbatim stdout of apksigner 31.0.2 run over a real signed APK, with and without
`--verbose` (ported from lucas-doggiehood, where hand-authored fixtures once
certified a gate that could not read a single real APK — its #752). The
throwaway key in that capture is deliberately NOT this repo's release key; the
fixtures pin apksigner's output *shape*.
"""

import os
import re
import subprocess
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from verify_release_signature import (  # noqa: E402
    EXPECTED_FINGERPRINT_ENV,
    MalformedApksignerOutput,
    MalformedFingerprint,
    SignerFacts,
    assess,
    expected_fingerprint_from_env,
    normalize_fingerprint,
    parse_apksigner_certs,
    print_certs,
)

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURES = os.path.join(HERE, "fixtures")
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))


def _read(*parts):
    with open(os.path.join(*parts), encoding="utf-8") as handle:
        return handle.read()


CAPTURED_VERBOSE_OUTPUT = _read(FIXTURES, "apksigner-verify-print-certs-verbose.txt")
CAPTURED_NON_VERBOSE_OUTPUT = _read(FIXTURES, "apksigner-verify-print-certs-nonverbose.txt")

CAPTURED_SHA256 = "10d315258da7c4c4830814ae6f876e84145f2195cfb077bc0bb04e3df0a61ed8"
CAPTURED_KEYTOOL_FORM = (
    "10:D3:15:25:8D:A7:C4:C4:83:08:14:AE:6F:87:6E:84:"
    "14:5F:21:95:CF:B0:77:BC:0B:B0:4E:3D:F0:A6:1E:D8")
CAPTURED_DN = "CN=Doggiehood Release, O=Derek Winters, L=Somewhere, C=US"

OTHER_SHA256 = "0123456789abcdef" * 4

DEBUG_OUTPUT = """Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
Signer #1 certificate SHA-256 digest: {0}
""".format(OTHER_SHA256)

# Hand-authored: a two-key APK is not something this repo can produce on demand.
TWO_SIGNER_OUTPUT = """Verifies
Number of signers: 2
Signer #1 certificate DN: CN=Road Trip, O=Derek Winters, C=US
Signer #1 certificate SHA-256 digest: {0}
Signer #2 certificate DN: CN=Somebody Else, O=Elsewhere, C=US
Signer #2 certificate SHA-256 digest: {1}
""".format(CAPTURED_SHA256, OTHER_SHA256)

KEYSTORE_SECRETS = (
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEYSTORE_SHA256",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_ALIAS_PASSWORD",
)


class NormalizeFingerprintTests(unittest.TestCase):
    """[ANDREL-005] keytool and apksigner print the same 32 bytes differently."""

    def test_accepts_the_keytool_colon_form(self):
        self.assertEqual(normalize_fingerprint(CAPTURED_KEYTOOL_FORM), CAPTURED_SHA256)

    def test_accepts_apksigners_bare_hex(self):
        self.assertEqual(normalize_fingerprint(CAPTURED_SHA256), CAPTURED_SHA256)

    def test_accepts_a_leading_sha256_label_and_surrounding_whitespace(self):
        labelled = "  SHA256: {0}\n".format(CAPTURED_KEYTOOL_FORM)
        self.assertEqual(normalize_fingerprint(labelled), CAPTURED_SHA256)

    def test_rejects_a_fingerprint_of_the_wrong_length(self):
        with self.assertRaises(MalformedFingerprint):
            normalize_fingerprint("B7:C7:99:EA")

    def test_rejects_a_fingerprint_that_is_not_hex(self):
        with self.assertRaises(MalformedFingerprint):
            normalize_fingerprint("zz" + CAPTURED_SHA256[2:])

    def test_rejects_an_empty_fingerprint(self):
        with self.assertRaises(MalformedFingerprint):
            normalize_fingerprint("   \n")


class ExpectedFingerprintFromEnvTests(unittest.TestCase):
    """[ANDREL-005] The pinned certificate comes from the ANDROID_KEYSTORE_SHA256 secret."""

    def test_the_env_var_name_is_the_repository_secret_name(self):
        self.assertEqual(EXPECTED_FINGERPRINT_ENV, "ANDROID_KEYSTORE_SHA256")

    def test_reads_and_normalizes_the_keytool_form(self):
        env = {"ANDROID_KEYSTORE_SHA256": CAPTURED_KEYTOOL_FORM}
        self.assertEqual(expected_fingerprint_from_env(env), CAPTURED_SHA256)

    def test_a_missing_secret_is_an_error_not_a_pass(self):
        with self.assertRaises(MalformedFingerprint) as raised:
            expected_fingerprint_from_env({})
        self.assertIn("ANDROID_KEYSTORE_SHA256", str(raised.exception))

    def test_a_blank_secret_is_an_error(self):
        with self.assertRaises(MalformedFingerprint):
            expected_fingerprint_from_env({"ANDROID_KEYSTORE_SHA256": "  "})


class ParseApksignerCertsTests(unittest.TestCase):
    """[ANDREL-005] apksigner output -> signers, against a real capture."""

    def test_reads_the_dn_and_digest_of_a_single_signer_from_a_real_capture(self):
        signers = parse_apksigner_certs(CAPTURED_VERBOSE_OUTPUT)
        self.assertEqual(len(signers), 1)
        self.assertEqual(signers[0].index, 1)
        self.assertEqual(signers[0].dn, CAPTURED_DN)
        self.assertEqual(signers[0].sha256, CAPTURED_SHA256)

    def test_reads_every_signer(self):
        signers = parse_apksigner_certs(TWO_SIGNER_OUTPUT)
        self.assertEqual([s.index for s in signers], [1, 2])
        self.assertEqual([s.sha256 for s in signers], [CAPTURED_SHA256, OTHER_SHA256])

    def test_uppercase_digests_are_normalized(self):
        signers = parse_apksigner_certs(
            CAPTURED_VERBOSE_OUTPUT.replace(CAPTURED_SHA256, CAPTURED_SHA256.upper()))
        self.assertEqual(signers[0].sha256, CAPTURED_SHA256)

    def test_reports_no_signers_when_apksigner_found_none(self):
        self.assertEqual(parse_apksigner_certs("Number of signers: 0\n"), [])

    def test_a_signer_count_that_disagrees_with_the_blocks_raises(self):
        truncated = TWO_SIGNER_OUTPUT.replace(
            "Signer #2 certificate DN: CN=Somebody Else, O=Elsewhere, C=US\n", "")
        truncated = truncated.replace(
            "Signer #2 certificate SHA-256 digest: {0}\n".format(OTHER_SHA256), "")
        with self.assertRaises(MalformedApksignerOutput):
            parse_apksigner_certs(truncated)

    def test_output_without_a_signer_count_raises(self):
        with self.assertRaises(MalformedApksignerOutput):
            parse_apksigner_certs("Verifies\n")

    def test_real_non_verbose_output_is_rejected(self):
        """Without --verbose apksigner omits 'Number of signers:'; stay strict."""
        self.assertNotIn("Number of signers:", CAPTURED_NON_VERBOSE_OUTPUT)
        self.assertIn("certificate SHA-256 digest:", CAPTURED_NON_VERBOSE_OUTPUT)
        with self.assertRaises(MalformedApksignerOutput) as raised:
            parse_apksigner_certs(CAPTURED_NON_VERBOSE_OUTPUT)
        self.assertIn("Number of signers:", str(raised.exception))

    def test_the_verbose_only_extra_lines_do_not_confuse_the_parser(self):
        self.assertIn("Signer #1 public key SHA-256 digest:", CAPTURED_VERBOSE_OUTPUT)
        signers = parse_apksigner_certs(CAPTURED_VERBOSE_OUTPUT)
        self.assertEqual(len(signers), 1)
        self.assertEqual(signers[0].sha256, CAPTURED_SHA256)


class ApksignerInvocationTests(unittest.TestCase):
    """[ANDREL-005] The command line the gate runs produces the format it parses."""

    def _run_print_certs(self):
        completed = subprocess.CompletedProcess(
            args=[], returncode=0, stdout=CAPTURED_VERBOSE_OUTPUT, stderr="")
        with mock.patch("verify_release_signature.subprocess.run",
                        return_value=completed) as run:
            print_certs("roadtrip-v9.9.9.apk", apksigner="/usr/bin/apksigner")
        self.assertEqual(run.call_count, 1)
        return list(run.call_args[0][0])

    def test_the_invocation_asks_for_the_verbose_output_the_parser_reads(self):
        self.assertIn("--verbose", self._run_print_certs())

    def test_the_invocation_verifies_and_prints_certs_for_the_apk(self):
        argv = self._run_print_certs()
        self.assertEqual(argv[0], "/usr/bin/apksigner")
        self.assertIn("verify", argv)
        self.assertIn("--print-certs", argv)
        self.assertEqual(argv[-1], "roadtrip-v9.9.9.apk")

    def test_a_failed_verification_raises(self):
        completed = subprocess.CompletedProcess(
            args=[], returncode=1, stdout="", stderr="DOES NOT VERIFY")
        with mock.patch("verify_release_signature.subprocess.run", return_value=completed):
            with self.assertRaises(MalformedApksignerOutput):
                print_certs("roadtrip-v9.9.9.apk", apksigner="/usr/bin/apksigner")


class AssessTests(unittest.TestCase):
    """[ANDREL-005] The pass/fail call."""

    def test_the_expected_certificate_passes(self):
        verdict = assess(parse_apksigner_certs(CAPTURED_VERBOSE_OUTPUT), CAPTURED_SHA256)
        self.assertTrue(verdict.ok, verdict.reasons)
        self.assertEqual(verdict.reasons, [])

    def test_a_different_certificate_fails_and_names_both_fingerprints(self):
        signers = [SignerFacts(index=1, dn="CN=Somebody Else", sha256=OTHER_SHA256)]
        verdict = assess(signers, CAPTURED_SHA256)
        self.assertFalse(verdict.ok)
        joined = " ".join(verdict.reasons)
        self.assertIn(OTHER_SHA256, joined)
        self.assertIn(CAPTURED_SHA256, joined)

    def test_the_android_debug_certificate_fails_as_a_debug_fallback(self):
        verdict = assess(parse_apksigner_certs(DEBUG_OUTPUT), CAPTURED_SHA256)
        self.assertFalse(verdict.ok)
        self.assertTrue(
            any("debug signing" in r.lower() for r in verdict.reasons),
            "a debug-signed release APK must be reported as a debug fallback: {0}".format(
                verdict.reasons))

    def test_an_unsigned_apk_fails(self):
        verdict = assess([], CAPTURED_SHA256)
        self.assertFalse(verdict.ok)
        self.assertTrue(any("unsigned" in r.lower() for r in verdict.reasons))

    def test_an_extra_signer_fails_even_when_the_release_key_is_present(self):
        verdict = assess(parse_apksigner_certs(TWO_SIGNER_OUTPUT), CAPTURED_SHA256)
        self.assertFalse(verdict.ok)

    def test_the_expected_fingerprint_may_be_given_in_keytool_form(self):
        verdict = assess(parse_apksigner_certs(CAPTURED_VERBOSE_OUTPUT), CAPTURED_KEYTOOL_FORM)
        self.assertTrue(verdict.ok, verdict.reasons)


def _step_body(text, step_name):
    """The YAML lines of one named step, up to the next step at its indent."""
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.strip() == "- name: {0}".format(step_name):
            indent = len(line) - len(line.lstrip())
            body = [line]
            for following in lines[index + 1:]:
                stripped = following.lstrip()
                if stripped.startswith("- ") and len(following) - len(stripped) == indent:
                    break
                body.append(following)
            return "\n".join(body)
    return None


class ReleaseWorkflowSigningTests(unittest.TestCase):
    """[ANDREL-005] release.yml signs, fails closed, verifies before publishing."""

    CHECK_STEP = "Check release keystore secrets"
    DECODE_STEP = "Decode release keystore"
    BUILD_STEP = "Build APKs"
    VERIFY_STEP = "Verify the release signature"
    DELETE_STEP = "Delete decoded release keystore"

    def setUp(self):
        self.text = _read(REPO_ROOT, ".github", "workflows", "release.yml")

    def _step(self, name):
        body = _step_body(self.text, name)
        self.assertIsNotNone(body, "release.yml has no '{0}' step".format(name))
        return body

    def test_the_secret_check_requires_all_five_secrets_and_fails_the_job(self):
        body = self._step(self.CHECK_STEP)
        for secret in KEYSTORE_SECRETS:
            with self.subTest(secret=secret):
                self.assertIn("secrets.{0}".format(secret), body)
        self.assertIn("exit 1", body, "a missing secret must fail the job, not skip it")
        self.assertNotIn("continue-on-error", body)

    def test_the_secret_check_runs_before_the_build(self):
        self.assertLess(self.text.index("- name: " + self.CHECK_STEP),
                        self.text.index("- name: " + self.BUILD_STEP))

    def test_the_keystore_is_decoded_from_the_base64_secret(self):
        body = self._step(self.DECODE_STEP)
        self.assertIn("secrets.ANDROID_KEYSTORE_BASE64", body)
        self.assertIn("base64", body)
        self.assertIn("runner.temp", body)

    def test_the_build_step_receives_every_signing_input_and_requires_signing(self):
        body = self._step(self.BUILD_STEP)
        for name in ("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD",
                     "ANDROID_KEY_ALIAS", "ANDROID_KEY_ALIAS_PASSWORD"):
            with self.subTest(env=name):
                self.assertRegex(body, r"\b{0}:".format(name))
        self.assertRegex(body, r"ROADTRIP_REQUIRE_RELEASE_SIGNING:\s*['\"]?true")
        self.assertIn(":app:assembleRelease", body)

    def test_the_signature_is_verified_against_the_pinned_secret_before_publishing(self):
        body = self._step(self.VERIFY_STEP)
        self.assertIn("secrets.ANDROID_KEYSTORE_SHA256", body)
        self.assertIn("verify_release_signature.py", body)
        verify_at = self.text.index("- name: " + self.VERIFY_STEP)
        for publish in ("gh release create", "gh release upload"):
            with self.subTest(publish=publish):
                self.assertLess(verify_at, self.text.index(publish),
                                "the signature must be verified before '{0}'".format(publish))

    def test_the_verify_step_checks_the_published_release_apk(self):
        body = self._step(self.VERIFY_STEP)
        self.assertIn("roadtrip-${{ steps.version.outputs.version }}.apk", body)

    def test_the_decoded_keystore_is_always_deleted(self):
        body = self._step(self.DELETE_STEP)
        self.assertRegex(body, r"if:\s*always\(\)")
        self.assertIn("rm -f", body)

    def test_the_release_workflow_never_runs_on_pull_requests(self):
        triggers = self.text.split("jobs:", 1)[0]
        self.assertNotIn("pull_request", triggers)


class PullRequestBuildTests(unittest.TestCase):
    """[ANDREL-006] PR builds never reach the release key."""

    def test_pr_workflow_references_no_keystore_secret(self):
        text = _read(REPO_ROOT, ".github", "workflows", "pr.yml")
        for secret in KEYSTORE_SECRETS:
            with self.subTest(secret=secret):
                self.assertNotIn(secret, text)

    def test_pr_workflow_runs_the_release_gate_tests(self):
        text = _read(REPO_ROOT, ".github", "workflows", "pr.yml")
        self.assertIn("unittest discover -s .github/scripts/tests", text)


class GradleSigningConfigTests(unittest.TestCase):
    """[ANDREL-006] Gradle falls back to debug signing only when signing is not required."""

    def setUp(self):
        self.text = _read(REPO_ROOT, "app", "build.gradle.kts")

    def test_release_signing_inputs_come_from_env_or_keystore_properties(self):
        for name in ("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD",
                     "ANDROID_KEY_ALIAS", "ANDROID_KEY_ALIAS_PASSWORD",
                     "keystore.properties"):
            with self.subTest(input=name):
                self.assertIn('"{0}"'.format(name), self.text)

    def test_required_release_signing_cannot_fall_back(self):
        self.assertIn("ROADTRIP_REQUIRE_RELEASE_SIGNING", self.text)

    def test_keystore_material_is_git_ignored(self):
        ignored = _read(REPO_ROOT, ".gitignore").splitlines()
        for pattern in ("keystore.properties", "*.jks", "*.keystore"):
            with self.subTest(pattern=pattern):
                self.assertIn(pattern, ignored)


if __name__ == "__main__":
    unittest.main()
