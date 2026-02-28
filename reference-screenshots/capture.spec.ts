import { test } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

const scenariosDir = path.join(__dirname, "scenarios");
const outputDir = path.join(__dirname, "output");

// Ensure output directory exists
fs.mkdirSync(outputDir, { recursive: true });

// Discover all HTML scenario files
const scenarios = fs
  .readdirSync(scenariosDir)
  .filter((f) => f.endsWith(".html"))
  .map((f) => f.replace(".html", ""));

for (const scenario of scenarios) {
  test(`capture ${scenario}`, async ({ page }) => {
    const filePath = path.join(scenariosDir, `${scenario}.html`);

    // Log console errors for debugging
    page.on("console", (msg) => {
      if (msg.type() === "error") console.log(`  [console] ${msg.text()}`);
    });
    page.on("pageerror", (err) =>
      console.log(`  [pageerror] ${err.message}`)
    );

    await page.goto(`file://${filePath}`);

    // Wait for CodeMirror to initialize
    await page.waitForSelector(".cm-editor", { timeout: 30000 });
    // Small delay for rendering to settle
    await page.waitForTimeout(500);

    await page.screenshot({
      path: path.join(outputDir, `${scenario}.png`),
      fullPage: false,
    });
  });
}
