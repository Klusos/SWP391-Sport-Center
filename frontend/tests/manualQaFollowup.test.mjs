import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const root = new URL("../src/pages/", import.meta.url);

test("exceptional membership cancellation validates a trimmed reason and keeps errors in the dialog", async () => {
  const source = await readFile(new URL("ReceptionistMembershipSales.jsx", root), "utf8");
  assert.match(source, /const reason = cancelReason\.trim\(\);/);
  assert.match(source, /if \(!reason\) \{[\s\S]*?setCancelDialogError\(["'][^"']+["']\);[\s\S]*?return;/);
  assert.match(source, /await cancelMembershipApi\(token, cancelDialogMembership\.id, reason\);/);
  assert.match(source, /catch \(requestError\) \{\s*setCancelDialogError\(cancelMembershipErrorMessage\(requestError\)\);/);
  assert.match(source, /disabled=\{saving\}>\{saving \?/);
  assert.match(source, /role="alert"/);
  assert.ok(source.indexOf("setCancelDialogMembership(null)") > source.indexOf("await cancelMembershipApi(token, cancelDialogMembership.id, reason)"));
});

test("Member renewal controls use the final terms and explain payment-gated intent creation", async () => {
  const member = await readFile(new URL("MemberMembership.jsx", root), "utf8");
  const dashboard = await readFile(new URL("Dashboard.jsx", root), "utf8");
  for (const text of [member, dashboard]) assert.doesNotMatch(text, /Nhắc tôi gia hạn/);
  assert.match(member, /Bật gia hạn/);
  assert.match(member, /Tắt gia hạn/);
  assert.match(member, /Gia hạn:/);
  assert.match(member, /three days|3 ngày|ba ngày/i);
  assert.match(member, /successful payment|thanh toán thành công/i);
  assert.match(member, /không tự động thu|no automatic charge/i);
});
