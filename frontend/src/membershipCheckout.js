export function membershipPaymentPayload(planId, method, purchasedMonths) {
  const id = Number(planId);
  if (!Number.isSafeInteger(id) || id <= 0 || !["ONLINE", "COUNTER"].includes(method)
      || ![1, 3, 6, 12].includes(Number(purchasedMonths))) {
    throw new Error("Invalid membership checkout selection.");
  }
  return { method, purpose: "MEMBERSHIP", referenceId: id, autoRenew: false, purchasedMonths: Number(purchasedMonths) };
}

export function membershipPaymentMessage(payment) {
  switch (payment?.status) {
    case "PENDING":
      return payment.method === "ONLINE"
        ? "Giao dịch đang được xác minh. Gói chưa hoạt động cho đến khi máy chủ xác nhận; không tạo giao dịch khác khi yêu cầu này còn chờ."
        : "Đang chờ thanh toán tại quầy. Gói chưa hoạt động cho đến khi nhân viên xác nhận đã nhận tiền.";
    case "SUCCESS":
      return "Máy chủ đã xác minh thanh toán. Hãy làm mới trạng thái gói để xem thời hạn được kích hoạt hoặc gia hạn.";
    case "CANCELLED":
      return "Thanh toán đã bị hủy hoặc không hoàn tất. Gói không được kích hoạt từ giao dịch này.";
    case "REFUND_REQUIRED":
      return "Thanh toán cần nhân viên đối soát hoặc xử lý hoàn tiền. Không thanh toán lại trước khi được giải quyết.";
    default:
      return "Hãy kiểm tra lịch sử thanh toán đã xác thực để xem trạng thái mới nhất.";
  }
}
