import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const appUrl = new URL("../src/App.jsx", import.meta.url);
const landingUrl = new URL("../src/landing/LandingPage.jsx", import.meta.url);

test("legacy Admission and membership URLs preserve query strings and land on Membership", async () => {
  const app = await readFile(appUrl, "utf8");
  const routes = app.split('<Route element={<PublicLayout')[1]?.split("</Route>")[0] || "";
  assert.match(routes, /path="\/tuyen-sinh" element=\{<LandingSectionRedirect sectionId="tuyen-sinh" \/>\}/);
  assert.match(routes, /path="\/membership-plans"\s+element=\{<LandingSectionRedirect sectionId="tuyen-sinh" \/>\}/);
  assert.match(app, /search: location\.search, hash: `#\$\{sectionId\}`/);
});

test("Landing routes Members to membership management, Guests to consultation, and exposes no staff purchase action", async () => {
  const landing = await readFile(landingUrl, "utf8");
  const card = landing.match(/<article className="lp-plan-card"[\s\S]*?<\/article>/)?.[0] || "";
  assert.match(card, /auth\?\.role === "MEMBER" \?[\s\S]*?to="\/member\/membership"/);
  assert.match(card, /: !auth \?[\s\S]*?to="\/#public-consultation"/);
  assert.match(card, /\) : null\}/);
  assert.doesNotMatch(card, /to="\/tuyen-sinh"|to="\/membership-plans"/);
});
