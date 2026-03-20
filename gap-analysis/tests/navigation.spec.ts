import { test, expect } from "../drivers/test-context";

test.describe("Navigation", () => {
  test.beforeEach(async ({ cm6, km }) => {
    await cm6.focus();
    if (km) await km.focus();
  });

  test("arrow keys - right", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    await cm6.press("ArrowRight");
    if (km) await km.press("ArrowRight");

    const cm6Cursor = await cm6.getCursor();
    expect(cm6Cursor.pos).toBe(1);

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.pos).toBe(cm6Cursor.pos);
      expect(kmCursor.col).toBe(cm6Cursor.col);
    }
  });

  test("arrow keys - left", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    await cm6.press("ArrowRight");
    await cm6.press("ArrowRight");
    await cm6.press("ArrowLeft");

    if (km) {
      await km.press("ArrowRight");
      await km.press("ArrowRight");
      await km.press("ArrowLeft");
    }

    const cm6Cursor = await cm6.getCursor();
    expect(cm6Cursor.pos).toBe(1);

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.pos).toBe(cm6Cursor.pos);
    }
  });

  test("arrow keys - down", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    await cm6.press("ArrowDown");
    if (km) await km.press("ArrowDown");

    const cm6Cursor = await cm6.getCursor();
    expect(cm6Cursor.line).toBe(2);

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.line).toBe(cm6Cursor.line);
      expect(kmCursor.col).toBe(cm6Cursor.col);
    }
  });

  test("arrow keys - up", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");

    await cm6.press("ArrowUp");
    if (km) await km.press("ArrowUp");

    const cm6Cursor = await cm6.getCursor();

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.line).toBe(cm6Cursor.line);
      expect(kmCursor.col).toBe(cm6Cursor.col);
    }
  });

  test("Home key", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    for (let i = 0; i < 5; i++) {
      await cm6.press("ArrowRight");
      if (km) await km.press("ArrowRight");
    }

    await cm6.press("Home");
    if (km) await km.press("Home");

    const cm6Cursor = await cm6.getCursor();

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.col).toBe(cm6Cursor.col);
    }
  });

  test("End key", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    await cm6.press("End");
    if (km) await km.press("End");

    const cm6Cursor = await cm6.getCursor();
    expect(cm6Cursor.col).toBeGreaterThan(0);

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.col).toBe(cm6Cursor.col);
    }
  });

  test("Ctrl+Home - go to document start", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");

    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    const cm6Cursor = await cm6.getCursor();
    expect(cm6Cursor.pos).toBe(0);

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.pos).toBe(0);
    }
  });

  test("Ctrl+End - go to document end", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    await cm6.press("Control+End");
    if (km) await km.press("Control+End");

    const cm6Cursor = await cm6.getCursor();

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.pos).toBe(cm6Cursor.pos);
    }
  });

  test("Ctrl+Right - word movement forward", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    await cm6.press("Control+ArrowRight");
    if (km) await km.press("Control+ArrowRight");

    const cm6Cursor = await cm6.getCursor();
    expect(cm6Cursor.pos).toBeGreaterThan(0);

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.pos).toBe(cm6Cursor.pos);
    }
  });

  test("Ctrl+Left - word movement backward", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");
    await cm6.press("End");
    if (km) await km.press("End");

    await cm6.press("Control+ArrowLeft");
    if (km) await km.press("Control+ArrowLeft");

    const cm6Cursor = await cm6.getCursor();

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.pos).toBe(cm6Cursor.pos);
    }
  });

  test("column memory across lines", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");
    await cm6.press("End");
    if (km) await km.press("End");

    await cm6.press("ArrowDown");
    if (km) await km.press("ArrowDown");

    await cm6.press("ArrowDown");
    if (km) await km.press("ArrowDown");

    const cm6Cursor = await cm6.getCursor();

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.col).toBe(cm6Cursor.col);
    }
  });
});
