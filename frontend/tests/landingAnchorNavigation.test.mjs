import assert from "node:assert/strict";
import test from "node:test";
import { resolveLandingHashTarget, scrollToLandingTarget } from "../src/landing/anchorNavigation.js";

function createBrowser({ top = 420, height = 1800, imageComplete = true } = {}) {
  let targetTop = top;
  let pageHeight = height;
  const frames = new Map();
  const listeners = new Map();
  const scrollCalls = [];
  let nextFrame = 1;
  const target = {
    getBoundingClientRect: () => ({ top: targetTop }),
    scrollIntoView: (options) => scrollCalls.push(options),
  };
  const image = { complete: imageComplete };
  const root = { querySelectorAll: (selector) => selector === "img" ? [image] : [] };
  const windowRef = {
    scrollY: 100,
    requestAnimationFrame(callback) { const id = nextFrame++; frames.set(id, callback); return id; },
    cancelAnimationFrame(id) { frames.delete(id); },
    addEventListener(type, callback) { listeners.set(type, callback); },
    removeEventListener(type) { listeners.delete(type); },
  };
  const documentRef = { documentElement: { get scrollHeight() { return pageHeight; } } };
  return {
    target, root, windowRef, documentRef, frames, listeners, scrollCalls,
    setTop(value) { targetTop = value; },
    setHeight(value) { pageHeight = value; },
    setImageComplete(value) { image.complete = value; },
    flushFrame() {
      const entry = frames.entries().next().value;
      assert.ok(entry, "Expected a pending animation frame");
      const [id, callback] = entry;
      frames.delete(id);
      callback();
    },
  };
}

const SECTION_IDS = ["bo-mon", "huan-luyen-vien", "co-so-vat-chat", "tuyen-sinh", "public-consultation"];

test("Landing hash resolver supports the consultation alias and rejects unknown anchors", () => {
  assert.equal(resolveLandingHashTarget("#consultation", SECTION_IDS), "public-consultation");
  assert.equal(resolveLandingHashTarget("#public-consultation", SECTION_IDS), "public-consultation");
  assert.equal(resolveLandingHashTarget("#unknown", SECTION_IDS), null);
});

test("cross-page anchor scroll waits for images and layout position to stabilize", async () => {
  const browser = createBrowser({ imageComplete: false });
  const cancel = scrollToLandingTarget({ ...browser, reducedMotion: false });
  await Promise.resolve();
  assert.equal(browser.frames.size, 1);

  browser.flushFrame();
  assert.equal(browser.scrollCalls.length, 0);
  browser.setImageComplete(true);
  browser.flushFrame();
  browser.setTop(610);
  browser.setHeight(2050);
  browser.flushFrame();
  assert.equal(browser.scrollCalls.length, 0);
  browser.flushFrame();
  browser.flushFrame();
  assert.deepEqual(browser.scrollCalls, [{ block: "start", behavior: "smooth" }]);
  assert.equal(browser.listeners.size, 0);
  cancel();
});

test("direct hash navigation uses instant scrolling when reduced motion is preferred", async () => {
  const browser = createBrowser();
  scrollToLandingTarget({ ...browser, reducedMotion: true });
  await Promise.resolve();
  browser.flushFrame();
  browser.flushFrame();
  browser.flushFrame();
  assert.deepEqual(browser.scrollCalls, [{ block: "start", behavior: "auto" }]);
});

test("user input cancels pending anchor scrolling instead of fighting the user", async () => {
  const browser = createBrowser();
  scrollToLandingTarget({ ...browser });
  await Promise.resolve();
  browser.listeners.get("wheel")?.();
  assert.equal(browser.frames.size, 0);
  assert.equal(browser.scrollCalls.length, 0);
});
