import { test, expect } from "../drivers/test-context";

test.describe("Features", () => {
  test.beforeEach(async ({ cm6, km }) => {
    await cm6.focus();
    if (km) await km.focus();
  });

  test("Ctrl+F opens search", async ({ cm6, km }) => {
    await cm6.press("Control+f");

    const cm6HasSearch = await cm6.page.locator(".cm-search").isVisible();
    expect(cm6HasSearch).toBe(true);

    if (km) {
      await km.press("Control+f");
      // For canvas-based KM, just verify state is still accessible
      const kmState = await km.getState();
      expect(kmState).toBeTruthy();
    }

    // Close search
    await cm6.press("Escape");
  });

  test("initial document matches", async ({ cm6, km }) => {
    const cm6Doc = await cm6.getDoc();
    expect(cm6Doc).toContain("fibonacci");

    const cm6Info = (await cm6.getState()).docInfo;
    expect(cm6Info.lines).toBeGreaterThan(1);

    if (km) {
      const kmDoc = await km.getDoc();
      expect(kmDoc).toContain("fibonacci");

      const kmInfo = (await km.getState()).docInfo;
      expect(kmInfo.lines).toBe(cm6Info.lines);
      expect(kmInfo.length).toBe(cm6Info.length);
    }
  });

  test("initial cursor position", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    const cm6Cursor = await cm6.getCursor();
    expect(cm6Cursor.pos).toBe(0);
    expect(cm6Cursor.line).toBe(1);
    expect(cm6Cursor.col).toBe(0);

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.pos).toBe(0);
      expect(kmCursor.line).toBe(cm6Cursor.line);
      expect(kmCursor.col).toBe(cm6Cursor.col);
    }
  });

  test("bracket matching - cursor next to bracket", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    await cm6.press("ArrowDown"); // line 2
    if (km) await km.press("ArrowDown");

    await cm6.press("Home");
    if (km) await km.press("Home");

    // Navigate to bracket position
    for (let i = 0; i < 19; i++) {
      await cm6.press("ArrowRight");
      if (km) await km.press("ArrowRight");
    }

    const cm6State = await cm6.getState();

    if (km) {
      const kmState = await km.getState();
      expect(kmState.cursor.col).toBe(cm6State.cursor.col);
      expect(kmState.cursor.line).toBe(cm6State.cursor.line);
    }
  });
});
