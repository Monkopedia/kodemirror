#!/usr/bin/env python3
"""Assemble `changelog.d/` fragments into a released `CHANGELOG.md` section.

Every change adds its own file under `changelog.d/`, so two pull requests never
edit the same file and never conflict (#281). This script is what turns that
pile of files into one dated section, and it runs at the release cut -- from the
version-bump PR described in `CLAUDE.md` -> "Release process".

Commands
--------
  check      Validate every fragment's name and body, and assert that
             CHANGELOG.md itself was not hand-edited. Run in CI.
  preview    Print the section assembly would produce; writes nothing.
  assemble   Insert that section into CHANGELOG.md and delete the fragments.

Deliberate properties, each earned from a real failure recorded on #281/#297:

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
* **A version is released once.** `assemble` refuses a version whose `## [X.Y.Z]`
  heading is already in the file. The `-SNAPSHOT` guard was the only defence and
  it is inert in this repo's steady state: the post-release SNAPSHOT bump has
  been performed after three of eight releases, so `main` normally sits on an
  already-released version. `_verify_additive` does not cover it -- a *heading*
  collision leaves the rendered section absent from the old file (#297).
* **`check` also guards CHANGELOG.md, not just the fragments.** Validating only
  the fragments that are present answers "are these well-formed?", never "did
  this change do the right thing with the changelog". Since #295 removed
  `## [Unreleased]`, an edit written against the old instructions no longer
  conflicts -- git lands the hunk at the nearest surviving context, which is
  *inside an already-published section*, and the merge is clean. So `check`
  refuses a reintroduced `## [Unreleased]` heading outright, and, given
  `--base-ref`, refuses any change to CHANGELOG.md that is not a release
  assembly (#297).
"""

from __future__ import annotations

import argparse
import datetime
import re
import subprocess
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

# The block #295 deleted. Its reappearance means someone is writing entries by
# the pre-fragment instructions, and those entries will never be assembled.
UNRELEASED_RE = re.compile(r"(?m)^## \[Unreleased\]")


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


def assemble(changelog: Path, version: str, section: str, fragments) -> str:
    old = changelog.read_text(encoding="utf-8")
    if re.search(rf"(?m)^## \[{re.escape(version)}\]", old):
        raise Failure(
            f"CHANGELOG.md already has a [{version}] section. Bump the version in "
            f"{VERSION_FILE} before assembling, or pass --version. (The -SNAPSHOT "
            "guard does not catch this: the post-release SNAPSHOT bump is usually "
            "skipped, so `main` normally sits on an already-released version.)"
        )
    match = SECTION_START_RE.search(old)
    cut = match.start() if match else len(old)
    new = old[:cut] + section + "\n" + old[cut:]
    _verify_additive(old, new, section + "\n", fragments)
    return new


def verify_no_unreleased_section(text: str) -> None:
    """Refuse a `## [Unreleased]` heading in CHANGELOG.md.

    Anchored to a heading rather than to the word, so prose that happens to say
    "unreleased" is not an error -- a guard that fires on the good case too gets
    weakened by the next person who trips over it.
    """
    if UNRELEASED_RE.search(text):
        raise Failure(
            "CHANGELOG.md has a `## [Unreleased]` heading. #295 removed it: entries "
            "live as one file per change under changelog.d/ and are assembled at the "
            "release cut. An entry written under that heading is never assembled."
        )


def _leading_section_insertion(old: str, new: str) -> str | None:
    """Return the text `new` inserts above `old`'s first released section.

    `None` unless `new` is exactly `old` with one new `## [` section spliced in
    at the point `assemble` splices -- i.e. every other byte of the file, before
    and after, is untouched.
    """
    match = SECTION_START_RE.search(old)
    cut = match.start() if match else len(old)
    tail = old[cut:]
    if new[:cut] != old[:cut]:
        return None
    if tail and not new.endswith(tail):
        return None
    inserted = new[cut : len(new) - len(tail)]
    if not inserted.startswith("## ["):
        return None
    if len(SECTION_START_RE.findall(inserted)) != 1:
        return None
    return inserted


def verify_changelog_matches_base(root: Path, old: str, new: str) -> None:
    """Assert this change either leaves CHANGELOG.md alone or assembles a release.

    `check` otherwise never reads CHANGELOG.md, so a hunk landing inside a
    published section passes CI with a green tick and a clean merge (#297).
    """
    if new == old:
        return
    inserted = _leading_section_insertion(old, new)
    if inserted is None:
        raise Failure(
            "CHANGELOG.md was modified. Entries go in changelog.d/ as one file per "
            "change; CHANGELOG.md is written only by `changelog.py assemble` at the "
            "release cut, and published sections are history. Since #295 removed "
            "`## [Unreleased]`, an edit written for the old layout merges cleanly "
            "into an already-published section instead of conflicting."
        )
    version = derive_version(root)
    if not inserted.startswith(f"## [{version}] "):
        raise Failure(
            f"CHANGELOG.md gained a section that is not this build's release: "
            f"expected `## [{version}] - <date>`, got {inserted.splitlines()[0]!r}."
        )


def _git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args], capture_output=True, text=True
    )
    if result.returncode != 0:
        raise Failure(
            f"`git {' '.join(args)}` failed: {result.stderr.strip() or 'no output'}"
        )
    return result.stdout


def base_changelog(root: Path, base_ref: str) -> str:
    """CHANGELOG.md as of the merge base with `base_ref`.

    Fails loudly if the ref cannot be resolved: a lookup that degrades to an
    empty string would make every comparison below read as "unchanged".
    """
    merge_base = _git(root, "merge-base", "HEAD", base_ref).strip()
    if not merge_base:
        raise Failure(f"cannot resolve a merge base with {base_ref}")
    return _git(root, "show", f"{merge_base}:CHANGELOG.md")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("command", choices=["check", "preview", "assemble"])
    parser.add_argument("--version", help="release version (default: read from the build)")
    parser.add_argument("--date", help="release date, YYYY-MM-DD (default: today)")
    parser.add_argument(
        "--base-ref",
        help="check only: git ref this change is based on. CHANGELOG.md is compared "
        "against it and must be either identical or a release assembly.",
    )
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
        current = (root / "CHANGELOG.md").read_text(encoding="utf-8")
        verify_no_unreleased_section(current)
        # Printed either way, so a log reader can see whether the comparison
        # actually happened -- a silently skipped guard is the failure mode this
        # whole check exists to close.
        if args.base_ref:
            verify_changelog_matches_base(root, base_changelog(root, args.base_ref), current)
            print(f"CHANGELOG.md: checked against {args.base_ref}")
        else:
            print("CHANGELOG.md: no --base-ref; compared against nothing")
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
    changelog.write_text(
        assemble(changelog, version, section, fragments), encoding="utf-8"
    )
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
