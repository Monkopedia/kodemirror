#!/usr/bin/env python3
"""Tests for `changelog.py`. Run: `python3 .github/scripts/test-changelog.py`.

Plain asserts, no dependencies -- this runs in the CI `check` job next to the
fragment validation, and the repo has no Python test harness to hook into.

Every safety property here is one the script is supposed to have; each is tested
with a **negative control** that must fail, because a guard that cannot be shown
to fire is indistinguishable from no guard at all.
"""

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
        result = changelog.assemble(self.changelog, self.section, self.fragments)
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
