import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const pageUrl = new URL("../src/public-site/pages/Admission.jsx", import.meta.url);
const appUrl = new URL("../src/App.jsx", import.meta.url);

test("Admission sends guests and Members through the protected membership route", async () => {
  const page = await readFile(pageUrl, "utf8");
  const app = await readFile(appUrl, "utf8");

  assert.match(page, /\(!auth \|\| auth\.role === 'MEMBER'\)/);
  assert.match(page, /<Link className="btn btn-outline-plan" to="\/member\/membership">/);
  assert.match(app, /<Route path="\/tuyen-sinh" element={<Admission auth=\{auth\} \/>} \/>/);
  assert.match(app, /function MemberRoute\(\{ auth \}\)/);
  assert.match(app, /state=\{\{ from: location\.pathname \+ location\.search \}\}/);
  assert.ok(app.includes('(?:course-checkout\\/[1-9]\\d*|membership)'));
});

test("authenticated non-Members cannot navigate into Member membership from Admission", async () => {
  const page = await readFile(pageUrl, "utf8");

  assert.match(page, /Chỉ dành cho hội viên/);
  assert.match(page, /type="button" disabled/);
  assert.doesNotMatch(page, /auth\.role !== 'MEMBER'[\s\S]{0,200}<Link[^>]+to="\/member\/membership"/);
});
