import { test, expect } from "../drivers/test-context";
import * as path from "path";
import * as fs from "fs";

const outputDir = path.join(__dirname, "..", "report", "screenshots");

test.describe("Visual Comparison", () => {
  test.beforeAll(() => {
    fs.mkdirSync(outputDir, { recursive: true });
  });

  test("initial render - side by side", async ({ cm6, km }) => {
    await cm6.screenshot(path.join(outputDir, "initial-cm6.png"));

    const cm6Shot = await cm6.screenshot();
    expect(cm6Shot.length).toBeGreaterThan(1000);

    if (km) {
      await km.screenshot(path.join(outputDir, "initial-km.png"));
      const kmShot = await km.screenshot();
      expect(kmShot.length).toBeGreaterThan(1000);
    }
  });

  test("after typing - visual state", async ({ cm6, km }) => {
    await cm6.focus();
    if (km) await km.focus();

    await cm6.press("Control+End");
    if (km) await km.press("Control+End");
    await cm6.press("Enter");
    if (km) await km.press("Enter");
    await cm6.type("// visual test");
    if (km) await km.type("// visual test");

    await cm6.screenshot(path.join(outputDir, "after-typing-cm6.png"));
    if (km) {
      await km.screenshot(path.join(outputDir, "after-typing-km.png"));
    }
  });

  test("with selection - visual state", async ({ cm6, km }) => {
    await cm6.focus();
    if (km) await km.focus();

    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    await cm6.press("Shift+End");
    if (km) await km.press("Shift+End");

    await cm6.screenshot(path.join(outputDir, "selection-cm6.png"));
    if (km) {
      await km.screenshot(path.join(outputDir, "selection-km.png"));
    }
  });
});
