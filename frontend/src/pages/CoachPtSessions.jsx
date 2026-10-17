import { useEffect, useState } from "react";
import { getAssignedPtBookingsApi } from "../api";
import Sidebar from "../components/Sidebar";
import "./PtBooking.css";

export default function CoachPtSessions({ token, role, onLogout, onNavigate }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    getAssignedPtBookingsApi(token)
      .then((data) => setRows(Array.isArray(data) ? data : []))
      .catch((requestError) => setError(requestError.message || "Could not load PT sessions."))
      .finally(() => setLoading(false));
  }, [token]);

  return <div className="layout">
    <Sidebar role={role} activePage="coach-pt-sessions" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main coach-pt-main">
      <div className="pt-page">
        <header className="pt-header"><div><p className="pt-eyebrow">SPORTCENTER / COACH</p><h1>Assigned PT sessions</h1></div></header>
        {error && <p className="pt-alert pt-error" role="alert">{error}</p>}
        <section className="pt-card"><h2>Sessions</h2>{loading ? <p>Loading sessions...</p> : rows.length === 0 ? <p className="pt-empty">No PT sessions assigned.</p> :
          <div className="pt-history">{rows.map((booking) => <article className="pt-history-row" key={booking.id}>
            <div><strong>{booking.memberName} / {booking.sportName}</strong><span>{new Date(booking.startTime).toLocaleString()} – {new Date(booking.endTime).toLocaleTimeString()}</span></div>
            <div><strong>{booking.status === "PENDING_PAYMENT" ? "Payment pending" : booking.status}</strong><span>{booking.durationMinutes} minutes</span></div>
          </article>)}</div>}</section>
      </div>
    </main>
  </div>;
}
