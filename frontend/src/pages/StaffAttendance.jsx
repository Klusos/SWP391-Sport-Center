import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import { getAttendanceHistoryApi, getAttendanceRosterApi, getStaffAttendanceSessionsApi, markAttendanceApi } from "../api";
import "./CoachAttendance.css";
import "./StaffAttendance.css";

const STATUS_LABELS = { NOT_MARKED: "Chưa điểm danh", PRESENT: "Có mặt", ABSENT: "Vắng mặt", EXCUSED: "Có phép" };
const SESSION_LABELS = { SCHEDULED: "Đã lên lịch", COMPLETED: "Đã hoàn thành", CANCELLED: "Đã hủy" };
const PAGE_SIZE = 5;
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
const sportKey = (item) => (item?.sportName ? `n:${item.sportName}` : item?.sportId != null ? `i:${item.sportId}` : "");
const sportLabel = (item) => item?.sportName || `Môn #${item?.sportId}`;
const keyFor = (sessionId, memberId) => `${sessionId}:${memberId}`;
const initials = (name) => {
  const parts = String(name || "?").trim().split(/\s+/);
  return ((parts[0]?.[0] || "") + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase() || "?";
};

export default function StaffAttendance({ token, role, onLogout, onNavigate }) {
  const [sessions, setSessions] = useState([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [sessionsError, setSessionsError] = useState("");
  const [query, setQuery] = useState("");
  
  const [selectedId, setSelectedId] = useState("");
  const [roster, setRoster] = useState(null);
  const [rosterLoading, setRosterLoading] = useState(false);
  const [rosterError, setRosterError] = useState("");
  const [choices, setChoices] = useState({});
  const [notes, setNotes] = useState({});
  const [reasons, setReasons] = useState({});
  const [rowErrors, setRowErrors] = useState({});
  const [savingKey, setSavingKey] = useState("");
  const [savingAll, setSavingAll] = useState(false);
  const [notice, setNotice] = useState("");
  const [history, setHistory] = useState([]);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState("");
  const [memberFilter, setMemberFilter] = useState("");
  const [page, setPage] = useState(1);
  const [sportFilter, setSportFilter] = useState("");
  

  const selectedSession = useMemo(() => sessions.find((item) => String(item?.classSessionId) === String(selectedId)) || null, [sessions, selectedId]);
  const members = Array.isArray(roster?.members) ? roster.members : [];
  const cancelled = String(roster?.sessionStatus || selectedSession?.sessionStatus || "").toUpperCase() === "CANCELLED";
  const sessionCounts = useMemo(() => sessions.reduce((acc, item) => {
    const key = String(item?.sessionStatus || "").toUpperCase();
    acc[key] = (acc[key] || 0) + 1;
    return acc;
  }, {}), [sessions]);
  const sportOptions = useMemo(() => {
    const map = new Map();
    sessions.forEach((item) => { const key = sportKey(item); if (key) map.set(key, sportLabel(item)); });
    return [...map].map(([key, name]) => ({ key, name })).sort((a, b) => a.name.localeCompare(b.name, "vi"));
  }, [sessions]);
  const visibleSessions = useMemo(() => {
    const term = query.trim().toLocaleLowerCase("vi");
    return sessions.filter((item) => {
      const haystack = [item?.sportName, item?.sportId, item?.courseName, item?.courseOfferingName, item?.courseClassName, item?.courseClassId, item?.classSessionId]
        .filter((value) => value != null).join(" ").toLocaleLowerCase("vi");
      return (!term || haystack.includes(term)) && (!sportFilter || sportKey(item) === sportFilter);
    });
  }, [sessions, query, sportFilter]);

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
    setMemberFilter(""); setPage(1);
  }
  function editRow(setter, rowKey, value) {
    setter((current) => ({ ...current, [rowKey]: value }));
    setRowErrors((current) => ({ ...current, [rowKey]: "" }));
  }
  const rowState = (member) => {
    const rowKey = keyFor(selectedId, member?.memberId);
    const current = member?.attendanceStatus || "NOT_MARKED";
    const next = choices[rowKey] ?? current;
    return { rowKey, current, next, correction: current !== "NOT_MARKED", changed: next !== current };
  };
  const canMark = Boolean(roster) && roster.markingAllowed === true && !cancelled;
  const busy = Boolean(savingKey) || savingAll;
  const stats = members.reduce((acc, member) => {
    const { next } = rowState(member);
    if (next === "PRESENT") acc.present += 1;
    else if (next === "ABSENT") acc.absent += 1;
    else if (next === "EXCUSED") acc.excused += 1;
    else acc.notMarked += 1;
    return acc;
  }, { present: 0, absent: 0, excused: 0, notMarked: 0 });
  const filteredMembers = memberFilter ? members.filter((member) => rowState(member).next === memberFilter) : members;
  const pageCount = Math.max(1, Math.ceil(filteredMembers.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount);
  const pagedMembers = filteredMembers.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
  const pendingMembers = members.filter((member) => {
    const state = rowState(member);
    return state.changed && ["PRESENT", "ABSENT", "EXCUSED"].includes(state.next);
  });

  async function submitMember(member) {
    const { rowKey, next, correction } = rowState(member);
    await markAttendanceApi(token, selectedId, member.memberId, {
      status: next,
      note: String(notes[rowKey] ?? member?.note ?? "").trim() || null,
      reason: correction ? String(reasons[rowKey] || "").trim() : null,
    });
  }
  async function save(member) {
    if (!canMark || busy) return;
    const memberId = member?.memberId;
    if (memberId == null) return;
    const { rowKey, current, next, correction, changed } = rowState(member);
    if (!["PRESENT", "ABSENT", "EXCUSED"].includes(next) || !changed) return;
    const reason = String(reasons[rowKey] || "").trim();
    if (correction && !reason) { setRowErrors((state) => ({ ...state, [rowKey]: "Vui lòng nhập lý do điều chỉnh." })); return; }
    if (correction && !window.confirm(`Xác nhận đổi trạng thái của ${member?.memberName || `Member #${memberId}`} từ “${labelStatus(current)}” sang “${labelStatus(next)}”?`)) return;
    setSavingKey(rowKey); setNotice(""); setRowErrors((state) => ({ ...state, [rowKey]: "" }));
    try {
      await submitMember(member);
      setNotice(`Đã lưu điểm danh cho ${member?.memberName || `Member #${memberId}`} trên máy chủ.`);
      await loadRoster(selectedId);
    } catch (error) { setRowErrors((state) => ({ ...state, [rowKey]: requestError(error, "Không lưu được điểm danh.") })); }
    finally { setSavingKey(""); }
  }
  async function saveAll() {
    if (!canMark || busy || pendingMembers.length === 0) return;
    const missing = pendingMembers.filter((member) => { const s = rowState(member); return s.correction && !String(reasons[s.rowKey] || "").trim(); });
    if (missing.length > 0) {
      setRowErrors((state) => {
        const nextState = { ...state };
        missing.forEach((member) => { nextState[rowState(member).rowKey] = "Vui lòng nhập lý do điều chỉnh."; });
        return nextState;
      });
      return;
    }
    const corrections = pendingMembers.filter((member) => rowState(member).correction).length;
    if (corrections > 0 && !window.confirm(`Xác nhận điều chỉnh điểm danh của ${corrections} thành viên đã được đánh dấu trước đó?`)) return;
    setSavingAll(true); setNotice("");
    let saved = 0;
    for (const member of pendingMembers) {
      try { await submitMember(member); saved += 1; }
      catch (error) { setRowErrors((state) => ({ ...state, [rowState(member).rowKey]: requestError(error, "Không lưu được điểm danh.") })); }
    }
    setSavingAll(false);
    if (saved > 0) setNotice(`Đã lưu điểm danh cho ${saved}/${pendingMembers.length} thành viên trên máy chủ.`);
    await loadRoster(selectedId);
  }
  function exportCsv() {
    if (!roster || members.length === 0) return;
    const escape = (value) => `"${String(value ?? "").replace(/"/g, '""')}"`;
    const rows = [["Mã thành viên", "Họ tên", "Trạng thái", "Thời điểm đánh dấu", "Ghi chú"]].concat(members.map((member) => {
      const { rowKey, next } = rowState(member);
      return [member?.memberId, member?.memberName, labelStatus(next), member?.markedAt ? dateTime(member.markedAt) : "", notes[rowKey] ?? member?.note ?? ""];
    }));
    const blob = new Blob(["\uFEFF" + rows.map((row) => row.map(escape).join(",")).join("\r\n")], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url; link.download = `diem-danh-buoi-${selectedId}.csv`; link.click();
    URL.revokeObjectURL(url);
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

  const sessionStatusKey = String(roster?.sessionStatus || selectedSession?.sessionStatus || "unknown").toLowerCase();
  const sessionStatusText = SESSION_LABELS[roster?.sessionStatus || selectedSession?.sessionStatus] || roster?.sessionStatus || "Chưa xác định";

  return <div className="layout">
    <Sidebar role={role} activePage="attendance" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main coach-attendance-main">
      <div className="coach-attendance-page">
        <header className="coach-attendance-header">
          <div>
            <p className="pt-eyebrow">SPORTCENTER / {role === "ADMIN" ? "ADMIN" : "RECEPTIONIST"}</p>
            <h1>Quản lý điểm danh lớp học</h1>
            <p>Tra cứu buổi học toàn trung tâm, cập nhật điểm danh và giám sát lịch sử điều chỉnh.</p>
          </div>
          <div className="coach-attendance-header-actions">
            <span className="coach-attendance-badge">{sessions.length} Buổi học</span>
            <span className="coach-attendance-badge status-scheduled">{sessionCounts.SCHEDULED || 0} Đã lên lịch</span>
            <span className="coach-attendance-badge status-completed">{sessionCounts.COMPLETED || 0} Hoàn thành</span>
            <span className="coach-attendance-badge status-cancelled">{sessionCounts.CANCELLED || 0} Đã hủy</span>
            <button type="button" className="coach-attendance-refresh" onClick={() => void loadSessions()} disabled={sessionsLoading}>{sessionsLoading ? "Đang tải…" : "Làm mới"}</button>
          </div>
        </header>
        {sessionsError && <p className="coach-attendance-alert is-error" role="alert">{sessionsError}</p>}
        {notice && <p className="coach-attendance-alert is-success" role="status">{notice}</p>}

        <div className="coach-attendance-grid">
          <aside className="coach-attendance-panel coach-attendance-sidepanel" aria-labelledby="staff-attendance-sessions-title">
            <div className="coach-attendance-section-heading">
              <h2 id="staff-attendance-sessions-title">Danh sách buổi học</h2>
              <span className="coach-attendance-count">{visibleSessions.length}/{sessions.length} buổi</span>
            </div>
            <div className="staff-attendance-selects">
              <label>Môn thể thao
                <select value={sportFilter} onChange={(event) => { setSportFilter(event.target.value); }}>
                  <option value="">Tất cả môn ({sportOptions.length})</option>
                  {sportOptions.map((option) => <option key={option.key} value={option.key}>{option.name}</option>)}
                </select>
              </label>

            </div>
            <input type="search" className="coach-attendance-search staff-attendance-search" aria-label="Tìm buổi học" value={query}
              onChange={(event) => setQuery(event.target.value)} placeholder="Tìm theo môn, khóa, lớp hoặc mã buổi…" />

            {sessionsLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách buổi học…</p>
              : sessionsError ? null
                : sessions.length === 0 ? <p className="coach-attendance-empty">Chưa có buổi học nào.</p>
                  : visibleSessions.length === 0 ? <p className="coach-attendance-empty">Không tìm thấy buổi học phù hợp.</p>
                    : <div className="coach-attendance-sessions">{visibleSessions.map((session) => {
                      const id = session?.classSessionId; const active = String(id) === String(selectedId);
                      return <button type="button" className={`coach-attendance-session${active ? " is-selected" : ""}`} key={id ?? `${session?.courseClassId}-${session?.startTime}`}
                        onClick={() => id != null && selectSession(id)} disabled={id == null} aria-pressed={active}>
                        <span className="coach-attendance-session-top">
                          <span className="coach-attendance-chip">{session?.sportName || (session?.sportId != null ? `Môn #${session.sportId}` : "Chưa có môn")}</span>
                          <span className={`coach-attendance-badge status-${String(session?.sessionStatus || "unknown").toLowerCase()}`}>{SESSION_LABELS[session?.sessionStatus] || session?.sessionStatus || "Chưa xác định"}</span>
                        </span>
                        <strong>{session?.courseName || session?.courseOfferingName || "Khóa học"}</strong>
                        <span className="coach-attendance-session-sub">{session?.courseClassName || (session?.courseClassId != null ? `Lớp #${session.courseClassId}` : "Chưa có tên lớp")} · Buổi #{id ?? "—"}</span>
                        <span className="coach-attendance-session-foot"><span>{dateTime(session?.startTime)} – {time(session?.endTime)}</span></span>
                      </button>;
                    })}</div>}
          </aside>

          <div className="coach-attendance-content">
            {!selectedId ? <section className="coach-attendance-panel coach-attendance-placeholder">
              <h2>Danh sách điểm danh</h2>
              <p>Chọn một buổi học ở cột bên trái để xem và điểm danh thành viên.</p>
            </section> : <>
              <section className="coach-attendance-panel" aria-labelledby="staff-attendance-roster-title">
                <div className="coach-attendance-roster-head">
                  <div>
                    <p className="staff-attendance-chips">
                      <span className="coach-attendance-chip">{roster?.sportName || selectedSession?.sportName || "Môn học"}</span>
                      <span className="coach-attendance-code">Mã buổi: #{roster?.classSessionId ?? selectedId}</span>
                      <span className={`coach-attendance-badge status-${sessionStatusKey}`}>{sessionStatusText}</span>
                    </p>
                    <h2 id="staff-attendance-roster-title">{roster?.courseName || selectedSession?.courseName || "Khóa học"} – {roster?.courseClassName || selectedSession?.courseClassName || "Lớp học"} (Buổi #{roster?.classSessionId ?? selectedId})</h2>
                    <p className="coach-attendance-sub">{dateTime(roster?.startTime || selectedSession?.startTime)} – {time(roster?.endTime || selectedSession?.endTime)}</p>
                  </div>
                  <div className="staff-attendance-head-actions">
                    <button type="button" className="coach-attendance-history-button" onClick={exportCsv} disabled={members.length === 0}>Xuất file</button>
                    <button type="button" className="coach-attendance-save" onClick={() => void saveAll()} disabled={!canMark || busy || pendingMembers.length === 0}>
                      {savingAll ? "Đang lưu…" : "Lưu toàn bộ" + (pendingMembers.length ? ` (${pendingMembers.length})` : "")}
                    </button>
                  </div>
                </div>
                {rosterLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách thành viên…</p>
                  : rosterError ? <p className="coach-attendance-alert is-error" role="alert">{rosterError}</p>
                    : !roster ? <p className="coach-attendance-empty">Chưa có dữ liệu danh sách điểm danh.</p>
                      : <div className="coach-attendance-stats">
                        <div className="stat stat-total"><span>Tổng sĩ số</span><strong>{members.length}</strong></div>
                        <div className="stat stat-present"><span>Có mặt</span><strong>{stats.present}</strong></div>
                        <div className="stat stat-absent"><span>Vắng mặt</span><strong>{stats.absent}</strong></div>
                        <div className="stat stat-excused"><span>Có phép</span><strong>{stats.excused}</strong></div>
                        <div className="stat stat-pending"><span>Chưa chấm</span><strong>{stats.notMarked}</strong></div>
                      </div>}
              </section>

              {roster && !rosterLoading && !rosterError && <>
                {cancelled ? <p className="coach-attendance-alert is-warning"><b>LƯU Ý QUẢN TRỊ VIÊN</b><br />Buổi học đã hủy. Chỉ có thể xem danh sách và lịch sử; không thể đánh dấu hoặc điều chỉnh.</p>
                  : roster.markingAllowed !== true ? <p className="coach-attendance-alert is-warning"><b>LƯU Ý QUẢN TRỊ VIÊN</b><br />Máy chủ chưa cho phép điểm danh buổi học này. Bạn chỉ có thể xem dữ liệu.</p>
                    : <p className="coach-attendance-alert is-warning"><b>LƯU Ý QUẢN TRỊ VIÊN</b><br />Quản trị viên có toàn quyền cập nhật điểm danh bổ sung hoặc điều chỉnh theo yêu cầu khiếu nại của học viên. Mọi thao tác đều được lưu vào nhật ký hệ thống. Hệ thống không tự động ghi vắng mặt.</p>}

                <section className="coach-attendance-panel coach-attendance-roster" aria-label="Danh sách điểm danh thành viên">
                  <div className="coach-attendance-toolbar staff-attendance-toolbar">
                    <div>
                      <strong>Danh sách điểm danh thành viên</strong>
                      <p className="coach-attendance-sub">Cập nhật trạng thái và ghi chú lý do thay đổi trạng thái</p>
                    </div>
                    <label className="staff-attendance-member-filter">Lọc theo:
                      <select value={memberFilter} onChange={(event) => { setMemberFilter(event.target.value); setPage(1); }}>
                        <option value="">Tất cả thành viên ({members.length})</option>
                        <option value="PRESENT">Có mặt ({stats.present})</option>
                        <option value="ABSENT">Vắng mặt ({stats.absent})</option>
                        <option value="EXCUSED">Có phép ({stats.excused})</option>
                        <option value="NOT_MARKED">Chưa điểm danh ({stats.notMarked})</option>
                      </select>
                    </label>
                  </div>
                  {members.length === 0 ? <p className="coach-attendance-empty">Không có thành viên đăng ký CONFIRMED cho buổi học này.</p> : <>
                    <div className="coach-attendance-table-wrap"><table className="coach-attendance-table">
                      <thead><tr><th>Thành viên</th><th>Trạng thái điểm danh</th><th>Lần cập nhật cuối</th><th>Ghi chú</th><th>Thao tác</th></tr></thead>
                      <tbody>{pagedMembers.length === 0 ? <tr><td colSpan={5}>Không có thành viên phù hợp bộ lọc.</td></tr> : pagedMembers.map((member) => {
                        const memberId = member?.memberId; const { rowKey, next, correction, changed } = rowState(member);
                        const canSave = canMark && changed && (!correction || String(reasons[rowKey] || "").trim().length > 0);
                        return <tr key={memberId ?? member?.memberName}>
                          <td><div className="coach-attendance-member">
                            <span className="coach-attendance-avatar">{initials(member?.memberName)}</span>
                            <span><strong>{member?.memberName || "Thành viên"}</strong><small>#{memberId ?? "—"}</small></span>
                          </div></td>
                          <td>
                            <div className="coach-attendance-segment" role="group" aria-label={`Trạng thái điểm danh ${member?.memberName || memberId}`}>
                              {["PRESENT", "ABSENT", "EXCUSED"].map((status) => <label key={status}
                                className={`seg seg-${status.toLowerCase()}${next === status ? " is-active" : ""}${!canMark || busy ? " is-disabled" : ""}`}>
                                <input type="radio" name={`staff-attendance-${memberId}`} value={status} checked={next === status}
                                  disabled={!roster.markingAllowed || cancelled || busy}
                                  onChange={(event) => editRow(setChoices, rowKey, event.target.value)} />
                                {labelStatus(status)}
                              </label>)}
                            </div>
                            {correction && changed && <label className="coach-attendance-reason"><span>Lý do điều chỉnh <b aria-hidden="true">*</b></span>
                              <input maxLength={500} value={reasons[rowKey] ?? ""} onChange={(event) => editRow(setReasons, rowKey, event.target.value)} disabled={busy} placeholder="Nhập lý do điều chỉnh" /></label>}
                          </td>
                          <td>{member?.markedAt ? <><strong>{time(member.markedAt)}</strong><small>{dateTime(member.markedAt)}</small><small>Người thực hiện ID: {member?.markedById ?? "—"}</small></>
                            : <em className="coach-attendance-muted">Chưa có dữ liệu</em>}</td>
                          <td><input className="coach-attendance-note" aria-label={`Ghi chú điểm danh thành viên ${memberId ?? ""}`} maxLength={500}
                            value={notes[rowKey] ?? member?.note ?? ""} onChange={(event) => editRow(setNotes, rowKey, event.target.value)}
                            disabled={!roster.markingAllowed || cancelled || busy} placeholder="Thêm ghi chú…" /></td>
                          <td>
                            <div className="coach-attendance-actions">
                              <button type="button" className="coach-attendance-save" disabled={!canSave || busy} onClick={() => void save(member)}>{savingKey === rowKey ? "Đang lưu…" : "Lưu"}</button>
                              {member?.attendanceId != null && <button type="button" className="coach-attendance-history-button" title="Lịch sử điểm danh" aria-label="Lịch sử điểm danh" disabled={historyLoading} onClick={() => void openHistory(member.attendanceId)}>⟲</button>}
                            </div>
                            {rowErrors[rowKey] && <small className="coach-attendance-row-error" role="alert">{rowErrors[rowKey]}</small>}
                          </td>
                        </tr>;
                      })}</tbody>
                    </table></div>
                    <div className="coach-attendance-footer">
                      <span>Hiển thị {pagedMembers.length} trên {filteredMembers.length} thành viên</span>
                      <div className="staff-attendance-pager">
                        <button type="button" onClick={() => setPage(currentPage - 1)} disabled={currentPage <= 1}>Trước</button>
                        {Array.from({ length: pageCount }, (_, index) => index + 1).map((number) =>
                          <button key={number} type="button" className={number === currentPage ? "is-active" : ""} aria-current={number === currentPage ? "page" : undefined} onClick={() => setPage(number)}>{number}</button>)}
                        <button type="button" onClick={() => setPage(currentPage + 1)} disabled={currentPage >= pageCount}>Sau</button>
                      </div>
                    </div>
                  </>}
                </section>
              </>}

              {historyOpen && <section className="coach-attendance-panel coach-attendance-history" aria-labelledby="staff-attendance-history-title">
                <div className="coach-attendance-section-heading"><h2 id="staff-attendance-history-title">Lịch sử điểm danh</h2>
                  <button type="button" className="coach-attendance-history-button" onClick={() => setHistoryOpen(false)}>Đóng</button></div>
                {historyLoading ? <p role="status">Đang tải lịch sử…</p> : historyError ? <p className="coach-attendance-alert is-error" role="alert">{historyError}</p>
                  : history.length === 0 ? <p className="coach-attendance-empty">Chưa có sự kiện lịch sử.</p>
                    : <ol className="coach-attendance-history-list">{history.map((event) => <li key={event?.id ?? `${event?.changedAt}-${event?.newStatus}`}>
                      <strong>{event?.oldStatus ? `${labelStatus(event.oldStatus)} → ${labelStatus(event.newStatus)}` : `Đánh dấu ban đầu: ${labelStatus(event?.newStatus)}`}</strong>
                      <span>{dateTime(event?.changedAt)} · Người thực hiện ID: {event?.changedById ?? "—"}</span>
                      {event?.reason && <p>Lý do: {event.reason}</p>}
                    </li>)}</ol>}
              </section>}
            </>}
          </div>
        </div>
      </div>
    </main>
  </div>;
}
