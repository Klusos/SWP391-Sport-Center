import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import postcss from "postcss";
import { publicCssIsolation } from "../vite.config.js";

const publicCss = readFileSync(new URL("../src/public-site/styles.css", import.meta.url), "utf8");
const landingCss = readFileSync(new URL("../src/landing/landing.css", import.meta.url), "utf8");
const publicHeader = readFileSync(new URL("../src/public-site/components/Header.jsx", import.meta.url), "utf8");
const sidebar = readFileSync(new URL("../src/components/Sidebar.jsx", import.meta.url), "utf8");

function splitSelectors(value) {
  const selectors = [];
  let current = "";
  let depth = 0;
  for (const character of value) {
    if (character === "(") depth += 1;
    if (character === ")") depth -= 1;
    if (character === "," && depth === 0) {
      selectors.push(current.trim());
      current = "";
    } else current += character;
  }
  if (current.trim()) selectors.push(current.trim());
  return selectors;
}

test("Public navigation and button selectors stay inside the Public shell", () => {
  for (const selector of [
    ".public-site .site-header",
    ".public-site .brand",
    ".public-site .nav-list",
    ".public-site .nav-item-link",
    ".public-site .nav-toggle",
    ".public-site .site-footer",
    ".public-site .btn-brand",
    ".public-site .btn-cyan, .member-course-detail .btn-cyan",
    ".public-site .btn-ghost",
    ".public-site .btn-white",
  ]) {
    assert.ok(publicCss.includes(selector), `Expected scoped selector: ${selector}`);
  }

  assert.match(publicHeader, /className="public-brand"/);
  assert.match(sidebar, /className="brand"/);
  assert.doesNotMatch(publicCss, /(^|[\n{}])\s*\.brand(?:\s|[.{:#,])/m);
  assert.match(readFileSync(new URL("../src/styles.css", import.meta.url), "utf8"), /\.layout \.sidebar \.brand\s*\{/);
});

test("shared consultation and image fallback styles retain their actual render owners", () => {
  assert.match(publicCss, /\.public-site \.btn-brand-dark\s*\{/);
  assert.match(publicCss, /\.public-site \.consult-form\s*\{/);
  assert.match(publicCss, /\.public-site \.consult-ok\s*\{/);
  assert.doesNotMatch(publicCss, /\.lp \.consult-form|\.lp \.btn-brand-dark|\.lp \.consult-ok/);
  assert.match(publicCss, /\.public-site \.img-fallback, \.lp \.img-fallback, \.layout \.img-fallback/);
  assert.match(publicCss, /\.public-site img, \.lp img, \.layout img/);
  assert.match(publicCss, /\.public-site \.course-registration-panel, \.member-course-detail \.course-registration-panel/);
  assert.match(publicCss, /\.public-site \.btn-cyan, \.member-course-detail \.btn-cyan/);
  assert.match(landingCss, /\.lp-consult \.consult-form/);
  assert.match(landingCss, /\.lp \.img-fallback/);
});

test("portal-owned Sport Management rules and component class contracts remain intact", () => {
  assert.match(publicCss, /\.sport-admin-grid\s*\{/);
  assert.match(publicCss, /\.sport-admin-table\s*\{/);
  assert.match(publicCss, /\.sport-status\.active\s*\{/);
});

test("compiled Public CSS keeps explicit Public, Landing, member, and portal scopes without double-prefixing", async () => {
  const sourceRoot = postcss.parse(publicCss);
  const uncontained = [];
  sourceRoot.walkRules((rule) => {
    for (const selector of splitSelectors(rule.selector)) {
      if (![".public-site", ".lp", ".layout", ".member-course-detail"].some((scope) => selector === scope || selector.startsWith(`${scope} `) || selector.startsWith(`${scope}:`) || selector.startsWith(`${scope}.`) || selector.startsWith(`${scope}[`)) && !selector.startsWith(".sport-admin-")) {
        uncontained.push(selector);
      }
    }
  });
  assert.deepEqual(uncontained, [], "all selectors declare a render owner; sport-admin rules are the documented portal exception");

  const compiled = await postcss([publicCssIsolation()]).process(publicCss, { from: "src/public-site/styles.css" });
  const selectors = [];
  compiled.root.walkRules((rule) => selectors.push(rule.selector));

  assert.ok(selectors.includes(".public-site .brand"));
  assert.ok(selectors.includes(".public-site .consult-form"));
  assert.ok(selectors.includes(".public-site .course-registration-panel, .member-course-detail .course-registration-panel"));
  assert.ok(selectors.includes(".public-site .img-fallback, .lp .img-fallback, .layout .img-fallback"));
  assert.ok(!selectors.some((selector) => /\.public-site\s+\.public-site(?:\s|[.#:[>+~])/.test(selector)));
  assert.ok(!selectors.some((selector) => selector.includes(".public-site .layout .img-fallback")));
});
