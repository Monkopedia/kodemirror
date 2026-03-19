import { test, expect } from "../drivers/test-context";

test.describe("Selection", () => {
  test.beforeEach(async ({ cm6, km }) => {
    await cm6.focus();
    await km.focus();
    // Start from beginning
    await cm6.press("Control+Home");
    await km.press("Control+Home");
  });

  test("Shift+Right - extend selection right", async ({ cm6, km }) => {
    await cm6.press("Shift+ArrowRight");
    await km.press("Shift+ArrowRight");

    const cm6Sel = await cm6.getSelection();
    const kmSel = await km.getSelection();

    expect(kmSel.empty).toBe(false);
    expect(cm6Sel.empty).toBe(false);
    expect(kmSel.anchor).toBe(cm6Sel.anchor);
    expect(kmSel.head).toBe(cm6Sel.head);
  });

  test("Shift+Left - extend selection left", async ({ cm6, km }) => {
    // Move right first
    for (let i = 0; i < 5; i++) {
      await cm6.press("ArrowRight");
      await km.press("ArrowRight");
    }

    await cm6.press("Shift+ArrowLeft");
    await km.press("Shift+ArrowLeft");

    const cm6Sel = await cm6.getSelection();
    const kmSel = await km.getSelection();

    expect(kmSel.empty).toBe(false);
    expect(kmSel.anchor).toBe(cm6Sel.anchor);
    expect(kmSel.head).toBe(cm6Sel.head);
  });

  test("Shift+Down - extend selection down", async ({ cm6, km }) => {
    await cm6.press("Shift+ArrowDown");
    await km.press("Shift+ArrowDown");

    const cm6Sel = await cm6.getSelection();
    const kmSel = await km.getSelection();

    expect(kmSel.empty).toBe(false);
    expect(kmSel.anchor).toBe(cm6Sel.anchor);
    expect(kmSel.head).toBe(cm6Sel.head);
  });

  test("Ctrl+Shift+Right - select word right", async ({ cm6, km }) => {
    await cm6.press("Control+Shift+ArrowRight");
    await km.press("Control+Shift+ArrowRight");

    const cm6Sel = await cm6.getSelection();
    const kmSel = await km.getSelection();

    expect(kmSel.empty).toBe(false);
    expect(kmSel.anchor).toBe(cm6Sel.anchor);
    expect(kmSel.head).toBe(cm6Sel.head);
  });

  test("Ctrl+Shift+Left - select word left", async ({ cm6, km }) => {
    // Go to end of line
    await cm6.press("End");
    await km.press("End");

    await cm6.press("Control+Shift+ArrowLeft");
    await km.press("Control+Shift+ArrowLeft");

    const cm6Sel = await cm6.getSelection();
    const kmSel = await km.getSelection();

    expect(kmSel.empty).toBe(false);
    expect(kmSel.anchor).toBe(cm6Sel.anchor);
    expect(kmSel.head).toBe(cm6Sel.head);
  });

  test("Shift+Home - select to line start", async ({ cm6, km }) => {
    // Move to middle of line
    for (let i = 0; i < 10; i++) {
      await cm6.press("ArrowRight");
      await km.press("ArrowRight");
    }

    await cm6.press("Shift+Home");
    await km.press("Shift+Home");

    const cm6Sel = await cm6.getSelection();
    const kmSel = await km.getSelection();

    expect(kmSel.empty).toBe(false);
    expect(kmSel.anchor).toBe(cm6Sel.anchor);
    expect(kmSel.head).toBe(cm6Sel.head);
  });

  test("Shift+End - select to line end", async ({ cm6, km }) => {
    await cm6.press("Shift+End");
    await km.press("Shift+End");

    const cm6Sel = await cm6.getSelection();
    const kmSel = await km.getSelection();

    expect(kmSel.empty).toBe(false);
    expect(kmSel.anchor).toBe(cm6Sel.anchor);
    expect(kmSel.head).toBe(cm6Sel.head);
  });

  test("Ctrl+A - select all", async ({ cm6, km }) => {
    await cm6.press("Control+a");
    await km.press("Control+a");

    const cm6Sel = await cm6.getSelection();
    const kmSel = await km.getSelection();

    expect(kmSel.empty).toBe(false);

    // Both should select entire doc
    const cm6Doc = await cm6.getDoc();
    const kmDoc = await km.getDoc();

    const cm6Range = cm6Sel.ranges[0];
    const kmRange = kmSel.ranges[0];

    expect(cm6Range.to - cm6Range.from).toBe(cm6Doc.length);
    expect(kmRange.to - kmRange.from).toBe(kmDoc.length);
  });

  test("typing replaces selection", async ({ cm6, km }) => {
    // Select first word
    await cm6.press("Control+Shift+ArrowRight");
    await km.press("Control+Shift+ArrowRight");

    // Get selected text bounds
    const cm6Sel = await cm6.getSelection();
    const kmSel = await km.getSelection();

    // Type replacement
    await cm6.type("replaced");
    await km.type("replaced");

    const cm6Doc = await cm6.getDoc();
    const kmDoc = await km.getDoc();

    expect(cm6Doc).toContain("replaced");
    expect(kmDoc).toContain("replaced");

    // Selection should now be collapsed (cursor)
    const cm6After = await cm6.getSelection();
    const kmAfter = await km.getSelection();
    expect(cm6After.empty).toBe(true);
    expect(kmAfter.empty).toBe(true);
  });
});
