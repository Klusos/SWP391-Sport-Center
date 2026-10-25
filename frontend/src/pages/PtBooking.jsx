import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { createPaymentApi, createPtBookingApi, getMyPtBookingsApi, getPublicSportsApi, getPtEligibleCoachesApi } from "../api";
import { validVnpayUrl } from "../courseCheckout";
import "./PtBooking.css";

const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];
const money = (value) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(value ?? 0);
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
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false);
  const [error, setError] = useState(""), [notice, setNotice] = useState("");
  const action = useRef(false);
  const lifecycle = useRef(0);
  const [searchParams] = useSearchParams();

  useEffect(() => {
    const version = ++lifecycle.current;
    let active = true;
    Promise.resolve().then(async () => {
      if (!active) return;
      setLoading(true); setError("");
      try {
        const [items, bookings] = await Promise.all([getPublicSportsApi(), getMyPtBookingsApi(token)]);
        if (active && lifecycle.current === version) { setSports(rows(items)); setHistory(rows(bookings)); }
      } catch (requestError) { if (active) setError(requestError.message || "Could not load booking data."); }
      finally { if (active) setLoading(false); }
    });
    return () => { active = false; lifecycle.current += 1; };
  }, [token]);

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
      setNotice("Your 90-minute session is held until " + vietnamTime(result.paymentExpiresAt, true) + " (Vietnam time). Complete payment within 15 minutes; this is not payment confirmation.");
      setCoaches([]); setSelectedCoach("");
      // Keep the created pending booking visible even if the history read fails.
      setHistory((previous) => [result, ...previous.filter((booking) => booking.id !== result.id)]);
      try {
        const bookings = await getMyPtBookingsApi(token);
        if (lifecycle.current === version) setHistory(rows(bookings));
      } catch (requestError) { if (lifecycle.current === version) setError("Booking #" + result.id + " was created, but history could not be refreshed: " + requestError.message); }
    } catch (requestError) { if (lifecycle.current === version) setError(requestError.message || "Booking failed."); }
    finally { action.current = false; if (lifecycle.current === version) setBusy(false); }
  }

  const holdActive = (booking) => booking.status === "PENDING_PAYMENT" && booking.paymentExpiresAt
    && new Date(/[zZ]|[+-]\d\d:\d\d$/.test(booking.paymentExpiresAt) ? booking.paymentExpiresAt : booking.paymentExpiresAt + "+07:00") > new Date();

  async function payOnline(booking) {
    if (action.current || loading) return;
    action.current = true; setBusy(true); setError(""); setNotice("");
    try {
      // The hold lasts 15 minutes, so PT bookings are paid online only (no counter queue for PT).
      const payment = await createPaymentApi(token, { method: "ONLINE", purpose: "PT_BOOKING", referenceId: booking.id });
      if (!validVnpayUrl(payment.paymentUrl)) throw new Error("Could not open a valid VNPay Sandbox URL. Check your payment history before retrying.");
      window.location.assign(payment.paymentUrl);
    } catch (requestError) {
      setError(requestError.message || "Could not start the VNPay payment.");
      action.current = false; setBusy(false);
    }
  }

  const returnedStatus = searchParams.get("paymentStatus");
  const returnedPaymentId = searchParams.get("paymentId");

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

      {returnedStatus && (
        <p className={"pt-alert " + (returnedStatus === "SUCCESS" ? "pt-success" : "pt-error")} role="status">
          {returnedStatus === "SUCCESS"
            ? `Payment #${returnedPaymentId} succeeded via VNPay. Your PT session is confirmed.`
            : returnedStatus === "REFUND_REQUIRED"
              ? `Payment #${returnedPaymentId} was received after the hold expired; the center will arrange a refund.`
              : `Payment ${returnedPaymentId ? "#" + returnedPaymentId + " " : ""}was not completed (${returnedStatus}). You can retry while the hold is active.`}
        </p>
      )}

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

                  {holdActive(booking) && (
                    <button type="button" disabled={busy} onClick={() => void payOnline(booking)}>
                      Pay with VNPay
                    </button>
                  )}
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
