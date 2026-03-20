import { Page } from "@playwright/test";
import {
  EditorDriver,
  EditorStateSnapshot,
  CursorInfo,
  SelectionInfo,
} from "./types";

export class KodemirrorDriver implements EditorDriver {
  readonly name = "kodemirror";

  constructor(readonly page: Page) {}

  async waitForReady(): Promise<void> {
    await this.page.waitForFunction(
      () => {
        const km = (globalThis as any).__kodemirror;
        return km && km.ready === true && km.state !== null;
      },
      undefined,
      { timeout: 90_000 }
    );
  }

  async getState(): Promise<EditorStateSnapshot> {
    return await this.page.evaluate(() => {
      return (globalThis as any).__kodemirror.state;
    });
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
    // Canvas-based editor: click on canvas to focus, then type
    const canvas = this.page.locator("canvas");
    await canvas.click();
    await this.page.keyboard.type(text);
  }

  async press(key: string): Promise<void> {
    await this.page.keyboard.press(key);
  }

  async click(x: number, y: number): Promise<void> {
    await this.page.mouse.click(x, y);
  }

  async focus(): Promise<void> {
    const canvas = this.page.locator("canvas");
    await canvas.click();
  }

  async screenshot(path?: string): Promise<Buffer> {
    return await this.page.screenshot({ path, fullPage: false });
  }

  async getVersion(): Promise<number> {
    return await this.page.evaluate(
      () => (globalThis as any).__kodemirror?.version ?? 0
    );
  }

  async waitForStateChange(
    fromVersion: number,
    timeoutMs = 5000
  ): Promise<void> {
    await this.page.waitForFunction(
      (v: number) => {
        const km = (globalThis as any).__kodemirror;
        return km && km.version > v;
      },
      fromVersion,
      { timeout: timeoutMs }
    );
  }
}
