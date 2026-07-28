# Task Workflow: Work → Review → Merge

> **SUPERSEDED — do not follow this document.**
>
> The authoritative workflow is `CLAUDE.md` § *Task Workflow: implement → automated review →
> merge*: implement on a branch, open a PR as `monkopedia-coder` via the coderbot wrapper with
> `--reviewer monkopedia-reviewer`, and let the reviewer merge.
>
> Specifically, the merge steps below are now **forbidden**. Do NOT `git checkout main`, do NOT
> `git merge` a branch into `main`, and do NOT `git push` to `main` — pushing directly to `main`
> and self-merging are both prohibited, and `main` is branch-protected requiring CI to be green.
>
> This file is retained only as a record of the older two-phase (work agent → review agent,
> no PR) pattern.

## Overview

All implementation work follows a two-phase agent workflow:

1. **Work agent** — implements the change in an isolated worktree branch
2. **Review agent** — reviews the diff, fixes style/tests, merges to main, pushes

Multiple work agents can run in parallel (each in its own worktree). Review agents
run **one at a time** — the main agent serializes them to avoid merge conflicts on main.

## Phase 1: Work Agent

Launch a work agent in a worktree to implement the task. The agent gets an isolated
copy of the repo and commits on its own branch.

```
Agent(
  subagent_type: "general-purpose",
  model: "sonnet",  // or "opus" for complex tasks
  isolation: "worktree",
  run_in_background: true,  // parallel OK
  description: "Implement [TASK]",
  prompt: """
    [TASK DESCRIPTION]

    Build environment: JAVA_HOME=/usr/lib/jvm/java-21-openjdk before any ./gradlew command.

    When done, commit your changes with a descriptive message. Do NOT push.
    Do NOT run style fixes, apiDump, or tests — the review agent handles that.
  """
)
```

**Key rules for work agents:**
- Commit changes on the worktree branch
- Do NOT fix style, run apiDump, or run tests — that's the review agent's job
- Do NOT push
- Multiple work agents can run in parallel

## Phase 2: Review Agent

After a work agent completes, launch a **foreground** review agent to verify and merge.
Only one review agent runs at a time.

```
Agent(
  subagent_type: "general-purpose",
  model: "sonnet",
  description: "Review and merge [TASK]",
  prompt: """
    Review and merge the changes from branch [BRANCH] in worktree [WORKTREE_PATH].

    1. Review the diff:
       - Run: git diff main...[BRANCH]
       - Check for correctness, obvious bugs, unnecessary changes
       - If the changes are fundamentally wrong, STOP and report why

    2. Merge to main:
       - git checkout main
       - git merge [BRANCH]

    3. Fix code style:
       - Run: JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew spotlessApply
       - Run: JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew ktlintFormat
       - Fix any remaining issues manually (long lines, empty files)

    4. Update API dumps:
       - Run: JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew apiDump

    5. Run tests:
       - Run: JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew jvmTest :state:wasmJsTest :collab:wasmJsTest :lezer-common:wasmJsTest :lezer-highlight:wasmJsTest :lezer-lr:wasmJsTest
       - If tests FAIL: try to fix them. Only STOP and report if there's no
         clear direction for the fix.

    6. Commit and push:
       - Amend or create a new commit as appropriate
       - git push

    7. Close related GitHub issues:
       - If the task fixes a GitHub issue, comment on it noting the fix
         and what release it will be in (e.g. "Fixed in 0.2.0-SNAPSHOT,
         will ship in the next release."), then close the issue.
       - Use: gh issue comment NUMBER --body "MESSAGE"
       - Use: gh issue close NUMBER

    IMPORTANT: If review finds substantive issues (wrong approach, missing cases,
    broken logic), STOP and report. Don't try to rewrite the implementation —
    that's a new work agent's job. Minor fixes (style, imports, small bugs) are
    fine to fix in-place.
  """
)
```

**Key rules for review agents:**
- Run in **foreground** (not background) — this serializes them
- Fix minor issues (style, imports, small bugs) directly
- Reject and report substantive problems — don't rewrite implementations
- Only one review agent merges to main at a time

## Orchestration Pattern

The main agent orchestrates like this:

```
# Launch multiple work agents in parallel (background, worktree)
work1 = Agent(worktree, background) → "Implement feature A"
work2 = Agent(worktree, background) → "Implement feature B"

# As each completes, review serially (foreground)
# work1 completes first:
Agent(foreground) → "Review and merge work1's branch"

# work2 completes:
Agent(foreground) → "Review and merge work2's branch"
```

## Error Handling

- **Style/lint failures**: Review agent fixes them automatically
- **Test failures**: Review agent tries to fix. If unclear, reports back.
- **Merge conflicts**: Review agent reports. Main agent decides resolution.
- **Bad implementation**: Review agent reports issues. Main agent launches a new work agent.
- **Review agent itself fails**: Main agent reads the output and either retries or fixes manually.
