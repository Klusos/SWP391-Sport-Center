import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const sales = await readFile(new URL("../src/pages/ReceptionistMembershipSales.jsx", import.meta.url), "utf8");
const salesCss = await readFile(new URL("../src/pages/ReceptionistMembershipSales.css", import.meta.url), "utf8");
const queue = await readFile(new URL("../src/pages/ConsultationQueue.jsx", import.meta.url), "utf8");
const queueCss = await readFile(new URL("../src/pages/ConsultationQueue.css", import.meta.url), "utf8");

test("membership sales presents real member and plan selections beside a truthful payment summary", () => {
  assert.match(sales, /membership-sales-checkout-layout/);
  assert.match(sales, /membership-sales-selected-member/);
  assert.match(sales, /membershipPlan/);
  assert.match(sales, /membership-sales-order-summary/);
  assert.match(sales, /pendingPayment \? money\(pendingPayment\.amount\) : selectedPlan \? money\(selectedPlan\.price\)/);
  assert.match(sales, /membership-sales-order-note/);
  assert.equal((sales.match(/name="membershipPlan"/g) || []).length, 1);
  assert.match(salesCss, /@media \(max-width:900px\)/);
  assert.match(salesCss, /membership-sales-order-summary/);
});

test("membership sales keeps counter payment creation, explicit confirmation, and duplicate-submit guards", () => {
  assert.match(sales, /createPaymentApi\(token, \{/);
  assert.match(sales, /method: "COUNTER"/);
  assert.match(sales, /if \(saving \|\| !selectedMember \|\| !selectedPlan \|\| pendingPayment\) return/);
  assert.match(sales, /processCounterPaymentApi\(token, pendingPayment\.id, success/);
  assert.match(sales, /pendingPayment\.status === "PENDING" && pendingPayment\.method === "COUNTER"/);
  assert.match(sales, /if \(result\.status === "SUCCESS"\)/);
});

test("consultation segmented filters preserve backend status paging and role restrictions", () => {
  assert.match(queue, /const STATUSES = \["", "PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"\]/);
  assert.match(queue, /getConsultationRequestsApi\(token, \{ status, page, size: PAGE_SIZE \}\)/);
  assert.match(queue, /aria-pressed=\{status === value\}/);
  assert.match(queue, /setSearchParams\(value \? \{ status: value \} : \{\}\); setPage\(0\); setNotice\(""\)/);
  assert.match(queue, /disabled=\{!allowedRole\}/);
  assert.match(queue, /request\.status === "PENDING" && allowedRole/);
  assert.match(queue, /actorId != null && request\.handledBy === actorId/);
  assert.match(queue, /setPage\(\(current\) => current \+ 1\)/);
  assert.match(queueCss, /consultation-filter-segments/);
  assert.match(queueCss, /consultation-workspace\.has-detail/);
  assert.match(queueCss, /@media \(max-width:1150px\)/);
});
