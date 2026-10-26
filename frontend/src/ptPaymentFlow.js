import { vietnamDate, validVnpayUrl } from "./courseCheckout.js";

export const canViewPtPayments = (role) => ["ADMIN", "RECEPTIONIST"].includes(role);
export const ptPaymentMoney = (amount) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount ?? 0);
export const ptPaymentExpired = (booking, now = Date.now()) => !(vietnamDate(booking?.paymentExpiresAt)?.getTime() > now);
export const bookingPayments = (payments, bookingId) => payments.filter((payment) =>
  payment.purpose === "PT_BOOKING" && Number(payment.referenceId) === Number(bookingId));
export const pendingPtPayment = (payments, bookingId) => bookingPayments(payments, bookingId)
  .find((payment) => payment.status === "PENDING");
export const ptPaymentPayload = (bookingId, method) => {
  if (!Number.isSafeInteger(Number(bookingId)) || Number(bookingId) < 1 || !["ONLINE", "COUNTER"].includes(method)) {
    throw new Error("Invalid PT payment request.");
  }
  return { purpose: "PT_BOOKING", referenceId: Number(bookingId), method };
};
export const ptOnlineUrlIsTrusted = validVnpayUrl;
export const ptCancellationWarning = (payment) => payment?.method === "ONLINE"
  ? "Cancel this pending online payment? Cancellation does not revoke an existing VNPay checkout URL. A later successful capture may require a refund."
  : "Cancel this pending counter payment?";
export function acquirePtPaymentAction(lock) { if (lock.current) return false; lock.current = true; return true; }

export function createPtCounterQueue({ fetchQueue, settle, fetchHistory, confirm, onQueue, onBusy, onOutcome,
  timeoutMs = 15000, setTimer = setTimeout, clearTimer = clearTimeout, initialUncertain = [], onUncertain = () => {}, saveUncertain = () => {} }) {
  let busy = false, page = 0, version = 0, disposed = false;
  const uncertain = new Map(initialUncertain.map((item) => [item.paymentId, item]));
  const emit = (fn, value) => { if (!disposed) fn(value); };
  const publishUncertain = () => { const values = [...uncertain.values()]; saveUncertain(values); emit(onUncertain, values); };
  const warn = (text) => emit(onOutcome, { kind: "warning", text });
  async function load(target = page) {
    if (disposed) return;
    const requestVersion = ++version; page = target;
    emit(onQueue, { loading: true, data: null, page, error: "" });
    try {
      const data = await fetchQueue(target, 20);
      if (disposed || requestVersion !== version) return;
      if (target > 0 && data.content.length === 0) return load(Math.max(0, Math.min(target - 1, data.totalPages - 1)));
      emit(onQueue, { loading: false, data, page, error: "" });
    } catch (error) {
      if (requestVersion === version) emit(onQueue, { loading: false, data: null, page,
        error: error.status === 401 ? "Your session expired. Sign in again." : error.status === 403 ? "You are not allowed to view PT counter payments." : error.message || "Could not load PT payment queue." });
    }
  }
  async function reconcile(row, prefix = "") {
    try {
      const history = await fetchHistory(row.memberId);
      const payment = history.find((item) => item.id === row.paymentId && item.memberId === row.memberId
        && item.purpose === "PT_BOOKING" && Number(item.referenceId) === Number(row.bookingId) && item.method === "COUNTER");
      if (payment && payment.status !== "PENDING") {
        uncertain.delete(row.paymentId); publishUncertain();
        emit(onOutcome, { kind: payment.status === "SUCCESS" ? "success" : "warning",
          text: `${prefix}Payment #${payment.id} is ${payment.status}. Booking state is verified separately from the current queue.` });
      }
      else warn(`${prefix}${payment ? "Payment remains pending" : "Payment not found in member history"}. Do not submit confirmation again until reconciled.`);
    } catch (error) { warn(`${prefix}Could not verify payment history: ${error.message}. Do not submit confirmation again.`); }
  }
  return {
    load,
    refresh: () => busy ? Promise.resolve() : load(),
    async check(row) { if (busy || disposed) return; busy = true; emit(onBusy, true); try { await reconcile(row); await load(); } finally { busy = false; emit(onBusy, false); } },
    async receive(row) {
      if (busy || disposed || uncertain.has(row.paymentId)) return;
      busy = true; emit(onBusy, true);
      try {
        const prompt = `Confirm cash actually received?\nMember: ${row.memberName} (#${row.memberId})\nSport: ${row.sportName}\nCoach: ${row.coachName}\nAppointment: ${row.startTime} – ${row.endTime}\nAmount: ${ptPaymentMoney(row.amount)}\nDeadline: ${row.paymentDeadline}\n\nOnly confirm if full cash payment was received.`;
        if (!confirm(prompt)) return;
        if (!(vietnamDate(row.paymentDeadline)?.getTime() > Date.now())) {
          warn("Displayed payment deadline has passed. Refresh and reconcile any cash already received; no confirmation was sent."); await load(); return;
        }
        uncertain.set(row.paymentId, { paymentId: row.paymentId, memberId: row.memberId, bookingId: row.bookingId }); publishUncertain();
        let timer;
        try {
          const timeout = new Promise((_, reject) => { timer = setTimer(() => reject(new Error("settlement request timed out")), timeoutMs); });
          const payment = await Promise.race([Promise.resolve().then(() => settle(row.paymentId)), timeout]);
          if (["SUCCESS", "CANCELLED", "REFUND_REQUIRED"].includes(payment?.status)) {
            uncertain.delete(row.paymentId); publishUncertain();
            emit(onOutcome, { kind: payment.status === "SUCCESS" ? "success" : "warning", text: `Payment #${payment.id} is ${payment.status}; booking status is independently checked in the refreshed queue.` });
          } else warn(`Settlement response is ${payment?.status || "unknown"}; refresh and reconcile before another action.`);
        } catch (error) {
          await reconcile(row, `${error.status === 409 || error.status === 404 ? "Payment may already have been processed or expired. " : "Settlement outcome is uncertain. "}`);
        } finally { clearTimer(timer); }
        await load();
      } finally { busy = false; emit(onBusy, false); }
    },
    dispose() { disposed = true; version += 1; },
  };
}
