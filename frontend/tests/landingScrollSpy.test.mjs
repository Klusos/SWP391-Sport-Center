import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { orderSectionsByDocumentPosition, selectActiveSection } from "../src/landing/scrollSpy.js";

const renderedSectionOrder = [
  "trang-chu",
  "bo-mon",
  "huan-luyen-vien",
  "co-so-vat-chat",
  "tuyen-sinh",
  "public-consultation",
];

function createPositionedSections(ids) {
  return ids.map((id, index) => ({
    id,
    compareDocumentPosition(other) {
      return index < ids.indexOf(other.id) ? 4 : 2;
    },
  }));
}

test("scroll spy uses rendered section order while preserving the visible navbar order", async () => {
  const page = await readFile(new URL("../src/landing/LandingPage.jsx", import.meta.url), "utf8");
  const nav = page.match(/const NAV = \[([\s\S]*?)\];/)?.[1] || "";
  const navIds = [...nav.matchAll(/id: "([^"]+)"/g)].map(([, id]) => id);
  const renderedIds = [...page.matchAll(/<section[^>]*\bid="([^"]+)"/g)].map(([, id]) => id);

  assert.deepEqual(navIds, [
    "trang-chu", "bo-mon", "co-so-vat-chat", "huan-luyen-vien", "tuyen-sinh", "public-consultation",
  ]);
  assert.deepEqual(renderedIds, renderedSectionOrder);

  const sectionsById = new Map(createPositionedSections(renderedIds).map((section) => [section.id, section]));
  const ordered = orderSectionsByDocumentPosition(navIds.map((id) => sectionsById.get(id))).map(({ id }) => id);
  assert.deepEqual(ordered, renderedSectionOrder);
});

test("scroll spy advances from Coaches to Facilities and later sections by rendered position", () => {
  const tops = {
    "trang-chu": -800,
    "bo-mon": -420,
    "huan-luyen-vien": 120,
    "co-so-vat-chat": 520,
    "tuyen-sinh": 900,
    "public-consultation": 1320,
  };

  assert.equal(selectActiveSection(renderedSectionOrder, tops, 350), "huan-luyen-vien");
  assert.equal(selectActiveSection(renderedSectionOrder, { ...tops, "co-so-vat-chat": 300 }, 350), "co-so-vat-chat");
  assert.equal(selectActiveSection(renderedSectionOrder, { ...tops, "tuyen-sinh": 240 }, 350), "tuyen-sinh");
  assert.equal(selectActiveSection(renderedSectionOrder, tops, 350, true), "public-consultation");
});
