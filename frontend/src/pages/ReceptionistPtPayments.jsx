import { useEffect, useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { getMemberPaymentsApi, getPendingPtPaymentsApi, processCounterPaymentApi } from "../api";
import { canViewPtPayments, createPtCounterQueue, ptPaymentExpired, ptPaymentMoney } from "../ptPaymentFlow";
import { vietnamDate } from "../courseCheckout";
import "./ReceptionistPtPayments.css";

function readUncertain(key) {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) || "[]");
    return Array.isArray(value) ? value.filter((row) => [row.paymentId, row.memberId, row.bookingId].every((id) => Number.isSafeInteger(id) && id > 0)) : [];
  } catch { return []; }
}

function formatTime(value) {
  const date = vietnamDate(value);
  return date ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh" }).format(date) : "Time unavailable";
}

export default function ReceptionistPtPayments({ token, role, userId, onNavigate, onLogout }) {
  const [queue, setQueue] = useState({ loading: true, data: null, page: 0, error: "" });
  const [busy, setBusy] = useState(false), [outcome, setOutcome] = useState(null), [uncertain, setUncertain] = useState([]), [now, setNow] = useState(0);
  const controller = useRef(null);
  const storageKey = `sportcenter.pt-counter.${userId}.${role}`;
  useEffect(() => {
    if (!token || !canViewPtPayments(role)) return undefined;
    const initial = readUncertain(storageKey);
    const workflow = createPtCounterQueue({
      fetchQueue: (page, size) => getPendingPtPaymentsApi(token, page, size),
      settle: (id) => processCounterPaymentApi(token, id, true, "PT booking counter payment received"),
      fetchHistory: (memberId) => getMemberPaymentsApi(token, memberId),
      confirm: (message) => window.confirm(message), onQueue: setQueue, onBusy: setBusy, onOutcome: setOutcome,
      initialUncertain: initial, onUncertain: setUncertain, saveUncertain: (rows) => sessionStorage.setItem(storageKey, JSON.stringify(rows)),
    });
    controller.current = workflow;
    let active = true;
    const immediate = window.setTimeout(() => setNow(Date.now()), 0);
    Promise.resolve().then(() => { if (active) { setUncertain(initial); void workflow.load(0); } });
    const refresh = () => void workflow.refresh();
    const poll = window.setInterval(refresh, 30000), clock = window.setInterval(() => setNow(Date.now()), 1000);
    window.addEventListener("focus", refresh);
    return () => { active = false; workflow.dispose(); controller.current = null; window.clearTimeout(immediate); window.clearInterval(poll); window.clearInterval(clock); window.removeEventListener("focus", refresh); };
  }, [token, role, storageKey]);

  if (!token) return <Navigate to="/login" replace />;
  if (!canViewPtPayments(role)) return <Navigate to="/" replace />;
  return <div className="layout"><Sidebar role={role} activePage="pt-payments" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main pt-payment-queue">
      <header><div><p>SPORTCENTER / COUNTER</p><h1>PT Booking counter payments</h1><p>Only confirm after receiving the full cash amount. The server rechecks the booking hold.</p></div>
        <button type="button" disabled={busy || queue.loading} onClick={() => void controller.current?.refresh()}>Refresh</button></header>
      <p>Queue refreshes every 30 seconds. The displayed deadline is advisory; the server remains authoritative.</p>
      {outcome && <p className={`pt-queue-outcome ${outcome.kind}`} role={outcome.kind === "warning" ? "alert" : "status"}>{outcome.text}</p>}
      {uncertain.length > 0 && <section className="pt-queue-review"><h2>Settlement needs reconciliation</h2><p>Do not submit confirmation again for these payments until the status is checked.</p>
        {uncertain.map((row) => <div key={row.paymentId}><span>Payment #{row.paymentId} · Booking #{row.bookingId} · Member #{row.memberId}</span>
          <button type="button" disabled={busy} onClick={() => void controller.current?.check(row)}>Check payment history</button></div>)}</section>}
      {queue.loading ? <p role="status">Loading PT counter queue…</p> : queue.error ? <div role="alert"><p>{queue.error}</p><button type="button" disabled={busy} onClick={() => void controller.current?.refresh()}>Try again</button></div>
        : queue.data && <>
          {queue.data.content.length === 0 ? <p role="status">No pending PT counter payments.</p> : <div className="pt-queue-table"><table><caption>PT Booking cash payments awaiting confirmation</caption>
            <thead><tr><th>Member</th><th>Sport / Coach</th><th>Appointment</th><th>Amount</th><th>Deadline</th><th>Status</th><th>Action</th></tr></thead>
            <tbody>{queue.data.content.map((row) => {
              const expired = ptPaymentExpired({ paymentExpiresAt: row.paymentDeadline }, now), blocked = uncertain.some((item) => item.paymentId === row.paymentId);
              return <tr key={row.paymentId}>
                <td>{row.memberName}<small>Member #{row.memberId}</small></td>
                <td>{row.sportName}<small>Coach: {row.coachName}</small></td>
                <td>{formatTime(row.startTime)}<small>Ends: {formatTime(row.endTime)}</small></td>
                <td>{ptPaymentMoney(row.amount)}</td>
                <td>{formatTime(row.paymentDeadline)}<small>{expired ? "Deadline passed; reconcile" : `${Math.max(0, Math.ceil(((vietnamDate(row.paymentDeadline)?.getTime() || 0) - now) / 60000))} min remaining (approx.)`}</small></td>
                <td>{row.paymentStatus}<small>Booking: {row.bookingStatus}</small></td>
                <td><button type="button" disabled={busy || expired || blocked || row.paymentStatus !== "PENDING" || row.bookingStatus !== "PENDING_PAYMENT"}
                  onClick={() => void controller.current?.receive(row)}>Confirm cash received · #{row.paymentId}</button></td>
              </tr>;
            })}</tbody>
          </table></div>}
          <nav className="pt-queue-pagination" aria-label="PT payment pages"><button type="button" disabled={busy || queue.page === 0} onClick={() => void controller.current?.load(queue.page - 1)}>Previous</button>
            <span>Page {queue.page + 1} / {Math.max(1, queue.data.totalPages)} · {queue.data.totalElements} payments</span>
            <button type="button" disabled={busy || queue.data.last} onClick={() => void controller.current?.load(queue.page + 1)}>Next</button></nav>
        </>}
    </main>
  </div>;
}
