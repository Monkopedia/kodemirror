# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

### Post-Task Completion

When implementation work is complete, ALWAYS invoke a `general-purpose` subagent with `model: sonnet`
to handle the post-task workflow. See `docs/post-task-workflow.md` for the full pattern.

The subagent should:
1. Fix code style automatically (spotlessApply, ktlintFormat, manual fixes for remaining issues)
2. Run tests - if tests FAIL, try to fix them. Only stop and report if you don't have a clear direction.
3. Commit changes with proper message format
4. Push to remote

Template invocation:
```
Task(subagent_type: "general-purpose", model: "sonnet", description: "Run post-task workflow") {
  prompt: """
    Complete the post-implementation workflow for [TASK_NAME]:

    1. Fix code style:
       - Run ./gradlew spotlessApply
       - Run ./gradlew ktlintFormat
       - Fix remaining issues (long lines, empty files)

    2. Run tests (must match CI — see .github/workflows/ci.yml):
       - Run ./gradlew jvmTest -x :collab:jvmTest :state:wasmJsTest :collab:wasmJsTest :lezer-common:wasmJsTest :lezer-highlight:wasmJsTest :lezer-lr:wasmJsTest
       - If FAIL: try to fix the failures yourself. Only STOP and report
         if you don't have a clear direction for the fix.

    3. Commit:
       - git add .
       - Commit with heredoc message including Co-Authored-By

    4. Push:
       - git push
  """
}
```

### Screenshot Compare & Fix

When the user asks to compare screenshots or fix visual differences, follow the workflow in
`docs/screenshot-compare-workflow.md`. Summary:

1. **Capture** both CodeMirror reference and Compose screenshots (skip reference if already captured)
2. **Compare** by reading both PNGs for each scenario
3. **Build a fix list** — one `TaskCreate` per visual difference, with scenario/description/severity
4. **Report the list** to the user before starting fixes
5. **Fix loop** — for each item: fix code, run post-task workflow (commit+push), then re-capture and re-compare
6. **Repeat** until all scenarios match or a blocker is hit
7. **Report final status** — what was fixed, what still differs, any blockers

Each fix should be a single focused commit. If tests fail during the post-task workflow, try to fix them.
Only stop and report if there's no clear direction for the fix.

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

5. **After implementing:** Run the post-task workflow via a `general-purpose` subagent with
   `model: sonnet` (see "Post-Task Completion" above). The subagent should:
   - Fix code style (`spotlessApply`, `ktlintFormat`, manual fixes)
   - Run tests (`./gradlew jvmTest -x :collab:jvmTest :state:wasmJsTest :collab:wasmJsTest :lezer-common:wasmJsTest :lezer-highlight:wasmJsTest :lezer-lr:wasmJsTest`)
   - If tests fail, try to fix them. If unfixable, mark the item `[BLOCKED]` with the failure info.
   - If tests pass, commit with a descriptive message and push.

6. **Mark the item `[DONE]`** in `docs/TODO.md` after a successful commit+push.

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
