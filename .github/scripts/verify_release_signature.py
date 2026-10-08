#!/usr/bin/env python3
"""Release gate: every published release APK carries the pinned release certificate (ANDREL-005, #185).

**Why this exists.** Until #185 every roadtrip-android release was signed with a
random debug key generated fresh on the CI runner, so no release APK could be
installed over the previous one. Android's only remedy is uninstall-then-
reinstall, which wipes the Room database, including the unsynced outbox.

**The invariant this enforces.** *A published release APK is signed by the
release certificate pinned in the `ANDROID_KEYSTORE_SHA256` secret, and by no
other key.* The failure it is built for is the quiet one: if a signing input is
mis-wired or a secret renamed, Gradle does not fail when release signing is not
required — it falls back to the debug key and produces a perfectly good APK that
can never be upgraded in place. Nothing but the certificate distinguishes it.

Run against each just-built release APK *before* anything is uploaded, so a
build that lost the release key fails the release job instead of reaching the
release page.

Ported from lucas-doggiehood's gate (its #630/#752), adapted to read the pinned
fingerprint from the environment instead of a committed file.

The decisions are pure functions so they unit-test without an Android SDK:
`normalize_fingerprint`, `parse_apksigner_certs`, `assess` and
`expected_fingerprint_from_env`. `main` wires them to apksigner.
"""

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
from collections import namedtuple

SignerFacts = namedtuple("SignerFacts", "index dn sha256")
Verdict = namedtuple("Verdict", "ok reasons")

# The default Android debug certificate's subject. Gradle signs with this key
# whenever release signing falls back to the debug config, so it is the
# signature of the exact mis-wiring this gate exists to catch — worth naming in
# the error rather than reporting as an anonymous fingerprint mismatch.
ANDROID_DEBUG_DN_MARKER = "cn=android debug"

# The repository Actions secret holding the release certificate's SHA-256.
EXPECTED_FINGERPRINT_ENV = "ANDROID_KEYSTORE_SHA256"

_SHA256_HEX_DIGITS = 64

# apksigner prints "Signer #1 certificate DN: ..." and, when signers differ per
# SDK range, "Signer (minSdkVersion=24, maxSdkVersion=32) #1 certificate DN:".
# Both shapes carry the signer index, which is what identifies the block.
_SIGNER_LINE = re.compile(
    r"^Signer\s*(?:\([^)]*\)\s*)?#(\d+)\s+certificate\s+(DN|SHA-256 digest):\s*(.*)$")
_SIGNER_COUNT_LINE = re.compile(r"^Number of signers:\s*(\d+)\s*$")

_FINGERPRINT_LABEL = re.compile(r"^\s*SHA-?256\s*:", re.IGNORECASE)


class MalformedFingerprint(ValueError):
    """A certificate fingerprint that is not 32 hex-encoded bytes."""


class MalformedApksignerOutput(ValueError):
    """apksigner output this parser cannot read with confidence."""


# --- Pure decisions ---------------------------------------------------------


def normalize_fingerprint(text):
    """A SHA-256 certificate fingerprint as 64 lowercase hex digits.

    keytool prints `SHA256: B7:C7:...` and apksigner prints bare lowercase hex,
    but both are SHA-256 over the DER-encoded certificate — the same 32 bytes
    written two ways. Normalizing here is what makes either form safe to store
    in the `ANDROID_KEYSTORE_SHA256` secret.
    """
    if text is None:
        raise MalformedFingerprint("no fingerprint given")

    bare = _FINGERPRINT_LABEL.sub("", text)
    bare = re.sub(r"[\s:]", "", bare).lower()

    if not bare:
        raise MalformedFingerprint("no fingerprint given")
    if len(bare) != _SHA256_HEX_DIGITS:
        raise MalformedFingerprint(
            "expected {0} hex digits for a SHA-256 fingerprint, got {1}: {2!r}".format(
                _SHA256_HEX_DIGITS, len(bare), text.strip()))
    if not re.fullmatch(r"[0-9a-f]+", bare):
        raise MalformedFingerprint(
            "fingerprint is not hexadecimal: {0!r}".format(text.strip()))
    return bare


def parse_apksigner_certs(text):
    """The signers described by `apksigner verify --print-certs --verbose`.

    The `--verbose` half of that command line is load-bearing here: it is what
    makes apksigner emit the `Number of signers:` header this parser requires
    (lucas-doggiehood #752). Read `print_certs` before changing either.

    Raises `MalformedApksignerOutput` when the declared signer count disagrees
    with the blocks actually parsed. That check is the point: this parser reads
    a human-readable format that could change under us, and a silent "no
    signers found" would read as an unsigned APK — a confusing failure — while
    a silently *short* list could let an unexamined signer through.
    """
    declared = None
    by_index = {}

    for line in text.splitlines():
        count_match = _SIGNER_COUNT_LINE.match(line.strip())
        if count_match and declared is None:
            declared = int(count_match.group(1))
            continue

        signer_match = _SIGNER_LINE.match(line.strip())
        if not signer_match:
            continue

        index = int(signer_match.group(1))
        field = signer_match.group(2)
        value = signer_match.group(3).strip()
        signer = by_index.setdefault(index, {"dn": "", "sha256": ""})

        if field == "DN":
            signer["dn"] = value
        else:
            digest = normalize_fingerprint(value)
            if signer["sha256"] and signer["sha256"] != digest:
                raise MalformedApksignerOutput(
                    "signer #{0} is reported with two different certificates, "
                    "{1} and {2}".format(index, signer["sha256"], digest))
            signer["sha256"] = digest

    if declared is None:
        raise MalformedApksignerOutput(
            "apksigner output has no 'Number of signers:' line — the format this "
            "gate reads has changed, so its verdict cannot be trusted")

    if declared != len(by_index):
        raise MalformedApksignerOutput(
            "apksigner reported {0} signer(s) but {1} could be parsed — the format "
            "this gate reads has changed".format(declared, len(by_index)))

    return [
        SignerFacts(index=index, dn=by_index[index]["dn"], sha256=by_index[index]["sha256"])
        for index in sorted(by_index)
    ]


def assess(signers, expected_sha256):
    """Whether these signers are exactly the pinned release certificate."""
    expected = normalize_fingerprint(expected_sha256)
    reasons = []

    if not signers:
        reasons.append(
            "the APK is unsigned — a release asset must carry the release "
            "certificate {0}".format(expected))
        return Verdict(ok=False, reasons=reasons)

    if len(signers) != 1:
        reasons.append(
            "the APK has {0} signers; a release asset is signed by the release key "
            "alone".format(len(signers)))

    for signer in signers:
        if signer.sha256 == expected:
            continue
        if ANDROID_DEBUG_DN_MARKER in signer.dn.lower():
            reasons.append(
                "signer #{0} is the Android debug certificate ({1}) — the build fell "
                "back to debug signing, so this APK can never be installed over a "
                "release build. The keystore inputs did not reach Gradle.".format(
                    signer.index, signer.dn))
        else:
            reasons.append(
                "signer #{0} certificate is {1}, expected the release certificate "
                "{2} (DN: {3})".format(signer.index, signer.sha256, expected, signer.dn))

    return Verdict(ok=not reasons, reasons=reasons)


def expected_fingerprint_from_env(environ=None):
    """The pinned release fingerprint from `ANDROID_KEYSTORE_SHA256`, normalized.

    A missing or blank value is an error, never a pass: without a pin there is
    nothing to compare the APK's certificate against.
    """
    env = os.environ if environ is None else environ
    value = env.get(EXPECTED_FINGERPRINT_ENV, "")
    if not value.strip():
        raise MalformedFingerprint(
            "{0} is not set — the release certificate fingerprint is required to "
            "verify release APKs".format(EXPECTED_FINGERPRINT_ENV))
    return normalize_fingerprint(value)


# --- Wiring -----------------------------------------------------------------


def find_apksigner():
    """apksigner from PATH, else the newest build-tools copy in the SDK."""
    on_path = shutil.which("apksigner")
    if on_path:
        return on_path

    for root in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if not root:
            continue
        candidates = sorted(glob.glob(os.path.join(root, "build-tools", "*", "apksigner")))
        if candidates:
            return candidates[-1]

    raise OSError(
        "apksigner not found on PATH or in ANDROID_HOME/ANDROID_SDK_ROOT build-tools")


def print_certs(apk, apksigner=None):
    """`apksigner verify --print-certs --verbose` output for one APK.

    `--verbose` is not decoration: apksigner prints the `Number of signers: N`
    header (along with `Verifies` and the `Verified using vN scheme` lines)
    only in verbose mode, while `--print-certs` alone emits the signer DN and
    SHA-256 blocks and nothing else. `parse_apksigner_certs` cross-checks that
    header against the blocks it parsed, so without the flag every real APK is
    rejected as malformed output — which is exactly what happened to
    lucas-doggiehood's v0.16.0 release, shipped with no assets at all (#752).
    """
    tool = apksigner or find_apksigner()
    result = subprocess.run(
        [tool, "verify", "--print-certs", "--verbose", apk],
        capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise MalformedApksignerOutput(
            "apksigner could not verify {0} (exit {1}): {2}".format(
                os.path.basename(apk), result.returncode,
                (result.stderr or result.stdout).strip()))
    return result.stdout


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk", nargs="+", help="release APK(s) to check")
    parser.add_argument(
        "--expected", default=None,
        help="the pinned fingerprint itself, overriding the {0} env var".format(
            EXPECTED_FINGERPRINT_ENV))
    args = parser.parse_args(argv)

    try:
        expected = (
            normalize_fingerprint(args.expected) if args.expected
            else expected_fingerprint_from_env())
    except (MalformedFingerprint, OSError) as exc:
        print("::error title=Release signature::{0}".format(exc))
        return 1

    failed = False
    for apk in args.apk:
        name = os.path.basename(apk)
        try:
            signers = parse_apksigner_certs(print_certs(apk))
        except (MalformedApksignerOutput, MalformedFingerprint, OSError) as exc:
            print("::error title=Release signature::{0}: {1}".format(name, exc))
            failed = True
            continue

        for signer in signers:
            print("{0}: signer #{1} {2}\n  DN: {3}".format(
                name, signer.index, signer.sha256, signer.dn))

        verdict = assess(signers, expected)
        if verdict.ok:
            print("OK: {0} is signed by the release certificate.".format(name))
            continue

        failed = True
        for reason in verdict.reasons:
            print("::error title=Release signature::{0}: {1}".format(name, reason))

    if failed:
        print(
            "\nA release asset is not signed by the pinned release certificate — "
            "refusing to publish it. Installing it would force an uninstall on the "
            "next upgrade, wiping the Room database and its unsynced outbox. See "
            "docs/spec/08-testing.md (release signing, ANDREL-005).")
        return 1

    print("\nOK: every release asset carries the pinned release certificate.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
