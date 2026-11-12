import test from "node:test";
import assert from "node:assert/strict";
import { filterPaymentHistory, paginateItems, sortMembershipHistory } from "../src/membershipHistoryModel.js";

test("Membership history sorts newest start date first with ID tie-breaking", () => {
  const rows = [{ id: 2, startDate: "2026-01-01" }, { id: 8, startDate: "2026-02-01" }, { id: 5, startDate: "2026-02-01" }];
  assert.deepEqual(sortMembershipHistory(rows).map((row) => row.id), [8, 5, 2]);
});

test("Membership pagination clamps to a refreshed shorter collection", () => {
  const initial = paginateItems(Array.from({ length: 11 }, (_, id) => id), 2, 5);
  assert.equal(initial.page, 2);
  const refreshed = paginateItems([1, 2, 3, 4, 5, 6], initial.page, 5);
  assert.equal(refreshed.page, 1);
  assert.deepEqual(refreshed.items, [6]);
});

test("Payment history filters by returned status and purpose, then sorts newest first", () => {
  const payments = [
    { id: 4, createdAt: "2026-01-03T00:00:00", purpose: "MEMBERSHIP", status: "PAID" },
    { id: 7, createdAt: "2026-01-04T00:00:00", purpose: "UPGRADE", status: "PAID" },
    { id: 8, createdAt: "2026-01-05T00:00:00", purpose: "MEMBERSHIP", status: "PENDING" },
  ];
  assert.deepEqual(filterPaymentHistory(payments, { status: "PAID", purpose: "MEMBERSHIP" }).map((row) => row.id), [4]);
  assert.deepEqual(filterPaymentHistory(payments).map((row) => row.id), [8, 7, 4]);
});

test("Payment history page size is ten and clamps after filtering", () => {
  const payments = Array.from({ length: 21 }, (_, id) => ({ id }));
  assert.equal(paginateItems(payments, 2, 10).items.length, 1);
  assert.equal(paginateItems(payments.slice(0, 4), 2, 10).page, 0);
});
