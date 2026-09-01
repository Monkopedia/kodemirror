#!/usr/bin/env python3
"""Assemble `changelog.d/` fragments into a released `CHANGELOG.md` section.

Every change adds its own file under `changelog.d/`, so two pull requests never
edit the same file and never conflict (#281). This script is what turns that
pile of files into one dated section, and it runs at the release cut -- from the
version-bump PR described in `CLAUDE.md` -> "Release process".

Commands
--------
  check      Validate every fragment's name and body. Run in CI.
  preview    Print the section assembly would produce; writes nothing.
  assemble   Insert that section into CHANGELOG.md and delete the fragments.

Deliberate properties, each earned from a real failure recorded on #281:

* **No parsing of entry text.** A fragment body is copied verbatim. An entry is
  not reliably "one bullet carrying one `(#N)`" -- a previous auto-resolver
  keyed on that shape and aborted on #278, whose entry has sub-bullets with no
  reference of their own. Grouping comes from the *filename*, never the prose.
* **Python, not a shell pipeline.** A changelog line is hostile input to a shell
  filter: a bullet's leading `-` gets parsed as an option, and `^+++` as a
  regex quantifier. Both produced false results while this was being resolved by
  hand.
* **The version is derived, never written down here.** A hardcoded version decays
  into a false "clean" the moment it ships (#293). A failed derivation aborts
  loudly rather than yielding an empty string.
* **Assembly is checked to be purely additive.** `_verify_additive` strips the
  new section back out and requires the remainder to reproduce the previous file
  byte for byte, with positive controls on both sides so that an empty-vs-empty
  comparison cannot pass as success.
"""

from __future__ import annotations

import argparse
import datetime
import re
import sys
from pathlib import Path

# Fragment section slug -> `### ` heading, in the order sections are emitted.
# Keep a Changelog's six first, then the extra headings this project uses.
SECTIONS = {
    "added": "Added",
    "changed": "Changed",
    "deprecated": "Deprecated",
    "removed": "Removed",
    "fixed": "Fixed",
    "security": "Security",
    "performance": "Performance",
    "build": "Build",
    "tests": "Tests",
    "documentation": "Documentation",
    "internal": "Internal",
}

# `<issue>.<section>.md`, or `<issue>.<section>.<discriminator>.md` when one
# issue needs two fragments in the same section (two PRs, one issue).
FRAGMENT_RE = re.compile(r"^(\d+)\.([a-z]+)(?:\.[A-Za-z0-9_-]+)?\.md$")

# Files allowed to sit in changelog.d/ without being fragments. Anything else is
# an error: silently skipping an unrecognised name is how an entry gets lost.
NON_FRAGMENTS = {"README.md", ".gitkeep"}

VERSION_FILE = Path("convention-plugins/src/main/kotlin/kodemirror.library.gradle.kts")
VERSION_RE = re.compile(r'(?m)^\s*version\s*=\s*"([^"]+)"')

# First released section in CHANGELOG.md; the new section is inserted above it.
SECTION_START_RE = re.compile(r"(?m)^## \[")


class Failure(Exception):
    """A loud, fatal condition. Never swallowed, never turned into a no-op."""


def repo_root() -> Path:
    root = Path(__file__).resolve().parents[2]
    if not (root / "CHANGELOG.md").is_file():
        raise Failure(f"cannot locate the repository root (tried {root})")
    return root


def load_fragments(directory: Path) -> list[tuple[int, str, str, Path]]:
    """Return (issue, section, body, path), sorted for emission.

    Newest first within a section (descending issue number), matching how
    entries were being prepended to `## [Unreleased]` before fragments.
    """
    if not directory.is_dir():
        raise Failure(f"no fragment directory at {directory}")

    fragments = []
    for path in sorted(directory.iterdir()):
        if path.name in NON_FRAGMENTS:
            continue
        if path.is_dir():
            raise Failure(f"unexpected directory in {directory}: {path.name}")
        match = FRAGMENT_RE.match(path.name)
        if not match:
            raise Failure(
                f"{path}: name does not match <issue>.<section>.md "
                f"(sections: {', '.join(sorted(SECTIONS))})"
            )
        issue, section = int(match.group(1)), match.group(2)
        if section not in SECTIONS:
            raise Failure(
                f"{path}: unknown section '{section}' "
                f"(expected one of: {', '.join(sorted(SECTIONS))})"
            )
        body = path.read_text(encoding="utf-8")
        check_body(path, issue, body)
        fragments.append((issue, section, body, path))

    section_order = list(SECTIONS)
    fragments.sort(key=lambda f: (section_order.index(f[1]), -f[0], f[3].name))
    return fragments


def check_body(path: Path, issue: int, body: str) -> None:
    """Validate a fragment body without parsing its entries.

    Only three things are asserted, all of them structural: it is not empty, it
    is a bullet list, and it carries no `##` heading of its own (the heading is
    the assembler's job). Anything stricter would re-create the
    one-bullet-one-reference assumption that failed on #278.
    """
    if not body.strip():
        raise Failure(f"{path}: empty fragment")
    lines = body.strip("\n").split("\n")
    if not lines[0].startswith("- "):
        raise Failure(f"{path}: must start with a '- ' bullet, got: {lines[0][:60]!r}")
    for line in lines:
        if line.startswith("#"):
            raise Failure(
                f"{path}: contains its own heading ({line[:40]!r}); the section "
                "heading comes from the filename"
            )
    if f"#{issue}" not in body:
        raise Failure(
            f"{path}: body never mentions #{issue}. Changelog entries carry their "
            "issue number so a reader of the released section can follow it."
        )


def render_section(version: str, date: str, fragments) -> str:
    if not version:
        raise Failure("refusing to render with an empty version")
    if not fragments:
        raise Failure("no fragments in changelog.d/; nothing to release")
    out = [f"## [{version}] - {date}\n"]
    current = None
    for _issue, section, body, _path in fragments:
        if section != current:
            current = section
            out.append(f"\n### {SECTIONS[section]}\n")
        out.append(body if body.endswith("\n") else body + "\n")
    return "".join(out)


def derive_version(root: Path) -> str:
    """Read the version from the build, and fail loudly rather than emptily."""
    path = root / VERSION_FILE
    if not path.is_file():
        raise Failure(f"cannot derive version: {path} does not exist")
    match = VERSION_RE.search(path.read_text(encoding="utf-8"))
    if not match:
        raise Failure(f"cannot derive version: no `version = \"...\"` in {path}")
    version = match.group(1).strip()
    if not version:
        raise Failure(f"cannot derive version: empty version in {path}")
    if version.endswith("-SNAPSHOT"):
        raise Failure(
            f"{path} still reads {version}. Drop the -SNAPSHOT suffix first "
            "(release step 1), or pass --version explicitly."
        )
    return version


def _verify_additive(old: str, new: str, section: str, fragments) -> None:
    """Assert assembly only *inserted*, and inserted everything.

    A line count is not enough: a reworded bullet keeps the count identical.
    So strip the new section back out and demand the remainder equal the
    previous file byte for byte -- with positive controls that the section is
    genuinely absent from one side and present in the other, so that an
    empty-vs-empty comparison cannot read as success.
    """
    if not section.strip():
        raise Failure("internal check: rendered section is empty")
    if section in old:
        raise Failure("internal check: the new section already exists in CHANGELOG.md")
    if section not in new:
        raise Failure("internal check: the new section is missing from the result")
    if new.replace(section, "", 1) != old:
        raise Failure(
            "internal check: assembly changed existing CHANGELOG.md content. "
            "Published sections are history and must not be rewritten."
        )
    for _issue, _section, body, path in fragments:
        if body not in section:
            raise Failure(f"internal check: fragment {path.name} did not reach the section")


def assemble(changelog: Path, section: str, fragments) -> str:
    old = changelog.read_text(encoding="utf-8")
    match = SECTION_START_RE.search(old)
    cut = match.start() if match else len(old)
    new = old[:cut] + section + "\n" + old[cut:]
    _verify_additive(old, new, section + "\n", fragments)
    return new


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("command", choices=["check", "preview", "assemble"])
    parser.add_argument("--version", help="release version (default: read from the build)")
    parser.add_argument("--date", help="release date, YYYY-MM-DD (default: today)")
    parser.add_argument(
        "--keep-fragments",
        action="store_true",
        help="assemble without deleting the fragment files",
    )
    args = parser.parse_args(argv)

    root = repo_root()
    fragments = load_fragments(root / "changelog.d")

    if args.command == "check":
        print(f"changelog.d: {len(fragments)} fragment(s) valid")
        for issue, section, _body, path in fragments:
            print(f"  #{issue:<5} {section:<14} {path.name}")
        return 0

    version = args.version or derive_version(root)
    date = args.date or datetime.date.today().isoformat()
    if not version or not date:
        raise Failure("refusing to proceed with an empty version or date")
    section = render_section(version, date, fragments)

    if args.command == "preview":
        sys.stdout.write(section)
        return 0

    changelog = root / "CHANGELOG.md"
    changelog.write_text(assemble(changelog, section, fragments), encoding="utf-8")
    print(f"CHANGELOG.md: added [{version}] - {date} from {len(fragments)} fragment(s)")
    if not args.keep_fragments:
        for _issue, _section, _body, path in fragments:
            path.unlink()
        print(f"changelog.d: removed {len(fragments)} fragment(s); `git add -A changelog.d/`")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except Failure as failure:
        print(f"error: {failure}", file=sys.stderr)
        sys.exit(1)
