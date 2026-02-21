# Post-Task Workflow Pattern

## Purpose

After completing implementation work, use a `general-purpose` subagent to handle routine verification and commit tasks. The subagent should:

1. **Automatically fix**: ktlint violations, license headers (spotless)
2. **Report failures**: test failures (defer to outer agent)
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
   - If tests FAIL, STOP and report the failure details (do not attempt to fix tests)
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

IMPORTANT: If tests fail in step 2, STOP immediately and return the test output. Do not commit or push. The outer agent will handle test failures.
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
           - If tests FAIL, STOP and report the failure details
           - If linting fails, fix any remaining issues

        3. Commit changes:
           - Stage all changes with `git add .`
           - Create commit: "Phase 1: Port @codemirror/state module\n\nCo-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

        4. Push to remote:
           - Run `git push`

        IMPORTANT: If tests fail in step 2, STOP and return test output.
    """
}
```

## Error Handling

- **ktlint/spotless failures**: Subagent reads error reports and fixes them (wrap long lines, add content to empty files, etc.)
- **Test failures**: Subagent immediately stops and returns failure details to outer agent
- **Git conflicts**: Subagent reports and stops (outer agent handles)

## Rationale

This pattern separates routine mechanical tasks (formatting, linting) from semantic issues (test failures). The subagent can autonomously fix style issues using the error reports, but test failures require understanding the code logic, so those are escalated.
