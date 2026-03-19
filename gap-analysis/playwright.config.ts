import { defineConfig, devices } from "@playwright/test";
import * as path from "path";

const fixturesDir = path.join(__dirname, "fixtures");
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

export default defineConfig({
  testDir: "./tests",
  timeout: 120_000,
  expect: { timeout: 30_000 },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ["html", { open: "never" }],
    ["json", { outputFile: "report/results.json" }],
  ],
  use: {
    viewport: { width: 800, height: 600 },
    actionTimeout: 15_000,
    screenshot: "only-on-failure",
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "gap-analysis",
      use: { ...devices["Desktop Chrome"] },
      testIgnore: ["**/performance.spec.ts"],
    },
    {
      name: "performance",
      use: { ...devices["Desktop Chrome"] },
      testMatch: ["**/performance.spec.ts"],
      retries: 0,
    },
  ],
  webServer: {
    command: `python3 -m http.server 8081 --directory "${showcaseDist}"`,
    port: 8081,
    reuseExistingServer: !process.env.CI,
    timeout: 10_000,
  },
});
