# `:view` Roborazzi screenshot tests

The `:view` module renders the editor into pixel baselines and compares them in CI so
that visual regressions (a missing highlight fill, a shifted caret, a broken gutter) fail
the build instead of slipping through a green CI (see #86 / #89).

## Layout

- Tests: `view/src/jvmTest/kotlin/com/monkopedia/kodemirror/view/screenshots/*ScreenshotTest.kt`
- Shared scenario + capture helpers: `.../screenshots/TestScenarios.kt`
- Committed baselines: `view/screenshots/compose/*.png`
- Pinned font + license: `view/src/jvmTest/resources/fonts/JetBrainsMono-Regular.ttf`,
  `view/src/jvmTest/resources/fonts/OFL.txt`

## Why a pinned font

The editor's default content font is `FontFamily.Monospace`, which resolves to whatever
monospace font the host OS provides. Different machines have different fonts (and even the
same family can differ by version), so glyph advance widths — and therefore the rendered
pixels — drift between the dev machine and the CI runner. Baselines recorded on one machine
then fail `verifyRoborazzi` on another for reasons unrelated to any code change.

The fix: the screenshot tests force a **bundled JetBrains Mono Regular** (OFL-1.1), loaded
from the test classpath via the Compose Desktop `Font(resource: String)` factory
(`TestScenarios.pinnedFont`). Compose Desktop rasterises text with Skiko, which is bundled
with the Compose dependency (it does *not* use OS-native rasterisation), so the same font
plus the same Skiko version produces the same pixels everywhere. CI runs on `ubuntu-latest`
and baselines are recorded on Linux x86_64, which share that Skiko native build.

## Tolerance

Even with a pinned font the comparison allows a small **0.1% pixel-change threshold**
(`TestScenarios.screenshotOptions`, `changeThreshold = 0.001f`) as a safety margin against
incidental antialiasing noise. It is small enough that a real regression still trips the
gate (verified by corrupting a baseline) while sub-pixel noise does not make the gate flaky.

## Commands

```bash
# Record / refresh baselines (after an intentional rendering change):
./gradlew :view:recordRoborazziJvm

# Verify against committed baselines (this is what CI runs):
./gradlew :view:verifyRoborazziJvm
```

Prefix gradle with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` on this machine.

`verifyRoborazziJvm` runs in CI as part of the `check` job (`.github/workflows/ci.yml`).

## When a `:view` rendering change is intentional

1. Make the change.
2. `./gradlew :view:recordRoborazziJvm` to regenerate baselines.
3. Eyeball the updated `view/screenshots/compose/*.png` to confirm the diff is the intended
   change and nothing else regressed.
4. Commit the regenerated PNGs with the code change.

If `verifyRoborazziJvm` fails on CI for a PR that did *not* intend a rendering change, that
is a regression — inspect `view/build/outputs/roborazzi/*_compare.png` in the build output.
