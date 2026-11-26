import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

test("Consultation detail uses a centered contained modal without narrowing the table", async () => {
  const css = await readFile(new URL("../src/pages/ConsultationQueue.css", import.meta.url), "utf8");
  assert.match(css, /\.consultation-admin-content\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/);
  assert.match(css, /\.consultation-queue-page\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/);
  assert.match(css, /\.main\.consultation-queue-page--receptionist\s*\{[^}]*box-sizing:\s*border-box/);
  assert.match(css, /\.consultation-modal-backdrop\s*\{[^}]*position:fixed/);
  assert.match(css, /\.consultation-modal\s*\{[^}]*max-height:min\(92vh,900px\); overflow:auto/);
  assert.doesNotMatch(css, /consultation-workspace\.has-detail/);
});
