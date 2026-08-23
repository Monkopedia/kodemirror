#!/usr/bin/env python3
"""Summarise the Android instrumented-test JUnit XML and refuse a silent zero.

A Gradle task that executes no tests still exits 0, which is how #197 and #200 both survived
as green-but-inert controls. The instrumented job is non-blocking, so nothing else would ever
notice; this script is the thing that notices. It prints the per-class breakdown and exits
non-zero if the suite executed nothing, or if anything failed.
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
print(f"{'class':<70} {'run':>5} {'failed':>7} {'skipped':>8}")
for cls in sorted(per_class):
    run, bad, skip = per_class[cls]
    print(f"{cls:<70} {run:>5} {bad:>7} {skip:>8}")
print()
print(f"EXECUTED: {total}   failures: {failures}   errors: {errors}   skipped: {skipped}")

if failed_names:
    print("\nFailed:")
    for name in failed_names:
        print(f"  {name}")

if total == 0:
    print("::error::Instrumented suite executed 0 tests — inert, not green.")
    sys.exit(1)

summary = (
    f"Android instrumented: **{total} executed**, {failures + errors} failed, "
    f"{skipped} skipped"
)
print(f"::notice::{summary}")

step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
if step_summary:
    with open(step_summary, "a") as handle:
        handle.write(f"### {summary}\n\n")
        handle.write("| class | run | failed | skipped |\n|---|---:|---:|---:|\n")
        for cls in sorted(per_class):
            run, bad, skip = per_class[cls]
            handle.write(f"| `{cls}` | {run} | {bad} | {skip} |\n")

sys.exit(1 if failed_names else 0)
