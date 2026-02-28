# Screenshot Compare & Fix Workflow

## Purpose

Iteratively compare Compose rendering against CodeMirror 6 reference screenshots, identify visual differences, fix them one at a time, and repeat until the output matches or a blocker is hit.

## Trigger

User says: "compare screenshots", "compare them", "fix visual differences", or similar.

## Workflow Steps

### Phase 1: Capture Screenshots

1. **Capture CodeMirror references** (skip if `reference-screenshots/output/` already has recent PNGs):
   ```bash
   cd reference-screenshots && npm install && npx playwright install chromium && npx playwright test
   ```
   Or: `./gradlew captureReferenceScreenshots`

2. **Capture Compose screenshots**:
   ```bash
   ./gradlew :view:recordRoborazziDesktop
   ```

### Phase 2: Compare & Build Fix List

3. **Read both sets of images** — for each scenario, read:
   - `reference-screenshots/output/<scenario>.png` (CodeMirror reference)
   - `view/screenshots/compose/<scenario>.png` (Compose output)

4. **Create a numbered fix list** using `TaskCreate`, one task per distinct visual difference. Each task should describe:
   - Which scenario(s) it affects
   - What looks different (e.g., "gutter background color is wrong", "line spacing too tight", "selection not rendering")
   - Severity: **blocking** (fundamentally broken), **major** (clearly wrong), **minor** (subtle difference)

5. **Report the fix list to the user** before starting fixes, so they can reprioritize or skip items.

### Phase 3: Fix Loop

For each fix item (in priority order):

6. **Mark task in_progress**, investigate the relevant Compose code, and implement the fix.

7. **Run post-task workflow** (sonnet subagent): spotlessApply, ktlintFormat, check, commit, push.
   - If tests fail, try to fix them. Only stop and report if there's no clear direction.

8. **After all current fixes are committed**, re-capture Compose screenshots:
   ```bash
   ./gradlew :view:recordRoborazziDesktop
   ```

9. **Re-compare** — read the updated Compose PNGs against the same references.
   - If new differences are found, add them as new tasks and continue the loop.
   - If an issue persists after a fix attempt, flag it as a **blocker** and report to user.

### Phase 4: Done

10. **Exit when**:
    - All scenarios look visually equivalent (minor rendering engine differences are acceptable), OR
    - A blocker is hit that requires user input or upstream changes.

11. **Report final status** — summary of what was fixed, what still differs, and any blockers.

## File Locations

| Path | Description |
|------|-------------|
| `reference-screenshots/output/<scenario>.png` | CodeMirror 6 reference images |
| `view/screenshots/compose/<scenario>.png` | Compose output images |
| `reference-screenshots/scenarios/<scenario>.html` | HTML fixtures (CodeMirror config) |
| `view/src/jvmTest/kotlin/.../screenshots/` | Roborazzi test classes |
| `view/src/jvmTest/kotlin/.../screenshots/TestScenarios.kt` | Shared sample content |

## Adding a New Scenario

1. Create `reference-screenshots/scenarios/<name>.html`
2. Create matching test in `view/src/jvmTest/kotlin/.../screenshots/<Name>ScreenshotTest.kt`
3. Re-run both capture commands

## Notes

- Minor pixel-level differences between browser rendering and Compose are expected and acceptable.
- Focus on structural/layout issues, color mismatches, missing elements, and spacing problems.
- Each fix should be a single focused commit so regressions are easy to bisect.
