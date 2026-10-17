import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { createPtBookingApi, getMyPtBookingsApi, getPublicSportsApi, getPtEligibleCoachesApi } from "../api";
import "./PtBooking.css";

const money = (value) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(value ?? 0);

export default function PtBooking({ token, onLogout }) {
  const [sports, setSports] = useState([]); const [sportId, setSportId] = useState("");
  const [startTime, setStartTime] = useState(""); const [coaches, setCoaches] = useState([]);
  const [history, setHistory] = useState([]); const [selectedCoach, setSelectedCoach] = useState("");
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false);
  const [error, setError] = useState(""); const [notice, setNotice] = useState("");
  useEffect(() => {
    Promise.all([getPublicSportsApi(), getMyPtBookingsApi(token)]).then(([items, rows]) => {
      setSports(Array.isArray(items) ? items : []); setHistory(Array.isArray(rows) ? rows : []);
    }).catch((e) => setError(e.message || "Could not load booking data.")).finally(() => setLoading(false));
  }, [token]);
  async function findCoaches(event) {
    event.preventDefault(); setBusy(true); setError(""); setNotice("");
    try { const rows = await getPtEligibleCoachesApi(token, sportId, startTime); setCoaches(rows);
      setSelectedCoach(""); if (!rows.length) setNotice("No qualified coaches are free at this time.");
    } catch (e) { setError(e.message || "Could not check coach schedules."); setCoaches([]); }
    finally { setBusy(false); }
  }
  async function book(event) {
    event.preventDefault(); const choice = coaches.find((item) => String(item.coachId) === selectedCoach); if (!choice) return;
    setBusy(true); setError(""); setNotice("");
    try { const result = await createPtBookingApi(token, { coachId: choice.coachId, sportId: Number(sportId), startTime });
      setNotice("Your 90-minute session is held until " + new Date(result.paymentExpiresAt).toLocaleTimeString() + " (Vietnam time). Complete payment within 15 minutes."); setCoaches([]); setSelectedCoach("");
      setHistory(await getMyPtBookingsApi(token));
    } catch (e) { setError(e.message || "Booking failed."); } finally { setBusy(false); }
  }
  if (loading) return <main className="pt-page"><p>Loading PT booking...</p></main>;
  return <main className="pt-page">
    <header className="pt-header"><div><p className="pt-eyebrow">SPORTCENTER / MEMBER</p><h1>Book a 1-on-1 PT session</h1><p>Each session lasts 90 minutes. New bookings remain pending payment.</p><Link to="/member/levels-assessments">Trình độ &amp; lịch sử đánh giá</Link></div><button onClick={onLogout}>Sign out</button></header>
    {error && <p className="pt-alert pt-error" role="alert">{error}</p>}{notice && <p className="pt-alert pt-success" role="status">{notice}</p>}
    <section className="pt-card"><h2>Choose a sport and start time</h2><form className="pt-form" onSubmit={findCoaches}>
      <label>Sport<select required value={sportId} onChange={(e) => setSportId(e.target.value)}><option value="">Select a sport</option>{sports.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
      <label>Start time<input required type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} /></label>
      <button disabled={busy || !sportId || !startTime}>{busy ? "Checking..." : "Find available coaches"}</button>
    </form>
    {coaches.length > 0 && <form className="pt-coach-form" onSubmit={book}><h3>Eligible coaches</h3><div className="pt-coach-list">{coaches.map((coach) => <label className="pt-coach" key={coach.coachId}>
      <input type="radio" name="coach" required value={coach.coachId} checked={selectedCoach === String(coach.coachId)} onChange={(e) => setSelectedCoach(e.target.value)} />
      <span><strong>{coach.coachName}</strong><small>{coach.sportName} / {coach.durationMinutes} minutes</small></span>
      <span className="pt-price"><del>{money(coach.originalPrice)}</del><strong>{money(coach.finalPrice)}</strong><small>Discount {money(coach.discount)}</small></span>
    </label>)}</div><button disabled={busy || !selectedCoach}>Book session / payment pending</button></form>}</section>
    <section className="pt-card"><h2>PT booking history</h2>{history.length === 0 ? <p className="pt-empty">No PT sessions yet.</p> : <div className="pt-history">{history.map((b) => <article className="pt-history-row" key={b.id}>
      <div><strong>{b.sportName} / {b.coachName}</strong><span>{new Date(b.startTime).toLocaleString()} – {new Date(b.endTime).toLocaleTimeString()}</span></div><div className="pt-price"><strong>{money(b.finalPrice)}</strong><span className="pt-status">{b.status === "PENDING_PAYMENT" ? "Payment pending" : b.status}</span></div>
    </article>)}</div>}</section>
  </main>;
}
