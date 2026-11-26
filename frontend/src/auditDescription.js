const fieldLabels = {
  actorUserId: "Người thực hiện",
  actorRole: "Vai trò",
  status: "Trạng thái Membership",
  cycleRootMembershipId: "Membership đầu chu kỳ",
  eligiblePaymentCount: "Số Payment đủ điều kiện",
  eligiblePaidAmount: "Tổng đã thu đủ điều kiện",
  refundAmountDue: "Khoản hoàn cần xử lý",
  refundDisposition: "Tình trạng hoàn tiền",
  membershipId: "Membership liên quan",
  actualPaidAmount: "Số tiền đã thu",
  disposition: "Tình trạng khoản hoàn",
  paymentStatus: "Trạng thái Payment",
  purpose: "Loại thanh toán",
  amount: "Số tiền",
  method: "Phương thức",
  renewalIntentId: "Renewal Intent",
  previous: "Trạng thái trước",
  new: "Trạng thái sau",
  confirmationNote: "Ghi chú xác nhận",
  refundTimeRecorded: "Đã ghi nhận thời điểm hoàn",
};

const enumLabels = {
  SUCCESS: "Thành công", PENDING: "Đang chờ", FAILED: "Thất bại", CANCELLED: "Đã hủy",
  REFUND_REQUIRED: "Cần hoàn tiền", REFUNDED: "Đã xác nhận hoàn tiền",
  ACTIVE: "Đang hiệu lực", UPGRADED: "Đã nâng cấp", COMPLETED: "Hoàn thành", NO_SHOW: "Vắng mặt",
  MEMBERSHIP: "Mua Membership", UPGRADE: "Nâng cấp Membership", COUNTER: "Thanh toán tại quầy", ONLINE: "Trực tuyến",
  NEW_REFUND_OBLIGATION_PENDING_DISBURSEMENT: "Tạo nghĩa vụ hoàn; chờ xác nhận đã chi tiền",
  EXISTING_REFUND_OBLIGATION_PRESERVED: "Giữ nguyên nghĩa vụ hoàn hiện có",
  ALREADY_REFUNDED_NO_DUPLICATE_OBLIGATION: "Đã hoàn trước đó; không tạo khoản hoàn trùng",
  REFUND_REQUIRED_PENDING_DISBURSEMENT_CONFIRMATION: "Chờ xác nhận đã chi tiền hoàn",
};

const moneyKeys = new Set(["eligiblePaidAmount", "refundAmountDue", "actualPaidAmount", "amount", "chargedAmount", "verifiedAmount"]);
const money = (value) => {
  const amount = Number(value);
  return Number.isFinite(amount) ? new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(amount) : value;
};
const state = (value) => enumLabels[value] || value;

function fieldsFromDescription(raw) {
  const result = {};
  const matches = [...raw.matchAll(/(?:^|;)([A-Za-z][A-Za-z0-9]*)=/g)];
  for (let index = 0; index < matches.length; index += 1) {
    const match = matches[index];
    const start = match.index + match[0].length;
    const end = matches[index + 1]?.index ?? raw.length;
    result[match[1]] = raw.slice(start, end).replace(/^\s*;\s*/, "").trim();
  }
  return result;
}

function readableValue(key, value) {
  if (moneyKeys.has(key)) return money(value);
  if (key === "status" || key === "paymentStatus" || key === "previous" || key === "new" || key === "purpose" || key === "method" || key === "refundDisposition" || key === "disposition") {
    return value.split("->").map((part) => state(part.trim())).join(" → ");
  }
  if (key === "actorRole") return value === "SYSTEM" ? "Hệ thống" : value;
  if (key === "refundTimeRecorded" || key === "confirmationNote") return value === "true" ? "Có" : value === "false" ? "Không" : value;
  return value;
}

function labelAction(action, entityType) {
  if (action === "CANCEL" && entityType === "MEMBERSHIP") return "Hủy Membership đặc cách";
  if (action === "EXCEPTIONAL_CANCEL_REFUND") return "Tạo nghĩa vụ hoàn cho Payment";
  if (action === "EXCEPTIONAL_CANCEL_PAYMENT_EVIDENCE") return "Ghi nhận Payment liên quan đến hủy Membership";
  if (action === "REFUND_CONFIRMED") return "Xác nhận đã hoàn Payment";
  if (action === "SETTLE" && entityType === "MEMBERSHIP_UPGRADE") return "Hoàn tất nâng cấp Membership";
  if (action === "SETTLE" && entityType === "RENEWAL_PAYMENT") return "Hoàn tất thanh toán gia hạn";
  if (action === "COUNTER_CONFIRMATION") return "Xác nhận thanh toán tại quầy";
  if (action === "ONLINE_CALLBACK") return "Đối soát phản hồi thanh toán trực tuyến";
  if (action === "CORRECT" && entityType === "PT_BOOKING_OUTCOME") return "Điều chỉnh kết quả buổi PT";
  if (action === "RECORD" && entityType === "PT_BOOKING_OUTCOME") return "Ghi nhận kết quả buổi PT";
  if (action === "CREATE" && entityType === "MEMBERSHIP_UPGRADE_PATH") return "Tạo lộ trình nâng cấp";
  if (action === "STATUS_CHANGE" && entityType === "MEMBERSHIP_UPGRADE_PATH") return "Thay đổi trạng thái lộ trình nâng cấp";
  return null;
}

export function formatAuditDescription(entry) {
  const raw = String(entry?.description ?? "");
  const action = labelAction(entry?.action, entry?.entityType);
  if (!raw) return { text: "Không có mô tả", raw, formatted: false };

  const fields = fieldsFromDescription(raw);
  let summary = "";
  if (/^Upgrade \d+;/.test(raw)) {
    const value = (pattern) => raw.match(pattern)?.[1];
    const id = raw.match(/^Upgrade (\d+)/)?.[1];
    const source = value(/sourceMembership=(\d+)/);
    const target = value(/replacementMembership=(\d+)/);
    const payment = value(/paymentId=(\d+)/);
    const amount = value(/chargedAmount=([\d.]+)/);
    summary = `Upgrade #${id}: Membership #${source || "?"} → Membership #${target || "?"}; Payment #${payment || "?"}; đã thu ${amount ? money(amount) : "không ghi rõ"}.`;
  } else if (/^Renewal Intent \d+;/.test(raw)) {
    const value = (pattern) => raw.match(pattern)?.[1];
    const intent = raw.match(/^Renewal Intent (\d+)/)?.[1];
    const payment = value(/Payment (\d+)/);
    const successor = value(/successor Membership (\d+)/);
    const amount = value(/verified amount ([\d.]+)/);
    summary = `Gia hạn Intent #${intent || "?"}: Payment #${payment || "?"} đã xác nhận ${amount ? money(amount) : ""}; Membership tiếp theo #${successor || "?"}.`;
  } else if (Object.keys(fields).length > 0) {
    const formattedFields = Object.entries(fields).map(([key, value]) => {
      const label = key === "status"
        ? (entry?.entityType === "PAYMENT" ? "Trạng thái Payment" : "Trạng thái Membership")
        : fieldLabels[key] || key;
      return `${label}: ${readableValue(key, value)}`;
    });
    summary = [action, ...formattedFields].filter(Boolean).join(" · ");
  } else if (action && /^(Admin created directed Membership Upgrade Path|Membership Upgrade Path enabled|Membership Upgrade Path disabled)\b/.test(raw)) {
    summary = raw;
  }

  if (!summary) return { text: raw, raw, formatted: false };
  return { text: summary, raw, formatted: summary !== raw };
}
