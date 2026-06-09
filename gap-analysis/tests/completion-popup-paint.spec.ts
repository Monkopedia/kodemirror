import { test, expect, Page } from "@playwright/test";
import * as path from "path";
import * as fs from "fs";
import { PNG } from "pngjs";

/**
 * #111 — verify the completion popup label actually PAINTS on Compose-wasmJs.
 *
 * The regression: the option label was an AnnotatedString that did not paint on
 * wasmJs, leaving each row as a bare type-icon with a blank label. JVM/desktop
 * was unaffected, so only a live wasmJs render catches it — hence this Playwright
 * spec screenshots the real showcase canvas (served at `?test=completion`) under
 * Xvfb and checks, by pixel analysis, that the popup contains label TEXT pixels
 * (light grey) and not just the blue type-icon column.
 *
 * The page exposes `__kodemirror.openCompletion()` to open the popup
 * deterministically. We diff a before/after screenshot to isolate the popup
 * region, then count light-grey label pixels within it.
 *
 * In headless environments (no DISPLAY) Skiko/Compose-wasmJs cannot render, so
 * the editor never becomes ready; like the rest of the gap-analysis suite this
 * spec then skips gracefully. It is meaningful only under Xvfb.
 */

const screenshotDir = path.join(__dirname, "..", "screenshots");
const hasDisplay = !!process.env.DISPLAY;
const KM_READY_TIMEOUT = parseInt(
  process.env.KM_READY_TIMEOUT ?? (hasDisplay ? "30000" : "5000"),
  10
);

async function openCompletionPage(page: Page): Promise<boolean> {
  try {
    await page.goto("http://localhost:8081/?test=completion", {
      timeout: KM_READY_TIMEOUT,
      waitUntil: "domcontentloaded",
    });
    await page.waitForFunction(
      () => {
        const km = (globalThis as any).__kodemirror;
        return km && km.ready === true && km.state !== null;
      },
      undefined,
      { timeout: KM_READY_TIMEOUT }
    );
    return true;
  } catch {
    return false;
  }
}

/** A light-grey label pixel: bright and roughly desaturated (text color ~#ABB2BF). */
function isLabelPixel(r: number, g: number, b: number): boolean {
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  return min > 120 && max - min < 60;
}

test.describe("Completion popup paint (#111)", () => {
  test.beforeAll(() => {
    fs.mkdirSync(screenshotDir, { recursive: true });
  });

  test("popup label text paints (not a blank label)", async ({ page }) => {
    const ready = await openCompletionPage(page);
    test.skip(
      !ready,
      "Kodemirror wasmJs not renderable (needs Xvfb/DISPLAY); skipping live paint check."
    );

    // Focus the canvas so the editor is interactive, then capture the baseline
    // (popup closed) for the before/after diff.
    await page.evaluate(() => {
      const canvas = document.body.shadowRoot?.querySelector("canvas");
      (canvas as HTMLElement | undefined)?.focus();
    });
    await page.waitForTimeout(300);
    const beforeBuf = await page.screenshot({ fullPage: false });

    // Open the completion popup deterministically and let it lay out + paint.
    await page.evaluate(() => (globalThis as any).__kodemirror.openCompletion());
    await page.waitForTimeout(600);
    const afterBuf = await page.screenshot({
      path: path.join(screenshotDir, "km-completion-popup.png"),
      fullPage: false,
    });

    const before = PNG.sync.read(beforeBuf);
    const after = PNG.sync.read(afterBuf);
    expect(after.width).toBe(before.width);
    expect(after.height).toBe(before.height);

    // The popup is the region that changed between the two shots. Within it,
    // count label (light-grey) pixels. A blank-label regression would change
    // pixels too (background + blue icons) but yield almost no light-grey text.
    let changedPixels = 0;
    let labelPixels = 0;
    const { width, height, data } = after;
    const b = before.data;
    for (let i = 0; i < data.length; i += 4) {
      const dr = Math.abs(data[i] - b[i]);
      const dg = Math.abs(data[i + 1] - b[i + 1]);
      const db = Math.abs(data[i + 2] - b[i + 2]);
      if (dr + dg + db > 30) {
        changedPixels++;
        if (isLabelPixel(data[i], data[i + 1], data[i + 2])) {
          labelPixels++;
        }
      }
    }

    // Sanity: the popup actually opened (a non-trivial region changed).
    expect(
      changedPixels,
      `popup did not appear to render (changed=${changedPixels}, ` +
        `image=${width}x${height})`
    ).toBeGreaterThan(500);

    // The load-bearing assertion: the labels painted as text, not blank. The
    // five-row popup with multi-character labels yields thousands of label
    // pixels when it paints; a blank-label regression leaves only the blue
    // icon column (~near zero light-grey).
    expect(
      labelPixels,
      `completion labels did not paint (labelPixels=${labelPixels}, ` +
        `changed=${changedPixels})`
    ).toBeGreaterThan(400);
  });
});
