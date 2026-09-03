# Changelog fragments

**Do not edit `CHANGELOG.md`.** Add a file here instead. `CHANGELOG.md` is assembled from these
files at release time and has no `[Unreleased]` section.

## When an entry is expected

**Not every pull request needs a fragment, and none is required.**
`python3 .github/scripts/changelog.py check` validates the fragments that are *present*; a PR
adding none passes, deliberately. Write one when the change is something a **user of the library
reads about**:

- user-visible behaviour, a bug fix, or a performance change
- public API — anything that moves `api/*.api`
- the build, packaging, or release process, and supported platforms or versions
- documentation a user reads (`README.md`, `docs-site/`)

Legitimately omit one for a change with no user-visible effect: a pure internal refactor, a
test-only change, CI plumbing, or repository-internal notes. A blanket requirement would only
produce a permanent stream of `N.internal.md` files reading "internal refactor", which makes the
released section *harder* to read, and whether a change is user-visible is a judgement no script
can make — asking for it every time turns that judgement into a rubber stamp.

When in doubt, write one: an unnecessary entry is deleted in review, a missing one is noticed
after the release.

## Adding an entry

Create `changelog.d/<issue>.<section>.md` containing the markdown bullet(s) your change should
appear as in the released changelog:

```
changelog.d/281.build.md
changelog.d/294.fixed.md
changelog.d/301.documentation.md
```

The body is copied into the release section **verbatim**, so write it exactly as it should read:

```markdown
- Fixed the thing that was broken (#294). One or two sentences on what was wrong and how it was
  established, in the past tense, the way the surrounding entries read.
```

Rules, all of them checked by `python3 .github/scripts/changelog.py check`:

- **`<issue>`** is the GitHub issue number (the PR number if there is no issue), digits only. The
  body must mention `#<issue>` at least once, so a reader of the released section can follow it.
- **`<section>`** is one of `added`, `changed`, `deprecated`, `removed`, `fixed`, `security`,
  `performance`, `build`, `tests`, `documentation`, `internal`. It becomes the `###` heading.
- The body starts with a `- ` bullet and contains no headings of its own. Multiple bullets and
  indented sub-bullets are fine — an entry is not required to be a single bullet.
- One issue needing two fragments in the same section (two PRs, one issue) adds a discriminator:
  `281.fixed.followup.md`.

## What `check` refuses

Beyond the fragment rules above, `check` reads `CHANGELOG.md` itself:

- a `## [Unreleased]` heading — #295 removed it, so its reappearance means an entry is being
  written where nothing will ever assemble it;
- any other change to `CHANGELOG.md`, when given a base to compare against — as CI does,
  passing the pull request's base commit. The only accepted change is a release assembly: one
  new `## [X.Y.Z]` section at the top, matching the version in the build files, with every
  other byte untouched.

That second check exists because removing `[Unreleased]` also removed the conflict that used to
force a human to look. A branch written for the old layout now merges **cleanly** — git places
the hunk at the nearest surviving context, which is inside an already-published section — so
nothing else in the pipeline would notice (#297). Locally, name the branch you are based on:

```bash
python3 .github/scripts/changelog.py check --base-ref origin/main
```

## Why files instead of one shared block

Every pull request used to append to the same `## [Unreleased]` block, so **merging any one PR
conflicted every other open PR** — on a file that had nothing to do with the code under review.
That cost more than the rebases: a GitHub approval pins to a commit, so each forced rebase
invalidated a review that had already passed, and the resulting stream of changelog-only conflicts
trained reviewers to skim exactly the diffs where changelog defects hide (#281).

Two different files never conflict, so pull-request order stops mattering.

## At release time

The release cut runs, after bumping the version files:

```bash
python3 .github/scripts/changelog.py preview     # the assembled section; writes nothing
python3 .github/scripts/changelog.py assemble    # writes CHANGELOG.md and deletes the fragments
```

See `CLAUDE.md` -> "Release process" step 1.
