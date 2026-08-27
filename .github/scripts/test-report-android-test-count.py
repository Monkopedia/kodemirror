#!/usr/bin/env python3
"""Regression test for report-android-test-count.py's "did anything execute" gate (#282).

WHY THIS EXISTS AS A TEST AND NOT A COMMENT. A correct gate and a broken one print the
identical thing on a healthy suite, which is how the skip blind spot survived: the script
counted every <testcase> as executed, so an entirely @Ignore'd suite reported "N executed"
and exited 0 — passing the very check written to catch an inert suite. Only a fixture where
everything is skipped tells the two apart.

So the fixture table below is checked twice over. First against the real script, which must
pass every case. Then against a table of MUTANTS — the script with the gate deliberately
broken in each known way — every one of which must be caught by some case. A fixture that
proves one bug is gone is weaker than one that proves the class is rejected, and a
verification that cannot fail is this issue's exact shape.

Two traps that make a broken gate look fixed, both closed here:

  * The script globs for result XML. A glob that MISSES also exits non-zero, so a fixture the
    script never read shows the all-skipped case "failing" for entirely the wrong reason.
    Every case asserts the fixture path back out of the script's own printed file list before
    believing the exit status, and `glob-miss` below pins the two apart explicitly.
  * A fixture carrying only tests="N"/skipped="N" ATTRIBUTES and no <testcase> children reads
    as zero cases here, because this script sums elements rather than attributes. These
    fixtures mirror the shapes in urithiru/workflows/fixtures/ in <testcase> form; the
    attribute form does not drive this script at all.

Run: python3 .github/scripts/test-report-android-test-count.py
"""
import pathlib
import subprocess
import sys
import tempfile

SCRIPT = pathlib.Path(__file__).resolve().parent / "report-android-test-count.py"
RESULT_DIR = "view/build/outputs/androidTest-results/connected/debug"
FIXTURE = f"{RESULT_DIR}/TEST-fixture.xml"


def suite(name: str, cases: list[tuple[str, str]]) -> str:
    """A <testsuite> whose cases are (name, kind) with kind in {"pass", "skip", "fail"}."""
    body = {
        "pass": "<testcase classname='{cls}' name='{n}'/>",
        "skip": "<testcase classname='{cls}' name='{n}'><skipped/></testcase>",
        "fail": (
            "<testcase classname='{cls}' name='{n}'>"
            "<failure message='x'>x</failure></testcase>"
        ),
    }
    rows = "".join(body[kind].format(cls=name, n=n) for n, kind in cases)
    return f"<testsuite name='{name}'>{rows}</testsuite>"


ALL_SKIPPED = suite("AllIgnored", [(f"t{i}", "skip") for i in range(5)])

BOUNDARY = suite("MostlyIgnored", [("t0", "pass")] + [(f"t{i}", "skip") for i in range(1, 5)])

NESTED = (
    "<testsuites>"
    + suite("A", [(f"a{i}", "pass") for i in range(4)] + [(f"a{i}", "skip") for i in range(4, 6)])
    + suite("B", [(f"b{i}", "pass") for i in range(2)] + [(f"b{i}", "skip") for i in range(2, 4)])
    + "</testsuites>"
)

LIVE = suite(
    "Live",
    [(f"t{i}", "fail") for i in range(13)] + [(f"t{i}", "pass") for i in range(13, 92)],
)

# name, xml, expected exit, substrings the output must contain
CASES = [
    (
        "all-skipped -> RED",
        ALL_SKIPPED,
        1,
        ["::error::Instrumented suite executed 0 tests", "(5 recorded, 5 skipped)"],
    ),
    (
        "boundary 5 recorded / 4 skipped -> GREEN on 1 executed",
        BOUNDARY,
        0,
        ["::notice::Android instrumented: **1 executed**, 0 failed, 4 skipped"],
    ),
    (
        "nested <testsuites> root -> 10 recorded, not double-counted",
        NESTED,
        0,
        [
            "EXECUTED: 6   recorded: 10",
            "::notice::Android instrumented: **6 executed**, 0 failed, 4 skipped",
        ],
    ),
    (
        "live shape -> 92 executed / 13 failed / 0 skipped",
        LIVE,
        1,
        ["::notice::Android instrumented: **92 executed**, 13 failed, 0 skipped"],
    ),
]

# name, source fragment to replace, replacement. Each must be caught by some case above.
MUTANTS = [
    ("executed counted as recorded (the #282 bug)", "total - skipped", "total"),
    ("skipped deducted twice", "total - skipped", "total - skipped - skipped"),
    (
        "nested root double-counted",
        '[root] if root.tag == "testsuite" else root.iter("testsuite")',
        '[root] + list(root.iter("testsuite"))',
    ),
    (
        "notice labelled with the recorded count",
        'f"Android instrumented: **{executed} executed**',
        'f"Android instrumented: **{total} executed**',
    ),
    ("gate never fires", "if executed == 0:", "if False:"),
]


def run(script: pathlib.Path, xml: str | None) -> subprocess.CompletedProcess:
    """Run `script` in a scratch tree holding `xml`, or an empty tree when xml is None."""
    with tempfile.TemporaryDirectory() as tmp:
        if xml is not None:
            target = pathlib.Path(tmp) / FIXTURE
            target.parent.mkdir(parents=True)
            target.write_text(f"<?xml version='1.0' encoding='UTF-8'?>{xml}", encoding="utf-8")
        return subprocess.run(
            [sys.executable, str(script)], cwd=tmp, capture_output=True, text=True
        )


def check(script: pathlib.Path, case) -> tuple[list[str], str]:
    name, xml, want_exit, want_text = case
    proc = run(script, xml)
    out = proc.stdout + proc.stderr
    problems = []
    # Prove the fixture was actually read before trusting the exit status: a glob that missed
    # would exit 1 with "No instrumented test-result XML found", which is also non-zero.
    if FIXTURE not in out:
        problems.append("fixture was not read — the glob missed it")
    if proc.returncode != want_exit:
        problems.append(f"exit {proc.returncode}, expected {want_exit}")
    problems += [f"missing output: {text!r}" for text in want_text if text not in out]
    return problems, out


def report(ok: bool, name: str, detail: list[str] = (), out: str = "") -> bool:
    print(f"{'ok  ' if ok else 'FAIL'} {name}")
    if not ok:
        for line in detail:
            print(f"       {line}")
        if out:
            print("     --- output ---")
            print("\n".join(f"     {line}" for line in out.splitlines()))
    return ok


passed = True

print("== the real gate must pass every case ==")
for case in CASES:
    problems, out = check(SCRIPT, case)
    passed &= report(not problems, case[0], problems, out)

print()
print("== the glob-miss exit must be distinguishable from a real failure ==")
proc = run(SCRIPT, None)
problems = []
if proc.returncode == 0:
    problems.append("an empty result tree exited 0")
if "No instrumented test-result XML found" not in proc.stdout:
    problems.append("no 'No instrumented test-result XML found' — a missed glob is unlabelled")
if FIXTURE in proc.stdout:
    problems.append("claimed to read a fixture that was never written")
passed &= report(not problems, "empty tree -> RED for a NAMED reason", problems, proc.stdout)

print()
print("== every mutant gate must be caught by some case ==")
with tempfile.TemporaryDirectory() as tmp:
    source = SCRIPT.read_text(encoding="utf-8")
    for name, old, new in MUTANTS:
        if source.count(old) != 1:
            passed &= report(False, name, [f"mutation site {old!r} occurs {source.count(old)}x"])
            continue
        mutant = pathlib.Path(tmp) / "mutant.py"
        mutant.write_text(source.replace(old, new), encoding="utf-8")
        caught = [case[0] for case in CASES if check(mutant, case)[0]]
        label = f"{name} -> caught by {caught or 'NOTHING'}"
        passed &= report(bool(caught), label, ["not caught"])

print()
if not passed:
    print("gate verification FAILED")
    sys.exit(1)
print("gate verified: all cases pass, all mutants caught")
