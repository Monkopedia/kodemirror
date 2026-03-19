import { Page } from "@playwright/test";

export interface CursorInfo {
  pos: number;
  line: number;
  col: number;
}

export interface SelectionRangeInfo {
  from: number;
  to: number;
  anchor: number;
  head: number;
}

export interface SelectionInfo {
  anchor: number;
  head: number;
  empty: boolean;
  ranges: SelectionRangeInfo[];
}

export interface DocInfo {
  lines: number;
  length: number;
}

export interface EditorStateSnapshot {
  doc: string;
  cursor: CursorInfo;
  selection: SelectionInfo;
  docInfo: DocInfo;
}

export interface EditorDriver {
  readonly name: string;
  readonly page: Page;

  /** Wait for the editor to be ready for interaction. */
  waitForReady(): Promise<void>;

  /** Get the current editor state snapshot. */
  getState(): Promise<EditorStateSnapshot>;

  /** Get the document text. */
  getDoc(): Promise<string>;

  /** Get cursor info. */
  getCursor(): Promise<CursorInfo>;

  /** Get selection info. */
  getSelection(): Promise<SelectionInfo>;

  /** Type text into the editor. */
  type(text: string): Promise<void>;

  /** Press a key or key combination (e.g., "Enter", "Control+z"). */
  press(key: string): Promise<void>;

  /** Click at a position in the editor. */
  click(x: number, y: number): Promise<void>;

  /** Focus the editor. */
  focus(): Promise<void>;

  /** Take a screenshot. */
  screenshot(path?: string): Promise<Buffer>;

  /** Get the current state version counter. */
  getVersion(): Promise<number>;

  /** Wait for the state version to change from the given version. */
  waitForStateChange(fromVersion: number, timeoutMs?: number): Promise<void>;
}
