import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import {
createPtBookingApi,
getMyPtBookingsApi,
getPublicSportsApi,
getPtEligibleCoachesApi,
} from "../api";
import "./PtBooking.css";

const money = (value) =>
new Intl.NumberFormat("vi-VN", {
style: "currency",
currency: "VND",
}).format(value ?? 0);

export default function PtBooking({
token,
role,
onLogout,
onNavigate,
}) {
const [sports, setSports] = useState([]);
const [sportId, setSportId] = useState("");
const [startTime, setStartTime] = useState("");
const [coaches, setCoaches] = useState([]);
const [history, setHistory] = useState([]);
const [selectedCoach, setSelectedCoach] = useState("");

const [loading, setLoading] = useState(true);
const [busy, setBusy] = useState(false);
const [error, setError] = useState("");
const [notice, setNotice] = useState("");

useEffect(() => {
let cancelled = false;

async function loadData() {
  setLoading(true);
  setError("");

  try {
    const [items, rows] = await Promise.all([
      getPublicSportsApi(),
      getMyPtBookingsApi(token),
    ]);

    if (!cancelled) {
      setSports(Array.isArray(items) ? items : []);
      setHistory(Array.isArray(rows) ? rows : []);
    }
  } catch (e) {
    if (!cancelled) {
      setError(
        e.message || "Could not load booking data."
      );
    }
  } finally {
    if (!cancelled) {
      setLoading(false);
    }
  }
}

loadData();

return () => {
  cancelled = true;
};

}, [token]);

async function findCoaches(event) {
event.preventDefault();

if (busy) return;

if (!sportId || !startTime) {
  setError("Please select a sport and start time.");
  return;
}

setBusy(true);
setError("");
setNotice("");
setCoaches([]);
setSelectedCoach("");

try {
  const rows = await getPtEligibleCoachesApi(
    token,
    sportId,
    startTime
  );

  const eligibleCoaches = Array.isArray(rows) ? rows : [];

  setCoaches(eligibleCoaches);

  if (eligibleCoaches.length === 0) {
    setNotice(
      "No qualified coaches are free at this time."
    );
  }
} catch (e) {
  setError(
    e.message || "Could not check coach schedules."
  );
} finally {
  setBusy(false);
}

}

async function book(event) {
event.preventDefault();

if (busy) return;

const choice = coaches.find(
  (item) => String(item.coachId) === selectedCoach
);

if (!choice) {
  setError("Please select an available coach.");
  return;
}

setBusy(true);
setError("");
setNotice("");

try {
  const result = await createPtBookingApi(token, {
    coachId: choice.coachId,
    sportId: Number(sportId),
    startTime,
  });

  setNotice(
    "Your 90-minute session is held until " +
      new Date(result.paymentExpiresAt).toLocaleTimeString(
        "vi-VN",
        { timeZone: "Asia/Ho_Chi_Minh" }
      ) +
      " (Vietnam time). Complete payment within 15 minutes."
  );

  setCoaches([]);
  setSelectedCoach("");

  const rows = await getMyPtBookingsApi(token);
  setHistory(Array.isArray(rows) ? rows : []);
} catch (e) {
  setError(e.message || "Booking failed.");
} finally {
  setBusy(false);
}

}

return (
<div className="layout pt-booking-layout">
<Sidebar
role={role || "MEMBER"}
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
                  setSportId(e.target.value)
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
                  setStartTime(e.target.value)
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
                    {new Date(
                      booking.startTime
                    ).toLocaleString("vi-VN")}
                  </span>

                  <small>
                    Ends at{" "}
                    {new Date(
                      booking.endTime
                    ).toLocaleTimeString("vi-VN")}
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