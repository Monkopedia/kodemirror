import { test, expect } from "../drivers/test-context";

test.describe("Typing", () => {
  test.beforeEach(async ({ cm6, km }) => {
    await cm6.focus();
    await km.focus();
  });

  test("single character", async ({ cm6, km }) => {
    // Move to end of doc to avoid affecting existing content unpredictably
    await cm6.press("Control+End");
    await km.press("Control+End");

    const cm6Before = await cm6.getState();
    const kmBefore = await km.getState();

    await cm6.press("Enter");
    await km.press("Enter");

    await cm6.type("x");
    await km.type("x");

    const cm6After = await cm6.getState();
    const kmAfter = await km.getState();

    expect(cm6After.doc).toContain("x");
    expect(kmAfter.doc).toContain("x");
    // Both should have the character at the end
    expect(kmAfter.doc.endsWith("x")).toBe(cm6After.doc.endsWith("x"));
  });

  test("word input", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    await km.press("Control+End");
    await cm6.press("Enter");
    await km.press("Enter");

    await cm6.type("hello");
    await km.type("hello");

    const cm6State = await cm6.getDoc();
    const kmState = await km.getDoc();

    expect(cm6State).toContain("hello");
    expect(kmState).toContain("hello");
  });

  test("special characters - brackets", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    await km.press("Control+End");
    await cm6.press("Enter");
    await km.press("Enter");

    await cm6.type("(");
    await km.type("(");

    const cm6State = await cm6.getState();
    const kmState = await km.getState();

    // CM6 with basicSetup auto-closes brackets
    // Check if both behave the same
    expect(kmState.doc.includes("(")).toBe(true);
    expect(cm6State.doc.includes("(")).toBe(true);

    // Check if auto-close produced matching bracket
    const cm6HasClose = cm6State.doc.includes("()");
    const kmHasClose = kmState.doc.includes("()");
    expect(kmHasClose).toBe(cm6HasClose);
  });

  test("special characters - quotes", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    await km.press("Control+End");
    await cm6.press("Enter");
    await km.press("Enter");

    await cm6.type('"');
    await km.type('"');

    const cm6State = await cm6.getState();
    const kmState = await km.getState();

    // Check quote auto-close behavior matches
    const cm6DoubleQuote =
      cm6State.doc.split("\n").pop()?.includes('""') ?? false;
    const kmDoubleQuote =
      kmState.doc.split("\n").pop()?.includes('""') ?? false;
    expect(kmDoubleQuote).toBe(cm6DoubleQuote);
  });

  test("Enter key and auto-indent", async ({ cm6, km }) => {
    // Type a function opening, then press Enter to see auto-indent
    await cm6.press("Control+End");
    await km.press("Control+End");
    await cm6.press("Enter");
    await km.press("Enter");

    await cm6.type("function test() {");
    await km.type("function test() {");

    await cm6.press("Enter");
    await km.press("Enter");

    const cm6State = await cm6.getState();
    const kmState = await km.getState();

    // After Enter inside braces, cursor should be indented
    expect(kmState.cursor.col).toBe(cm6State.cursor.col);
  });

  test("Tab key", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    await km.press("Control+End");
    await cm6.press("Enter");
    await km.press("Enter");

    await cm6.press("Tab");
    await km.press("Tab");

    const cm6Cursor = await cm6.getCursor();
    const kmCursor = await km.getCursor();

    // Tab should indent - check cursor column matches
    expect(kmCursor.col).toBe(cm6Cursor.col);
  });
});
