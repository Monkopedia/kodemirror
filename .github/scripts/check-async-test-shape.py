#!/usr/bin/env python3
"""Reject test functions that would pass on wasmJs without running (#361).

`runComposeUiTest` and `runTest` return a promise on wasmJs, and the test framework waits on a
test function's *value*. A test written with a block body runs the call as a statement and
returns `Unit`, so the browser suite reports it passed before the composition or coroutine has
done anything: a green build, at exactly the expected test count, with no assertion evaluated.
JVM runs the same body synchronously and still fails a false assertion, so no JVM run warns.
Written as an expression body (`fun foo() = runComposeUiTest { ... }`) the promise is returned
and awaited.

This checks every Kotlin file in a test source set that compiles for wasmJs (`commonTest`,
`wasmJsTest`). A *runner* is `runComposeUiTest`, `runTest`, or any function whose expression
body calls a runner (such as `runEditorTest`), found transitively. A function with a block body
that calls a runner, other than as the operand of `return`, is reported.

Usage: check-async-test-shape.py [ROOT]     (exit 1 when anything is reported)
"""

import re
import sys
from pathlib import Path

SEED_RUNNERS = {"runComposeUiTest", "runTest"}
WASM_TEST_SOURCE_SETS = ("commonTest", "wasmJsTest")

FUN = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(`[^`]+`|\w+)\s*\(")


def strip_comments_and_strings(text):
    """Blank out comments and string literals, keeping offsets, so braces in them don't count."""
    out = list(text)
    i, n = 0, len(text)

    def blank(a, b):
        for k in range(a, b):
            if out[k] != "\n":
                out[k] = " "

    while i < n:
        if text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            blank(i, j)
            i = j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            blank(i, j)
            i = j
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            blank(i + 3, j - 3)
            i = j
        elif text[i] in "\"'":
            quote, j = text[i], i + 1
            while j < n and text[j] != quote and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            blank(i + 1, min(j, n))
            i = j + 1
        else:
            i += 1
    return "".join(out)


def matching(text, start, open_ch, close_ch):
    """Index just past the bracket matching the one at `start`."""
    depth = 0
    for k in range(start, len(text)):
        if text[k] == open_ch:
            depth += 1
        elif text[k] == close_ch:
            depth -= 1
            if depth == 0:
                return k + 1
    return len(text)


def functions(text):
    """Yield (name, kind, body, offset): kind is '=' or '{', body the expression or block."""
    for m in FUN.finditer(text):
        params_end = matching(text, m.end() - 1, "(", ")")
        k = params_end
        # Skip a return type up to the body; a lambda type's own braces never appear here.
        while k < len(text) and text[k] not in "={;\n":
            k += 1
        while k < len(text) and text[k] == "\n":
            nxt = text[k:].lstrip()
            if nxt[:1] in ("=", "{"):
                k = len(text) - len(nxt)
                break
            break
        if k >= len(text) or text[k] not in "={":
            continue  # abstract, or no body
        if text[k] == "{":
            end = matching(text, k, "{", "}")
            yield m.group(1), "{", text[k:end], m.start()
        else:
            yield m.group(1), "=", text[k:expression_end(text, m.start(), k)], m.start()


def expression_end(text, fun_offset, body_start):
    """End of an expression body: the first later line indented no deeper than the `fun`.

    A line at that depth that starts by closing a bracket still belongs to the expression,
    as in `fun t() = runEditorTest {` ... `}`.
    """
    line_start = text.rfind("\n", 0, fun_offset) + 1
    indent = len(text[line_start:fun_offset]) - len(text[line_start:fun_offset].lstrip())
    pos = text.find("\n", body_start)
    while pos >= 0:
        nxt = text.find("\n", pos + 1)
        line = text[pos + 1:nxt if nxt >= 0 else len(text)]
        stripped = line.lstrip()
        if stripped and len(line) - len(stripped) <= indent and stripped[0] not in ")]}":
            return pos
        pos = nxt
    return len(text)


def calls(body, names):
    return any(re.search(r"\b" + re.escape(name) + r"\s*[({<]", body) for name in names)


def scan(files):
    texts = {f: strip_comments_and_strings(f.read_text(encoding="utf-8")) for f in files}
    runners = set(SEED_RUNNERS)
    while True:
        found = {name for text in texts.values() for name, kind, body, _ in functions(text)
                 if kind == "=" and calls(body, runners)} - runners
        if not found:
            break
        runners |= found
    problems = []
    for f, text in texts.items():
        for name, kind, body, offset in functions(text):
            if kind != "{" or name in SEED_RUNNERS:
                continue
            for runner in runners:
                for call in re.finditer(r"\b" + re.escape(runner) + r"\s*[({<]", body):
                    before = body[:call.start()].rstrip()
                    if before.endswith("return"):
                        continue
                    line = text.count("\n", 0, offset) + 1
                    problems.append(f"{f}:{line}: `{name}` calls `{runner}` in a block body")
    return runners, problems


def test_files(root):
    for path in root.rglob("*.kt"):
        parts = path.parts
        if "build" in parts or "node_modules" in parts:
            continue
        if any(f"src/{s}/" in path.as_posix() for s in WASM_TEST_SOURCE_SETS):
            yield path


def main(argv):
    root = Path(argv[1]) if len(argv) > 1 else Path(".")
    files = sorted(test_files(root))
    runners, problems = scan(files)
    print(f"checked {len(files)} wasm test files against {len(runners)} async runners")
    for p in problems:
        print(p)
    if problems:
        print(
            "\nOn wasmJs these run without awaiting the test body, so they pass without "
            "evaluating anything. Write the test as an expression body, "
            "`fun name() = runEditorTest { ... }` (#361)."
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
