import { Page } from "@playwright/test";
import {
  EditorDriver,
  EditorStateSnapshot,
  CursorInfo,
  SelectionInfo,
} from "./types";

export class CM6Driver implements EditorDriver {
  readonly name = "cm6";

  constructor(readonly page: Page) {}

  async waitForReady(): Promise<void> {
    await this.page.waitForFunction(() => (window as any).cmReady === true, {
      timeout: 15_000,
    });
  }

  async getState(): Promise<EditorStateSnapshot> {
    return await this.page.evaluate(() => (window as any).getState());
  }

  async getDoc(): Promise<string> {
    const state = await this.getState();
    return state.doc;
  }

  async getCursor(): Promise<CursorInfo> {
    const state = await this.getState();
    return state.cursor;
  }

  async getSelection(): Promise<SelectionInfo> {
    const state = await this.getState();
    return state.selection;
  }

  async type(text: string): Promise<void> {
    const editor = this.page.locator(".cm-content");
    await editor.click();
    await this.page.keyboard.type(text);
  }

  async press(key: string): Promise<void> {
    await this.page.keyboard.press(key);
  }

  async click(x: number, y: number): Promise<void> {
    await this.page.mouse.click(x, y);
  }

  async focus(): Promise<void> {
    const editor = this.page.locator(".cm-content");
    await editor.click();
  }

  async screenshot(path?: string): Promise<Buffer> {
    return await this.page.screenshot({ path, fullPage: false });
  }

  async getVersion(): Promise<number> {
    return await this.page.evaluate(() => (window as any).cmVersion ?? 0);
  }

  async waitForStateChange(
    fromVersion: number,
    timeoutMs = 5000
  ): Promise<void> {
    await this.page.waitForFunction(
      (v: number) => (window as any).cmVersion > v,
      fromVersion,
      { timeout: timeoutMs }
    );
  }
}
