export const REFUND_PURPOSES = ["MEMBERSHIP", "COURSE_REGISTRATION", "PT_BOOKING", "UPGRADE"];

const labels = {
  MEMBERSHIP: "Gói hội viên",
  COURSE_REGISTRATION: "Đăng ký khóa học",
  PT_BOOKING: "Buổi PT",
  UPGRADE: "Nâng cấp gói",
};

export function refundPurposeLabel(payment) {
  const purpose = payment?.purpose;
  return labels[purpose] || `Mục đích ${purpose || "không xác định"}`;
}

export function refundReferenceLabel(payment) {
  if (payment?.purpose === "COURSE_REGISTRATION") {
    return `Đăng ký #${payment.registrationId ?? payment.referenceId}`;
  }
  if (payment?.purpose === "PT_BOOKING") return `Booking PT #${payment.referenceId}`;
  if (payment?.purpose === "MEMBERSHIP") return `Gói #${payment.referenceId}`;
  if (payment?.purpose === "UPGRADE") return `Gói nâng cấp #${payment.referenceId}`;
  return `Tham chiếu #${payment?.referenceId ?? "—"}`;
}

export function refundQueueRows(page) {
  return Array.isArray(page?.content) ? page.content : [];
}

export function refundQueueRowsForPurposes(payments, purposes) {
  const allowed = new Set(purposes);
  return (Array.isArray(payments) ? payments : []).filter((payment) => allowed.has(payment?.purpose));
}

export function refundQueuePage(rows, page, size = 10) {
  const start = page * size;
  return {
    content: rows.slice(start, start + size),
    totalElements: rows.length,
    totalPages: Math.max(1, Math.ceil(rows.length / size)),
    number: page,
    last: start + size >= rows.length,
  };
}

export async function loadAllRefundQueuePages(fetchPage, pageSize = 100) {
  const rows = [];
  let page = 0;
  let last = false;
  while (!last) {
    const result = await fetchPage(page, pageSize);
    rows.push(...refundQueueRows(result));
    last = result?.last ?? (page + 1 >= Number(result?.totalPages || 0));
    page += 1;
    if (page > 10000) throw new Error("Refund queue has too many pages.");
  }
  return rows;
}
