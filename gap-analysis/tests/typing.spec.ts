import { test, expect } from "../drivers/test-context";

test.describe("Typing", () => {
  test.beforeEach(async ({ cm6, km }) => {
    await cm6.focus();
    if (km) await km.focus();
  });

  test("single character", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");

    await cm6.press("Enter");
    if (km) await km.press("Enter");

    await cm6.type("x");
    if (km) await km.type("x");

    const cm6After = await cm6.getState();
    expect(cm6After.doc).toContain("\nx");

    if (km) {
      const kmAfter = await km.getState();
      expect(kmAfter.doc).toContain("\nx");
    }
  });

  test("word input", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");
    await cm6.press("Enter");
    if (km) await km.press("Enter");

    await cm6.type("hello");
    if (km) await km.type("hello");

    const cm6Doc = await cm6.getDoc();
    expect(cm6Doc).toContain("hello");

    if (km) {
      const kmDoc = await km.getDoc();
      expect(kmDoc).toContain("hello");
    }
  });

  test("special characters - brackets", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");
    await cm6.press("Enter");
    if (km) await km.press("Enter");

    await cm6.type("(");
    if (km) await km.type("(");

    const cm6State = await cm6.getState();
    expect(cm6State.doc.includes("(")).toBe(true);
    const cm6HasClose = cm6State.doc.includes("()");

    if (km) {
      const kmState = await km.getState();
      expect(kmState.doc.includes("(")).toBe(true);
      const kmHasClose = kmState.doc.includes("()");
      expect(kmHasClose).toBe(cm6HasClose);
    }
  });

  test("special characters - quotes", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");
    await cm6.press("Enter");
    if (km) await km.press("Enter");

    await cm6.type('"');
    if (km) await km.type('"');

    const cm6State = await cm6.getState();
    const cm6DoubleQuote =
      cm6State.doc.split("\n").pop()?.includes('""') ?? false;

    if (km) {
      const kmState = await km.getState();
      const kmDoubleQuote =
        kmState.doc.split("\n").pop()?.includes('""') ?? false;
      expect(kmDoubleQuote).toBe(cm6DoubleQuote);
    }
  });

  test("Enter key and auto-indent", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");
    await cm6.press("Enter");
    if (km) await km.press("Enter");

    await cm6.type("function test() {");
    if (km) await km.type("function test() {");

    await cm6.press("Enter");
    if (km) await km.press("Enter");

    const cm6State = await cm6.getState();
    // After Enter inside braces, cursor should be indented
    expect(cm6State.cursor.col).toBeGreaterThan(0);

    if (km) {
      const kmState = await km.getState();
      expect(kmState.cursor.col).toBe(cm6State.cursor.col);
    }
  });

  test("Tab key", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");
    await cm6.press("Enter");
    if (km) await km.press("Enter");

    await cm6.press("Tab");
    if (km) await km.press("Tab");

    const cm6Cursor = await cm6.getCursor();
    // Tab behavior depends on basicSetup config; just capture what CM6 does
    // (basicSetup doesn't include indentWithTab, so Tab may not indent)
    const cm6Col = cm6Cursor.col;

    if (km) {
      const kmCursor = await km.getCursor();
      expect(kmCursor.col).toBe(cm6Col);
    }
  });
});
