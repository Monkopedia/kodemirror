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

  private canvasLocator() {
    // Compose for Web renders inside a shadow DOM on <body>.
    // Playwright's default locator pierces shadow DOM automatically.
    return this.page.locator("canvas");
  }

  private async waitForUpdate(fromVersion: number): Promise<void> {
    try {
      await this.page.waitForFunction(
        (v: number) => {
          const km = (globalThis as any).__kodemirror;
          return km && km.version > v;
        },
        fromVersion,
        { timeout: 500 }
      );
    } catch {
      // Key press may not change state (e.g. Ctrl+F, arrow at boundary)
    }
  }

  async type(text: string): Promise<void> {
    const ver = await this.getVersion();
    await this.page.keyboard.type(text);
    await this.waitForUpdate(ver);
  }

  async press(key: string): Promise<void> {
    const ver = await this.getVersion();
    await this.page.keyboard.press(key);
    await this.waitForUpdate(ver);
  }

  async click(x: number, y: number): Promise<void> {
    await this.page.mouse.click(x, y);
  }

  async focus(): Promise<void> {
    const ver = await this.getVersion();
    const canvas = this.canvasLocator();
    await canvas.click();
    await this.waitForUpdate(ver);
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
