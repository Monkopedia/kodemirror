import { test, expect } from "../drivers/test-context";

test.describe("Editing", () => {
  test.beforeEach(async ({ cm6, km }) => {
    await cm6.focus();
    await km.focus();
  });

  test("Backspace", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    // Move to position 5 and delete
    for (let i = 0; i < 5; i++) {
      await cm6.press("ArrowRight");
      await km.press("ArrowRight");
    }

    const cm6Before = await cm6.getState();
    const kmBefore = await km.getState();

    await cm6.press("Backspace");
    await km.press("Backspace");

    const cm6After = await cm6.getState();
    const kmAfter = await km.getState();

    // One character should be deleted
    expect(cm6After.docInfo.length).toBe(cm6Before.docInfo.length - 1);
    expect(kmAfter.docInfo.length).toBe(kmBefore.docInfo.length - 1);

    // Cursor should move back by 1
    expect(cm6After.cursor.pos).toBe(cm6Before.cursor.pos - 1);
    expect(kmAfter.cursor.pos).toBe(kmBefore.cursor.pos - 1);
  });

  test("Delete key", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    const cm6Before = await cm6.getState();
    const kmBefore = await km.getState();

    await cm6.press("Delete");
    await km.press("Delete");

    const cm6After = await cm6.getState();
    const kmAfter = await km.getState();

    // One character should be deleted
    expect(cm6After.docInfo.length).toBe(cm6Before.docInfo.length - 1);
    expect(kmAfter.docInfo.length).toBe(kmBefore.docInfo.length - 1);

    // Cursor position should stay the same
    expect(cm6After.cursor.pos).toBe(cm6Before.cursor.pos);
    expect(kmAfter.cursor.pos).toBe(kmBefore.cursor.pos);
  });

  test("Ctrl+Backspace - delete word backward", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    await km.press("Control+Home");

    // Move to end of first word
    await cm6.press("Control+ArrowRight");
    await km.press("Control+ArrowRight");

    const cm6Before = await cm6.getState();
    const kmBefore = await km.getState();

    await cm6.press("Control+Backspace");
    await km.press("Control+Backspace");

    const cm6After = await cm6.getState();
    const kmAfter = await km.getState();

    // Word should be deleted - doc should be shorter
    expect(cm6After.docInfo.length).toBeLessThan(cm6Before.docInfo.length);
    expect(kmAfter.docInfo.length).toBeLessThan(kmBefore.docInfo.length);

    // Deleted same amount
    const cm6Deleted = cm6Before.docInfo.length - cm6After.docInfo.length;
    const kmDeleted = kmBefore.docInfo.length - kmAfter.docInfo.length;
    expect(kmDeleted).toBe(cm6Deleted);
  });

  test("Ctrl+Z - undo", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    await km.press("Control+End");

    const cm6Before = await cm6.getDoc();
    const kmBefore = await km.getDoc();

    // Type something
    await cm6.type("UNDO_TEST");
    await km.type("UNDO_TEST");

    // Verify it was typed
    const cm6Mid = await cm6.getDoc();
    const kmMid = await km.getDoc();
    expect(cm6Mid).toContain("UNDO_TEST");
    expect(kmMid).toContain("UNDO_TEST");

    // Undo
    await cm6.press("Control+z");
    await km.press("Control+z");

    const cm6After = await cm6.getDoc();
    const kmAfter = await km.getDoc();

    // Should be back to original (or at least not contain the typed text)
    expect(cm6After).not.toContain("UNDO_TEST");
    expect(kmAfter).not.toContain("UNDO_TEST");
  });

  test("Ctrl+Y / Ctrl+Shift+Z - redo", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    await km.press("Control+End");

    // Type, undo, then redo
    await cm6.type("REDO_TEST");
    await km.type("REDO_TEST");

    await cm6.press("Control+z");
    await km.press("Control+z");

    // Redo (CM6 uses Ctrl+Y or Ctrl+Shift+Z on Linux)
    await cm6.press("Control+y");
    await km.press("Control+y");

    const cm6After = await cm6.getDoc();
    const kmAfter = await km.getDoc();

    // The text should be restored
    expect(cm6After).toContain("REDO_TEST");
    expect(kmAfter).toContain("REDO_TEST");
  });

  test("bracket auto-delete", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    await km.press("Control+End");
    await cm6.press("Enter");
    await km.press("Enter");

    // Type opening bracket (should auto-close)
    await cm6.type("(");
    await km.type("(");

    const cm6Mid = await cm6.getState();
    const kmMid = await km.getState();

    // Delete the opening bracket - should also remove the auto-closed one
    await cm6.press("Backspace");
    await km.press("Backspace");

    const cm6After = await cm6.getState();
    const kmAfter = await km.getState();

    // Get last line to check bracket behavior
    const cm6LastLine = cm6After.doc.split("\n").pop() ?? "";
    const kmLastLine = kmAfter.doc.split("\n").pop() ?? "";

    // Both should behave the same regarding bracket deletion
    const cm6HasBracket = cm6LastLine.includes("(") || cm6LastLine.includes(")");
    const kmHasBracket = kmLastLine.includes("(") || kmLastLine.includes(")");
    expect(kmHasBracket).toBe(cm6HasBracket);
  });
});
