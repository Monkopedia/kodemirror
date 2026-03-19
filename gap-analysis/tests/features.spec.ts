import { test, expect } from "../drivers/test-context";

test.describe("Features", () => {
  test.beforeEach(async ({ cm6, km }) => {
    await cm6.focus();
    await km.focus();
  });

  test("Ctrl+F opens search", async ({ cm6, km }) => {
    await cm6.press("Control+f");

    // CM6 should show search panel - check for .cm-search class
    const cm6HasSearch = await cm6.page.locator(".cm-search").isVisible();
    expect(cm6HasSearch).toBe(true);

    // For Kodemirror on canvas, we can't check DOM - just verify
    // the key combo doesn't crash and state is still accessible
    await km.press("Control+f");

    // State should still be readable
    const kmState = await km.getState();
    expect(kmState).toBeTruthy();
    expect(kmState.doc).toBeTruthy();

    // Close search on CM6
    await cm6.press("Escape");
  });

  test("bracket matching - cursor next to bracket", async ({ cm6, km }) => {
    // Go to start, find a bracket
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    // Move to the "(" in "fibonacci(n)"  - line 2, find position
    await cm6.press("ArrowDown"); // line 2
    await km.press("ArrowDown");

    // Move to end of "fibonacci" (the "(" position)
    await cm6.press("Home");
    await km.press("Home");

    // Navigate to first bracket character
    // "function fibonacci(n) {" - ( is at column 18
    for (let i = 0; i < 19; i++) {
      await cm6.press("ArrowRight");
      await km.press("ArrowRight");
    }

    // State after positioning near bracket
    const cm6State = await cm6.getState();
    const kmState = await km.getState();

    // Both should have cursor at same position
    expect(kmState.cursor.col).toBe(cm6State.cursor.col);
    expect(kmState.cursor.line).toBe(cm6State.cursor.line);

    // We can't directly test bracket highlighting on canvas,
    // but we verify cursor positioning is consistent
  });

  test("initial document matches", async ({ cm6, km }) => {
    const cm6Doc = await cm6.getDoc();
    const kmDoc = await km.getDoc();

    // Both editors should start with the same fibonacci document
    expect(cm6Doc).toContain("fibonacci");
    expect(kmDoc).toContain("fibonacci");

    // Line count should match
    const cm6Info = (await cm6.getState()).docInfo;
    const kmInfo = (await km.getState()).docInfo;
    expect(kmInfo.lines).toBe(cm6Info.lines);
    expect(kmInfo.length).toBe(cm6Info.length);
  });

  test("initial cursor position", async ({ cm6, km }) => {
    // After focus, before any navigation, check initial state
    // Focus puts cursor at click position, so use Ctrl+Home for consistency
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    expect(kmCursor.pos).toBe(0);
    expect(cm6Cursor.pos).toBe(0);
    expect(kmCursor.line).toBe(cm6Cursor.line);
    expect(kmCursor.col).toBe(cm6Cursor.col);
  });
});
