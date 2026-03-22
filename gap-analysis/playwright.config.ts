import { defineConfig, devices } from "@playwright/test";
import * as path from "path";
import * as fs from "fs";

const showcaseDist = path.join(
  __dirname,
  "..",
  "samples",
  "showcase",
  "build",
  "dist",
  "wasmJs",
  "developmentExecutable"
);

// Only start the KM web server if the showcase build exists.
// In headless environments, Skiko/Compose wasmJs cannot render (needs GPU/WebGL),
// so KM tests will gracefully degrade to CM6-only mode.
const kmBuildExists = fs.existsSync(path.join(showcaseDist, "index.html"));

// Skiko/Compose requires WebGL which only works in headed mode with a display.
// When DISPLAY is set (e.g., via Xvfb), launch Chrome in headed mode.
const hasDisplay = !!process.env.DISPLAY;

export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 2,
  reporter: [
    ["html", { open: "never" }],
    ["json", { outputFile: "report/results.json" }],
  ],
  use: {
    viewport: { width: 800, height: 600 },
    actionTimeout: 15_000,
    screenshot: "only-on-failure",
    trace: "on-first-retry",
    locale: "en-US",
  },
  projects: [
    {
      name: "gap-analysis",
      use: {
        ...devices["Desktop Chrome"],
        ...(hasDisplay ? { launchOptions: { headless: false } } : {}),
      },
      testIgnore: ["**/performance.spec.ts"],
    },
    {
      name: "performance",
      use: {
        ...devices["Desktop Chrome"],
        ...(hasDisplay ? { launchOptions: { headless: false } } : {}),
      },
      testMatch: ["**/performance.spec.ts"],
      retries: 0,
    },
  ],
  ...(kmBuildExists
    ? {
        webServer: {
          command: `python3 -m http.server 8081 --directory "${showcaseDist}"`,
          port: 8081,
          reuseExistingServer: !process.env.CI,
          timeout: 10_000,
        },
      }
    : {}),
});
