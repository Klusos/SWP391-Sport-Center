import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import PaymentMethodModal from "../components/PaymentMethodModal";
import { cancelPaymentApi, cancelPtBookingApi, createPaymentApi, createPtBookingApi, getMyPaymentsApi, getMyPtBookingsApi, getPublicSportsApi, getPtEligibleCoachesApi } from "../api";
import { acquirePtPaymentAction, bookingPayments, filterPtBookingHistory, focusPtPaymentPanel, paginatePtBookingHistory, partitionPtBookings, pendingPtPayment, ptPaymentExpired, ptPaymentMoney, ptOnlineUrlIsTrusted, ptPaymentPayload, ptCancellationWarning } from "../ptPaymentFlow";
import { canMemberCancel, ptErrorMessage } from "../ptBooking.js";
import "./PtBooking.css";

const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];
const money = (value) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(value ?? 0);
const currentTimestamp = () => Date.now();
const PT_STATUS_LABELS = { PENDING_PAYMENT: "Chờ thanh toán", CONFIRMED: "Đã xác nhận", CANCELLED: "Đã hủy", EXPIRED: "Đã hết hạn", COMPLETED: "Đã hoàn thành", NO_SHOW: "Vắng mặt (buổi đã sử dụng)" };
const PAYMENT_STATUS_LABELS = { PENDING: "Đang chờ", SUCCESS: "Thành công", CANCELLED: "Đã hủy", FAILED: "Thất bại", REFUND_REQUIRED: "Chờ xử lý hoàn tiền", REFUNDED: "Đã hoàn tiền" };
const PAYMENT_METHOD_LABELS = { ONLINE: "Trực tuyến", COUNTER: "Tại quầy" };
function vietnamTime(value, date = false) {
  if (!value) return "Chưa có thời gian";
  const local = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value);
  const time = new Date(local ? value + "+07:00" : value);
  if (Number.isNaN(time.getTime())) return "Chưa có thời gian";
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
      setPaymentError("Hãy làm mới trạng thái lịch đặt và thanh toán trước khi bắt đầu giao dịch mới.");
      return;
    }
    if (paymentBusyId !== null || booking.status !== "PENDING_PAYMENT" || ptPaymentExpired(booking, currentTimestamp())) {
      setPaymentError("Thời hạn thanh toán cho lịch PT này đã hết. Hãy làm mới trạng thái lịch đặt trước khi tiếp tục.");
      return;
    }
    if (pendingPtPayment(payments, booking.id)) {
      setPaymentError("Lịch đặt này đã có giao dịch đang chờ. Hãy hủy giao dịch hoặc chờ xử lý xong trước khi đổi phương thức.");
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
      setPaymentError("Thời hạn thanh toán cho lịch PT này đã hết. Hãy làm mới trạng thái lịch đặt trước khi tiếp tục."); paymentAction.current = false; return false;
    }
    if (pendingPtPayment(payments, booking.id)) {
      setPaymentError("Lịch đặt này đã có giao dịch đang chờ. Hãy hủy giao dịch hoặc chờ xử lý xong trước khi đổi phương thức.");
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
        setPaymentError("Đã tạo giao dịch nhưng chưa thể làm mới lịch đặt và lịch sử thanh toán. Hãy cập nhật trạng thái trước khi thao tác tiếp.");
      }
      if (!refreshedPayments?.some((item) => Number(item.id) === Number(payment.id))) {
        setPayments((previous) => [payment, ...previous.filter((item) => item.id !== payment.id)]);
        setPaymentReconciliationRequired(true);
        setPaymentError("Giao dịch đã tạo chưa xuất hiện trong lịch sử vừa tải. Hãy cập nhật trạng thái trước khi thao tác tiếp.");
      }
      if (method === "ONLINE" && (!payment?.paymentUrl || !ptOnlineUrlIsTrusted(payment.paymentUrl))) {
        setPaymentError("Đã tạo thanh toán trực tuyến nhưng đường dẫn VNPay Sandbox không hợp lệ hoặc không có. Giao dịch vẫn được lưu; không tạo giao dịch khác."); return true;
      }
      if (method === "ONLINE") window.location.assign(payment.paymentUrl);
      return true;
    } catch (requestError) {
      setPaymentError(requestError.status === 409
        ? "Lịch đặt đã có giao dịch hoặc không còn đủ điều kiện thanh toán. Lịch đặt và lịch sử thanh toán đã được tải lại."
        : `Chưa xác định được kết quả thanh toán: ${requestError.message || "lỗi kết nối"}. Hãy kiểm tra lịch sử vừa tải trước khi thử lại.`);
      try { await refreshAuthoritative(); } catch { /* Keep payment actions locked until the user refreshes. */ }
      setPaymentReconciliationRequired(true);
      setPaymentError("Chưa thể xác nhận kết quả yêu cầu thanh toán. Hãy kiểm tra lịch sử đã tải và làm mới thêm lần nữa trước khi thử lại.");
      return false;
    } finally { paymentAction.current = false; setPaymentBusyId(null); }
  }

  async function cancelBooking(booking) {
    if (paymentBusyId !== null || !canMemberCancel(booking, currentTimestamp()) || !acquirePtPaymentAction(paymentAction)) return;
    const paid = bookingPayments(payments, booking.id).some((payment) => payment.status === "SUCCESS");
    const prompt = paid
      ? `Hủy lịch PT #${booking.id} đã xác nhận? Hệ thống sẽ ghi nhận khoản cần hoàn theo số tiền đã trả. Nhân viên cần xử lý và xác nhận hoàn tiền; thông báo này không có nghĩa là tiền đã được hoàn.`
      : booking.status === "PENDING_PAYMENT"
        ? `Hủy lịch PT #${booking.id} đang chờ thanh toán? Lịch đặt và giao dịch đang chờ sẽ bị hủy. Giao dịch chưa thanh toán không phát sinh khoản hoàn tiền.`
        : `Hủy lịch PT #${booking.id}?`;
    if (!window.confirm(prompt)) { paymentAction.current = false; return; }
    setPaymentBusyId(booking.id); setPaymentError(""); setNotice("");
    try {
      await cancelPtBookingApi(token, booking.id);
      await refreshAuthoritative();
      setNotice(paid
        ? `Đã hủy lịch PT #${booking.id}. Khoản đã thanh toán đang chờ nhân viên xác nhận hoàn tiền.`
        : `Đã hủy lịch PT #${booking.id}.`);
    } catch (requestError) {
      setPaymentError(ptErrorMessage(requestError, "Không thể hủy lịch PT này."));
      try { await refreshAuthoritative(); } catch { /* Keep the server cancellation error visible. */ }
    } finally { paymentAction.current = false; setPaymentBusyId(null); }
  }
  async function cancelPendingPayment(payment) {
    if (paymentBusyId !== null || payment.status !== "PENDING" || !acquirePtPaymentAction(paymentAction)) return;
    if (!window.confirm(ptCancellationWarning(payment))) { paymentAction.current = false; return; }
    setPaymentBusyId(payment.id); setPaymentError("");
    try { await cancelPaymentApi(token, payment.id); await refreshAuthoritative(); }
    catch (requestError) {
      setPaymentError(`Chưa xác định được kết quả hủy giao dịch: ${requestError.message || "lỗi kết nối"}. Hãy làm mới lịch sử và chờ xác nhận trạng thái trước khi tạo thanh toán khác.`);
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
      } catch (requestError) { if (active) setError(requestError.message || "Không thể tải dữ liệu đặt lịch PT."); }
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
        <strong>Thanh toán {PAYMENT_METHOD_LABELS[payment.method] || payment.method} #{payment.id}: {PAYMENT_STATUS_LABELS[payment.status] || payment.status}</strong>
        <span>{ptPaymentMoney(payment.amount)}</span>
        {payment.status === "PENDING" && <>
          <small>{payment.method === "COUNTER" ? "Chờ xác nhận tại quầy. Lịch đặt chỉ được xác nhận sau khi nhân viên ghi nhận thanh toán." : "Đang chờ VNPay xác nhận. Việc quay về từ trình duyệt không tự xác nhận thanh toán."}</small>
          <button type="button" className="pt-secondary-button" disabled={paymentBusyId !== null} onClick={() => void cancelPendingPayment(payment)}>Hủy thanh toán đang chờ</button>
        </>}
      </div>)}
      {booking.status === "PENDING_PAYMENT" && (expired
        ? <p role="status">Đã hết hạn giữ chỗ; không thể tạo thanh toán mới cho lịch đặt này.</p>
        : paymentReconciliationRequired
          ? <p role="alert">Hãy làm mới lịch sử thanh toán trước khi tiếp tục.</p>
          : pending
            ? <p role="status">Đã có thanh toán {PAYMENT_METHOD_LABELS[pending.method] || pending.method} đang chờ. Không tạo thanh toán khác; hãy chờ xác nhận hoặc hủy yêu cầu đang chờ.</p>
            : allowCreate && <div className="pt-payment-choice">
              <p>Chọn phương thức thanh toán. Lịch đặt chỉ được xác nhận theo trạng thái từ máy chủ.</p>
              <button id={`pt-payment-button-${booking.id}`} type="button" disabled={paymentBusyId !== null || paymentReconciliationRequired} onClick={() => openPaymentModal(booking)}>
                {paymentBusyId === booking.id ? "Đang xử lý…" : "Thanh toán"}
              </button>
            </div>)}
    </div>;
  }

  function bookingStatus(booking) {
    return PT_STATUS_LABELS[booking.status] || booking.status;
  }

  function bookingCard(booking, actionable = false) {
    return <article className={actionable ? "pt-booking-summary is-actionable" : "pt-booking-summary"} key={booking.id}>
      <div className="pt-booking-summary-main">
        <strong>{booking.sportName || "Môn thể thao"} · {booking.coachName || "Huấn luyện viên"}</strong>
        <span>{vietnamTime(booking.startTime, true)} – {vietnamTime(booking.endTime)}</span>
        {actionable && <small>Giữ chỗ đến {vietnamTime(booking.paymentExpiresAt, true)} (giờ Việt Nam)</small>}
        <span className="pt-status">{bookingStatus(booking)}</span>
        {canMemberCancel(booking, now) && <button type="button" className="pt-secondary-button" disabled={paymentBusyId !== null} onClick={() => void cancelBooking(booking)}>{paymentBusyId === booking.id ? "Đang hủy…" : "Hủy lịch đặt"}</button>}
        {booking.status === "PENDING_PAYMENT" && ptPaymentExpired(booking, now) && <small className="pt-expired-hold">Đã hết hạn giữ chỗ; máy chủ vẫn đang ghi nhận trạng thái chờ thanh toán.</small>}
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
      setError("Vui lòng chọn bộ môn và thời gian bắt đầu."); return;
    }
    action.current = true; setBusy(true); setError(""); setNotice(""); setCoaches([]); setSelectedCoach("");
    const version = lifecycle.current;
    try {
      const eligible = rows(await getPtEligibleCoachesApi(token, sportId, startTime));
      if (lifecycle.current !== version) return;
      setCoaches(eligible);
      if (!eligible.length) setNotice("Hiện không có huấn luyện viên phù hợp vào thời gian này.");
    } catch (requestError) { if (lifecycle.current === version) setError(requestError.message || "Không thể kiểm tra lịch của huấn luyện viên."); }
    finally { action.current = false; if (lifecycle.current === version) setBusy(false); }
  }

  async function book(event) {
    event.preventDefault();
    if (action.current || loading) return;
    const choice = coaches.find((coach) => String(coach.coachId) === selectedCoach);
    if (!choice) { setError("Vui lòng chọn một huấn luyện viên còn lịch."); return; }
    action.current = true; setBusy(true); setError(""); setNotice("");
    const version = lifecycle.current;
    try {
      const result = await createPtBookingApi(token, { coachId: choice.coachId, sportId: Number(sportId), startTime });
      if (lifecycle.current !== version) return;
      setNotice(`Lịch PT #${result.id} (${PT_STATUS_LABELS[result.status] || result.status}). Buổi tập 90 phút được giữ chỗ đến ${vietnamTime(result.paymentExpiresAt, true)} (giờ Việt Nam). Vui lòng thanh toán trong 15 phút; thông báo này không xác nhận thanh toán.`);
      setCoaches([]); setSelectedCoach("");
      // Keep the created pending booking visible even if the history read fails.
      setHistory((previous) => [result, ...previous.filter((booking) => booking.id !== result.id)]);
      focusBookingId.current = result.id;
      try {
        await refreshAuthoritative();
      } catch (requestError) { if (lifecycle.current === version) setError(`Đã tạo lịch PT #${result.id} nhưng chưa thể làm mới lịch sử: ${requestError.message}`); }
    } catch (requestError) { if (lifecycle.current === version) setError(requestError.message || "Không thể tạo lịch PT."); }
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

          <h1>Đặt buổi huấn luyện cá nhân</h1>

          <p className="pt-description">
            Mỗi buổi tập kéo dài 90 phút. Lịch đặt mới sẽ ở trạng thái chờ thanh toán.
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
            <h2>Chọn bộ môn và thời gian bắt đầu</h2>
            <p>
              Chọn bộ môn và khung giờ phù hợp cho buổi tập.
            </p>
          </div>
        </div>

        {loading ? (
          <div className="pt-loading" role="status">
            <span className="pt-spinner" />
            <span>Đang tải dữ liệu đặt lịch PT…</span>
          </div>
        ) : (
          <form
            className="pt-form"
            onSubmit={findCoaches}
          >
            <label className="pt-field">
              <span>Bộ môn</span>

              <select
                required
                value={sportId}
                onChange={(e) =>
                  changeCriteria(setSportId, e.target.value)
                }
                disabled={busy}
              >
                <option value="">Chọn bộ môn</option>

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
              <span>Thời gian bắt đầu</span>

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
                ? "Đang kiểm tra…"
                : "Tìm huấn luyện viên phù hợp"}
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
                <h3>Huấn luyện viên phù hợp</h3>

                <p>
                  {coaches.length} huấn luyện viên có lịch phù hợp
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
                      {coach.durationMinutes} phút
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
                      Giảm giá {money(coach.discount)}
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
                ? "Đang xử lý…"
                : "Đặt lịch · chờ thanh toán"}
            </button>
          </form>
        )}
      </section>

      <section className="pt-card pt-actionable-card" aria-labelledby="pt-actionable-title">
        <div className="pt-section-heading">
          <span className="pt-section-icon"><span aria-hidden="true">⏳</span></span>
          <div>
            <h2 id="pt-actionable-title">Cần xử lý</h2>
            <p>Lịch đặt đang được giữ chỗ sắp xếp theo hạn thanh toán gần nhất.</p>
          </div>
          <button type="button" className="pt-secondary-button pt-actionable-refresh" aria-label={"L\u00e0m m\u1edbi tr\u1ea1ng th\u00e1i l\u1ecbch \u0111\u1eb7t v\u00e0 thanh to\u00e1n"} title={"L\u00e0m m\u1edbi tr\u1ea1ng th\u00e1i l\u1ecbch \u0111\u1eb7t v\u00e0 thanh to\u00e1n"} disabled={paymentBusyId !== null} onClick={() => void refreshAuthoritative().catch((requestError) => setPaymentError("Kh\u00f4ng th\u1ec3 l\u00e0m m\u1edbi l\u1ecbch \u0111\u1eb7t v\u00e0 l\u1ecbch s\u1eed thanh to\u00e1n: " + requestError.message))}>{"L\u00e0m m\u1edbi"}</button>
        </div>
        {loading ? <div className="pt-loading" role="status">Đang tải lịch đặt cần xử lý…</div>
          : actionableBookings.length === 0 ? <div className="pt-empty"><strong>Không có lịch đặt cần xử lý.</strong><p>Lịch đặt mới sẽ xuất hiện ở đây sau khi được tạo.</p></div>
            : <div className="pt-actionable-list">{actionableBookings.map((booking) => bookingCard(booking, true))}</div>}
      </section>

      <section className="pt-card pt-history-card" aria-labelledby="pt-history-title">
        <div className="pt-section-heading">
          <span className="pt-section-icon">
            <span aria-hidden="true">📅</span>
          </span>

          <div>
            <h2 id="pt-history-title">Lịch sử đặt lịch</h2>
            <p>Lịch đặt đã xác nhận, đã hủy hoặc hết hạn được lưu tại đây.</p>
          </div>
        </div>

        {loading ? (
          <div className="pt-loading" role="status">
            Đang tải lịch sử đặt lịch…
          </div>
        ) : history.length === 0 ? (
          <div className="pt-empty">
            <strong>Chưa có lịch sử đặt lịch.</strong>
            <p>Lịch đặt sẽ chuyển vào đây sau khi được xác nhận, hủy hoặc hết hạn.</p>
          </div>
        ) : <>
          <div className="pt-history-controls">
            <label>Tìm môn hoặc huấn luyện viên
              <input type="search" value={historyQuery} onChange={(event) => { setHistoryQuery(event.target.value); setHistoryPage(0); }} placeholder="Nhập tên môn hoặc huấn luyện viên" />
            </label>
            <label>Lọc trạng thái
              <select value={historyStatus} onChange={(event) => { setHistoryStatus(event.target.value); setHistoryPage(0); }}>
                <option value="ALL">Tất cả trạng thái</option>
                <option value="CONFIRMED">Đã xác nhận</option>
                <option value="CANCELLED">Đã hủy</option>
                <option value="EXPIRED">Đã hết hạn</option>
                <option value="PENDING_PAYMENT">Chờ thanh toán (đã hết hạn)</option>
              </select>
            </label>
          </div>
          {filteredHistory.length === 0
            ? <div className="pt-empty"><strong>{historicalBookings.length ? "Không tìm thấy lịch đặt phù hợp." : "Chưa có lịch đặt trong lịch sử."}</strong><p>Thay đổi bộ lọc hoặc từ khóa để xem các lịch đặt khác.</p></div>
            : <div className="pt-compact-history" aria-live="polite">{historyPageData.items.map((booking) => bookingCard(booking))}</div>}
          {filteredHistory.length > 0 && <nav className="pt-history-pagination" aria-label="Phân trang lịch sử đặt lịch">
            <span>{filteredHistory.length} lịch đặt · Trang {historyPageData.page + 1} / {historyPageData.totalPages}</span>
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
  context={`Lịch PT #${paymentModalBooking.id} · ${paymentModalBooking.sportName} với ${paymentModalBooking.coachName} · giữ chỗ đến ${vietnamTime(paymentModalBooking.paymentExpiresAt, true)} (giờ Việt Nam)`}
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
