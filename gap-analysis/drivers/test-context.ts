import { test as base, BrowserContext, Page } from "@playwright/test";
import { CM6Driver } from "./cm6-driver";
import { KodemirrorDriver } from "./kodemirror-driver";
import { EditorDriver } from "./types";
import * as path from "path";

export interface GapTestFixtures {
  cm6: EditorDriver;
  km: EditorDriver;
}

const cm6FixturePath = path.join(__dirname, "..", "fixtures", "cm6-test.html");

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
    const page = await context.newPage();
    await page.goto("http://localhost:8081?test=true");
    const driver = new KodemirrorDriver(page);
    await driver.waitForReady();
    await use(driver);
    await page.close();
  },
});

export { expect } from "@playwright/test";
