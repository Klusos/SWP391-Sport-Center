import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import { getAttendanceHistoryApi, getAttendanceRosterApi, getStaffAttendanceSessionsApi, markAttendanceApi } from "../api";
import "./CoachAttendance.css";
import "./StaffAttendance.css";

const STATUS_LABELS = { NOT_MARKED: "Chưa điểm danh", PRESENT: "Có mặt", ABSENT: "Vắng mặt", EXCUSED: "Có phép" };
const SESSION_LABELS = { SCHEDULED: "Đã lên lịch", COMPLETED: "Đã hoàn thành", CANCELLED: "Đã hủy" };
const labelStatus = (value) => STATUS_LABELS[value] || value || "Chưa xác định";
const dateValue = (value) => {
  if (typeof value !== "string" || !value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};
const dateTime = (value) => dateValue(value)?.toLocaleString("vi-VN") || "Chưa có thời gian";
const time = (value) => dateValue(value)?.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) || "—";
const requestError = (error, fallback) => {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Bạn không có quyền thực hiện thao tác điểm danh này.";
  if (error?.status === 404) return "Buổi học hoặc dữ liệu điểm danh không còn tồn tại.";
  if (error?.status === 409) return error.message || "Dữ liệu đã thay đổi. Vui lòng tải lại danh sách.";
  return error?.message || fallback;
};
const keyFor = (sessionId, memberId) => `${sessionId}:${memberId}`;

export default function StaffAttendance({ token, role, onLogout, onNavigate }) {
  const [sessions, setSessions] = useState([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [sessionsError, setSessionsError] = useState("");
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [roster, setRoster] = useState(null);
  const [rosterLoading, setRosterLoading] = useState(false);
  const [rosterError, setRosterError] = useState("");
  const [choices, setChoices] = useState({});
  const [notes, setNotes] = useState({});
  const [reasons, setReasons] = useState({});
  const [rowErrors, setRowErrors] = useState({});
  const [savingKey, setSavingKey] = useState("");
  const [notice, setNotice] = useState("");
  const [history, setHistory] = useState([]);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState("");

  const selectedSession = useMemo(() => sessions.find((item) => String(item?.classSessionId) === String(selectedId)) || null, [sessions, selectedId]);
  const members = Array.isArray(roster?.members) ? roster.members : [];
  const cancelled = String(roster?.sessionStatus || selectedSession?.sessionStatus || "").toUpperCase() === "CANCELLED";
  const visibleSessions = useMemo(() => {
    const term = query.trim().toLocaleLowerCase("vi");
    return sessions.filter((item) => {
      const haystack = [item?.sportName, item?.sportId, item?.courseName, item?.courseOfferingName, item?.courseClassName, item?.courseClassId, item?.classSessionId]
        .filter((value) => value != null).join(" ").toLocaleLowerCase("vi");
      return (!term || haystack.includes(term)) && (!statusFilter || String(item?.sessionStatus || "").toUpperCase() === statusFilter);
    });
  }, [sessions, query, statusFilter]);

  const loadSessions = useCallback(async () => {
    setSessionsLoading(true); setSessionsError("");
    try {
      const result = await getStaffAttendanceSessionsApi(token);
      setSessions(Array.isArray(result) ? result : []);
    } catch (error) { setSessions([]); setSessionsError(requestError(error, "Không tải được danh sách buổi học.")); }
    finally { setSessionsLoading(false); }
  }, [token]);
  useEffect(() => { void loadSessions(); }, [loadSessions]);

  const loadRoster = useCallback(async (sessionId) => {
    setRosterLoading(true); setRosterError(""); setRoster(null);
    try {
      const result = await getAttendanceRosterApi(token, sessionId);
      setRoster(result && typeof result === "object" ? result : null);
    } catch (error) { setRosterError(requestError(error, "Không tải được danh sách điểm danh.")); }
    finally { setRosterLoading(false); }
  }, [token]);
  useEffect(() => { if (selectedId) void loadRoster(selectedId); else setRoster(null); }, [selectedId, loadRoster]);

  function selectSession(id) {
    setSelectedId(String(id)); setNotice(""); setRowErrors({}); setHistory([]); setHistoryOpen(false); setHistoryError("");
  }
  function editRow(setter, rowKey, value) {
    setter((current) => ({ ...current, [rowKey]: value }));
    setRowErrors((current) => ({ ...current, [rowKey]: "" }));
  }
  async function save(member) {
    if (!roster || !selectedId || savingKey || roster.markingAllowed !== true || cancelled) return;
    const memberId = member?.memberId;
    if (memberId == null) return;
    const rowKey = keyFor(selectedId, memberId);
    const current = member?.attendanceStatus || "NOT_MARKED";
    const next = choices[rowKey] ?? current;
    if (!["PRESENT", "ABSENT", "EXCUSED"].includes(next) || next === current) return;
    const correction = current !== "NOT_MARKED";
    const reason = String(reasons[rowKey] || "").trim();
    if (correction && !reason) { setRowErrors((state) => ({ ...state, [rowKey]: "Vui lòng nhập lý do điều chỉnh." })); return; }
    if (correction && !window.confirm(`Xác nhận đổi trạng thái của ${member?.memberName || `Member #${memberId}`} từ “${labelStatus(current)}” sang “${labelStatus(next)}”?`)) return;
    setSavingKey(rowKey); setNotice(""); setRowErrors((state) => ({ ...state, [rowKey]: "" }));
    try {
      await markAttendanceApi(token, selectedId, memberId, {
        status: next,
        note: String(notes[rowKey] ?? member?.note ?? "").trim() || null,
        reason: correction ? reason : null,
      });
      setNotice(`Đã lưu điểm danh cho ${member?.memberName || `Member #${memberId}`} trên máy chủ.`);
      await loadRoster(selectedId);
    } catch (error) { setRowErrors((state) => ({ ...state, [rowKey]: requestError(error, "Could not save attendance.") })); }
    finally { setSavingKey(""); }
  }
  async function openHistory(attendanceId) {
    if (attendanceId == null || historyLoading) return;
    setHistoryLoading(true); setHistoryError(""); setHistory([]); setHistoryOpen(true);
    try {
      const result = await getAttendanceHistoryApi(token, attendanceId);
      setHistory(Array.isArray(result) ? result : []);
    } catch (error) { setHistoryError(requestError(error, "Không tải được lịch sử điểm danh.")); }
    finally { setHistoryLoading(false); }
  }

  return <div className="layout">
    <Sidebar role={role} activePage="attendance" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main coach-attendance-main">
      <div className="coach-attendance-page">
        <header className="coach-attendance-header">
          <div><p className="pt-eyebrow">SPORTCENTER / {role === "ADMIN" ? "ADMIN" : "RECEPTIONIST"}</p><h1>Quản lý điểm danh lớp học</h1><p>Tra cứu buổi học, cập nhật điểm danh và xem lịch sử điều chỉnh.</p></div>
          <button type="button" className="coach-attendance-refresh" onClick={() => void loadSessions()} disabled={sessionsLoading}>{sessionsLoading ? "Đang tải…" : "Làm mới"}</button>
        </header>
        {sessionsError && <p className="coach-attendance-alert is-error" role="alert">{sessionsError}</p>}
        {notice && <p className="coach-attendance-alert is-success" role="status">{notice}</p>}
        <section className="coach-attendance-panel" aria-labelledby="staff-attendance-sessions-title">
          <div className="coach-attendance-section-heading"><div><h2 id="staff-attendance-sessions-title">Danh sách buổi học</h2><p>Hiển thị dữ liệu từ các lớp học hiện có.</p></div><span>{visibleSessions.length}/{sessions.length} buổi</span></div>
          <div className="staff-attendance-filters">
            <label>Tìm theo môn, khóa, lớp hoặc mã buổi<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nhập từ khóa…" /></label>
            <label>Trạng thái buổi học<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="">Tất cả</option><option value="SCHEDULED">Đã lên lịch</option><option value="COMPLETED">Đã hoàn thành</option><option value="CANCELLED">Đã hủy</option></select></label>
          </div>
          {sessionsLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách buổi học…</p>
            : sessionsError ? null
              : sessions.length === 0 ? <p className="coach-attendance-empty">Chưa có buổi học nào.</p>
                : visibleSessions.length === 0 ? <p className="coach-attendance-empty">Không tìm thấy buổi học phù hợp.</p>
                  : <div className="coach-attendance-sessions">{visibleSessions.map((session) => {
                    const id = session?.classSessionId; const active = String(id) === String(selectedId);
                    return <button type="button" className={`coach-attendance-session${active ? " is-selected" : ""}`} key={id ?? `${session?.courseClassId}-${session?.startTime}`} onClick={() => id != null && selectSession(id)} disabled={id == null} aria-pressed={active}>
                      <span className="coach-attendance-session-title"><strong>{session?.courseName || session?.courseOfferingName || "Khóa học"}</strong><span>{session?.courseClassName || (session?.courseClassId != null ? `Lớp #${session.courseClassId}` : "Chưa có tên lớp")}</span><span>{session?.sportName || (session?.sportId != null ? `Môn #${session.sportId}` : "Chưa có môn")}</span></span>
                      <span className="coach-attendance-session-time"><strong>{dateTime(session?.startTime)}</strong><span>{time(session?.startTime)} – {time(session?.endTime)}</span></span>
                      <span className={`coach-attendance-badge status-${String(session?.sessionStatus || "unknown").toLowerCase()}`}>{SESSION_LABELS[session?.sessionStatus] || session?.sessionStatus || "Chưa xác định"}</span>
                    </button>;
                  })}</div>}
        </section>

        {selectedId && <section className="coach-attendance-panel" aria-labelledby="staff-attendance-roster-title">
          <div className="coach-attendance-section-heading"><div><h2 id="staff-attendance-roster-title">Danh sách điểm danh</h2><p>{roster?.courseClassName || selectedSession?.courseClassName || "Lớp học"} · Buổi #{roster?.classSessionId ?? selectedId}</p></div><span>{members.length} thành viên</span></div>
          {rosterLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách thành viên…</p>
            : rosterError ? <p className="coach-attendance-alert is-error" role="alert">{rosterError}</p>
              : !roster ? <p className="coach-attendance-empty">Chưa có dữ liệu danh sách điểm danh.</p>
                : <>
                  <div className="coach-attendance-session-meta"><span>{dateTime(roster.startTime)}</span><span>{time(roster.startTime)} – {time(roster.endTime)}</span><span className={`coach-attendance-badge status-${String(roster.sessionStatus || "unknown").toLowerCase()}`}>{SESSION_LABELS[roster.sessionStatus] || roster.sessionStatus || "Chưa xác định"}</span></div>
                  {cancelled && <p className="coach-attendance-alert is-warning">Buổi học đã hủy. Chỉ có thể xem danh sách và lịch sử; không thể đánh dấu hoặc điều chỉnh.</p>}
                  {!cancelled && roster.markingAllowed !== true && <p className="coach-attendance-alert is-warning">Máy chủ chưa cho phép điểm danh buổi học này. Bạn chỉ có thể xem dữ liệu.</p>}
                  {members.length === 0 ? <p className="coach-attendance-empty">Không có thành viên đăng ký CONFIRMED cho buổi học này.</p>
                    : <div className="coach-attendance-table-wrap"><table className="coach-attendance-table"><thead><tr><th>Thành viên</th><th>Trạng thái</th><th>Lần đánh dấu</th><th>Ghi chú</th><th>Thao tác</th></tr></thead>
                      <tbody>{members.map((member) => {
                        const memberId = member?.memberId; const rowKey = keyFor(selectedId, memberId); const current = member?.attendanceStatus || "NOT_MARKED";
                        const next = choices[rowKey] ?? current; const correction = current !== "NOT_MARKED"; const changed = next !== current;
                        const canSave = roster.markingAllowed === true && !cancelled && changed && (!correction || String(reasons[rowKey] || "").trim().length > 0);
                        return <tr key={memberId ?? member?.memberName}>
                          <td><strong>{member?.memberName || "Thành viên"}</strong><small>ID: {memberId ?? "—"}</small></td>
                          <td><span className={`coach-attendance-badge attendance-${String(current).toLowerCase()}`}>{labelStatus(current)}</span><label className="coach-attendance-select-label" htmlFor={`staff-attendance-${memberId}`}>Trạng thái cập nhật</label>
                            <select id={`staff-attendance-${memberId}`} value={next} onChange={(event) => editRow(setChoices, rowKey, event.target.value)} disabled={!roster.markingAllowed || cancelled || Boolean(savingKey)}>
                              {["NOT_MARKED", "PRESENT", "ABSENT", "EXCUSED"].map((status) => <option key={status} value={status} disabled={status === "NOT_MARKED"}>{labelStatus(status)}</option>)}
                            </select>
                            {correction && changed && <label className="coach-attendance-reason"><span>Lý do điều chỉnh <b aria-hidden="true">*</b></span><input maxLength={500} value={reasons[rowKey] ?? ""} onChange={(event) => editRow(setReasons, rowKey, event.target.value)} disabled={Boolean(savingKey)} placeholder="Nhập lý do điều chỉnh" /></label>}
                          </td>
                          <td>{member?.markedAt ? <><span>{dateTime(member.markedAt)}</span><small>Người thực hiện ID: {member?.markedById ?? "—"}</small></> : <span>Chưa đánh dấu</span>}</td>
                          <td><textarea aria-label={`Ghi chú điểm danh thành viên ${memberId ?? ""}`} maxLength={500} value={notes[rowKey] ?? member?.note ?? ""} onChange={(event) => editRow(setNotes, rowKey, event.target.value)} disabled={!roster.markingAllowed || cancelled || Boolean(savingKey)} placeholder="Ghi chú không bắt buộc" /></td>
                          <td><button type="button" className="coach-attendance-save" disabled={!canSave || Boolean(savingKey)} onClick={() => void save(member)}>{savingKey === rowKey ? "Đang lưu…" : correction ? "Lưu điều chỉnh" : "Lưu điểm danh"}</button>
                            {member?.attendanceId != null && <button type="button" className="coach-attendance-history-button" disabled={historyLoading} onClick={() => void openHistory(member.attendanceId)}>Lịch sử</button>}
                            {rowErrors[rowKey] && <small className="coach-attendance-row-error" role="alert">{rowErrors[rowKey]}</small>}
                          </td>
                        </tr>;
                      })}</tbody>
                    </table></div>}
                  <p className="coach-attendance-footnote">Không tự động ghi vắng mặt. Thời gian lịch học được hiển thị theo giờ địa phương của thiết bị; máy chủ quyết định quyền điểm danh.</p>
                </>}
        </section>}

        {historyOpen && <section className="coach-attendance-panel coach-attendance-history" aria-labelledby="staff-attendance-history-title"><div className="coach-attendance-section-heading"><h2 id="staff-attendance-history-title">Lịch sử điểm danh</h2><button type="button" className="coach-attendance-history-button" onClick={() => setHistoryOpen(false)}>Đóng</button></div>
          {historyLoading ? <p role="status">Đang tải lịch sử…</p> : historyError ? <p className="coach-attendance-alert is-error" role="alert">{historyError}</p> : history.length === 0 ? <p className="coach-attendance-empty">Chưa có sự kiện lịch sử.</p> : <ol className="coach-attendance-history-list">{history.map((event) => <li key={event?.id ?? `${event?.changedAt}-${event?.newStatus}`}><strong>{event?.oldStatus ? `${labelStatus(event.oldStatus)} → ${labelStatus(event.newStatus)}` : `Đánh dấu ban đầu: ${labelStatus(event?.newStatus)}`}</strong><span>{dateTime(event?.changedAt)} · Người thực hiện ID: {event?.changedById ?? "—"}</span>{event?.reason && <p>Lý do: {event.reason}</p>}</li>)}</ol>}
        </section>}
      </div>
    </main>
  </div>;
}