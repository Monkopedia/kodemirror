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

### General

- When the user says to "always" do something, record that instruction in this file.

## Architecture & Decisions

- Maintain comprehensive lists of decisions and architecture notes in the `docs/` folder.
