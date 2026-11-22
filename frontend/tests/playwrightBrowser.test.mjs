import assert from "node:assert/strict";
import test from "node:test";
import { playwrightLaunchOptions, playwrightUseOptions, resolvePlaywrightBrowser } from "../scripts/playwrightBrowser.js";

test("Playwright browser selection maps Chromium and Edge to their actual launch options", () => {
  assert.equal(resolvePlaywrightBrowser("chromium"), "chromium");
  assert.equal(resolvePlaywrightBrowser("msedge"), "msedge");
  assert.deepEqual(playwrightLaunchOptions("chromium", false), { headless: false });
  assert.deepEqual(playwrightLaunchOptions("msedge", false), { channel: "msedge", headless: false });
  assert.deepEqual(playwrightUseOptions("chromium"), {});
  assert.deepEqual(playwrightUseOptions("msedge"), { channel: "msedge" });
});

test("unsupported Playwright browser names fail with supported choices", () => {
  assert.throws(() => resolvePlaywrightBrowser("firefox"), /Unsupported Playwright browser "firefox"\. Use chromium or msedge\./);
});
