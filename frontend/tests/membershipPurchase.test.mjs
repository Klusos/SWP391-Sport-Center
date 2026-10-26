import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import { membershipPurchaseErrorMessage } from "../src/membershipPurchase.js";

test("disabled demo online payments produce a clear, non-success message", () => {
  const message = membershipPurchaseErrorMessage({ status: 503, message: "An unexpected error occurred" });

  assert.match(message, /chưa được bật cho bản demo/);
  assert.match(message, /Không có thanh toán mới nào được tạo/);
});

test("purchase errors remain visible after payment history is reloaded", async () => {
  const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
  const reloadIndex = source.indexOf("await load();", source.indexOf("membershipPurchaseErrorMessage(requestError)"));
  const displayIndex = source.indexOf("setError(message);", reloadIndex);

  assert.notEqual(reloadIndex, -1);
  assert.ok(displayIndex > reloadIndex);
});
