import { test, expect } from "../drivers/test-context";

test.describe("Editing", () => {
  test.beforeEach(async ({ cm6, km }) => {
    await cm6.focus();
    if (km) await km.focus();
  });

  test("Backspace", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    for (let i = 0; i < 5; i++) {
      await cm6.press("ArrowRight");
      if (km) await km.press("ArrowRight");
    }

    const cm6Before = await cm6.getState();
    if (km) var kmBefore = await km.getState();

    await cm6.press("Backspace");
    if (km) await km.press("Backspace");

    const cm6After = await cm6.getState();
    expect(cm6After.docInfo.length).toBe(cm6Before.docInfo.length - 1);
    expect(cm6After.cursor.pos).toBe(cm6Before.cursor.pos - 1);

    if (km) {
      const kmAfter = await km.getState();
      expect(kmAfter.docInfo.length).toBe(kmBefore!.docInfo.length - 1);
      expect(kmAfter.cursor.pos).toBe(kmBefore!.cursor.pos - 1);
    }
  });

  test("Delete key", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    const cm6Before = await cm6.getState();
    if (km) var kmBefore = await km.getState();

    await cm6.press("Delete");
    if (km) await km.press("Delete");

    const cm6After = await cm6.getState();
    expect(cm6After.docInfo.length).toBe(cm6Before.docInfo.length - 1);
    expect(cm6After.cursor.pos).toBe(cm6Before.cursor.pos);

    if (km) {
      const kmAfter = await km.getState();
      expect(kmAfter.docInfo.length).toBe(kmBefore!.docInfo.length - 1);
      expect(kmAfter.cursor.pos).toBe(kmBefore!.cursor.pos);
    }
  });

  test("Ctrl+Backspace - delete word backward", async ({ cm6, km }) => {
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    await cm6.press("Control+ArrowRight");
    if (km) await km.press("Control+ArrowRight");

    const cm6Before = await cm6.getState();
    if (km) var kmBefore = await km.getState();

    await cm6.press("Control+Backspace");
    if (km) await km.press("Control+Backspace");

    const cm6After = await cm6.getState();
    expect(cm6After.docInfo.length).toBeLessThan(cm6Before.docInfo.length);

    if (km) {
      const kmAfter = await km.getState();
      const cm6Deleted = cm6Before.docInfo.length - cm6After.docInfo.length;
      const kmDeleted = kmBefore!.docInfo.length - kmAfter.docInfo.length;
      expect(kmDeleted).toBe(cm6Deleted);
    }
  });

  test("Ctrl+Z - undo", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");

    await cm6.type("UNDO_TEST");
    if (km) await km.type("UNDO_TEST");

    const cm6Mid = await cm6.getDoc();
    expect(cm6Mid).toContain("UNDO_TEST");

    await cm6.press("Control+z");
    if (km) await km.press("Control+z");

    const cm6After = await cm6.getDoc();
    expect(cm6After).not.toContain("UNDO_TEST");

    if (km) {
      const kmAfter = await km.getDoc();
      expect(kmAfter).not.toContain("UNDO_TEST");
    }
  });

  test("Ctrl+Y - redo", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");

    await cm6.type("REDO_TEST");
    if (km) await km.type("REDO_TEST");

    await cm6.press("Control+z");
    if (km) await km.press("Control+z");

    await cm6.press("Control+y");
    if (km) await km.press("Control+y");

    const cm6After = await cm6.getDoc();
    expect(cm6After).toContain("REDO_TEST");

    if (km) {
      const kmAfter = await km.getDoc();
      expect(kmAfter).toContain("REDO_TEST");
    }
  });

  test("bracket auto-delete", async ({ cm6, km }) => {
    await cm6.press("Control+End");
    if (km) await km.press("Control+End");
    await cm6.press("Enter");
    if (km) await km.press("Enter");

    await cm6.type("(");
    if (km) await km.type("(");

    await cm6.press("Backspace");
    if (km) await km.press("Backspace");

    const cm6After = await cm6.getState();
    const cm6LastLine = cm6After.doc.split("\n").pop() ?? "";
    const cm6HasBracket =
      cm6LastLine.includes("(") || cm6LastLine.includes(")");

    if (km) {
      const kmAfter = await km.getState();
      const kmLastLine = kmAfter.doc.split("\n").pop() ?? "";
      const kmHasBracket =
        kmLastLine.includes("(") || kmLastLine.includes(")");
      expect(kmHasBracket).toBe(cm6HasBracket);
    }
  });
});
