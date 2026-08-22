import { test } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";
import { CM6Driver } from "../drivers/cm6-driver";

/**
 * Captures what real CodeMirror 6 does for each `navigation.spec.ts` and
 * `selection.spec.ts` sequence, and freezes it as JSON.
 *
 * Same contract as `keymap-expectations-capture.spec.ts`, for the second twin
 * batch of #196: the Playwright suite is *differential* — it asserts
 * KodeMirror == CM6 and never records what either one did — so a `commonTest`
 * twin needs an **absolute** expectation, and the only honest source for it is
 * CM6 itself. Deriving the numbers from KodeMirror would make each twin a
 * tautology that passes by construction on whichever platform generated it.
 *
 * Runs **CM6 only**, against a plain `file://` page with no KodeMirror, no
 * Skiko and no WebGL, so it needs neither the showcase wasm build nor Xvfb.
 *
 * Output: `fixtures/cm6-navigation-selection-expectations.json`. Re-run it when
 * the fixture document changes — every offset below is relative to that
 * document and to nothing else.
 */

type Step =
  | { press: string; times?: number }
  | { type: string };

interface Sequence {
  /** Test name in the spec file, verbatim — this is the parity key. */
  name: string;
  steps: Step[];
}

const cm6FixturePath = path.join(__dirname, "..", "fixtures", "cm6-test.html");
const outputPath = path.join(
  __dirname,
  "..",
  "fixtures",
  "cm6-navigation-selection-expectations.json"
);

const p = (press: string, times?: number): Step => ({ press, times });
const t = (type: string): Step => ({ type });

const HOME = p("Control+Home");
const END = p("Control+End");

/**
 * The 11 sequences from `navigation.spec.ts`, transcribed verbatim including
 * its `beforeEach` (focus only).
 */
const NAVIGATION: Sequence[] = [
  { name: "arrow keys - right", steps: [HOME, p("ArrowRight")] },
  { name: "arrow keys - left", steps: [HOME, p("ArrowRight", 2), p("ArrowLeft")] },
  { name: "arrow keys - down", steps: [HOME, p("ArrowDown")] },
  { name: "arrow keys - up", steps: [END, p("ArrowUp")] },
  { name: "Home key", steps: [HOME, p("ArrowRight", 5), p("Home")] },
  { name: "End key", steps: [HOME, p("End")] },
  { name: "Ctrl+Home - go to document start", steps: [END, HOME] },
  { name: "Ctrl+End - go to document end", steps: [HOME, END] },
  { name: "Ctrl+Right - word movement forward", steps: [HOME, p("Control+ArrowRight")] },
  { name: "Ctrl+Left - word movement backward", steps: [HOME, p("End"), p("Control+ArrowLeft")] },
  { name: "column memory across lines", steps: [HOME, p("End"), p("ArrowDown"), p("ArrowDown")] },
];

/**
 * The 9 sequences from `selection.spec.ts`. Its `beforeEach` presses
 * `Control+Home` after focusing, so every sequence here starts with it.
 */
const SELECTION: Sequence[] = [
  { name: "Shift+Right - extend selection right", steps: [HOME, p("Shift+ArrowRight")] },
  { name: "Shift+Left - extend selection left", steps: [HOME, p("ArrowRight", 5), p("Shift+ArrowLeft")] },
  { name: "Shift+Down - extend selection down", steps: [HOME, p("Shift+ArrowDown")] },
  { name: "Ctrl+Shift+Right - select word right", steps: [HOME, p("Control+Shift+ArrowRight")] },
  { name: "Ctrl+Shift+Left - select word left", steps: [HOME, p("End"), p("Control+Shift+ArrowLeft")] },
  { name: "Shift+Home - select to line start", steps: [HOME, p("ArrowRight", 10), p("Shift+Home")] },
  { name: "Shift+End - select to line end", steps: [HOME, p("Shift+End")] },
  { name: "Ctrl+A - select all", steps: [HOME, p("Control+a")] },
  { name: "typing replaces selection", steps: [HOME, p("Control+Shift+ArrowRight"), t("replaced")] },
];

const SPECS: Array<{ spec: string; sequences: Sequence[] }> = [
  { spec: "navigation.spec.ts", sequences: NAVIGATION },
  { spec: "selection.spec.ts", sequences: SELECTION },
];

test("capture CM6 navigation and selection expectations", async ({ browser }) => {
  const page = await browser.newPage();
  const cm6 = new CM6Driver(page);

  // The pristine document, captured once so a fixture change shows up as a
  // diff here rather than as twenty confusing off-by-N failures.
  await page.goto(`file://${cm6FixturePath}`);
  await cm6.waitForReady();
  const initial = await cm6.getState();

  const bySpec: Record<string, Record<string, unknown>> = {};
  let total = 0;

  for (const { spec, sequences } of SPECS) {
    const results: Record<string, unknown> = {};

    for (const seq of sequences) {
      // Reload rather than undo — a fresh document per sequence, matching the
      // per-test page the differential specs get from the `cm6` fixture.
      await page.goto(`file://${cm6FixturePath}`);
      await cm6.waitForReady();
      await cm6.focus();

      for (const step of seq.steps) {
        if ("type" in step) {
          await cm6.type(step.type);
        } else {
          for (let i = 0; i < (step.times ?? 1); i++) {
            await cm6.press(step.press);
          }
        }
      }

      const state = await cm6.getState();
      results[seq.name] = {
        doc: state.doc,
        cursor: state.cursor.pos,
        line: state.cursor.line,
        col: state.cursor.col,
        anchor: state.selection.anchor,
        head: state.selection.head,
        empty: state.selection.empty,
      };
    }

    const captured = Object.keys(results).length;
    if (captured !== sequences.length) {
      throw new Error(
        `captured ${captured} of ${sequences.length} sequences for ${spec} — duplicate test name?`
      );
    }
    bySpec[spec] = results;
    total += captured;
  }

  await page.close();

  fs.writeFileSync(
    outputPath,
    JSON.stringify(
      {
        note:
          "Generated by navigation-selection-expectations-capture.spec.ts from real " +
          "CodeMirror 6. Do not hand-edit — regenerate. Offsets are relative to the " +
          "initialDoc below.",
        source: "gap-analysis/fixtures/cm6-test.html",
        sequenceCount: total,
        initialDoc: initial.doc,
        expectations: bySpec,
      },
      null,
      2
    ) + "\n"
  );

  console.log(`captured ${total} CM6 expectations -> ${outputPath}`);
});
