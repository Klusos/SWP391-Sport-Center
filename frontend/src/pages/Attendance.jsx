import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  getStaffAttendanceSessionsApi,
  getAttendanceRosterApi,
  markAttendanceApi,
} from "../api";

// Ghi chú: mình chưa biết chắc tên field thật trong response của
// getAttendanceRosterApi / payload mà markAttendanceApi cần, nên đoán theo
// quy ước đã thấy ở các API khác (status viết hoa, kiểu PENDING/ACTIVE...).
// Nếu khi chạy không đúng, mở DevTools > Network xem response thật rồi gửi
// mình chỉnh lại field cho khớp.

function fmtTime(value) {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString("vi-VN", {
      day: "2-digit",
      month: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return value;
  }
}

export default function Attendance({ token, role, onNavigate, onLogout }) {
  const [sessions, setSessions] = useState([]);
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [sessionsError, setSessionsError] = useState("");

  const [selectedId, setSelectedId] = useState("");
  const [roster, setRoster] = useState([]);
  const [loadingRoster, setLoadingRoster] = useState(false);
  const [rosterError, setRosterError] = useState("");

  const [savingMemberId, setSavingMemberId] = useState(null);
  const [actionError, setActionError] = useState("");

  useEffect(() => {
    (async () => {
      setLoadingSessions(true);
      setSessionsError("");
      try {
        const data = await getStaffAttendanceSessionsApi(token);
        setSessions(Array.isArray(data) ? data : data?.content || []);
      } catch (e) {
        setSessionsError(e.message || "Không tải được danh sách buổi học.");
      } finally {
        setLoadingSessions(false);
      }
    })();
  }, [token]);

  async function loadRoster(sessionId) {
    setSelectedId(sessionId);
    setRoster([]);
    setRosterError("");
    setActionError("");
    if (!sessionId) return;
    setLoadingRoster(true);
    try {
      const data = await getAttendanceRosterApi(token, sessionId);
      setRoster(Array.isArray(data) ? data : data?.content || []);
    } catch (e) {
      setRosterError(e.message || "Không tải được danh sách điểm danh.");
    } finally {
      setLoadingRoster(false);
    }
  }

  async function handleMark(member, status) {
    const memberId = member.memberId ?? member.id;
    setSavingMemberId(memberId);
    setActionError("");
    try {
      await markAttendanceApi(token, selectedId, memberId, { status });
      setRoster((prev) =>
        prev.map((m) =>
          (m.memberId ?? m.id) === memberId ? { ...m, status } : m
        )
      );
    } catch (e) {
      setActionError(e.message || "Cập nhật điểm danh thất bại.");
    } finally {
      setSavingMemberId(null);
    }
  }

  const selectedSession = sessions.find((s) => String(s.id) === String(selectedId));

  return (
    <div className="layout">
      <Sidebar role={role} activePage="attendance" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main">
        <h1>Điểm danh</h1>
        <p className="subtitle">Chọn buổi học để xem và cập nhật điểm danh.</p>

        <div className="queue-toolbar">
          <div className="field queue-filter">
            <label htmlFor="sessionSelect">Buổi học</label>
            <select
              id="sessionSelect"
              value={selectedId}
              onChange={(e) => loadRoster(e.target.value)}
            >
              <option value="">— Chọn buổi học —</option>
              {sessions.map((s) => (
                <option value={s.id} key={s.id}>
                  {(s.className || s.classId) + " — " + fmtTime(s.startTime)}
                </option>
              ))}
            </select>
          </div>
        </div>

        {loadingSessions && <div className="queue-state">Đang tải danh sách buổi học...</div>}
        {sessionsError && <div className="banner error">{sessionsError}</div>}

        {!selectedId && !loadingSessions && !sessionsError && (
          <div className="queue-state">Chọn một buổi học ở trên để xem danh sách điểm danh.</div>
        )}

        {selectedId && (
          <>
            {selectedSession && (
              <p className="subtitle">
                {selectedSession.className} · {fmtTime(selectedSession.startTime)} —{" "}
                {fmtTime(selectedSession.endTime)}
                {selectedSession.facilityName ? " · " + selectedSession.facilityName : ""}
              </p>
            )}

            {loadingRoster && <div className="queue-state">Đang tải danh sách...</div>}
            {rosterError && <div className="banner error">{rosterError}</div>}
            {actionError && <div className="banner error">{actionError}</div>}

            {!loadingRoster && !rosterError && (
              <div className="queue-table-wrap">
                <table className="queue-table">
                  <thead>
                    <tr>
                      <th>Thành viên</th>
                      <th>Trạng thái</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {roster.length === 0 && (
                      <tr>
                        <td colSpan={3}>Chưa có thành viên nào trong buổi học này.</td>
                      </tr>
                    )}
                    {roster.map((m) => {
                      const memberId = m.memberId ?? m.id;
                      const status = (m.status || "PENDING").toUpperCase();
                      return (
                        <tr key={memberId}>
                          <td>{m.memberName || m.name || "—"}</td>
                          <td>
                            <span className={"status-pill status-" + status.toLowerCase()}>
                              {status}
                            </span>
                          </td>
                          <td>
                            <div className="queue-actions">
                              <button
                                type="button"
                                disabled={savingMemberId === memberId}
                                onClick={() => handleMark(m, "PRESENT")}
                              >
                                Có mặt
                              </button>
                              <button
                                type="button"
                                disabled={savingMemberId === memberId}
                                onClick={() => handleMark(m, "ABSENT")}
                              >
                                Vắng
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}