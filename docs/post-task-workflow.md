# Post-Task Workflow Pattern

## Purpose

After completing implementation work, use a `general-purpose` subagent to handle routine verification and commit tasks. The subagent should:

1. **Automatically fix**: ktlint violations, license headers (spotless)
2. **Fix test failures** if the fix is clear; only report back if there's no clear direction
3. **Commit and push**: if all checks pass

## Subagent Prompt Template

```
Complete the post-implementation workflow for [TASK_DESCRIPTION]:

1. Fix code style:
   - Run `./gradlew spotlessApply` to fix license headers
   - Run `./gradlew ktlintFormat` to fix auto-correctable ktlint violations
   - If ktlint still fails, read the error report and manually fix remaining issues (line length, etc.)

2. Run tests:
   - Run `./gradlew check`
   - If tests FAIL, try to fix them. Only STOP and report if you don't have a clear direction.
   - If linting fails after step 1, fix any remaining issues

3. Commit changes:
   - Stage all changes with `git add .`
   - Create a commit with message:
     ```
     [COMMIT_MESSAGE]

     Co-Authored-By: Claude [MODEL] <noreply@anthropic.com>
     ```
   - Use a heredoc for the commit message to ensure proper formatting

4. Push to remote:
   - Run `git push`

IMPORTANT: If tests fail in step 2, try to fix them yourself. Only STOP and report if you don't have a clear direction for the fix. Do not commit or push until tests pass.
```

## Usage Example

```kotlin
// After implementing a feature:
invoke Task {
    subagent_type = "general-purpose"
    description = "Run post-task workflow"
    prompt = """
        Complete the post-implementation workflow for Phase 1 state module port:

        1. Fix code style:
           - Run `./gradlew spotlessApply` to fix license headers
           - Run `./gradlew ktlintFormat` to fix auto-correctable ktlint violations
           - If ktlint still fails, read the error report and manually fix remaining issues

        2. Run tests:
           - Run `./gradlew check`
           - If tests FAIL, try to fix them. Only STOP and report if you
             don't have a clear direction for the fix.
           - If linting fails, fix any remaining issues

        3. Commit changes:
           - Stage all changes with `git add .`
           - Create commit: "Phase 1: Port @codemirror/state module\n\nCo-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

        4. Push to remote:
           - Run `git push`
    """
}
```

## Error Handling

- **ktlint/spotless failures**: Subagent reads error reports and fixes them (wrap long lines, add content to empty files, etc.)
- **Test failures**: Subagent tries to fix them. Only escalates to outer agent if the fix direction is unclear.
- **Git conflicts**: Subagent reports and stops (outer agent handles)

## Rationale

This pattern delegates as much as possible to the subagent. Style issues are fixed automatically. Test failures should be attempted — the subagent has full access to read code and make fixes. Only truly ambiguous failures (unclear root cause, multiple possible fixes, architectural questions) should be escalated.
