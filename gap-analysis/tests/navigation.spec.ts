import { test, expect } from "../drivers/test-context";

test.describe("Navigation", () => {
  test.beforeEach(async ({ cm6, km }) => {
    await cm6.focus();
    await km.focus();
  });

  test("arrow keys - right", async ({ cm6, km }) => {
    // Go to start of doc
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    await cm6.press("ArrowRight");
    await km.press("ArrowRight");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.pos).toBe(cm6Cursor.pos);
    expect(kmCursor.col).toBe(cm6Cursor.col);
    expect(kmCursor.line).toBe(cm6Cursor.line);
  });

  test("arrow keys - left", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    // Move right first, then left
    await cm6.press("ArrowRight");
    await cm6.press("ArrowRight");
    await cm6.press("ArrowLeft");

    await km.press("ArrowRight");
    await km.press("ArrowRight");
    await km.press("ArrowLeft");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.pos).toBe(cm6Cursor.pos);
  });

  test("arrow keys - down", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    await cm6.press("ArrowDown");
    await km.press("ArrowDown");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.line).toBe(cm6Cursor.line);
    expect(kmCursor.col).toBe(cm6Cursor.col);
  });

  test("arrow keys - up", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    await km.press("Control+End");

    await cm6.press("ArrowUp");
    await km.press("ArrowUp");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.line).toBe(cm6Cursor.line);
    expect(kmCursor.col).toBe(cm6Cursor.col);
  });

  test("Home key", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    // Move to middle of first line
    for (let i = 0; i < 5; i++) {
      await cm6.press("ArrowRight");
      await km.press("ArrowRight");
    }

    await cm6.press("Home");
    await km.press("Home");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.col).toBe(cm6Cursor.col);
    expect(kmCursor.line).toBe(cm6Cursor.line);
  });

  test("End key", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    await cm6.press("End");
    await km.press("End");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.col).toBe(cm6Cursor.col);
    expect(kmCursor.line).toBe(cm6Cursor.line);
  });

  test("Ctrl+Home - go to document start", async ({ cm6, km }) => {
    // Start somewhere in the middle
    await cm6.press("Control+End");
    await km.press("Control+End");

    await cm6.press("Control+Home");
    await km.press("Control+Home");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.pos).toBe(0);
    expect(cm6Cursor.pos).toBe(0);
    expect(kmCursor.line).toBe(cm6Cursor.line);
  });

  test("Ctrl+End - go to document end", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    await cm6.press("Control+End");
    await km.press("Control+End");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.pos).toBe(cm6Cursor.pos);
    expect(kmCursor.line).toBe(cm6Cursor.line);
  });

  test("Ctrl+Right - word movement forward", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    await cm6.press("Control+ArrowRight");
    await km.press("Control+ArrowRight");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.pos).toBe(cm6Cursor.pos);
  });

  test("Ctrl+Left - word movement backward", async ({ cm6, km }) => {
    // Go to end of first line
    await cm6.press("Control+Home");
    await km.press("Control+Home");
    await cm6.press("End");
    await km.press("End");

    await cm6.press("Control+ArrowLeft");
    await km.press("Control+ArrowLeft");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.pos).toBe(cm6Cursor.pos);
  });

  test("column memory across lines", async ({ cm6, km }) => {
    // Go to end of a longer line, then move down to a shorter line and back
    await cm6.press("Control+Home");
    await km.press("Control+Home");
    await cm6.press("End");
    await km.press("End");

    const startCol = (await cm6.getCursor()).col;

    // Move down (line might be shorter, so col is clamped)
    await cm6.press("ArrowDown");
    await km.press("ArrowDown");

    // Move down again
    await cm6.press("ArrowDown");
    await km.press("ArrowDown");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    // Column should match CM6 behavior (column memory)
    expect(kmCursor.col).toBe(cm6Cursor.col);
  });
});
