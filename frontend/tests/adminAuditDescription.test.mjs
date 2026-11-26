import assert from "node:assert/strict";
import test from "node:test";
import { formatAuditDescription } from "../src/auditDescription.js";

test("exceptional cancellation is summarized with cycle, totals, disposition and reason", () => {
  const result = formatAuditDescription({
    action: "CANCEL", entityType: "MEMBERSHIP",
    description: "actorUserId=7;actorRole=ADMIN;status=ACTIVE->CANCELLED;cycleRootMembershipId=41;eligiblePaymentCount=2;eligiblePaidAmount=599000;refundAmountDue=599000;refundDisposition=REFUND_REQUIRED_PENDING_DISBURSEMENT_CONFIRMATION;reason=Owner approved full refund",
  });
  assert.match(result.text, /Hủy Membership đặc cách/);
  assert.match(result.text, /Membership đầu chu kỳ: 41/);
  assert.match(result.text, /599\.000/);
  assert.match(result.text, /Owner approved full refund/);
  assert.equal(result.formatted, true);
  assert.match(result.raw, /actorUserId=7/);
});

test("payment and PT transitions translate internal status and money fields", () => {
  const counter = formatAuditDescription({ action: "COUNTER_CONFIRMATION", entityType: "PAYMENT", description: "status=PENDING->SUCCESS;purpose=UPGRADE;amount=300000;method=COUNTER" });
  assert.match(counter.text, /Xác nhận thanh toán tại quầy/);
  assert.match(counter.text, /300\.000/);
  assert.match(counter.text, /Đang chờ → Thành công/);
  const pt = formatAuditDescription({ action: "CORRECT", entityType: "PT_BOOKING_OUTCOME", description: "previous=COMPLETED;new=NO_SHOW;reason=Wrong attendance mark" });
  assert.match(pt.text, /Điều chỉnh kết quả buổi PT/);
  assert.match(pt.text, /Hoàn thành/);
  assert.match(pt.text, /Vắng mặt/);
  assert.match(pt.text, /Wrong attendance mark/);
});

test("Upgrade, Renewal and refund confirmations include their related IDs", () => {
  const upgrade = formatAuditDescription({ action: "SETTLE", entityType: "MEMBERSHIP_UPGRADE", description: "Upgrade 9; sourceMembership=42 ACTIVE->UPGRADED; replacementMembership=43 -> ACTIVE; sourcePlan=2; targetPlan=3; paymentId=901; paymentStatus=PENDING->SUCCESS; chargedAmount=300000" });
  assert.match(upgrade.text, /Membership #42/);
  assert.match(upgrade.text, /Membership #43/);
  assert.match(upgrade.text, /Payment #901/);
  const renewal = formatAuditDescription({ action: "SETTLE", entityType: "RENEWAL_PAYMENT", description: "Renewal Intent 71; Payment 902 PENDING -> SUCCESS; verified amount 800000; successor Membership 44" });
  assert.match(renewal.text, /Intent #71/);
  assert.match(renewal.text, /Payment #902/);
  assert.match(renewal.text, /Membership tiếp theo #44/);
  const refund = formatAuditDescription({ action: "REFUND_CONFIRMED", entityType: "PAYMENT", description: "status=REFUND_REQUIRED->REFUNDED;amount=299000;refundTimeRecorded=true;confirmationNote=true" });
  assert.match(refund.text, /Xác nhận đã hoàn Payment/);
  assert.match(refund.text, /Trạng thái Payment/);
  assert.match(refund.text, /299\.000/);
});

test("readable and unknown descriptions remain unchanged as safe fallback", () => {
  const readable = "Admin created directed Membership Upgrade Path 2 -> 3.";
  assert.deepEqual(formatAuditDescription({ action: "CREATE", entityType: "MEMBERSHIP_UPGRADE_PATH", description: readable }), { text: readable, raw: readable, formatted: false });
  const legacy = "<unrecognized legacy description>";
  assert.deepEqual(formatAuditDescription({ action: "UNKNOWN", entityType: "UNKNOWN", description: legacy }), { text: legacy, raw: legacy, formatted: false });
});
