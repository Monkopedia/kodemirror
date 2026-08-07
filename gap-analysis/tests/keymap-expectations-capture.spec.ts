import { test } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";
import { CM6Driver } from "../drivers/cm6-driver";

/**
 * Captures what real CodeMirror 6 does for each `keymap-commands.spec.ts`
 * sequence, and freezes it as JSON.
 *
 * This exists because the Playwright suite is *differential* — it asserts
 * KodeMirror == CM6 and never records what either one actually did. A
 * `commonTest` twin needs an **absolute** expectation, and the only honest
 * source for it is CM6 itself. Deriving the numbers from KodeMirror's own
 * behaviour would make the twin assert a tautology: it would pass by
 * construction on the platform it was generated from, and prove nothing about
 * the others.
 *
 * Note this capture runs **CM6 only**. The fixture is a plain `file://` page
 * with no KodeMirror, no Skiko and no WebGL, so unlike the differential specs
 * it needs neither the showcase wasm build nor Xvfb.
 *
 * Output: `fixtures/cm6-keymap-expectations.json`, consumed when writing the
 * twins for #201. Re-run it when the fixture document changes — the offsets
 * below are relative to that document and to nothing else.
 */

type Step =
  | { press: string; times?: number }
  | { type: string };

interface Sequence {
  /** Test name in keymap-commands.spec.ts, verbatim — this is the parity key. */
  name: string;
  steps: Step[];
}

const cm6FixturePath = path.join(__dirname, "..", "fixtures", "cm6-test.html");
const outputPath = path.join(
  __dirname,
  "..",
  "fixtures",
  "cm6-keymap-expectations.json"
);

const p = (press: string, times?: number): Step => ({ press, times });
const t = (type: string): Step => ({ type });

const HOME = p("Control+Home");
const END = p("Control+End");

/**
 * The 60 sequences from keymap-commands.spec.ts, transcribed verbatim.
 *
 * Kept as data rather than as 60 tests so the capture is one file to audit
 * against the spec. Any edit here without the corresponding edit there breaks
 * the parity link the twins depend on.
 */
const SEQUENCES: Sequence[] = [
  // ─── Standard: arrow keys ───────────────────────────────────────────────
  { name: "ArrowLeft moves cursor left", steps: [HOME, p("ArrowRight", 5), p("ArrowLeft")] },
  { name: "ArrowRight moves cursor right", steps: [HOME, p("ArrowRight")] },
  { name: "ArrowUp moves cursor up", steps: [HOME, p("ArrowDown", 3), p("ArrowUp")] },
  { name: "ArrowDown moves cursor down", steps: [HOME, p("ArrowDown")] },

  // ─── Standard: Home/End ─────────────────────────────────────────────────
  { name: "Home moves to line start", steps: [HOME, p("ArrowDown", 2), p("ArrowRight", 5), p("Home")] },
  { name: "End moves to line end", steps: [HOME, p("ArrowDown", 2), p("End")] },
  { name: "Ctrl-Home moves to document start", steps: [END, HOME] },
  { name: "Ctrl-End moves to document end", steps: [HOME, END] },

  // ─── Standard: PageUp/PageDown ──────────────────────────────────────────
  // Viewport-dependent: the spec asserts only a direction, and so must the
  // twin. Captured anyway so the direction can be confirmed from real data.
  { name: "PageDown moves cursor down a page", steps: [HOME, p("PageDown")] },
  { name: "PageUp moves cursor up a page", steps: [END, p("PageUp")] },

  // ─── Standard: Enter ────────────────────────────────────────────────────
  { name: "Enter inserts newline", steps: [END, p("Enter")] },

  // ─── Standard: Backspace/Delete ─────────────────────────────────────────
  { name: "Backspace deletes char backward", steps: [HOME, p("ArrowRight", 5), p("Backspace")] },
  { name: "Delete deletes char forward", steps: [HOME, p("Delete")] },

  // ─── Standard: word movement ────────────────────────────────────────────
  { name: "Ctrl-ArrowRight moves word right", steps: [HOME, p("Control+ArrowRight")] },
  { name: "Ctrl-ArrowRight twice", steps: [HOME, p("Control+ArrowRight", 2)] },
  { name: "Ctrl-ArrowLeft moves word left", steps: [HOME, p("End"), p("Control+ArrowLeft")] },
  { name: "Ctrl-ArrowLeft twice", steps: [HOME, p("End"), p("Control+ArrowLeft", 2)] },

  // ─── Standard: word delete ──────────────────────────────────────────────
  { name: "Ctrl-Backspace deletes word backward", steps: [END, p("Enter"), t("hello world"), p("Control+Backspace")] },
  { name: "Ctrl-Backspace on typed text multiple times", steps: [END, p("Enter"), t("one two three"), p("Control+Backspace"), p("Control+Backspace"), p("Control+Backspace")] },
  { name: "Ctrl-Delete deletes word forward", steps: [HOME, p("Control+Delete")] },
  { name: "Ctrl-Delete twice", steps: [HOME, p("Control+Delete"), p("Control+Delete")] },
  { name: "Ctrl-Backspace at line start crosses line boundary", steps: [HOME, p("ArrowDown", 2), p("Home"), p("Control+Backspace")] },
  { name: "Ctrl-Delete at line end crosses line boundary", steps: [HOME, p("End"), p("Control+Delete")] },

  // ─── Default: move / copy / delete line ─────────────────────────────────
  { name: "Alt-ArrowUp moves line up", steps: [HOME, p("ArrowDown", 2), p("Alt+ArrowUp")] },
  { name: "Alt-ArrowDown moves line down", steps: [HOME, p("ArrowDown"), p("Alt+ArrowDown")] },
  { name: "Shift-Alt-ArrowUp copies line up", steps: [HOME, p("ArrowDown", 2), p("Shift+Alt+ArrowUp")] },
  { name: "Shift-Alt-ArrowDown copies line down", steps: [HOME, p("ArrowDown"), p("Shift+Alt+ArrowDown")] },
  { name: "Ctrl-Shift-k deletes current line", steps: [HOME, p("ArrowDown"), p("Control+Shift+k")] },

  // ─── Default: indent / brackets / transpose ─────────────────────────────
  { name: "Ctrl-] indents line", steps: [HOME, p("ArrowDown", 2), p("Control+]")] },
  { name: "Ctrl-[ dedents line", steps: [HOME, p("ArrowDown", 2), p("Control+]"), p("Control+[")] },
  { name: "Ctrl-Shift-\\ goes to matching bracket", steps: [HOME, p("ArrowDown"), p("End"), p("ArrowLeft"), p("Control+Shift+\\")] },
  { name: "Ctrl-t transposes characters", steps: [HOME, p("ArrowRight", 3), p("Control+t")] },

  // ─── Tab / Shift-Tab ────────────────────────────────────────────────────
  { name: "Tab indents (or inserts tab depending on config)", steps: [HOME, p("ArrowDown", 2), p("Tab")] },
  { name: "Shift-Tab dedents", steps: [HOME, p("ArrowDown", 2), p("Shift+Tab")] },

  // ─── Emacs: navigation ──────────────────────────────────────────────────
  { name: "Ctrl-a goes to line start", steps: [HOME, p("ArrowDown", 2), p("ArrowRight", 5), p("Control+a")] },
  { name: "Ctrl-e goes to line end", steps: [HOME, p("ArrowDown", 2), p("Control+e")] },
  { name: "Ctrl-f moves char right", steps: [HOME, p("Control+f")] },
  { name: "Ctrl-b moves char left", steps: [HOME, p("ArrowRight", 5), p("Control+b")] },
  { name: "Ctrl-p moves line up", steps: [HOME, p("ArrowDown", 3), p("Control+p")] },
  { name: "Ctrl-n moves line down", steps: [HOME, p("Control+n")] },

  // ─── Emacs: editing ─────────────────────────────────────────────────────
  { name: "Ctrl-d deletes char forward", steps: [HOME, p("Control+d")] },
  { name: "Ctrl-h deletes char backward", steps: [HOME, p("ArrowRight", 5), p("Control+h")] },
  { name: "Ctrl-k kills to end of line", steps: [HOME, p("ArrowRight", 3), p("Control+k")] },
  { name: "Ctrl-t transposes characters (emacs)", steps: [HOME, p("ArrowRight", 2), p("Control+t")] },

  // ─── Selection with Shift ───────────────────────────────────────────────
  { name: "Shift-ArrowRight extends selection right", steps: [HOME, p("Shift+ArrowRight", 5)] },
  { name: "Shift-ArrowLeft extends selection left", steps: [HOME, p("ArrowRight", 10), p("Shift+ArrowLeft", 3)] },
  { name: "Shift-ArrowDown extends selection down", steps: [HOME, p("Shift+ArrowDown")] },
  { name: "Shift-ArrowUp extends selection up", steps: [HOME, p("ArrowDown", 3), p("Shift+ArrowUp")] },
  { name: "Shift-Home selects to line start", steps: [HOME, p("ArrowDown"), p("End"), p("Shift+Home")] },
  { name: "Shift-End selects to line end", steps: [HOME, p("ArrowDown"), p("Home"), p("Shift+End")] },
  { name: "Ctrl-Shift-ArrowRight selects word right", steps: [HOME, p("Control+Shift+ArrowRight")] },
  { name: "Ctrl-Shift-ArrowLeft selects word left", steps: [HOME, p("End"), p("Control+Shift+ArrowLeft")] },
  { name: "Ctrl-Shift-Home selects to doc start", steps: [END, p("Control+Shift+Home")] },
  { name: "Ctrl-Shift-End selects to doc end", steps: [HOME, p("Control+Shift+End")] },

  // ─── Select all ─────────────────────────────────────────────────────────
  { name: "Ctrl-a selects all text", steps: [p("Control+a")] },

  // ─── Undo/Redo ──────────────────────────────────────────────────────────
  { name: "Ctrl-z undoes last change", steps: [END, t("X"), p("Control+z")] },
  { name: "Ctrl-Shift-z redoes after undo", steps: [END, t("Y"), p("Control+z"), p("Control+Shift+z")] },

  // ─── Tab handling ───────────────────────────────────────────────────────
  { name: "Tab key behavior matches between CM6 and KM", steps: [HOME, p("ArrowDown", 2), p("Home"), p("Tab")] },
  { name: "Tab then Shift-Tab round-trips", steps: [HOME, p("ArrowDown", 2), p("Home"), p("Tab"), p("Shift+Tab")] },
  { name: "multiple Tab presses increase indent consistently", steps: [HOME, p("ArrowDown", 2), p("Home"), p("Tab"), p("Tab"), p("Tab")] },
];

test("capture CM6 keymap expectations", async ({ browser }) => {
  const results: Record<string, unknown> = {};
  const page = await browser.newPage();
  const cm6 = new CM6Driver(page);

  // Capture the pristine document once, so the twins can assert against the
  // same starting point and a fixture change is visible as a diff here.
  await page.goto(`file://${cm6FixturePath}`);
  await cm6.waitForReady();
  const initial = await cm6.getState();

  for (const seq of SEQUENCES) {
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

  await page.close();

  const captured = Object.keys(results).length;
  if (captured !== SEQUENCES.length) {
    throw new Error(
      `captured ${captured} of ${SEQUENCES.length} sequences — duplicate test name in SEQUENCES?`
    );
  }

  fs.writeFileSync(
    outputPath,
    JSON.stringify(
      {
        note:
          "Generated by keymap-expectations-capture.spec.ts from real CodeMirror 6. " +
          "Do not hand-edit — regenerate. Offsets are relative to the initialDoc below.",
        source: "gap-analysis/fixtures/cm6-test.html",
        sequenceCount: SEQUENCES.length,
        initialDoc: initial.doc,
        expectations: results,
      },
      null,
      2
    ) + "\n"
  );

  console.log(`captured ${captured} CM6 expectations -> ${outputPath}`);
});
