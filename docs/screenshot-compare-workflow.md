# Screenshot Compare & Fix Workflow

## Purpose

Compare Kodemirror's rendering against CodeMirror 6, identify visual differences, fix them one
at a time, and repeat until the output matches or a blocker is hit.

## Trigger

User says: "compare screenshots", "compare them", "fix visual differences", or similar.

## The two screenshot rigs

There are two, and they answer different questions. Pick deliberately.

| | `:view` Roborazzi | `gap-analysis` Playwright |
|---|---|---|
| Renders | Compose Desktop (JVM, Skiko) | CodeMirror 6 in Chromium, and the wasmJs showcase |
| Baselines | committed: `view/screenshots/compose/*.png` | not committed — `gap-analysis/screenshots/` is gitignored, regenerate on demand |
| Runs in CI | yes, `:view:verifyRoborazziJvm` in the `check` job | no |
| Good for | locking Compose rendering against regression | seeing what CM6 actually does, and what wasmJs actually paints |

Roborazzi is the **regression gate**. The gap-analysis rig is the **reference**: it is where the
CodeMirror 6 ground truth lives now. (The old standalone `reference-screenshots/` directory was
folded into `gap-analysis/` in commit `8a27c44`; the fixtures and the capture spec moved, nothing
was lost.)

The two share scenario names deliberately, so `<scenario>.png` from either rig is the same
scenario: `gap-analysis/fixtures/cm6-scenarios/basic-light.html` is the CM6 side of
`view/screenshots/compose/basic-light.png`. Both render at an 800x600 viewport.

Prefix every Gradle command below with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` on this machine.

## Workflow Steps

### Phase 1: Capture

1. **Capture the CodeMirror 6 references** (skip if `gap-analysis/screenshots/cm6-reference/`
   already has recent PNGs — it is gitignored, so a fresh checkout will not have them):

   ```bash
   cd gap-analysis
   npm install                     # first time only
   npx playwright install chromium # first time only
   npm run test:cm6-ref            # == npx playwright test --project=cm6-reference
   ```

   This writes one PNG per fixture in `gap-analysis/fixtures/cm6-scenarios/` to
   `gap-analysis/screenshots/cm6-reference/<scenario>.png`. It runs headless and needs **network
   access** — the fixtures load CodeMirror from `esm.sh` via an import map.

2. **Capture the Compose screenshots**:

   ```bash
   ./gradlew :view:recordRoborazziJvm
   ```

   This rewrites the committed baselines in `view/screenshots/compose/*.png`. See
   `docs/roborazzi-screenshots.md` for why the tests pin a bundled font and what the
   0.1% tolerance is for.

### Phase 2: Compare & Build Fix List

3. **Read both sets of images** — for each scenario, read:
   - `gap-analysis/screenshots/cm6-reference/<scenario>.png` (CodeMirror 6 reference)
   - `view/screenshots/compose/<scenario>.png` (Compose output)

   Two Compose baselines have no CM6 counterpart (`completion-popup`, `wrapped-line-cursor`) —
   they are Compose-only regression locks, not comparisons.

4. **Create a numbered fix list** using `TaskCreate`, one task per distinct visual difference.
   Each task should describe:
   - Which scenario(s) it affects
   - What looks different (e.g. "gutter background color is wrong", "line spacing too tight",
     "selection not rendering")
   - Severity: **blocking** (fundamentally broken), **major** (clearly wrong), **minor** (subtle)

5. **Report the fix list to the user** before starting fixes, so they can reprioritize or skip
   items.

### Phase 3: Fix Loop

For each fix item (in priority order):

6. **Mark task in_progress**, investigate the relevant Compose code, and implement the fix.

7. **Ship the fix through the review workflow in `CLAUDE.md`** — one focused PR per fix. Do NOT
   commit to `main` and do NOT self-merge. In short:

   ```bash
   ./gradlew spotlessApply ktlintFormat
   ./gradlew :view:jvmTest :view:verifyRoborazziJvm   # plus apiCheck if public API moved
   # update CHANGELOG.md under [Unreleased] with the issue/PR number
   /home/jmonk/git/urithiru/coder-bot/coderbot git push -u origin <branch>
   /home/jmonk/git/urithiru/coder-bot/coderbot gh pr create --base main \
     --reviewer monkopedia-reviewer --title "..." --body "...Fixes #<n>..."
   ```

   `monkopedia-reviewer` reviews and merges. See `CLAUDE.md` § *Task Workflow* for the
   authoritative version of this flow.

   If the fix intentionally changes `:view` rendering, `verifyRoborazziJvm` will fail until you
   re-record: run `./gradlew :view:recordRoborazziJvm`, eyeball the regenerated PNGs to confirm
   the diff is only the intended change, and commit them alongside the code.

8. **After a fix lands, re-capture the Compose screenshots**:

   ```bash
   ./gradlew :view:recordRoborazziJvm
   ```

9. **Re-compare** — read the updated Compose PNGs against the same references.
   - If new differences are found, add them as new tasks and continue the loop.
   - If an issue persists after a fix attempt, flag it as a **blocker** and report to the user.

### Phase 4: Done

10. **Exit when**:
    - All scenarios look visually equivalent (minor rendering engine differences are acceptable),
      OR
    - A blocker is hit that requires user input or upstream changes.

11. **Report final status** — summary of what was fixed, what still differs, and any blockers.

## When the bug is wasmJs-only

Roborazzi renders on Compose Desktop, so a difference that only shows up in the browser will not
reproduce there. For those, use the live side-by-side specs in `gap-analysis/tests/`
(`tab-render-compare.spec.ts`, `search-panel-compare.spec.ts`, `vim-cursor-compare.spec.ts`,
`vim-prompt-compare.spec.ts`, `completion-popup-paint.spec.ts`), which drive CM6 and the wasmJs
showcase in parallel and screenshot both.

These need the showcase build **and a display**. Skiko/Compose wasmJs needs WebGL, which headless
Chrome does not provide; `gap-analysis/playwright.config.ts` launches headed when `DISPLAY` is
set, and skips the Kodemirror half gracefully when it is not — so a missing display produces a
green CM6-only run, not a failure. Watch for that.

```bash
# 1. Build the showcase distribution the config serves on :8081.
#    The 2 GB default heap in gradle.properties is not enough for the wasmJs link step.
./gradlew :samples:showcase:wasmJsBrowserDevelopmentExecutableDistribution \
  -Dorg.gradle.jvmargs="-Xmx6g -Dfile.encoding=UTF-8"

# 2. Give it a display.
__EGL_VENDOR_LIBRARY_FILENAMES=/dev/null LIBGL_ALWAYS_SOFTWARE=1 \
  Xvfb :99 -screen 0 1024x768x24 -noreset &

# 3. Run.
DISPLAY=:99 npx --prefix gap-analysis playwright test \
  --project=gap-analysis --config=gap-analysis/playwright.config.ts
```

`./gradlew runGapAnalysis` bundles step 1 (it depends on the distribution task) with a bare `npx
playwright test`, i.e. *all* projects — `gap-analysis`, `performance` and `cm6-reference`. It
does not raise the heap and does not set up a display, so run it under an already-exported
`DISPLAY` and `-Dorg.gradle.jvmargs`, or just use the three explicit commands above when
iterating on one difference. `./gradlew generateGapReport` regenerates
`gap-analysis/report/gap-report.md` from the run's `report/results.json`.

Note that both Playwright entry points overwrite `gap-analysis/report/results.json`, which is a
tracked file — check `git status` afterwards and revert it if the run was exploratory.

Caveat from #171: headless CanvasKit renders a **fallback** font, not the app's bundled
Compose-Resources font, so font-specific paint bugs cannot reproduce in this rig — those need a
real browser.

## File Locations

| Path | Description |
|------|-------------|
| `gap-analysis/fixtures/cm6-scenarios/<scenario>.html` | CodeMirror 6 scenario fixtures |
| `gap-analysis/screenshots/cm6-reference/<scenario>.png` | CM6 reference images (gitignored) |
| `gap-analysis/tests/cm6-reference-capture.spec.ts` | The capture spec |
| `gap-analysis/tests/*-compare.spec.ts` | Live CM6 vs wasmJs side-by-side specs |
| `view/screenshots/compose/<scenario>.png` | Compose baselines (committed) |
| `view/src/jvmTest/kotlin/com/monkopedia/kodemirror/view/screenshots/` | Roborazzi test classes |
| `.../screenshots/TestScenarios.kt` | Shared sample content, pinned font, compare options |
| `view/build/outputs/roborazzi/*_compare.png` | Diff images from a failed `verifyRoborazziJvm` |

## Adding a New Scenario

1. Create `gap-analysis/fixtures/cm6-scenarios/<name>.html` (copy an existing one; keep the
   editor at 800x600). The capture spec discovers fixtures by directory listing — no
   registration needed.
2. Create a matching
   `view/src/jvmTest/kotlin/com/monkopedia/kodemirror/view/screenshots/<Name>ScreenshotTest.kt`,
   modelled on `BasicLightScreenshotTest`. Use `runDesktopComposeUiTest(width = 800, height =
   600)`, pull content and the pinned font from `TestScenarios`, and capture to
   `screenshots/compose/<name>.png`.
3. Re-run both capture commands, and commit the new Compose baseline.

## Notes

- Minor pixel-level differences between browser rendering and Compose are expected and
  acceptable. Focus on structural/layout issues, color mismatches, missing elements, and spacing.
- Each fix is a single focused PR so regressions are easy to bisect.
- A `coordsAtPos` ↔ click round-trip is blind to rendered-vs-layout mismatches (both read the
  same `TextLayoutResult`); use a pixel/ink probe when investigating hit-test bugs.
