import { test, expect } from "../drivers/test-context";

test.describe("Performance", () => {
  test("input latency - single character", async ({ cm6, km }) => {
    await cm6.focus();
    if (km) await km.focus();

    await cm6.press("Control+End");
    if (km) await km.press("Control+End");
    await cm6.press("Enter");
    if (km) await km.press("Enter");

    // Measure CM6 input latency
    const cm6Version = await cm6.getVersion();
    const cm6Start = Date.now();
    await cm6.type("x");
    await cm6.waitForStateChange(cm6Version);
    const cm6Latency = Date.now() - cm6Start;
    console.log(`Input latency - CM6: ${cm6Latency}ms`);

    if (km) {
      const kmVersion = await km.getVersion();
      const kmStart = Date.now();
      await km.type("x");
      await km.waitForStateChange(kmVersion);
      const kmLatency = Date.now() - kmStart;
      console.log(`Input latency - KM: ${kmLatency}ms`);
      expect(kmLatency).toBeLessThan(2000);
    }
  });

  test("rapid typing - 50 characters", async ({ cm6, km }) => {
    await cm6.focus();
    if (km) await km.focus();

    await cm6.press("Control+End");
    if (km) await km.press("Control+End");
    await cm6.press("Enter");
    if (km) await km.press("Enter");

    const text = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMN";

    const cm6Start = Date.now();
    await cm6.type(text);
    const cm6Duration = Date.now() - cm6Start;
    console.log(`Rapid typing 50 chars - CM6: ${cm6Duration}ms`);

    const cm6Doc = await cm6.getDoc();
    expect(cm6Doc).toContain(text);

    if (km) {
      const kmStart = Date.now();
      await km.type(text);
      const kmDuration = Date.now() - kmStart;
      console.log(`Rapid typing 50 chars - KM: ${kmDuration}ms`);

      const kmDoc = await km.getDoc();
      expect(kmDoc).toContain(text);
    }
  });

  test("navigation performance - 100 arrow keys", async ({ cm6, km }) => {
    await cm6.focus();
    if (km) await km.focus();

    await cm6.press("Control+Home");
    if (km) await km.press("Control+Home");

    const cm6Start = Date.now();
    for (let i = 0; i < 100; i++) {
      await cm6.press("ArrowRight");
    }
    const cm6Duration = Date.now() - cm6Start;
    console.log(`100 arrow keys - CM6: ${cm6Duration}ms`);

    const cm6Cursor = await cm6.getCursor();

    if (km) {
      const kmStart = Date.now();
      for (let i = 0; i < 100; i++) {
        await km.press("ArrowRight");
      }
      const kmDuration = Date.now() - kmStart;
      console.log(`100 arrow keys - KM: ${kmDuration}ms`);

      const kmCursor = await km.getCursor();
      expect(kmCursor.pos).toBe(cm6Cursor.pos);
    }
  });
});
