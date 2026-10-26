import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import PaymentMethodModal from "../components/PaymentMethodModal";
import { cancelPaymentApi, createPaymentApi, createPtBookingApi, getMyPaymentsApi, getMyPtBookingsApi, getPublicSportsApi, getPtEligibleCoachesApi } from "../api";
import { acquirePtPaymentAction, bookingPayments, filterPtBookingHistory, focusPtPaymentPanel, paginatePtBookingHistory, partitionPtBookings, pendingPtPayment, ptPaymentExpired, ptPaymentMoney, ptOnlineUrlIsTrusted, ptPaymentPayload, ptCancellationWarning } from "../ptPaymentFlow";
import "./PtBooking.css";

const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];
const money = (value) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(value ?? 0);
const currentTimestamp = () => Date.now();
function vietnamTime(value, date = false) {
  if (!value) return "Time unavailable";
  const local = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value);
  const time = new Date(local ? value + "+07:00" : value);
  if (Number.isNaN(time.getTime())) return "Time unavailable";
  return new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", ...(date ? { dateStyle: "medium", timeStyle: "short" } : { timeStyle: "short" }) }).format(time);
}

export default function PtBooking({ token, role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]), [sportId, setSportId] = useState("");
  const [startTime, setStartTime] = useState(""), [coaches, setCoaches] = useState([]);
  const [history, setHistory] = useState([]), [selectedCoach, setSelectedCoach] = useState("");
  const [historyStatus, setHistoryStatus] = useState("ALL"), [historyQuery, setHistoryQuery] = useState(""), [historyPage, setHistoryPage] = useState(0);
  const [payments, setPayments] = useState([]), [paymentBusyId, setPaymentBusyId] = useState(null);
  const [paymentError, setPaymentError] = useState("");
  const [paymentModalBooking, setPaymentModalBooking] = useState(null);
  const [paymentReconciliationRequired, setPaymentReconciliationRequired] = useState(false);
  const [now, setNow] = useState(0);
  const focusBookingId = useRef(null);
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false);
  const [error, setError] = useState(""), [notice, setNotice] = useState("");
  const action = useRef(false);
  const lifecycle = useRef(0);
  const paymentAction = useRef(false);
  const { actionable: actionableBookings, history: historicalBookings } = useMemo(
    () => partitionPtBookings(history, now), [history, now],
  );
  const filteredHistory = useMemo(
    () => filterPtBookingHistory(historicalBookings, historyStatus, historyQuery),
    [historicalBookings, historyStatus, historyQuery],
  );
  const historyPageSize = 5;
  const historyPageData = paginatePtBookingHistory(filteredHistory, historyPage, historyPageSize);

  async function refreshAuthoritative() {
    const [bookings, paymentRows] = await Promise.all([getMyPtBookingsApi(token), getMyPaymentsApi(token)]);
    const refreshedPayments = rows(paymentRows);
    setHistory(rows(bookings)); setPayments(refreshedPayments);
    setPaymentReconciliationRequired(false);
    return refreshedPayments;
  }

  function openPaymentModal(booking) {
    if (paymentReconciliationRequired) {
      setPaymentError("Refresh booking and payment status before starting another payment.");
      return;
    }
    if (paymentBusyId !== null || booking.status !== "PENDING_PAYMENT" || ptPaymentExpired(booking, currentTimestamp())) {
      setPaymentError("This PT booking payment hold has expired. Refresh booking status before continuing.");
      return;
    }
    if (pendingPtPayment(payments, booking.id)) {
      setPaymentError("A pending payment already exists for this booking. Cancel it or wait for settlement before switching methods.");
      void refreshAuthoritative().catch(() => {});
      return;
    }
    setPaymentError("");
    setPaymentModalBooking(booking);
  }

  function closePaymentModal() {
    if (paymentBusyId !== null) return;
    setPaymentModalBooking(null);
    setPaymentError("");
  }

  async function startPayment(booking, method) {
    if (paymentBusyId !== null || paymentReconciliationRequired || !acquirePtPaymentAction(paymentAction)) return false;
    if (booking.status !== "PENDING_PAYMENT" || ptPaymentExpired(booking, currentTimestamp())) {
      setPaymentError("This PT booking payment hold has expired. Refresh booking status before continuing."); paymentAction.current = false; return false;
    }
    if (pendingPtPayment(payments, booking.id)) {
      setPaymentError("A pending payment already exists for this booking. Cancel it or wait for settlement before switching methods.");
      try { await refreshAuthoritative(); } catch { /* Keep the current payment state visible. */ }
      paymentAction.current = false;
      return false;
    }
    setPaymentBusyId(booking.id); setPaymentError("");
    try {
      const payment = await createPaymentApi(token, ptPaymentPayload(booking.id, method));
      setPayments((previous) => [payment, ...previous.filter((item) => item.id !== payment.id)]);
      let refreshedPayments;
      try { refreshedPayments = await refreshAuthoritative(); }
      catch {
        setPaymentReconciliationRequired(true);
        setPaymentError("Payment was created, but booking/payment history could not refresh. Refresh status before any further payment action.");
      }
      if (!refreshedPayments?.some((item) => Number(item.id) === Number(payment.id))) {
        setPayments((previous) => [payment, ...previous.filter((item) => item.id !== payment.id)]);
        setPaymentReconciliationRequired(true);
        setPaymentError("Payment was created but is not visible in refreshed history yet. Refresh status before any further payment action.");
      }
      if (method === "ONLINE" && (!payment?.paymentUrl || !ptOnlineUrlIsTrusted(payment.paymentUrl))) {
        setPaymentError("Online payment was created, but its URL is missing or not a trusted VNPay Sandbox URL. The payment remains visible; do not create another payment."); return true;
      }
      if (method === "ONLINE") window.location.assign(payment.paymentUrl);
      return true;
    } catch (requestError) {
      setPaymentError(requestError.status === 409
        ? "A payment already exists or the booking is no longer payable. Booking and payment history were refreshed."
        : `Payment outcome is uncertain: ${requestError.message || "network error"}. Check refreshed history before trying again.`);
      try { await refreshAuthoritative(); } catch { /* Keep payment actions locked until the user refreshes. */ }
      setPaymentReconciliationRequired(true);
      setPaymentError("Could not confirm the payment request outcome. Review the refreshed history, then manually refresh once more before trying again.");
      return false;
    } finally { paymentAction.current = false; setPaymentBusyId(null); }
  }

  async function cancelPendingPayment(payment) {
    if (paymentBusyId !== null || payment.status !== "PENDING" || !acquirePtPaymentAction(paymentAction)) return;
    if (!window.confirm(ptCancellationWarning(payment))) { paymentAction.current = false; return; }
    setPaymentBusyId(payment.id); setPaymentError("");
    try { await cancelPaymentApi(token, payment.id); await refreshAuthoritative(); }
    catch (requestError) {
      setPaymentError(`Cancellation outcome is uncertain: ${requestError.message || "network error"}. Refresh history; do not create another payment until status is confirmed.`);
      try { await refreshAuthoritative(); } catch { /* Preserve uncertainty warning. */ }
    } finally { paymentAction.current = false; setPaymentBusyId(null); }
  }

  useEffect(() => {
    const version = ++lifecycle.current;
    let active = true;
    Promise.resolve().then(async () => {
      if (!active) return;
      setLoading(true); setError("");
      try {
        const [items, bookings, paymentRows] = await Promise.all([getPublicSportsApi(), getMyPtBookingsApi(token), getMyPaymentsApi(token)]);
        if (active && lifecycle.current === version) { setSports(rows(items)); setHistory(rows(bookings)); setPayments(rows(paymentRows)); }
      } catch (requestError) { if (active) setError(requestError.message || "Could not load booking data."); }
      finally { if (active) setLoading(false); }
    });
    return () => { active = false; lifecycle.current += 1; };
  }, [token]);

  useEffect(() => {
    const immediate = window.setTimeout(() => setNow(Date.now()), 0);
    const timer = window.setInterval(() => setNow(Date.now()), 15000);
    return () => { window.clearTimeout(immediate); window.clearInterval(timer); };
  }, []);

  useEffect(() => {
    const targetId = focusBookingId.current;
    if (targetId == null || !history.some((booking) => booking.id === targetId)) return;
    if (focusPtPaymentPanel(targetId)) focusBookingId.current = null;
  }, [history]);

  function changeCriteria(setter, value) {
    setter(value); setCoaches([]); setSelectedCoach(""); setNotice("");
  }

  function paymentDetails(booking, allowCreate = false) {
    const related = bookingPayments(payments, booking.id);
    const pending = pendingPtPayment(payments, booking.id);
    const expired = ptPaymentExpired(booking, now);
    if (!related.length && !(booking.status === "PENDING_PAYMENT" && (allowCreate || expired))) return null;
    return <div className="pt-payment-actions">
      {related.map((payment) => <div className="pt-payment-state" key={payment.id}>
        <strong>{payment.method} payment #{payment.id}: {payment.status}</strong>
        <span>{ptPaymentMoney(payment.amount)}</span>
        {payment.status === "PENDING" && <>
          <small>{payment.method === "COUNTER" ? "Chờ xác nhận tại quầy. Booking chỉ được xác nhận sau khi nhân viên ghi nhận thanh toán." : "Đang chờ VNPay xác nhận. Việc quay về từ trình duyệt không tự xác nhận thanh toán."}</small>
          <button type="button" className="pt-secondary-button" disabled={paymentBusyId !== null} onClick={() => void cancelPendingPayment(payment)}>Hủy thanh toán đang chờ</button>
        </>}
      </div>)}
      {booking.status === "PENDING_PAYMENT" && (expired
        ? <p role="status">Đã hết hạn giữ chỗ; không thể tạo thanh toán mới cho booking này.</p>
        : paymentReconciliationRequired
          ? <p role="alert">Hãy làm mới lịch sử thanh toán trước khi tiếp tục.</p>
          : pending
            ? <p role="status">Đã có thanh toán {pending.method} đang chờ. Không tạo thanh toán khác; hãy chờ xác nhận hoặc hủy yêu cầu đang chờ.</p>
            : allowCreate && <div className="pt-payment-choice">
              <p>Chọn phương thức thanh toán. Booking chỉ được xác nhận theo trạng thái từ máy chủ.</p>
              <button id={`pt-payment-button-${booking.id}`} type="button" disabled={paymentBusyId !== null || paymentReconciliationRequired} onClick={() => openPaymentModal(booking)}>
                {paymentBusyId === booking.id ? "Đang xử lý…" : "Thanh toán"}
              </button>
            </div>)}
    </div>;
  }

  function bookingStatus(booking) {
    return booking.status === "PENDING_PAYMENT" ? "Chờ thanh toán" : booking.status;
  }

  function bookingCard(booking, actionable = false) {
    return <article className={actionable ? "pt-booking-summary is-actionable" : "pt-booking-summary"} key={booking.id}>
      <div className="pt-booking-summary-main">
        <strong>{booking.sportName || "Môn thể thao"} · {booking.coachName || "Huấn luyện viên"}</strong>
        <span>{vietnamTime(booking.startTime, true)} – {vietnamTime(booking.endTime)}</span>
        {actionable && <small>Giữ chỗ đến {vietnamTime(booking.paymentExpiresAt, true)} (giờ Việt Nam)</small>}
        <span className="pt-status">{bookingStatus(booking)}</span>
        {booking.status === "PENDING_PAYMENT" && ptPaymentExpired(booking, now) && <small className="pt-expired-hold">Đã hết hạn theo thời gian giữ chỗ (trạng thái máy chủ: PENDING_PAYMENT).</small>}
      </div>
      <div className="pt-booking-summary-side">
        <strong>{money(booking.finalPrice)}</strong>
        {actionable && paymentDetails(booking, true)}
        {!actionable && (bookingPayments(payments, booking.id).length > 0 || booking.status === "PENDING_PAYMENT")
          && <details className="pt-history-payment-details"><summary>Chi tiết thanh toán và thao tác được phép</summary>{paymentDetails(booking)}</details>}
      </div>
    </article>;
  }

  async function findCoaches(event) {
    event.preventDefault();
    if (action.current || loading) return;
    if (!sports.some((sport) => String(sport.id) === sportId) || !startTime) {
      setError("Please select a sport and start time."); return;
    }
    action.current = true; setBusy(true); setError(""); setNotice(""); setCoaches([]); setSelectedCoach("");
    const version = lifecycle.current;
    try {
      const eligible = rows(await getPtEligibleCoachesApi(token, sportId, startTime));
      if (lifecycle.current !== version) return;
      setCoaches(eligible);
      if (!eligible.length) setNotice("No qualified coaches are free at this time.");
    } catch (requestError) { if (lifecycle.current === version) setError(requestError.message || "Could not check coach schedules."); }
    finally { action.current = false; if (lifecycle.current === version) setBusy(false); }
  }

  async function book(event) {
    event.preventDefault();
    if (action.current || loading) return;
    const choice = coaches.find((coach) => String(coach.coachId) === selectedCoach);
    if (!choice) { setError("Please select an available coach."); return; }
    action.current = true; setBusy(true); setError(""); setNotice("");
    const version = lifecycle.current;
    try {
      const result = await createPtBookingApi(token, { coachId: choice.coachId, sportId: Number(sportId), startTime });
      if (lifecycle.current !== version) return;
      setNotice(`Booking #${result.id} is ${result.status}. Your 90-minute session is held until ${vietnamTime(result.paymentExpiresAt, true)} (Vietnam time). Complete payment within 15 minutes; this is not payment confirmation.`);
      setCoaches([]); setSelectedCoach("");
      // Keep the created pending booking visible even if the history read fails.
      setHistory((previous) => [result, ...previous.filter((booking) => booking.id !== result.id)]);
      focusBookingId.current = result.id;
      try {
        await refreshAuthoritative();
      } catch (requestError) { if (lifecycle.current === version) setError("Booking #" + result.id + " was created, but history could not be refreshed: " + requestError.message); }
    } catch (requestError) { if (lifecycle.current === version) setError(requestError.message || "Booking failed."); }
    finally { action.current = false; if (lifecycle.current === version) setBusy(false); }
  }

return (
<>
<div className="layout pt-booking-layout">
<Sidebar
role={role}
activePage="member-pt-booking"
onNavigate={onNavigate}
onLogout={onLogout}
/>

  <main className="main pt-main">
    <div className="pt-page">
      <header className="pt-header">
        <div className="pt-header-content">
          <p className="pt-eyebrow">
            SPORTCENTER / MEMBER
          </p>

          <h1>Book a 1-on-1 PT session</h1>

          <p className="pt-description">
            Each session lasts 90 minutes. New bookings
            remain pending payment.
          </p>

          <Link
            className="pt-assessment-link"
            to="/member/levels-assessments"
          >
            Trình độ &amp; lịch sử đánh giá
          </Link>
        </div>
      </header>

      {error && (
        <p className="pt-alert pt-error" role="alert">
          {error}
        </p>
      )}

      {notice && (
        <p className="pt-alert pt-success" role="status">
          {notice}
        </p>
      )}

      {paymentError && <p className="pt-alert pt-error" role="alert">{paymentError}</p>}

      <section className="pt-card">
        <div className="pt-section-heading">
          <span className="pt-section-icon">
            <span aria-hidden="true">🏋️</span>
          </span>

          <div>
            <h2>Choose a sport and start time</h2>
            <p>
              Select a sport and a suitable time for your
              session.
            </p>
          </div>
        </div>

        {loading ? (
          <div className="pt-loading" role="status">
            <span className="pt-spinner" />
            <span>Loading PT booking...</span>
          </div>
        ) : (
          <form
            className="pt-form"
            onSubmit={findCoaches}
          >
            <label className="pt-field">
              <span>Sport</span>

              <select
                required
                value={sportId}
                onChange={(e) =>
                  changeCriteria(setSportId, e.target.value)
                }
                disabled={busy}
              >
                <option value="">Select a sport</option>

                {sports.map((sport) => (
                  <option
                    key={sport.id}
                    value={sport.id}
                  >
                    {sport.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="pt-field">
              <span>Start time</span>

              <input
                required
                type="datetime-local"
                value={startTime}
                onChange={(e) =>
                  changeCriteria(setStartTime, e.target.value)
                }
                disabled={busy}
              />
            </label>

            <button
              className="pt-primary-button pt-find-button"
              type="submit"
              disabled={busy || !sportId || !startTime}
            >
              {busy
                ? "Checking..."
                : "Find available coaches"}
            </button>
          </form>
        )}

        {!loading && coaches.length > 0 && (
          <form
            className="pt-coach-form"
            onSubmit={book}
          >
            <div className="pt-subsection-heading">
              <div>
                <h3>Eligible coaches</h3>

                <p>
                  {coaches.length} coach
                  {coaches.length > 1 ? "es" : ""} available
                </p>
              </div>
            </div>

            <div className="pt-coach-list">
              {coaches.map((coach) => (
                <label
                  className={
                    "pt-coach" +
                    (selectedCoach ===
                    String(coach.coachId)
                      ? " selected"
                      : "")
                  }
                  key={coach.coachId}
                >
                  <input
                    type="radio"
                    name="coach"
                    required
                    value={coach.coachId}
                    checked={
                      selectedCoach ===
                      String(coach.coachId)
                    }
                    onChange={(e) =>
                      setSelectedCoach(e.target.value)
                    }
                    disabled={busy}
                  />

                  <span className="pt-coach-info">
                    <strong>{coach.coachName}</strong>

                    <small>
                      {coach.sportName} /{" "}
                      {coach.durationMinutes} minutes
                    </small>
                  </span>

                  <span className="pt-price">
                    <del>
                      {money(coach.originalPrice)}
                    </del>

                    <strong>
                      {money(coach.finalPrice)}
                    </strong>

                    <small>
                      Discount {money(coach.discount)}
                    </small>
                  </span>
                </label>
              ))}
            </div>

            <button
              className="pt-primary-button pt-book-button"
              type="submit"
              disabled={busy || !selectedCoach}
            >
              {busy
                ? "Processing..."
                : "Book session / payment pending"}
            </button>
          </form>
        )}
      </section>

      <section className="pt-card pt-actionable-card" aria-labelledby="pt-actionable-title">
        <div className="pt-section-heading">
          <span className="pt-section-icon"><span aria-hidden="true">⏳</span></span>
          <div>
            <h2 id="pt-actionable-title">Cần xử lý</h2>
            <p>Booking đang giữ chỗ được sắp theo hạn thanh toán gần nhất.</p>
          </div>
        </div>
        {loading ? <div className="pt-loading" role="status">Đang tải booking cần xử lý…</div>
          : actionableBookings.length === 0 ? <div className="pt-empty"><strong>Không có booking cần xử lý.</strong><p>Booking mới sẽ xuất hiện ở đây sau khi được tạo.</p></div>
            : <div className="pt-actionable-list">{actionableBookings.map((booking) => bookingCard(booking, true))}</div>}
      </section>

      <section className="pt-card pt-history-card" aria-labelledby="pt-history-title">
        <div className="pt-section-heading">
          <span className="pt-section-icon">
            <span aria-hidden="true">📅</span>
          </span>

          <div>
            <h2 id="pt-history-title">Lịch sử Booking</h2>
            <p>Booking đã xác nhận, đã hủy hoặc hết hạn được lưu tại đây.</p>
            <button type="button" className="pt-secondary-button" disabled={paymentBusyId !== null} onClick={() => void refreshAuthoritative().catch((requestError) => setPaymentError("Could not refresh booking and payment history: " + requestError.message))}>Làm mới trạng thái booking và thanh toán</button>
          </div>
        </div>

        {loading ? (
          <div className="pt-loading" role="status">
            Đang tải lịch sử booking…
          </div>
        ) : history.length === 0 ? (
          <div className="pt-empty">
            <strong>Chưa có lịch sử booking.</strong>
            <p>Booking sẽ chuyển vào lịch sử sau khi được xác nhận, hủy hoặc hết hạn.</p>
          </div>
        ) : <>
          <div className="pt-history-controls">
            <label>Tìm môn hoặc huấn luyện viên
              <input type="search" value={historyQuery} onChange={(event) => { setHistoryQuery(event.target.value); setHistoryPage(0); }} placeholder="Nhập tên môn hoặc huấn luyện viên" />
            </label>
            <label>Lọc trạng thái
              <select value={historyStatus} onChange={(event) => { setHistoryStatus(event.target.value); setHistoryPage(0); }}>
                <option value="ALL">Tất cả trạng thái</option>
                <option value="CONFIRMED">CONFIRMED</option>
                <option value="CANCELLED">CANCELLED</option>
                <option value="EXPIRED">EXPIRED</option>
                <option value="PENDING_PAYMENT">PENDING_PAYMENT đã hết hạn</option>
              </select>
            </label>
          </div>
          {filteredHistory.length === 0
            ? <div className="pt-empty"><strong>{historicalBookings.length ? "Không tìm thấy booking phù hợp." : "Chưa có booking trong lịch sử."}</strong><p>Thay đổi bộ lọc hoặc từ khóa để xem các booking khác.</p></div>
            : <div className="pt-compact-history" aria-live="polite">{historyPageData.items.map((booking) => bookingCard(booking))}</div>}
          {filteredHistory.length > 0 && <nav className="pt-history-pagination" aria-label="Phân trang lịch sử booking">
            <span>{filteredHistory.length} booking · Trang {historyPageData.page + 1} / {historyPageData.totalPages}</span>
            <div>
              <button type="button" className="pt-secondary-button" onClick={() => setHistoryPage((page) => Math.max(0, page - 1))} disabled={historyPageData.page <= 0}>Trước</button>
              <button type="button" className="pt-secondary-button" onClick={() => setHistoryPage((page) => page + 1)} disabled={historyPageData.page + 1 >= historyPageData.totalPages}>Sau</button>
            </div>
          </nav>}
        </>}
      </section>
    </div>
  </main>
</div>

{paymentModalBooking && <PaymentMethodModal
  amount={money(paymentModalBooking.finalPrice)}
  context={`PT Booking #${paymentModalBooking.id} · ${paymentModalBooking.sportName} với ${paymentModalBooking.coachName} · giữ chỗ đến ${vietnamTime(paymentModalBooking.paymentExpiresAt, true)} (giờ Việt Nam)`}
  busy={paymentBusyId === paymentModalBooking.id}
  error={paymentError}
  canContinue={!paymentReconciliationRequired && !pendingPtPayment(payments, paymentModalBooking.id)}
  onCancel={closePaymentModal}
  onSuccess={() => setPaymentModalBooking(null)}
  onContinue={(method) => startPayment(paymentModalBooking, method)}
/>}
 </>
);
}
