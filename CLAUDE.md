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
   - update `CHANGELOG.md` (Unreleased) with the issue/PR number
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
- CI must be green before merge — fix ALL CI issues, don't just report them.
- Implementation branches may proceed in parallel; the reviewer serializes merges.

The legacy two-phase pattern (work agent in a worktree → separate review agent merges to `main`
without a PR) in `docs/post-task-workflow.md` is superseded by this automated PR review for
routine work.

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

### TODO Loop

When the user asks to "work through the TODO list", "do the next TODO", or similar, follow this
loop for each item in `docs/TODO.md`:

#### Loop Steps

1. **Read `docs/TODO.md`** and find the next item that has no status prefix (i.e. not `[DONE]`,
   `[BLOCKED]`, or `[SKIP]`). Process items in order (lowest number first).

2. **Assess the item.** Read any files referenced in the item. Determine if it can be completed
   now or if it is blocked by something (missing context, dependency on another item, architectural
   question that needs user input, etc.).

3. **If blocked:** Mark the item `[BLOCKED]` in `docs/TODO.md` and add a `- **Blocked:**` line
   explaining why. Report the blocker to the user, then continue to the next item.

4. **If actionable:** Implement the change. Keep changes focused to what the item describes — do
   not scope-creep into adjacent items.

5. **After implementing:** Follow the automated review workflow (see Task Workflow above) —
   open a coderbot PR requesting `monkopedia-reviewer`. If the reviewer requests changes you
   can't resolve, or CI can't be made green, mark the item `[BLOCKED]` with the failure info.

6. **Mark the item `[DONE]`** in `docs/TODO.md` after the PR is merged.

7. **Report** a brief summary of what was done (or why it was blocked), then **continue to the
   next item**. Do not stop between items unless the user asks to stop or a blocker needs user input.

#### Status Format in `docs/TODO.md`

```markdown
### 1. [DONE] Convert `Text.kt` comments to KDoc

### 3. [BLOCKED] Make lezer-lr internal state `internal`
- **Blocked:** Requires `-internal` module split; needs architectural decision on module boundaries.

### 4. [SKIP] Add `basicSetup` convenience bundle
- **Skip reason:** User decided to defer until after 1.0.
```

#### Important Rules

- **One commit per item.** Each TODO item gets its own focused commit.
- **Do not skip ahead.** Process items in numerical order. If an item is blocked, mark it and
  move to the next one — do not jump to a later priority tier unless all earlier items are
  done/blocked/skipped.
- **Don't stop to ask questions.** If an item requires a design decision (e.g. "Should `tags` be
  renamed to `Tags` with a deprecation cycle or a hard rename?"), mark it `[BLOCKED]` with the
  question in the blocked reason, then move on to the next item. The user will review blocked
  items and provide answers later.
- **Research before implementing.** For items that reference upstream CodeMirror APIs, read the
  relevant Kodemirror source files first. For completeness items, check what upstream provides
  before porting.
- **Update the TODO file atomically** with each status change — include it in the same commit as
  the implementation work.

### General

- When the user says to "always" do something, record that instruction in this file.

## Architecture & Decisions

- Maintain comprehensive lists of decisions and architecture notes in the `docs/` folder.
