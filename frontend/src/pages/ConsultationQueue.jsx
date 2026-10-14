import { useCallback, useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import { getConsultationRequestsApi, takeConsultationRequestApi } from "../api";

const PAGE_SIZE = 20;
const STATUSES = ["", "PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"];

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export default function ConsultationQueue({ token, role, onLogout, onNavigate }) {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [takingId, setTakingId] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const allowedRole = role === "RECEPTIONIST" || role === "ADMIN";

  const loadQueue = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getConsultationRequestsApi(token, { status, page, size: PAGE_SIZE });
      setResult(data);
    } catch (requestError) {
      setResult(null);
      if (requestError.status === 401) {
        setError("Your session is missing or expired. Sign in again to view the queue.");
      } else if (requestError.status === 403) {
        setError("Your account is not authorized to view the consultation queue.");
      } else {
        setError(requestError.message || "Could not load consultation requests.");
      }
    } finally {
      setLoading(false);
    }
  }, [token, status, page]);

  useEffect(() => { loadQueue(); }, [loadQueue]);

  async function handleTake(requestId) {
    setTakingId(requestId);
    setError("");
    setNotice("");
    try {
      await takeConsultationRequestApi(token, requestId);
      setNotice("Request #" + requestId + " was taken.");
      await loadQueue();
    } catch (requestError) {
      if (requestError.status === 409) {
        await loadQueue();
        setError("This request was already taken or changed by another staff member. The queue has been refreshed.");
      } else if (requestError.status === 401) {
        setError("Your session is missing or expired. Sign in again to continue.");
      } else if (requestError.status === 403) {
        setError("Your account is not authorized to take this request.");
      } else {
        setError(requestError.message || "Could not take this request.");
      }
    } finally {
      setTakingId(null);
    }
  }

  const requests = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;

  return (
    <div className="layout">
      <Sidebar role={role} activePage="consultations" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main">
        <h1>Consultation queue</h1>
        <p className="subtitle">Review and take customer consultation requests.</p>
        {!allowedRole && <div className="banner error" role="alert">Only Receptionist and Admin accounts can access this queue.</div>}
        {notice && <div className="banner success" role="status">{notice}</div>}
        {error && <div className="banner error" role="alert">{error}</div>}

        <div className="queue-toolbar">
          <div className="field queue-filter">
            <label htmlFor="consultationStatus">Status</label>
            <select id="consultationStatus" value={status}
              onChange={(event) => { setStatus(event.target.value); setPage(0); setNotice(""); }}
              disabled={!allowedRole}>
              {STATUSES.map((value) => <option key={value || "ALL"} value={value}>{value || "All statuses"}</option>)}
            </select>
          </div>
          <button className="queue-refresh" type="button" onClick={loadQueue} disabled={loading || !allowedRole}>
            {loading ? "Loading…" : "Refresh"}
          </button>
        </div>

        {loading ? (
          <div className="queue-state" role="status">Loading consultation requests…</div>
        ) : error && !result ? (
          <div className="queue-state">The queue is unavailable. Check access or try refreshing.</div>
        ) : requests.length === 0 ? (
          <div className="queue-state">No consultation requests found.</div>
        ) : (
          <>
            <div className="queue-table-wrap">
              <table className="queue-table">
                <thead><tr>
                  <th>Request ID</th><th>Customer</th><th>Phone</th><th>Sport</th>
                  <th>Status</th><th>Created</th><th>Handler</th><th>Action</th>
                </tr></thead>
                <tbody>
                  {requests.map((request) => (
                    <tr key={request.id}>
                      <td>#{request.id}</td>
                      <td>{request.name}</td>
                      <td>{request.phone}</td>
                      <td>{request.sportName || "—"}</td>
                      <td><span className={"status-pill status-" + String(request.status).toLowerCase()}>{request.status}</span></td>
                      <td>{formatDate(request.createdAt)}</td>
                      <td>{request.handledBy == null ? "—" : "#" + request.handledBy}</td>
                      <td>{request.status === "PENDING" && allowedRole ? (
                        <button className="queue-take" type="button" onClick={() => handleTake(request.id)}
                          disabled={takingId !== null}>
                          {takingId === request.id ? "Taking…" : "Take"}
                        </button>
                      ) : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="queue-pagination">
              <span>Page {page + 1} of {Math.max(totalPages, 1)} · {result?.totalElements ?? requests.length} requests</span>
              <div>
                <button type="button" onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={page <= 0 || loading}>Previous</button>
                <button type="button" onClick={() => setPage((current) => current + 1)} disabled={loading || totalPages === 0 || page + 1 >= totalPages}>Next</button>
              </div>
            </div>
          </>
        )}
      </main>
    </div>
  );
}

