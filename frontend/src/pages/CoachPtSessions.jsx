import { useEffect, useState } from "react";
import { getAssignedPtBookingsApi } from "../api";
import "./PtBooking.css";

export default function CoachPtSessions({ token, onLogout }) {
  const [rows, setRows] = useState([]); const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  useEffect(() => { getAssignedPtBookingsApi(token).then((data) => setRows(Array.isArray(data) ? data : []))
    .catch((e) => setError(e.message || "Could not load PT sessions.")).finally(() => setLoading(false)); }, [token]);
  return <main className="pt-page"><header className="pt-header"><div><p className="pt-eyebrow">SPORTCENTER / COACH</p><h1>Assigned PT sessions</h1></div><button onClick={onLogout}>Sign out</button></header>
    {error && <p className="pt-alert pt-error" role="alert">{error}</p>}<section className="pt-card"><h2>Sessions</h2>{loading ? <p>Loading sessions...</p> : rows.length === 0 ? <p className="pt-empty">No PT sessions assigned.</p> :
      <div className="pt-history">{rows.map((b) => <article className="pt-history-row" key={b.id}><div><strong>{b.memberName} / {b.sportName}</strong><span>{new Date(b.startTime).toLocaleString()} – {new Date(b.endTime).toLocaleTimeString()}</span></div><div><strong>{b.status === "PENDING_PAYMENT" ? "Payment pending" : b.status}</strong><span>{b.durationMinutes} minutes</span></div></article>)}</div>}</section>
  </main>;
}
