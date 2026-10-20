import { vietnamDate } from "./courseCheckout.js";

export const canViewCoursePayments = (role) => ["ADMIN", "RECEPTIONIST"].includes(role);
export const coursePaymentMoney = (amount) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount);
export function coursePaymentTime(value) {
  const date = vietnamDate(value);
  return date ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh" }).format(date) : "Không có thời gian hợp lệ";
}
export const coursePaymentExpired = (row, now = Date.now()) => !(vietnamDate(row.paymentDeadline)?.getTime() > now);
export const coursePaymentPrompt = (row) => `XÁC NHẬN TIỀN ĐÃ NHẬN TẠI QUẦY\nThành viên: ${row.memberName} (#${row.memberId})\nKhóa học: ${row.courseName}\nLớp: ${row.className}\nĐăng ký #${row.registrationId} · Thanh toán #${row.paymentId}\nSố tiền: ${coursePaymentMoney(row.amount)}\nChỉ tiếp tục nếu bạn đã thực sự nhận đủ tiền. Máy chủ sẽ kiểm tra lại hạn giữ chỗ; tiền nhận muộn có thể cần hoàn tiền.`;

export function coursePaymentOutcome(payment) {
  const label = `Thanh toán #${payment.id}: ${payment.status}. `;
  if (payment.status === "SUCCESS") return { kind: "success", text: label + "Máy chủ đã ghi nhận tiền. API này không trả trạng thái đăng ký; không suy ra đăng ký CONFIRMED từ việc giao dịch rời hàng chờ." };
  if (payment.status === "REFUND_REQUIRED") return { kind: "exception", text: label + "Tiền không thể áp dụng cho đăng ký. Cần đối soát / hoàn tiền; không thu tiền lần nữa hoặc khôi phục chỗ học." };
  if (payment.status === "CANCELLED") return { kind: "warning", text: label + "Giao dịch đã bị hủy. Kiểm tra việc nhận tiền và trạng thái đăng ký riêng." };
  return { kind: "warning", text: label + "Chưa xác định kết quả nhận tiền. Không gửi lại xác nhận; hãy kiểm tra lịch sử hoặc đối soát." };
}

function timedSettlement(request, timeoutMs, setTimer, clearTimer) {
  let timer;
  const timeout = new Promise((_, reject) => { timer = setTimer(() => reject(new Error("Hết thời gian chờ kết quả nhận tiền.")), timeoutMs); });
  // Timeout never repeats or assumes cancellation of the original server transaction.
  return Promise.race([Promise.resolve().then(request), timeout]).finally(() => clearTimer(timer));
}

/** UI controller with injectable APIs/dialog/timers for dependency-free workflow tests. */
export function createCoursePaymentQueue({ fetchQueue, settle, fetchHistory, confirm, onQueue, onBusy, onOutcome,
  initialUncertain = [], saveUncertain = () => {}, onUncertain = () => {}, timeoutMs = 15000,
  setTimer = setTimeout, clearTimer = clearTimeout }) {
  let disposed = false;
  let busy = false;
  let page = 0;
  let version = 0;
  const uncertain = new Map(initialUncertain.map((row) => [row.paymentId, row]));
  const emit = (callback, value) => { if (!disposed) callback(value); };
  const publish = () => { const rows = [...uncertain.values()]; saveUncertain(rows); emit(onUncertain, rows); };
  const setBusy = (value) => { busy = value; emit(onBusy, value); };
  const warn = (text) => emit(onOutcome, { kind: "warning", text });

  async function load(target = page) {
    if (disposed) return;
    const request = ++version;
    page = target;
    emit(onQueue, { loading: true, data: null, error: "", page });
    try {
      const data = await fetchQueue(target, 20);
      if (disposed || request !== version) return;
      if (target > 0 && data.content.length === 0) return load(Math.max(0, Math.min(target - 1, data.totalPages - 1)));
      emit(onQueue, { loading: false, data, error: "", page });
    } catch (error) {
      if (request === version) emit(onQueue, { loading: false, data: null, page,
        error: error.status === 401 ? "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại." : error.status === 403 ? "Bạn không có quyền xem hàng chờ thanh toán khóa học." : error.message || "Không tải được hàng chờ." });
    }
  }

  async function reconcile(row, prefix = "") {
    if (disposed) return;
    try {
      const history = await fetchHistory(row.memberId);
      const payment = history.find((item) => item.id === row.paymentId && item.memberId === row.memberId
        && item.purpose === "COURSE_REGISTRATION" && item.referenceId === row.registrationId && item.method === "COUNTER");
      if (payment && ["SUCCESS", "CANCELLED", "REFUND_REQUIRED"].includes(payment.status)) {
        uncertain.delete(row.paymentId); publish();
        const outcome = coursePaymentOutcome(payment);
        emit(onOutcome, { ...outcome, text: prefix + outcome.text });
      } else warn(prefix + (payment ? "Lịch sử vẫn là PENDING. Đăng ký có thể đã hết hạn/hủy hoặc yêu cầu trước còn đang xử lý. Không gửi lại xác nhận; cần đối soát." : "Không tìm thấy giao dịch phù hợp trong lịch sử. Không gửi lại xác nhận; cần đối soát."));
    } catch (error) { warn(prefix + "Không kiểm tra được lịch sử: " + error.message + " Không gửi lại xác nhận nhận tiền."); }
  }

  return {
    load,
    refresh: () => busy ? Promise.resolve() : load(),
    async check(row) {
      if (busy || disposed) return;
      setBusy(true);
      try { await reconcile(row); await load(); } finally { setBusy(false); }
    },
    async receive(row) {
      if (busy || disposed || uncertain.has(row.paymentId)) return;
      setBusy(true); // Synchronous lock covers the dialog and every awaited operation.
      try {
        if (!confirm(coursePaymentPrompt(row))) return;
        if (coursePaymentExpired(row)) { warn("Hạn hiển thị đã qua. Không gửi xác nhận từ hàng chờ; làm mới và đối soát tiền đã nhận với nhân viên phụ trách."); await load(); return; }
        // Save minimal IDs before dispatch so a reload cannot silently retry an ambiguous receipt.
        uncertain.set(row.paymentId, { paymentId: row.paymentId, memberId: row.memberId, registrationId: row.registrationId }); publish();
        try {
          const payment = await timedSettlement(() => settle(row.paymentId, true, "Course payment received at reception"), timeoutMs, setTimer, clearTimer);
          if (["SUCCESS", "CANCELLED", "REFUND_REQUIRED"].includes(payment.status)) { uncertain.delete(row.paymentId); publish(); }
          emit(onOutcome, coursePaymentOutcome(payment));
        } catch (error) {
          await reconcile(row, `${error.status === 409 || error.status === 404 ? "Giao dịch có thể đã được nhân viên khác xử lý hoặc không còn khả dụng. " : "Yêu cầu chưa có kết quả chắc chắn: " + error.message + ". "}`);
        }
        await load();
      } catch (error) { warn("Không thể lưu/kiểm tra yêu cầu an toàn: " + error.message + ". Không tự gửi lại xác nhận."); }
      finally { setBusy(false); }
    },
    dispose() { disposed = true; version += 1; },
  };
}
