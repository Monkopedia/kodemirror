# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

### Post-Task Completion

When implementation work is complete, ALWAYS invoke a `general-purpose` subagent with `model: sonnet`
to handle the post-task workflow. See `docs/post-task-workflow.md` for the full pattern.

The subagent should:
1. Fix code style automatically (spotlessApply, ktlintFormat, manual fixes for remaining issues)
2. Run tests - if tests FAIL, stop and report back (do NOT fix tests)
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

    2. Run tests:
       - Run ./gradlew check
       - If FAIL: STOP and report (do not fix tests)

    3. Commit:
       - git add .
       - Commit with heredoc message including Co-Authored-By

    4. Push:
       - git push
  """
}
```

### General

- When the user says to "always" do something, record that instruction in this file.

## Architecture & Decisions

- Maintain comprehensive lists of decisions and architecture notes in the `docs/` folder.
