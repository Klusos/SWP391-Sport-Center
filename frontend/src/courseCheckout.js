export function positiveId(value) {
  return /^[1-9]\d*$/.test(String(value ?? "")) && Number.isSafeInteger(Number(value)) ? Number(value) : null;
}

export function checkoutAccess(auth) {
  return !auth?.accessToken ? "LOGIN" : auth.role === "MEMBER" ? "MEMBER" : "FORBIDDEN";
}

export function checkoutPayload(registrationId, method) {
  if (!positiveId(registrationId) || !["ONLINE", "COUNTER"].includes(method)) throw new Error("Thông tin thanh toán không hợp lệ.");
  return { method, purpose: "COURSE_REGISTRATION", referenceId: Number(registrationId) };
}

export function vietnamDate(value) {
  if (!value) return null;
  const date = new Date(/^\d{4}-\d\d-\d\dT\d\d:\d\d(?::\d\d(?:\.\d+)?)?$/.test(value) ? `${value}+07:00` : value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function canCheckout(registration, now = Date.now()) {
  return registration?.status === "PENDING_PAYMENT" && vietnamDate(registration.paymentDeadline)?.getTime() > now;
}

// Deliberately allow only the sandbox checkout host/path used by the current gateway.
// A different provider URL requires an explicit frontend review, not a wildcard allowlist.
export function validVnpayUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname === "sandbox.vnpayment.vn" && !url.port
      && !url.username && !url.password && url.pathname === "/paymentv2/vpcpay.html"
      && url.searchParams.get("vnp_Command") === "pay" && !!url.searchParams.get("vnp_SecureHash");
  } catch { return false; }
}

export function registrationPayments(payments, id) {
  return payments.filter((payment) => payment.purpose === "COURSE_REGISTRATION" && payment.referenceId === Number(id));
}

export function pendingCounter(payments) {
  return payments.find((payment) => payment.method === "COUNTER" && payment.status === "PENDING");
}

export function acquireAction(lock) {
  if (lock.current) return false;
  lock.current = true;
  return true;
}

export function prepareCheckout(snapshot, id, method, retryAccepted, interrupted) {
  const registration = snapshot.registrations.find((item) => item.id === id);
  if (!canCheckout(registration)) throw new Error("Đăng ký không còn đủ điều kiện thanh toán. Vui lòng kiểm tra trạng thái và hạn máy chủ trả về.");
  const payments = registrationPayments(snapshot.payments, id);
  if (payments.some((payment) => ["SUCCESS", "REFUND_REQUIRED"].includes(payment.status))) throw new Error("Đã có khoản thanh toán cần đối soát. Vui lòng liên hệ lễ tân trước khi thanh toán thêm.");
  const counter = pendingCounter(payments);
  if (counter) return { counter };
  if ((interrupted || payments.some((payment) => payment.method === "ONLINE" && payment.status === "PENDING")) && !retryAccepted) throw new Error("Có giao dịch có thể đang xử lý. Kiểm tra lại và đọc cảnh báo trước khi thử lại.");
  return { payload: checkoutPayload(id, method) };
}

export function startStatusPolling(read, onData, onError, continuePolling, {
  setTimer = setTimeout, clearTimer = clearTimeout, maxReads = 12, delay = 5000,
} = {}) {
  let stopped = false;
  let timer;
  let count = 0;
  async function poll() {
    try {
      const data = await read();
      if (stopped) return;
      const again = continuePolling(data) && ++count < maxReads;
      onData(data, !again);
      if (again) timer = setTimer(poll, delay);
    } catch (error) { if (!stopped) onError(error); }
  }
  void poll();
  return () => { stopped = true; clearTimer(timer); };
}

export function shouldPoll(registration, payments) {
  return registration?.status === "PENDING_PAYMENT" && payments.some((payment) => payment.status === "PENDING");
}

export function registrationMessage(registration) {
  return ({ CONFIRMED: "Đăng ký đã được máy chủ xác nhận. Bạn có thể xem lịch học.",
    EXPIRED: "Đã hết hạn thanh toán. Chỗ giữ đã được giải phóng.",
    CANCELLED: "Đăng ký đã bị hủy. Không tiếp tục thanh toán.",
    PENDING_PAYMENT: "Đăng ký đang chờ thanh toán; chỗ học chưa được xác nhận." })[registration?.status] || "Chưa có trạng thái đăng ký được xác thực.";
}

export function paymentMessage(payment) {
  return ({ PENDING: "Đang chờ xử lý. Chưa có xác nhận thanh toán thành công.",
    SUCCESS: "Máy chủ đã ghi nhận thanh toán thành công. Kiểm tra trạng thái đăng ký bên dưới.",
    CANCELLED: "Thanh toán đã bị hủy. Trạng thái đăng ký được theo dõi riêng.",
    REFUND_REQUIRED: "Khoản tiền không thể áp dụng cho đăng ký. Vui lòng liên hệ lễ tân để đối soát / hoàn tiền; không thanh toán lại." })[payment.status] || payment.status;
}
