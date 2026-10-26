import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { cancelPaymentApi, createPaymentApi, createPtBookingApi, getMyPaymentsApi, getMyPtBookingsApi, getPublicSportsApi, getPtEligibleCoachesApi } from "../api";
import { acquirePtPaymentAction, bookingPayments, focusPtPaymentPanel, pendingPtPayment, ptPaymentExpired, ptPaymentMoney, ptOnlineUrlIsTrusted, ptPaymentPayload, ptCancellationWarning } from "../ptPaymentFlow";
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
  const [payments, setPayments] = useState([]), [paymentBusyId, setPaymentBusyId] = useState(null);
  const [paymentError, setPaymentError] = useState("");
  const [now, setNow] = useState(0);
  const focusBookingId = useRef(null);
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false);
  const [error, setError] = useState(""), [notice, setNotice] = useState("");
  const action = useRef(false);
  const lifecycle = useRef(0);
  const paymentAction = useRef(false);

  async function refreshAuthoritative() {
    const [bookings, paymentRows] = await Promise.all([getMyPtBookingsApi(token), getMyPaymentsApi(token)]);
    setHistory(rows(bookings)); setPayments(rows(paymentRows));
  }

  async function startPayment(booking, method) {
    if (paymentBusyId !== null || !acquirePtPaymentAction(paymentAction)) return;
    if (booking.status !== "PENDING_PAYMENT" || ptPaymentExpired(booking, currentTimestamp())) {
      setPaymentError("This PT booking payment hold has expired. Refresh booking status before continuing."); paymentAction.current = false; return;
    }
    if (pendingPtPayment(payments, booking.id)) {
      setPaymentError("A pending payment already exists for this booking. Cancel it or wait for settlement before switching methods.");
      try { await refreshAuthoritative(); } catch { /* Keep the current payment state visible. */ }
      paymentAction.current = false;
      return;
    }
    setPaymentBusyId(booking.id); setPaymentError("");
    try {
      const payment = await createPaymentApi(token, ptPaymentPayload(booking.id, method));
      setPayments((previous) => [payment, ...previous.filter((item) => item.id !== payment.id)]);
      if (method === "ONLINE" && (!payment?.paymentUrl || !ptOnlineUrlIsTrusted(payment.paymentUrl))) {
        try { await refreshAuthoritative(); } catch { /* Preserve the created response in UI state. */ }
        setPaymentError("Online payment was created, but its URL is missing or not a trusted VNPay Sandbox URL. The payment remains visible; do not create another payment."); return;
      }
      try { await refreshAuthoritative(); }
      catch { setPaymentError("Payment was created, but booking/payment history could not refresh. Check status before any further action."); }
      if (method === "ONLINE") window.location.assign(payment.paymentUrl);
    } catch (requestError) {
      setPaymentError(requestError.status === 409
        ? "A payment already exists or the booking is no longer payable. Booking and payment history were refreshed."
        : `Payment outcome is uncertain: ${requestError.message || "network error"}. Check refreshed history before trying again.`);
      try { await refreshAuthoritative(); } catch { /* Preserve the uncertainty warning. */ }
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

      <section className="pt-card pt-history-card">
        <div className="pt-section-heading">
          <span className="pt-section-icon">
            <span aria-hidden="true">📅</span>
          </span>

          <div>
            <h2>PT booking history</h2>
            <p>
              Review your previous and upcoming PT sessions.
            </p>
            <button type="button" className="pt-secondary-button" disabled={paymentBusyId !== null} onClick={() => void refreshAuthoritative().catch((requestError) => setPaymentError("Could not refresh booking and payment history: " + requestError.message))}>Refresh booking and payment status</button>
          </div>
        </div>

        {loading ? (
          <div className="pt-loading" role="status">
            Loading booking history...
          </div>
        ) : history.length === 0 ? (
          <div className="pt-empty">
            <span
              className="pt-empty-icon"
              aria-hidden="true"
            >
              📋
            </span>

            <strong>No PT sessions yet.</strong>

            <p>
              Your bookings will appear here after you
              book a session.
            </p>
          </div>
        ) : (
          <div className="pt-history">
            {history.map((booking) => (
              <article
                className="pt-history-row"
                key={booking.id}
              >
                <div className="pt-history-main">
                  <strong>
                    {booking.sportName} /{" "}
                    {booking.coachName}
                  </strong>

                  <span>
                    {vietnamTime(booking.startTime, true)}
                  </span>

                  <small>
                    Ends at{" "}
                    {vietnamTime(booking.endTime)}
                  </small>
                </div>

                <div className="pt-history-meta">
                  <strong className="pt-history-price">
                    {money(booking.finalPrice)}
                  </strong>

                  <span
                    className={
                      "pt-status " +
                      (booking.status === "PENDING_PAYMENT"
                        ? "pending"
                        : booking.status === "COMPLETED"
                          ? "completed"
                          : booking.status === "CANCELLED"
                            ? "cancelled"
                            : "")
                    }
                  >
                    {booking.status === "PENDING_PAYMENT"
                      ? "Payment pending"
                      : booking.status}
                  </span>
                  {booking.paymentExpiresAt && <small>Payment hold deadline: {vietnamTime(booking.paymentExpiresAt, true)} (Vietnam time)</small>}
                  {(() => {
                    const related = bookingPayments(payments, booking.id);
                    const pending = pendingPtPayment(payments, booking.id);
                    const expired = ptPaymentExpired(booking, now);
                    return <div id={`pt-payment-panel-${booking.id}`} className="pt-payment-actions" tabIndex={-1} aria-label={`Payment options for PT booking ${booking.id}`}>
                      {related.map((payment) => <div className="pt-payment-state" key={payment.id}>
                        <strong>{payment.method} payment #{payment.id}: {payment.status}</strong>
                        <span>{ptPaymentMoney(payment.amount)}</span>
                        {payment.status === "PENDING" && <>
                          <small>{payment.method === "COUNTER" ? "Pay at Receptionist. The booking is confirmed only after staff records settlement." : "Awaiting VNPay settlement. Browser return alone does not confirm payment."}</small>
                          <button type="button" className="pt-secondary-button" disabled={paymentBusyId !== null} onClick={() => void cancelPendingPayment(payment)}>Cancel pending payment</button>
                        </>}
                      </div>)}
                      {booking.status === "PENDING_PAYMENT" && (expired
                        ? <p role="status">Payment hold expired. Refresh booking and payment history; no new payment can be started for this hold.</p>
                        : pending
                          ? <p role="status">A pending {pending.method} payment already exists. Cancel it or wait for settlement before switching methods.</p>
                          : <div className="pt-payment-choice">
                            <p>Choose one payment method. Booking confirmation comes only from the backend.</p>
                            <button type="button" disabled={paymentBusyId !== null} onClick={() => void startPayment(booking, "ONLINE")}>{paymentBusyId === booking.id ? "Processing..." : "Pay online with VNPay Sandbox"}</button>
                            <button type="button" disabled={paymentBusyId !== null} onClick={() => void startPayment(booking, "COUNTER")}>Pay at Receptionist</button>
                          </div>)}
                    </div>;
                  })()}
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  </main>
</div>

);
}
