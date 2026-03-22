import { test as base, Page } from "@playwright/test";
import { CM6Driver } from "./cm6-driver";
import { KodemirrorDriver } from "./kodemirror-driver";
import { EditorDriver } from "./types";
import * as path from "path";

export interface GapTestFixtures {
  cm6: EditorDriver;
  km: EditorDriver | null;
}

const cm6FixturePath = path.join(__dirname, "..", "fixtures", "cm6-test.html");

// How long to wait for the Kodemirror wasmJs app to become ready.
// With Xvfb (DISPLAY set), Skiko/Compose can render; use 30s default.
// Without a display, KM will fail quickly (5s timeout).
const hasDisplay = !!process.env.DISPLAY;
const KM_READY_TIMEOUT = parseInt(
  process.env.KM_READY_TIMEOUT ?? (hasDisplay ? "30000" : "5000"),
  10
);

let kmAvailableChecked = false;
let kmAvailable = false;

export const test = base.extend<GapTestFixtures>({
  cm6: async ({ context }, use) => {
    const page = await context.newPage();
    await page.goto(`file://${cm6FixturePath}`);
    const driver = new CM6Driver(page);
    await driver.waitForReady();
    await use(driver);
    await page.close();
  },

  km: async ({ context }, use) => {
    // If we already determined KM is unavailable, skip immediately
    if (kmAvailableChecked && !kmAvailable) {
      await use(null);
      return;
    }

    let driver: KodemirrorDriver | null = null;
    let page: Page | null = null;
    try {
      page = await context.newPage();
      await page.goto("http://localhost:8081/?test=true", {
        timeout: KM_READY_TIMEOUT,
        waitUntil: "domcontentloaded",
      });
      const kmDriver = new KodemirrorDriver(page);
      // Use same short timeout for ready check
      await page.waitForFunction(
        () => {
          const km = (globalThis as any).__kodemirror;
          return km && km.ready === true && km.state !== null;
        },
        undefined,
        { timeout: KM_READY_TIMEOUT }
      );
      driver = kmDriver;
      kmAvailable = true;
    } catch {
      // KM not available (headless Chrome can't run Skiko/Compose wasmJs)
      if (page) {
        try {
          await page.close();
        } catch {
          // ignore close errors
        }
      }
      page = null;
      kmAvailable = false;
    }
    kmAvailableChecked = true;
    await use(driver);
    if (driver && page) {
      try {
        await page.close();
      } catch {
        // ignore close errors
      }
    }
  },
});

export { expect } from "@playwright/test";
