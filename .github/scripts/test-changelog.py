#!/usr/bin/env python3
"""Tests for `changelog.py`. Run: `python3 .github/scripts/test-changelog.py`.

Plain asserts, no dependencies -- this runs in the CI `check` job next to the
fragment validation, and the repo has no Python test harness to hook into.

Every safety property here is one the script is supposed to have; each is tested
with a **negative control** that must fail, because a guard that cannot be shown
to fire is indistinguishable from no guard at all.
"""

import subprocess
import tempfile
import unittest
from pathlib import Path

import changelog

BASE = """# Changelog

## [1.0.0] - 2020-01-01

### Fixed
- An old entry (#1).
"""


def write(directory: Path, name: str, body: str) -> Path:
    path = directory / name
    path.write_text(body, encoding="utf-8")
    return path


class FragmentNames(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def valid(self, name, body="- Entry (#7).\n"):
        write(self.dir, name, body)
        return changelog.load_fragments(self.dir)

    def test_accepts_issue_section_and_optional_discriminator(self):
        write(self.dir, "7.fixed.md", "- One (#7).\n")
        write(self.dir, "7.fixed.followup.md", "- Two (#7).\n")
        write(self.dir, "README.md", "not a fragment")
        self.assertEqual([f[0] for f in changelog.load_fragments(self.dir)], [7, 7])

    def test_rejects_unknown_section(self):
        with self.assertRaises(changelog.Failure):
            self.valid("7.misc.md")

    def test_rejects_unparseable_name(self):
        # Silently skipping an unrecognised name is how an entry gets lost.
        with self.assertRaises(changelog.Failure):
            self.valid("notes.md")

    def test_rejects_empty_body(self):
        with self.assertRaises(changelog.Failure):
            self.valid("7.fixed.md", "\n")

    def test_rejects_body_without_its_issue_number(self):
        with self.assertRaises(changelog.Failure):
            self.valid("7.fixed.md", "- An entry with no reference.\n")

    def test_accepts_multiple_bullets_and_sub_bullets_without_own_reference(self):
        # #278's entry had sub-bullets carrying no `(#N)`; an assembler that keys
        # on that shape aborts on real input. Grouping comes from the filename.
        body = "- Lead bullet (#7).\n  - Sub-bullet with no reference.\n- Second bullet.\n"
        self.assertEqual(len(self.valid("7.fixed.md", body)), 1)


class Rendering(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def test_orders_sections_canonically_and_entries_newest_first(self):
        write(self.dir, "3.documentation.md", "- Doc three (#3).\n")
        write(self.dir, "9.fixed.md", "- Fix nine (#9).\n")
        write(self.dir, "4.fixed.md", "- Fix four (#4).\n")
        section = changelog.render_section("2.0.0", "2020-02-02",
                                           changelog.load_fragments(self.dir))
        self.assertEqual(section, (
            "## [2.0.0] - 2020-02-02\n"
            "\n### Fixed\n- Fix nine (#9).\n- Fix four (#4).\n"
            "\n### Documentation\n- Doc three (#3).\n"
        ))

    def test_refuses_to_render_nothing(self):
        with self.assertRaises(changelog.Failure):
            changelog.render_section("2.0.0", "2020-02-02", [])


class Assembly(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.changelog = self.dir / "CHANGELOG.md"
        self.changelog.write_text(BASE, encoding="utf-8")
        self.fragments = [(9, "fixed", "- Fix nine (#9).\n", self.dir / "9.fixed.md")]
        self.section = changelog.render_section("2.0.0", "2020-02-02", self.fragments)

    def test_inserts_above_the_newest_released_section(self):
        result = changelog.assemble(self.changelog, "2.0.0", self.section, self.fragments)
        self.assertTrue(result.startswith("# Changelog\n\n## [2.0.0] - 2020-02-02\n"))
        self.assertIn("## [1.0.0] - 2020-01-01", result)
        # Purely additive: strip the new section, get the original back exactly.
        self.assertEqual(result.replace(self.section + "\n", "", 1), BASE)

    def test_additive_check_catches_a_reworded_published_line(self):
        # The negative control. A rewording keeps the line count identical, so a
        # `--numstat 1 0` style check passes it; this one must not.
        reworded = BASE.replace("An old entry", "An OLD entry")
        self.assertNotEqual(reworded, BASE)
        with self.assertRaises(changelog.Failure):
            changelog._verify_additive(BASE, "## [2.0.0]\n" + reworded,
                                       self.section, self.fragments)

    def test_additive_check_catches_a_dropped_fragment(self):
        dropped = self.fragments + [(8, "fixed", "- Fix eight (#8).\n", self.dir / "8.fixed.md")]
        with self.assertRaises(changelog.Failure):
            changelog._verify_additive(BASE, BASE + self.section, self.section, dropped)

    def test_additive_check_is_not_vacuous_on_empty_input(self):
        # empty-vs-empty is the silent twin of empty-vs-value: it reads as a pass.
        with self.assertRaises(changelog.Failure):
            changelog._verify_additive("", "", "", [])


class DuplicateReleasedHeading(unittest.TestCase):
    """`assemble` must refuse a version that is already in CHANGELOG.md.

    The `-SNAPSHOT` guard is the intended defence and is inert here: the
    post-release SNAPSHOT bump is usually skipped, so `main` normally reads an
    already-released version. `_verify_additive` does not cover it either -- on a
    heading collision the rendered section is still absent from the old file.
    """

    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.changelog = self.dir / "CHANGELOG.md"
        self.changelog.write_text(BASE, encoding="utf-8")
        self.fragments = [(9, "fixed", "- Fix nine (#9).\n", self.dir / "9.fixed.md")]

    def section(self, version):
        return changelog.render_section(version, "2020-02-02", self.fragments)

    def test_refuses_a_version_already_released(self):
        with self.assertRaises(changelog.Failure):
            changelog.assemble(self.changelog, "1.0.0", self.section("1.0.0"),
                               self.fragments)

    def test_refuses_it_even_when_the_date_differs(self):
        # The measured failure: same version, new date, so the rendered section is
        # not a substring of the old file and `_verify_additive` stays silent.
        section = self.section("1.0.0").replace("2020-02-02", "2020-03-03")
        self.assertNotIn(section, BASE)
        with self.assertRaises(changelog.Failure):
            changelog.assemble(self.changelog, "1.0.0", section, self.fragments)

    def test_accepts_a_version_not_yet_released(self):
        result = changelog.assemble(self.changelog, "2.0.0", self.section("2.0.0"),
                                    self.fragments)
        self.assertIn("## [2.0.0] - 2020-02-02", result)

    def test_a_version_that_is_a_prefix_of_a_released_one_is_accepted(self):
        # Over-strictness control: `1.0` must not match `## [1.0.0]`. A guard that
        # reddens the good case reads as strictness and gets removed later.
        result = changelog.assemble(self.changelog, "1.0", self.section("1.0"),
                                    self.fragments)
        self.assertIn("## [1.0] - 2020-02-02", result)


class UnreleasedHeading(unittest.TestCase):
    def test_accepts_a_changelog_of_released_sections(self):
        changelog.verify_no_unreleased_section(BASE)

    def test_rejects_a_reintroduced_unreleased_heading(self):
        with self.assertRaises(changelog.Failure):
            changelog.verify_no_unreleased_section(
                "# Changelog\n\n## [Unreleased]\n\n### Fixed\n- An entry (#2).\n" + BASE
            )

    def test_does_not_fire_on_the_word_in_an_entry(self):
        # Over-strictness control: the guard is about a heading, not a word.
        changelog.verify_no_unreleased_section(
            BASE + "- Documented the unreleased [Unreleased] convention (#3).\n"
        )


class ChangelogAgainstBase(unittest.TestCase):
    """`check`'s CHANGELOG.md guard: a PR may not touch published history.

    The live victim is a branch opened before #295: it still carries a
    `## [Unreleased]` hunk, that heading no longer exists, and git lands the hunk
    inside the newest *published* section. `mergeable: MERGEABLE` is accurate.
    """

    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        version_file = self.root / changelog.VERSION_FILE
        version_file.parent.mkdir(parents=True)
        version_file.write_text('version = "2.0.0"\n', encoding="utf-8")

    def assembled(self, version="2.0.0"):
        return BASE.replace(
            "## [1.0.0]",
            f"## [{version}] - 2020-02-02\n\n### Fixed\n- Fix nine (#9).\n\n## [1.0.0]",
            1,
        )

    def test_an_untouched_changelog_passes(self):
        changelog.verify_changelog_matches_base(self.root, BASE, BASE)

    def test_a_release_assembly_passes(self):
        # The good case that must stay green: the version-bump PR does edit this
        # file, and its own CI has to pass.
        changelog.verify_changelog_matches_base(self.root, BASE, self.assembled())

    def test_an_edit_inside_a_published_section_fails(self):
        landed = BASE.replace("- An old entry (#1).",
                              "- An old entry (#1).\n- A bullet from a pre-#295 branch (#268).")
        self.assertNotEqual(landed, BASE)
        with self.assertRaises(changelog.Failure):
            changelog.verify_changelog_matches_base(self.root, BASE, landed)

    def test_a_reworded_published_line_fails(self):
        with self.assertRaises(changelog.Failure):
            changelog.verify_changelog_matches_base(
                self.root, BASE, BASE.replace("An old entry", "An OLD entry"))

    def test_an_appended_bullet_fails(self):
        with self.assertRaises(changelog.Failure):
            changelog.verify_changelog_matches_base(self.root, BASE, BASE + "- Extra (#4).\n")

    def test_a_section_for_some_other_version_fails(self):
        # A hand-written section is not an assembly of the version being built.
        with self.assertRaises(changelog.Failure):
            changelog.verify_changelog_matches_base(self.root, BASE, self.assembled("9.9.9"))

    def test_an_assembly_that_also_edits_the_preamble_fails(self):
        # A release PR is the one PR allowed to touch this file, so it is also the
        # one place another edit could ride along. Same *length* as the original,
        # so the inserted slice is still exactly the new section and only comparing
        # the preamble byte for byte catches it.
        edited = self.assembled().replace("# Changelog", "# CHANGELOG", 1)
        self.assertEqual(len(edited), len(self.assembled()))
        with self.assertRaises(changelog.Failure):
            changelog.verify_changelog_matches_base(self.root, BASE, edited)

    def test_an_assembly_that_also_rewords_a_published_line_fails(self):
        # The same trick below the insertion point: only comparing the tail byte
        # for byte catches it.
        reworded = self.assembled().replace("An old entry", "An OLD entry", 1)
        self.assertEqual(len(reworded), len(self.assembled()))
        with self.assertRaises(changelog.Failure):
            changelog.verify_changelog_matches_base(self.root, BASE, reworded)

    def test_two_sections_at_once_fails(self):
        doubled = self.assembled()
        doubled = doubled.replace("## [1.0.0]", "## [1.5.0] - 2020-01-15\n\n## [1.0.0]", 1)
        with self.assertRaises(changelog.Failure):
            changelog.verify_changelog_matches_base(self.root, BASE, doubled)


class BaseLookup(unittest.TestCase):
    """`base_changelog` must read a real commit, and fail loudly if it cannot.

    A lookup that degrades to an empty string would make every comparison above
    read as "unchanged" -- the inert-guard shape this issue is about.
    """

    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.git("init", "-q")
        (self.root / "CHANGELOG.md").write_text(BASE, encoding="utf-8")
        self.git("add", "CHANGELOG.md")
        self.git("-c", "user.email=t@t", "-c", "user.name=t",
                 "-c", "commit.gpgsign=false", "commit", "-qm", "base")

    def git(self, *args):
        subprocess.run(["git", "-C", str(self.root), *args], check=True,
                       capture_output=True, text=True)

    def test_reads_the_committed_file_not_the_working_tree(self):
        (self.root / "CHANGELOG.md").write_text(BASE + "- Local edit (#5).\n",
                                                encoding="utf-8")
        self.assertEqual(changelog.base_changelog(self.root, "HEAD"), BASE)

    def test_a_file_absent_at_the_base_is_loud_not_empty(self):
        # `git show` failing while `merge-base` succeeds: the one path where a
        # swallowed git error would return "" and read as "the base was empty".
        (self.root / "other").write_text("x", encoding="utf-8")
        self.git("add", "other")
        self.git("-c", "user.email=t@t", "-c", "user.name=t",
                 "-c", "commit.gpgsign=false", "commit", "-qm", "second")
        self.git("rm", "-q", "CHANGELOG.md")
        self.git("-c", "user.email=t@t", "-c", "user.name=t",
                 "-c", "commit.gpgsign=false", "commit", "-qm", "drop the changelog")
        with self.assertRaises(changelog.Failure):
            changelog.base_changelog(self.root, "HEAD")

    def test_an_unresolvable_ref_is_loud_not_empty(self):
        with self.assertRaises(changelog.Failure):
            changelog.base_changelog(self.root, "no-such-ref")


class VersionDerivation(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.file = self.root / changelog.VERSION_FILE
        self.file.parent.mkdir(parents=True)

    def test_reads_the_version_from_the_build(self):
        self.file.write_text('plugins { }\nversion = "9.9.9"\n', encoding="utf-8")
        self.assertEqual(changelog.derive_version(self.root), "9.9.9")

    def test_refuses_a_snapshot_rather_than_stamping_one(self):
        self.file.write_text('version = "9.9.9-SNAPSHOT"\n', encoding="utf-8")
        with self.assertRaises(changelog.Failure):
            changelog.derive_version(self.root)

    def test_a_failed_derivation_is_loud_not_empty(self):
        self.file.write_text("plugins { }\n", encoding="utf-8")
        with self.assertRaises(changelog.Failure):
            changelog.derive_version(self.root)


if __name__ == "__main__":
    unittest.main(verbosity=2)
