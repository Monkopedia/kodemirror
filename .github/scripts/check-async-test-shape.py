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
body's *value* is a runner call (such as `runEditorTest`), found transitively within the same
Gradle module. Reported:

- a block body that calls a runner, other than as the operand of `return`;
- an expression body that calls a runner but whose value is not that call, such as
  `= run { runEditorTest { ... }; println() }` or `= runEditorTest { ... }.let { }`.

An expression's value is a runner call when it is the call itself, the receiver of a trailing
`apply`/`also` (which return their receiver), or, recursively, the last statement of the
lambda of a trailing `run`/`let`/`with` (which return their lambda's value).

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
    as in `fun t() = runEditorTest {` ... `}`; one shallower (the enclosing class's `}`) does not.
    """
    line_start = text.rfind("\n", 0, fun_offset) + 1
    indent = len(text[line_start:fun_offset]) - len(text[line_start:fun_offset].lstrip())
    pos = text.find("\n", body_start)
    while pos >= 0:
        nxt = text.find("\n", pos + 1)
        line = text[pos + 1:nxt if nxt >= 0 else len(text)]
        stripped = line.lstrip()
        depth = len(line) - len(stripped)
        if stripped and (depth < indent or (depth == indent and stripped[0] not in ")]}")):
            return pos
        pos = nxt
    return len(text)


def calls(body, names):
    return any(re.search(r"\b" + re.escape(name) + r"\s*[({<]", body) for name in names)


# Scope functions whose value is their lambda's last expression, and those returning the receiver.
LAMBDA_VALUE = {"run", "let", "with"}
RECEIVER_VALUE = {"apply", "also"}
OPENERS, CLOSERS = "([{", ")]}"
# A line ending in one of these, or the next starting with one, continues the same statement.
CONTINUES_AFTER = ("=", ",", "(", "[", "{", "->", "+", "-", "*", "/", "&&", "||", "?:", ".", "?")
CONTINUES_BEFORE = (".", "?.", "?:", "&&", "||", ")", "]", "}")


def top_level(text):
    """Yield (index, char) for characters at bracket depth 0."""
    depth = 0
    for k, c in enumerate(text):
        if c in OPENERS:
            depth += 1
        elif c in CLOSERS:
            depth -= 1
        elif depth == 0:
            yield k, c


def last_statement(block):
    """The last statement of a lambda or block's text (without its braces)."""
    arrow = None
    for k, c in top_level(block):
        if block.startswith("->", k):
            arrow = k
            break
    if arrow is not None and re.fullmatch(r"[\w\s,:()<>?.]*", block[:arrow]):
        block = block[arrow + 2:]
    breaks = [-1]
    for k, c in top_level(block):
        if c == ";":
            breaks.append(k)
        elif c == "\n":
            before = block[:k].rstrip()
            after = block[k + 1:].lstrip()
            if before.endswith(CONTINUES_AFTER) or after.startswith(CONTINUES_BEFORE):
                continue
            breaks.append(k)
    statements = [block[a + 1:b] for a, b in zip(breaks, breaks[1:] + [len(block)])]
    statements = [st.strip() for st in statements if st.strip()]
    return statements[-1] if statements else ""


def segments(expr):
    """Split a postfix chain `a.b(x) { }?.c` at its top-level `.`/`?.` into segment texts."""
    cuts = [k for k, c in top_level(expr) if c == "." and not expr[k + 1:k + 2] == "."
            and not expr[k - 1:k] == "."]
    parts, start = [], 0
    for k in cuts:
        parts.append(expr[start:k].rstrip("?").strip())
        start = k + 1
    parts.append(expr[start:].strip())
    return parts


def value_is_runner(expr, names):
    """Whether the value of Kotlin expression `expr` is a call to one of `names`."""
    expr = expr.strip()
    while expr.startswith("(") and matching(expr, 0, "(", ")") == len(expr):
        expr = expr[1:-1].strip()
    if not expr:
        return False
    parts = segments(expr)
    head = re.match(r"(`[^`]+`|\w+)\s*(<[^>]*>)?\s*", parts[-1])
    if head is None:
        return False
    name, rest = head.group(1), parts[-1][head.end():]
    if name in names:
        return True
    if name in RECEIVER_VALUE:
        return len(parts) > 1 and value_is_runner(".".join(parts[:-1]), names)
    if name in LAMBDA_VALUE:
        rest = rest.rstrip()
        if rest.startswith("("):
            rest = rest[matching(rest, 0, "(", ")"):].lstrip()
        if not (rest.startswith("{") and matching(rest, 0, "{", "}") == len(rest)):
            return False
        return value_is_runner(last_statement(rest[1:-1]), names)
    return False


def module_of(path):
    """The Gradle module directory of a source file: everything before `/src/`."""
    posix = path.as_posix()
    return posix[:posix.rfind("/src/")]


def scan(files):
    texts = {f: strip_comments_and_strings(f.read_text(encoding="utf-8")) for f in files}
    funs = {f: list(functions(text)) for f, text in texts.items()}
    modules = {}
    for f in files:
        modules.setdefault(module_of(f), []).append(f)
    # Runner names are scoped to a Gradle module: test sources are not visible across modules,
    # so a same-named sync helper elsewhere must not make a call here look async.
    runners_by_module = {}
    for module, members in modules.items():
        runners = set(SEED_RUNNERS)
        while True:
            found = {name for f in members for name, kind, body, _ in funs[f]
                     if kind == "=" and value_is_runner(body[1:], runners)} - runners
            if not found:
                break
            runners |= found
        runners_by_module[module] = runners
    problems = []
    for f, text in texts.items():
        runners = runners_by_module[module_of(f)]
        for name, kind, body, offset in funs[f]:
            if name in SEED_RUNNERS:
                continue
            line = text.count("\n", 0, offset) + 1
            if kind == "=":
                if calls(body, runners) and not value_is_runner(body[1:], runners):
                    called = sorted(r for r in runners if calls(body, [r]))
                    problems.append(
                        f"{f}:{line}: `{name}` calls `{called[0]}` but its expression body's "
                        "value is not that call"
                    )
                continue
            for runner in sorted(runners):
                for call in re.finditer(r"\b" + re.escape(runner) + r"\s*[({<]", body):
                    before = body[:call.start()].rstrip()
                    if before.endswith("return"):
                        continue
                    problems.append(f"{f}:{line}: `{name}` calls `{runner}` in a block body")
    all_runners = set().union(*runners_by_module.values()) if runners_by_module else set()
    return all_runners, problems


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
            "evaluating anything. Write the test as an expression body whose value is the "
            "runner call, `fun name() = runEditorTest { ... }`, with any setup before it "
            "inside `= run { setup; runEditorTest { ... } }` (#361)."
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
