"""Workflow script-injection guard for the release pipeline (#193).

covers: ANDREL-009 (no `run:` script interpolates PR/commit-controlled text)

Release run 38170101789 died in "Determine version" with
`syntax error near unexpected token '('`: the step inlined release-please's PR
JSON into a single-quoted shell string (`echo '${{ ...outputs.pr }}'`), and the
release PR body had gained an apostrophe from a commit title ("...reads CI's
apksigner"). Actions substitutes `${{ }}` into the script text *before* bash
parses it, so any quote in PR or commit text breaks the whole step -- and any
`$(...)` in it would run on the release runner, next to the signing secrets.

The rule these tests pin: free-text values reach a `run:` script only through
the step's `env:` and are read as quoted shell variables. Harmless expressions
(run_number, sha, step outputs this workflow computes itself) are not
policed here.
"""

import os
import re
import subprocess
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
WORKFLOWS = os.path.join(REPO_ROOT, ".github", "workflows")

# Expressions whose values carry PR, issue, comment or commit text (or JSON
# containing it). Any of these inside a `run:` script is an injection hole.
FREE_TEXT_EXPRESSIONS = re.compile(
    r"\$\{\{[^}]*("
    r"needs\.release-please\.outputs\.(pr|tag_name|release_created)"
    r"|steps\.rp\.outputs\."
    r"|github\.event\.(pull_request|issue|comment|review|head_commit|commits|discussion)"
    r"|github\.head_ref"
    r")[^}]*\}\}"
)


def _read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def _run_scripts(text):
    """Yield (step_name, script) for every `run:` in a workflow file.

    Stdlib only (no PyYAML on a bare runner): a `run:` is either inline
    (`run: cmd`) or a block scalar whose lines are indented deeper than the key.
    """
    lines = text.splitlines()
    step_name = None
    for index, line in enumerate(lines):
        named = re.match(r"\s*- name:\s*(.+)$", line)
        if named:
            step_name = named.group(1).strip()
        match = re.match(r"(\s*)(?:- )?run:\s*(.*)$", line)
        if not match:
            continue
        indent = len(match.group(1))
        rest = match.group(2)
        if rest and rest[0] not in "|>":
            yield step_name, rest
            continue
        body = []
        for following in lines[index + 1:]:
            if following.strip() and len(following) - len(following.lstrip()) <= indent:
                break
            body.append(following)
        yield step_name, "\n".join(body)


def _step_script(text, step_name):
    for name, script in _run_scripts(text):
        if name == step_name:
            return script
    return None


class NoFreeTextInRunScriptsTests(unittest.TestCase):
    """[ANDREL-009] PR/commit-controlled text never lands in shell source."""

    def test_the_run_script_parser_sees_release_yml_steps(self):
        names = [name for name, _ in _run_scripts(_read(os.path.join(WORKFLOWS, "release.yml")))]
        self.assertIn("Determine version", names)

    def test_no_workflow_run_script_interpolates_free_text(self):
        for filename in sorted(os.listdir(WORKFLOWS)):
            if not filename.endswith((".yml", ".yaml")):
                continue
            text = _read(os.path.join(WORKFLOWS, filename))
            for step_name, script in _run_scripts(text):
                with self.subTest(workflow=filename, step=step_name):
                    found = FREE_TEXT_EXPRESSIONS.search(script)
                    self.assertIsNone(
                        found,
                        "{0} step '{1}' interpolates {2} into its run script; pass it "
                        "through the step's env: and read it as a quoted shell "
                        "variable".format(filename, step_name, found and found.group(0)))


class DetermineVersionStepTests(unittest.TestCase):
    """[ANDREL-009] The version step survives hostile release-PR text."""

    STEP = "Determine version"

    def setUp(self):
        self.script = _step_script(_read(os.path.join(WORKFLOWS, "release.yml")), self.STEP)
        self.assertIsNotNone(self.script, "release.yml has no '{0}' run step".format(self.STEP))
        self.assertNotIn("${{", self.script,
                         "the version script must read only shell variables")

    def _run(self, **env):
        with tempfile.TemporaryDirectory() as scratch:
            output_path = os.path.join(scratch, "github_output")
            open(output_path, "w").close()
            environ = {
                "PATH": os.environ.get("PATH", ""),
                "GITHUB_OUTPUT": output_path,
                "GITHUB_RUN_NUMBER": "42",
                "RP_RELEASE_CREATED": "",
                "RP_TAG_NAME": "",
                "RP_PR": "",
            }
            environ.update(env)
            result = subprocess.run(
                # The shell Actions uses for `run:` on Linux.
                ["bash", "--noprofile", "--norc", "-eo", "pipefail", "-c", self.script],
                env=environ, cwd=scratch,
                capture_output=True, text=True)
            outputs = dict(
                line.split("=", 1) for line in _read(output_path).splitlines() if "=" in line)
            return result, outputs

    def test_an_rc_survives_quotes_and_command_substitution_in_the_pr(self):
        # The #196 body that broke the release, plus a payload that would execute.
        pr = (
            '{"number":196,"title":"chore(main): release 1.2.1",'
            '"body":"fix(release): a gate that reads CI\'s apksigner (#197) $(touch pwned) `touch pwned2`"}'
        )
        result, outputs = self._run(RP_PR=pr)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(outputs.get("kind"), "rc")
        self.assertEqual(outputs.get("version"), "v1.2.1-rc.42")
        self.assertEqual(outputs.get("tag"), "rc-42")

    def test_the_pr_text_is_never_executed(self):
        with tempfile.TemporaryDirectory() as scratch:
            marker = os.path.join(scratch, "pwned")
            pr = '{"title":"chore(main): release 1.2.1","body":"$(touch %s)\'"}' % marker
            result, _ = self._run(RP_PR=pr)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse(os.path.exists(marker), "PR text was executed as shell")

    def test_a_final_release_uses_the_release_please_tag(self):
        result, outputs = self._run(RP_RELEASE_CREATED="true", RP_TAG_NAME="v1.2.1")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(outputs, {"kind": "final", "version": "v1.2.1", "tag": "v1.2.1"})

    def test_an_rc_without_a_version_in_the_pr_title_fails_the_job(self):
        result, outputs = self._run(RP_PR='{"title":"chore(main): release"}')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("version", outputs)


if __name__ == "__main__":
    unittest.main()
