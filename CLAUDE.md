# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

### Task Workflow: implement → automated review → merge

By default, route implementation work through the **automated review process** rather than
committing or pushing directly to `main`. An independent reviewer pass produces better code.

1. **Implement** on a branch (a worktree is fine for parallel work). Before opening review,
   verify locally — this preserves the verification spirit of the older two-phase flow:
   - tests green: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :<module>:jvmTest`
   - style: `spotlessApply` / `ktlintFormat`
   - API: `apiCheck` (run `apiDump` only for an *intended* public-API change)
   - changelog: when the change is one a **user of the library reads about** — behaviour, public
     API, build/release process, or user-facing docs — add **a new file**
     `changelog.d/<issue>.<section>.md` holding the entry. A change with no user-visible effect
     (pure internal refactor, test-only, CI plumbing) legitimately gets none; `check` does not
     require one. Either way **do not edit `CHANGELOG.md`** — it is assembled at release time,
     and `check --base-ref origin/main` refuses any other change to it (#297). Entries still
     carry the issue/PR number. One file per change is the whole point: two PRs never touch the
     same file, so merging one no longer conflicts every other open PR and invalidates its
     approval (#281). `changelog.d/README.md` has the format and the when-to-write-one criterion;
     `python3 .github/scripts/changelog.py check` validates it.
2. **Open a PR via the coderbot wrapper** (`/home/jmonk/git/urithiru/coder-bot/coderbot`),
   authored by `monkopedia-coder`, requesting the reviewer on creation:
   - `coderbot git push -u origin <branch>`
   - `coderbot gh pr create --base main --reviewer monkopedia-reviewer --title "…" --body "…Fixes #<n>…"`
3. **Automated review.** Opening the PR triggers the `monkopedia-reviewer` webhook: a reviewer
   subagent classifies the change by tier, independently re-verifies it (tests / format /
   apiCheck green, per the CI-must-be-green policy), and for tier-1 changes approves and
   squash-merges. Tier-2/3 changes are surfaced to the user instead of auto-merging.

**Key constraints:**
- Do NOT push directly to `main`, and do NOT self-merge — `monkopedia-reviewer` merges.
- Author PRs as `monkopedia-coder` via coderbot and always pass `--reviewer monkopedia-reviewer`;
  PRs opened outside this flow sit unreviewed.
- Keep each PR to one focused change and reference the issue (`Fixes #<n>`).
- CI must be green before merge — fix ALL CI issues, don't just report them. **"Green" means the
  PR's actual GitHub Actions checks have completed and passed — `check` (JVM/wasmJs/Android),
  `check-macos`, and `check-ios` (native). The reviewer MUST wait for these (e.g. `gh pr checks <n>
  --watch`, or `gh pr merge <n> --squash --auto`) before merging; a local `./gradlew` re-run is a
  fast first pass, NOT a substitute — it does not run the macOS/iOS native suite. `main` is branch-
  protected requiring these three checks, so merges block until they pass.** Do not merge while
  checks are still in progress.
- Implementation branches may proceed in parallel; the reviewer serializes merges.

The legacy two-phase pattern (work agent in a worktree → separate review agent merges to `main`
without a PR) in `docs/post-task-workflow.md` is superseded by this automated PR review for
routine work.

### Parity tests — a binding obligation on BOTH the coder and the reviewer

Some tests carry a `parity:` comment naming a counterpart in the other suite (a `commonTest` twin
names a Playwright test, and the Playwright test names the twin). The full rules and the harness
split are in `docs/testing-strategy.md`; the obligation itself is here because it binds every PR.

- **Coder:** if your PR changes, renames or deletes a test carrying a `parity:` comment, you MUST
  open its named counterpart in the same PR and state in the PR body whether the counterpart needed
  to change. Exactly three answers are acceptable — both updated; counterpart deliberately unchanged
  *with the reason*; or parity intentionally broken and the `parity:` comments removed from both
  sides in that same PR. **"I didn't look" is not one of them.**
- **Reviewer:** for any PR touching a file containing `parity:` comments, you MUST `grep` for the
  named counterparts yourself and confirm the PR body addresses them. A PR that changes a
  parity-annotated test without mentioning its counterpart is `request_changes` — not a nit. **Do
  not accept the author's assertion that the counterpart is unaffected without checking it**; that
  is the same verify-don't-relay standard this pipeline applies everywhere else.

An orphaned `parity:` comment — one whose named counterpart no longer exists or no longer matches —
is the specific decay this rule exists to prevent, and is itself grounds for `request_changes`.

### Screenshot Compare & Fix

When the user asks to compare screenshots or fix visual differences, follow the workflow in
`docs/screenshot-compare-workflow.md`. Summary:

1. **Capture** both CodeMirror reference and Compose screenshots (skip reference if already captured)
2. **Compare** by reading both PNGs for each scenario
3. **Build a fix list** — one `TaskCreate` per visual difference, with scenario/description/severity
4. **Report the list** to the user before starting fixes
5. **Fix loop** — for each item: fix code via the automated review workflow (see Task Workflow above), then re-capture and re-compare
6. **Repeat** until all scenarios match or a blocker is hit
7. **Report final status** — what was fixed, what still differs, any blockers

Each fix should be a single focused PR via the automated review workflow.

### Release process

Publishing is irreversible and runs only on maintainer go. Git tags and GitHub Releases exist
**only** for fully-published versions; a partial/failed publish is a burned version — skip it and
cut the next patch.

1. **Version-bump PR** (normal coderbot → reviewer flow): set `version = "X.Y.Z"` (drop the
   `-SNAPSHOT` suffix) in both `convention-plugins/src/main/kotlin/kodemirror.library.gradle.kts`
   and `kodemirror-bom/build.gradle.kts`; **assemble the `changelog.d/` fragments** into a new
   `## [X.Y.Z] - <date>` section (below); **and bump the hardcoded Maven coordinates in the
   user-facing docs** — `README.md` and `docs-site/docs/**` — to `X.Y.Z`, including the "This is vX.Y.Z"
   line. Those coordinates carry no `-SNAPSHOT`, so grepping for `SNAPSHOT` can never find them;
   grep for the *previous* released version instead and read each hit before touching it — a
   dependency snippet or a current-version claim gets bumped, a genuine historical reference
   ("changed in <that version>", "if you are upgrading from <that version>") does not, so do not
   blind-`sed` it. **Derive the version from the tag list; never copy a literal out of this
   document.** The coordinate `com.monkopedia.kodemirror:<module>` is a fact about the artefact
   and stays true, but "the previous release is 0.3.5" is a fact about a queue and is false the
   moment the next version ships — a hardcoded version here would then match nothing and *read
   as though the docs were already clean*, skipping the step this block exists to enforce:

   ```bash
   PREV=$(git describe --tags --abbrev=0 --match 'v*' | sed 's/^v//')
   echo "superseding: $PREV"                    # positive control: must name the last release
   grep -rn "$PREV" README.md docs-site/docs/
   ```

   **Zero hits means `$PREV` is wrong — not that the docs are clean.** An empty result here is
   the failure mode, not the pass condition; read the control line before believing it.

   Omitting this step is what left the published docs advertising a superseded version at every
   release cut after the first: `README.md` and `docs-site/` still said `0.1.0` at v0.2.0,
   v0.3.0, v0.3.2, v0.3.3, v0.3.4 and v0.3.5, and said `0.3.5` at v0.3.6.

   `CHANGELOG.md` has **no `## [Unreleased]` section** — entries accumulate as one file per
   change under `changelog.d/` so that open PRs cannot conflict on them (#281). Assemble them
   *after* setting the version files, since the assembler reads the version from the build
   rather than taking a literal:

   ```bash
   python3 .github/scripts/changelog.py preview          # read the entries in aggregate first
   python3 .github/scripts/changelog.py assemble         # writes the section, deletes the fragments
   git add -A changelog.d/ CHANGELOG.md
   ```

   **`preview` is the point at which someone reads the entries together**, which is when a wrong
   default or a wrong test count is most visible — two such defects were caught that way and
   both were in changelog diffs a reviewer had been trained to skim. `assemble` refuses to run
   on an empty `changelog.d/`, refuses a `-SNAPSHOT` or underivable version rather than
   producing an empty one, refuses a version whose `## [X.Y.Z]` heading is already in the file
   (the `-SNAPSHOT` guard does not cover that — the post-release bump is usually skipped, so
   `main` normally reads an already-released version, #297), and verifies its own output is
   **purely additive**: it strips the new section back out and requires the remainder to match
   the previous file byte for byte, so a published section cannot be silently reworded. It aborts
   and writes nothing if any of that fails.
2. **Tag** `vX.Y.Z` on the merged release commit and push the tag.
3. **Dispatch `deploy.yml`** (`gh workflow run deploy.yml`). A single macOS runner publishes all
   targets (incl. the BOM) to Maven Central in one Gradle invocation — one atomic Central Portal
   deployment, validated + auto-released. After it succeeds, the `release` job auto-creates the
   matching GitHub Release (`vX.Y.Z`) from the CHANGELOG section. The job skips `-SNAPSHOT`/`-RC`
   versions and is re-run safe (no-op if the release already exists). `--verify-tag` fails the job
   if the tag was never pushed.
4. **Verify** the BOM resolves on Maven Central (`repo1.maven.org`); propagation can take 10–35 min.
5. **Notify** downstream consumers.

`docs/release-checklist.md` is the long-form operational companion to these steps — the exact
commands, the CI gates to check first, the post-release SNAPSHOT bump. **This section is
authoritative for the release process: where the checklist disagrees with it, this section wins
and the checklist is the file that gets corrected.** The checklist may add detail; it may not
contradict. (`docs/` remains authoritative for architecture and design decisions — a separate
concern from release mechanics.)

### General

- When the user says to "always" do something, record that instruction in this file.

## Architecture & Decisions

- Maintain comprehensive lists of decisions and architecture notes in the `docs/` folder.
