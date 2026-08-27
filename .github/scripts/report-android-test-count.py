#!/usr/bin/env python3
"""Summarise the Android instrumented-test JUnit XML and refuse a silent zero.

A Gradle task that executes no tests still exits 0, which is how #197 and #200 both survived
as green-but-inert controls. The instrumented job is non-blocking, so nothing else would ever
notice; this script is the thing that notices. It prints the per-class breakdown and exits
non-zero if the suite executed nothing, or if anything failed.

"Executed" means recorded minus skipped (#282). An entirely @Ignore'd suite records a full
complement of <testcase> elements and would otherwise pass the very gate written to catch an
inert suite.

A DIVERGENCE BETWEEN executed AND recorded IS THIS GATE WORKING, NOT A REGRESSION. If the suite
ever reports `executed` below `recorded`, real skipping is being surfaced for the first time —
find out what got @Ignore'd. Do not treat the number as having broken, and do not restore the old
arithmetic to make it match a historical baseline. This job has printed 92 on every branch for its
entire existence; that constant was never evidence of health, only evidence that the instrument
had not yet been asked a question it could answer differently (#275).
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

PATTERNS = (
    "**/build/outputs/androidTest-results/**/*.xml",
    "**/build/outputs/androidTest-results/**/TEST-*.xml",
)

files = sorted({p for pat in PATTERNS for p in glob.glob(pat, recursive=True)})
if not files:
    print("::error::No instrumented test-result XML found — the suite did not run.")
    sys.exit(1)

total = failures = errors = skipped = 0
per_class: dict[str, list[int]] = {}
failed_names: list[str] = []

for path in files:
    root = ET.parse(path).getroot()
    suites = [root] if root.tag == "testsuite" else root.iter("testsuite")
    for suite in suites:
        for case in suite.iter("testcase"):
            cls = case.get("classname", "?")
            entry = per_class.setdefault(cls, [0, 0, 0])
            entry[0] += 1
            total += 1
            bad = case.findall("failure") + case.findall("error")
            if bad:
                entry[1] += 1
                failures += 1 if case.findall("failure") else 0
                errors += 1 if case.findall("error") else 0
                failed_names.append(f"{cls}.{case.get('name')}")
            elif case.findall("skipped"):
                entry[2] += 1
                skipped += 1

print(f"Result XML files: {len(files)}")
for path in files:
    print(f"  {path}")
print()
print(f"{'class':<70} {'total':>5} {'failed':>7} {'skipped':>8}")
for cls in sorted(per_class):
    count, bad, skip = per_class[cls]
    print(f"{cls:<70} {count:>5} {bad:>7} {skip:>8}")
print()
executed = total - skipped
print(
    f"EXECUTED: {executed}   recorded: {total}   failures: {failures}   "
    f"errors: {errors}   skipped: {skipped}"
)

if failed_names:
    print("\nFailed:")
    for name in failed_names:
        print(f"  {name}")

if executed == 0:
    print(
        "::error::Instrumented suite executed 0 tests — inert, not green. "
        f"({total} recorded, {skipped} skipped)"
    )
    sys.exit(1)

summary = (
    f"Android instrumented: **{executed} executed**, {failures + errors} failed, "
    f"{skipped} skipped"
)
print(f"::notice::{summary}")

step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
if step_summary:
    with open(step_summary, "a") as handle:
        handle.write(f"### {summary}\n\n")
        handle.write("| class | total | failed | skipped |\n|---|---:|---:|---:|\n")
        for cls in sorted(per_class):
            count, bad, skip = per_class[cls]
            handle.write(f"| `{cls}` | {count} | {bad} | {skip} |\n")

sys.exit(1 if failed_names else 0)
