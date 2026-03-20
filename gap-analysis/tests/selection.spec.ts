import { test, expect } from "../drivers/test-context";

test.describe("Selection", () => {
  test.beforeEach(async ({ cm6, km }) => {
    await cm6.focus();
    if (km) await km.focus();
    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");
  });

  test("Shift+Right - extend selection right", async ({ cm6, km }) => {
    await cm6.press("Shift+ArrowRight");
    if (km) await km.press("Shift+ArrowRight");

    const cm6Sel = await cm6.getSelection();
    expect(cm6Sel.empty).toBe(false);

    if (km) {
      const kmSel = await km.getSelection();
      expect(kmSel.empty).toBe(false);
      expect(kmSel.anchor).toBe(cm6Sel.anchor);
      expect(kmSel.head).toBe(cm6Sel.head);
    }
  });

  test("Shift+Left - extend selection left", async ({ cm6, km }) => {
    for (let i = 0; i < 5; i++) {
      await cm6.press("ArrowRight");
      if (km) await km.press("ArrowRight");
    }

    await cm6.press("Shift+ArrowLeft");
    if (km) await km.press("Shift+ArrowLeft");

    const cm6Sel = await cm6.getSelection();
    expect(cm6Sel.empty).toBe(false);

    if (km) {
      const kmSel = await km.getSelection();
      expect(kmSel.anchor).toBe(cm6Sel.anchor);
      expect(kmSel.head).toBe(cm6Sel.head);
    }
  });

  test("Shift+Down - extend selection down", async ({ cm6, km }) => {
    await cm6.press("Shift+ArrowDown");
    if (km) await km.press("Shift+ArrowDown");

    const cm6Sel = await cm6.getSelection();
    expect(cm6Sel.empty).toBe(false);

    if (km) {
      const kmSel = await km.getSelection();
      expect(kmSel.anchor).toBe(cm6Sel.anchor);
      expect(kmSel.head).toBe(cm6Sel.head);
    }
  });

  test("Ctrl+Shift+Right - select word right", async ({ cm6, km }) => {
    await cm6.press("Control+Shift+ArrowRight");
    if (km) await km.press("Control+Shift+ArrowRight");

    const cm6Sel = await cm6.getSelection();
    expect(cm6Sel.empty).toBe(false);

    if (km) {
      const kmSel = await km.getSelection();
      expect(kmSel.anchor).toBe(cm6Sel.anchor);
      expect(kmSel.head).toBe(cm6Sel.head);
    }
  });

  test("Ctrl+Shift+Left - select word left", async ({ cm6, km }) => {
    await cm6.press("End");
    if (km) await km.press("End");

    await cm6.press("Control+Shift+ArrowLeft");
    if (km) await km.press("Control+Shift+ArrowLeft");

    const cm6Sel = await cm6.getSelection();
    expect(cm6Sel.empty).toBe(false);

    if (km) {
      const kmSel = await km.getSelection();
      expect(kmSel.anchor).toBe(cm6Sel.anchor);
      expect(kmSel.head).toBe(cm6Sel.head);
    }
  });

  test("Shift+Home - select to line start", async ({ cm6, km }) => {
    for (let i = 0; i < 10; i++) {
      await cm6.press("ArrowRight");
      if (km) await km.press("ArrowRight");
    }

    await cm6.press("Shift+Home");
    if (km) await km.press("Shift+Home");

    const cm6Sel = await cm6.getSelection();
    expect(cm6Sel.empty).toBe(false);

    if (km) {
      const kmSel = await km.getSelection();
      expect(kmSel.anchor).toBe(cm6Sel.anchor);
      expect(kmSel.head).toBe(cm6Sel.head);
    }
  });

  test("Shift+End - select to line end", async ({ cm6, km }) => {
    await cm6.press("Shift+End");
    if (km) await km.press("Shift+End");

    const cm6Sel = await cm6.getSelection();
    expect(cm6Sel.empty).toBe(false);

    if (km) {
      const kmSel = await km.getSelection();
      expect(kmSel.anchor).toBe(cm6Sel.anchor);
      expect(kmSel.head).toBe(cm6Sel.head);
    }
  });

  test("Ctrl+A - select all", async ({ cm6, km }) => {
    await cm6.press("Control+a");
    if (km) await km.press("Control+a");

    const cm6Sel = await cm6.getSelection();
    const cm6Doc = await cm6.getDoc();
    expect(cm6Sel.empty).toBe(false);
    expect(cm6Sel.ranges[0].to - cm6Sel.ranges[0].from).toBe(cm6Doc.length);

    if (km) {
      const kmSel = await km.getSelection();
      const kmDoc = await km.getDoc();
      expect(kmSel.ranges[0].to - kmSel.ranges[0].from).toBe(kmDoc.length);
    }
  });

  test("typing replaces selection", async ({ cm6, km }) => {
    await cm6.press("Control+Shift+ArrowRight");
    if (km) await km.press("Control+Shift+ArrowRight");

    await cm6.type("replaced");
    if (km) await km.type("replaced");

    const cm6Doc = await cm6.getDoc();
    expect(cm6Doc).toContain("replaced");

    const cm6After = await cm6.getSelection();
    expect(cm6After.empty).toBe(true);

    if (km) {
      const kmDoc = await km.getDoc();
      expect(kmDoc).toContain("replaced");
      const kmAfter = await km.getSelection();
      expect(kmAfter.empty).toBe(true);
    }
  });
});
