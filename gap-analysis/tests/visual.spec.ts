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
    await km.screenshot(path.join(outputDir, "initial-km.png"));

    // Both should have rendered something (non-trivial screenshot)
    const cm6Shot = await cm6.screenshot();
    const kmShot = await km.screenshot();
    expect(cm6Shot.length).toBeGreaterThan(1000);
    expect(kmShot.length).toBeGreaterThan(1000);
  });

  test("after typing - visual state", async ({ cm6, km }) => {
    await cm6.focus();
    await km.focus();

    await cm6.press("Control+End");
    await km.press("Control+End");
    await cm6.press("Enter");
    await km.press("Enter");
    await cm6.type("// visual test");
    await km.type("// visual test");

    await cm6.screenshot(path.join(outputDir, "after-typing-cm6.png"));
    await km.screenshot(path.join(outputDir, "after-typing-km.png"));
  });

  test("with selection - visual state", async ({ cm6, km }) => {
    await cm6.focus();
    await km.focus();

    await cm6.press("Control+Home");
    await km.press("Control+Home");

    // Select first line
    await cm6.press("Shift+End");
    await km.press("Shift+End");

    await cm6.screenshot(path.join(outputDir, "selection-cm6.png"));
    await km.screenshot(path.join(outputDir, "selection-km.png"));
  });
});
