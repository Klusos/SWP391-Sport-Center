import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  getAttendanceHistoryApi,
  getAttendanceRosterApi,
  getCoachAttendanceSessionsApi,
  markAttendanceApi,
} from "../api";
import { attendanceRowKey, isCurrentAttendanceRosterRequest } from "../attendanceRequests";
import { attendanceClassOptions, attendanceSessionsForClass } from "../coachAttendanceView.js";
import "./CoachAttendance.css";

const STATUS_LABELS = {
  NOT_MARKED: "Chưa điểm danh",
  PRESENT: "Có mặt",
  ABSENT: "Vắng mặt",
  EXCUSED: "Có phép",
};

const SESSION_STATUS_LABELS = {
  SCHEDULED: "Đã lên lịch",
  COMPLETED: "Đã hoàn thành",
  CANCELLED: "Đã hủy",
};

function parseLocalDateTime(value) {
  if (typeof value !== "string" || !value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDateTime(value) {
  const date = parseLocalDateTime(value);
  return date ? date.toLocaleString("vi-VN") : "Chưa có thời gian";
}

function formatTime(value) {
  const date = parseLocalDateTime(value);
  return date ? date.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) : "—";
}

function statusLabel(value) {
  return STATUS_LABELS[value] || value || "Chưa xác định";
}

function errorMessage(error, fallback) {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Bạn không có quyền truy cập dữ liệu điểm danh này.";
  return error?.message || fallback;
}

export default function CoachAttendance({ token, role, onLogout, onNavigate }) {
  const [sessions, setSessions] = useState([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [sessionsError, setSessionsError] = useState("");
  const [selectedSessionId, setSelectedSessionId] = useState("");
  const [classFilter, setClassFilter] = useState("");
  const [roster, setRoster] = useState(null);
  const [rosterLoading, setRosterLoading] = useState(false);
  const [rosterError, setRosterError] = useState("");
  const [selectedStatuses, setSelectedStatuses] = useState({});
  const [notes, setNotes] = useState({});
  const [reasons, setReasons] = useState({});
  const [savingMemberId, setSavingMemberId] = useState(null);
  const [rowErrors, setRowErrors] = useState({});
  const [notice, setNotice] = useState("");
  const [search, setSearch] = useState("");
  const [savingAll, setSavingAll] = useState(false);
  const [attendanceIds, setAttendanceIds] = useState({});
  const [historyForMemberId, setHistoryForMemberId] = useState(null);
  const [historyMemberId, setHistoryMemberId] = useState(null);
  const [history, setHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState("");
  const selectedSessionIdRef = useRef("");
  const rosterRequestSequenceRef = useRef(0);
  const sessionsRequestSequenceRef = useRef(0);
  const mountedRef = useRef(false);

  const selectedSession = useMemo(
    () => sessions.find((session) => String(session.classSessionId) === String(selectedSessionId)) || null,
    [sessions, selectedSessionId],
  );
  const classOptions = useMemo(() => attendanceClassOptions(sessions), [sessions]);
  const filteredSessions = useMemo(() => attendanceSessionsForClass(sessions, classFilter), [sessions, classFilter]);
  const members = Array.isArray(roster?.members) ? roster.members : [];
  const sessionCancelled = String(roster?.sessionStatus || selectedSession?.sessionStatus || "").toUpperCase() === "CANCELLED";

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      rosterRequestSequenceRef.current += 1;
      sessionsRequestSequenceRef.current += 1;
    };
  }, []);

  const loadSessions = useCallback(async () => {
    const requestId = ++sessionsRequestSequenceRef.current;
    setSessionsLoading(true);
    setSessionsError("");
    try {
      const result = await getCoachAttendanceSessionsApi(token);
      if (mountedRef.current && requestId === sessionsRequestSequenceRef.current) {
        setSessions(Array.isArray(result) ? result : []);
      }
    } catch (error) {
      if (mountedRef.current && requestId === sessionsRequestSequenceRef.current) setSessions([]);
      if (mountedRef.current && requestId === sessionsRequestSequenceRef.current) {
        setSessionsError(errorMessage(error, "Không tải được danh sách buổi học được phân công."));
      }
    } finally {
      if (mountedRef.current && requestId === sessionsRequestSequenceRef.current) setSessionsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadSessions(), 0);
    return () => window.clearTimeout(timer);
  }, [loadSessions]);

  const loadRoster = useCallback(async (sessionId, active = () => true) => {
    if (String(sessionId) !== String(selectedSessionIdRef.current)) return;
    const requestId = ++rosterRequestSequenceRef.current;
    const isCurrent = () => isCurrentAttendanceRosterRequest({
      requestId,
      latestRequestId: rosterRequestSequenceRef.current,
      sessionId,
      selectedSessionId: selectedSessionIdRef.current,
      mounted: mountedRef.current,
      effectActive: active(),
    });
    setRosterLoading(true);
    setRosterError("");
    try {
      const result = await getAttendanceRosterApi(token, sessionId);
      if (isCurrent()) setRoster(result && typeof result === "object" ? result : null);
    } catch (error) {
      if (isCurrent()) {
        setRoster(null);
        setRosterError(errorMessage(error, "Không tải được danh sách điểm danh."));
      }
    } finally {
      if (isCurrent()) setRosterLoading(false);
    }
  }, [token]);

  useEffect(() => {
    if (!selectedSessionId) {
      setRoster(null);
      setRosterError("");
      return undefined;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      void loadRoster(selectedSessionId, () => active);
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [loadRoster, selectedSessionId]);

  function chooseSession(sessionId) {
    const nextSessionId = String(sessionId);
    if (nextSessionId !== String(selectedSessionIdRef.current)) {
      selectedSessionIdRef.current = nextSessionId;
      rosterRequestSequenceRef.current += 1;
    }
    setSelectedSessionId(nextSessionId);
    setRoster(null);
    setRosterLoading(false);
    setRowErrors({});
    setNotice("");
    setHistoryForMemberId(null);
    setHistory([]);
  }

  function chooseClass(classKey) {
    if (String(selectedSessionIdRef.current) !== "") {
      selectedSessionIdRef.current = "";
      rosterRequestSequenceRef.current += 1;
    }
    setClassFilter(classKey);
    setSelectedSessionId("");
    setRoster(null);
    setRosterLoading(false);
    setRosterError("");
    setRowErrors({});
    setNotice("");
    setHistoryForMemberId(null);
    setHistoryMemberId(null);
    setHistory([]);
    setHistoryError("");
  }

  function setRowValue(setter, memberId, value) {
    setter((current) => ({ ...current, [memberId]: value }));
    setRowErrors((current) => ({ ...current, [memberId]: "" }));
  }

  async function saveAttendance(member) {
    if (!roster || !selectedSessionId || savingMemberId !== null || sessionCancelled || roster.markingAllowed !== true) return;
    const memberId = member?.memberId;
    if (memberId == null) return;
    const rowKey = attendanceRowKey(selectedSessionId, memberId);
    const currentStatus = member.attendanceStatus || "NOT_MARKED";
    const nextStatus = selectedStatuses[rowKey] ?? currentStatus;
    if (!["PRESENT", "ABSENT", "EXCUSED"].includes(nextStatus) || nextStatus === currentStatus) return;

    const correcting = currentStatus !== "NOT_MARKED";
    const reason = String(reasons[rowKey] || "").trim();
    if (correcting && !reason) {
      setRowErrors((current) => ({ ...current, [rowKey]: "Vui lòng nhập lý do điều chỉnh." }));
      return;
    }
    if (correcting && !window.confirm(
      "Xác nhận điều chỉnh điểm danh của " + (member.memberName || "Member #" + memberId)
      + " từ “" + statusLabel(currentStatus) + "” sang “" + statusLabel(nextStatus) + "”?",
    )) return;

    setSavingMemberId(memberId);
    setRowErrors((current) => ({ ...current, [rowKey]: "" }));
    setNotice("");
    try {
      const response = await markAttendanceApi(token, selectedSessionId, memberId, {
        status: nextStatus,
        note: String(notes[rowKey] ?? member.note ?? "").trim() || null,
        reason: correcting ? reason : null,
      });
      if (!mountedRef.current) return;
      if (response?.id != null) setAttendanceIds((current) => ({ ...current, [rowKey]: response.id }));
      setSelectedStatuses((current) => ({ ...current, [rowKey]: nextStatus }));
      if (String(selectedSessionIdRef.current) === String(selectedSessionId)) {
        setNotice("Đã lưu điểm danh cho " + (member.memberName || "Member #" + memberId) + ".");
        await loadRoster(selectedSessionId);
      }
    } catch (error) {
      if (mountedRef.current) setRowErrors((current) => ({ ...current, [rowKey]: errorMessage(error, "Không lưu được điểm danh.") }));
    } finally {
      if (mountedRef.current) setSavingMemberId(null);
    }
  }

  async function openHistory(memberId, attendanceId) {
    if (attendanceId == null || historyLoading) return;
    setHistoryForMemberId(memberId);
    setHistoryMemberId(memberId);
    setHistory([]);
    setHistoryError("");
    setHistoryLoading(true);
    try {
      const result = await getAttendanceHistoryApi(token, attendanceId);
      if (mountedRef.current) setHistory(Array.isArray(result) ? result : []);
    } catch (error) {
      if (mountedRef.current) setHistoryError(errorMessage(error, "Không tải được lịch sử điểm danh."));
    } finally {
      if (mountedRef.current) setHistoryLoading(false);
    }
  }

  function sessionAvailability(session) {
    if (String(session?.sessionStatus || "").toUpperCase() === "CANCELLED") return "Buổi học đã hủy";
    const start = parseLocalDateTime(session?.startTime);
    if (!start) return "Chưa xác định thời điểm điểm danh";
    if (Date.now() < start.getTime()) return "Chưa đến giờ điểm danh";
    if (String(session?.sessionStatus || "").toUpperCase() === "COMPLETED") return "Có thể điểm danh bổ sung";
    return "Có thể điểm danh";
  }

  const rowState = (member) => {
    const rowKey = attendanceRowKey(selectedSessionId, member?.memberId);
    const currentStatus = member?.attendanceStatus || "NOT_MARKED";
    const chosenStatus = selectedStatuses[rowKey] ?? currentStatus;
    return { rowKey, currentStatus, chosenStatus, correcting: currentStatus !== "NOT_MARKED", changed: chosenStatus !== currentStatus };
  };
  const canMark = Boolean(roster) && roster.markingAllowed === true && !sessionCancelled;
  const busy = savingMemberId !== null || savingAll;
  const stats = members.reduce((acc, member) => {
    const { chosenStatus } = rowState(member);
    if (chosenStatus === "PRESENT") acc.present += 1;
    else if (chosenStatus === "ABSENT") acc.absent += 1;
    else if (chosenStatus === "EXCUSED") acc.excused += 1;
    else acc.notMarked += 1;
    return acc;
  }, { present: 0, absent: 0, excused: 0, notMarked: 0 });
  const keyword = search.trim().toLowerCase();
  const visibleMembers = keyword
    ? members.filter((member) => `${member?.memberName || ""} ${member?.memberId ?? ""}`.toLowerCase().includes(keyword))
    : members;
  const pendingMembers = members.filter((member) => {
    const state = rowState(member);
    return state.changed && ["PRESENT", "ABSENT", "EXCUSED"].includes(state.chosenStatus);
  });

  function markAllPresent() {
    if (!canMark || busy) return;
    setSelectedStatuses((current) => {
      const next = { ...current };
      members.forEach((member) => { next[attendanceRowKey(selectedSessionId, member?.memberId)] = "PRESENT"; });
      return next;
    });
  }

  async function saveAll() {
    if (!canMark || busy || pendingMembers.length === 0) return;
    const missing = pendingMembers.filter((member) => {
      const state = rowState(member);
      return state.correcting && !String(reasons[state.rowKey] || "").trim();
    });
    if (missing.length > 0) {
      setRowErrors((current) => {
        const next = { ...current };
        missing.forEach((member) => { next[rowState(member).rowKey] = "Vui lòng nhập lý do điều chỉnh."; });
        return next;
      });
      return;
    }
    const corrections = pendingMembers.filter((member) => rowState(member).correcting).length;
    if (corrections > 0 && !window.confirm("Xác nhận điều chỉnh điểm danh của " + corrections + " học viên đã được đánh dấu trước đó?")) return;
    const sessionId = selectedSessionId;
    setSavingAll(true);
    setNotice("");
    let saved = 0;
    for (const member of pendingMembers) {
      const { rowKey, chosenStatus, correcting } = rowState(member);
      try {
        const response = await markAttendanceApi(token, sessionId, member.memberId, {
          status: chosenStatus,
          note: String(notes[rowKey] ?? member.note ?? "").trim() || null,
          reason: correcting ? String(reasons[rowKey] || "").trim() : null,
        });
        if (!mountedRef.current) return;
        if (response?.id != null) setAttendanceIds((current) => ({ ...current, [rowKey]: response.id }));
        saved += 1;
      } catch (error) {
        if (mountedRef.current) setRowErrors((current) => ({ ...current, [rowKey]: errorMessage(error, "Không lưu được điểm danh.") }));
      }
    }
    if (!mountedRef.current) return;
    setSavingAll(false);
    if (saved > 0) setNotice("Đã lưu điểm danh cho " + saved + "/" + pendingMembers.length + " học viên.");
    if (String(selectedSessionIdRef.current) === String(sessionId)) await loadRoster(sessionId);
  }

  function exportCsv() {
    if (!roster || members.length === 0) return;
    const escape = (value) => '"' + String(value ?? "").replace(/"/g, '""') + '"';
    const rows = [["Mã học viên", "Họ tên", "Trạng thái", "Thời điểm đánh dấu", "Ghi chú"]].concat(members.map((member) => {
      const { rowKey, chosenStatus } = rowState(member);
      return [member?.memberId, member?.memberName, statusLabel(chosenStatus), member?.markedAt ? formatDateTime(member.markedAt) : "", notes[rowKey] ?? member?.note ?? ""];
    }));
    const blob = new Blob(["\uFEFF" + rows.map((row) => row.map(escape).join(",")).join("\r\n")], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "diem-danh-buoi-" + selectedSessionId + ".csv";
    link.click();
    URL.revokeObjectURL(url);
  }

  function initials(name) {
    const parts = String(name || "?").trim().split(/\s+/);
    return ((parts[0]?.[0] || "") + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase() || "?";
  }

  const todayLabel = new Date().toLocaleDateString("vi-VN");

  return (
    <div className="layout">
      <Sidebar role={role} activePage="coach-attendance" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main coach-attendance-main">
        <div className="coach-attendance-page">
          <header className="coach-attendance-header">
            <div>
              <p className="pt-eyebrow">SPORTCENTER › COACH › QUẢN LÝ ĐIỂM DANH</p>
              <h1>Quản lý điểm danh</h1>
              <p>Chọn lớp và buổi học được phân công để điểm danh học viên.</p>
            </div>
            <div className="coach-attendance-header-actions">
              <span className="coach-attendance-today">Hôm nay: <b>{todayLabel}</b></span>
              <button type="button" className="coach-attendance-refresh" onClick={() => void loadSessions()} disabled={sessionsLoading}>
                {sessionsLoading ? "Đang tải…" : "Làm mới"}
              </button>
            </div>
          </header>

          {sessionsError && <p className="coach-attendance-alert is-error" role="alert">{sessionsError}</p>}
          {notice && <p className="coach-attendance-alert is-success" role="status">{notice}</p>}

          <div className="coach-attendance-grid">
            <aside className="coach-attendance-panel coach-attendance-sidepanel" aria-labelledby="coach-attendance-sessions-title">
              <div className="coach-attendance-section-heading">
                <h2 id="coach-attendance-sessions-title">Buổi học được phân công</h2>
                <span className="coach-attendance-count">{filteredSessions.length} buổi</span>
              </div>
              <p className="coach-attendance-sub">Chọn lớp trước, sau đó chọn buổi cần điểm danh.</p>
              {sessionsLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách buổi học…</p>
                : sessionsError ? null
                  : sessions.length === 0 ? <p className="coach-attendance-empty">Hiện chưa có buổi học nào được phân công.</p>
                    : <>
                      <label className="coach-attendance-class-filter">
                        <span>Lọc theo lớp</span>
                        <select value={classFilter} onChange={(event) => chooseClass(event.target.value)}>
                          <option value="">Chọn lớp để xem các buổi học</option>
                          {classOptions.map((option) => <option key={option.key} value={option.key}>
                            {option.name}{option.courseName ? ` · ${option.courseName}` : ""} · {option.sessionCount} buổi
                          </option>)}
                        </select>
                      </label>
                      {!classFilter ? <p className="coach-attendance-selection-hint">Hãy chọn một lớp để lọc danh sách buổi học được phân công.</p>
                        : filteredSessions.length === 0 ? <p className="coach-attendance-empty">Lớp này hiện chưa có buổi học nào.</p>
                          : <div className="coach-attendance-sessions">
                            {filteredSessions.map((session) => {
                              const id = session?.classSessionId;
                              const active = String(id) === String(selectedSessionId);
                              return <button
                                type="button"
                                className={"coach-attendance-session" + (active ? " is-selected" : "")}
                                key={id ?? String(session?.courseClassId) + "-" + String(session?.startTime)}
                                onClick={() => id != null && chooseSession(id)}
                                disabled={id == null}
                                aria-pressed={active}
                              >
                                <span className="coach-attendance-session-top">
                                  {session?.sportName && <span className="coach-attendance-chip">{session.sportName}</span>}
                                  <span className={"coach-attendance-badge status-" + String(session?.sessionStatus || "unknown").toLowerCase()}>
                                    {SESSION_STATUS_LABELS[session?.sessionStatus] || session?.sessionStatus || "Chưa xác định"}
                                  </span>
                                </span>
                                <strong>{session?.courseName || session?.courseOfferingName || "Khóa học"}</strong>
                                <span className="coach-attendance-session-sub">
                                  {session?.courseClassName || (session?.courseClassId != null ? "Lớp #" + session.courseClassId : "Chưa có tên lớp")} · Buổi #{id ?? "—"}
                                </span>
                                <span className="coach-attendance-session-foot">
                                  <span>{formatDateTime(session?.startTime)} – {formatTime(session?.endTime)}</span>
                                  <span className="coach-attendance-availability">{sessionAvailability(session)}</span>
                                </span>
                              </button>;
                            })}
                          </div>}
                    </>}
            </aside>

            <div className="coach-attendance-content">
              {!selectedSessionId ? <section className="coach-attendance-panel coach-attendance-placeholder">
                <h2>Danh sách điểm danh</h2>
                <p>Chọn một buổi học ở cột bên trái để xem và điểm danh học viên.</p>
              </section> : <>
                <section className="coach-attendance-panel" aria-labelledby="coach-attendance-roster-title">
                  <div className="coach-attendance-roster-head">
                    <div>
                      <h2 id="coach-attendance-roster-title">
                        Danh sách điểm danh{" "}
                        <span className={"coach-attendance-badge status-" + String(roster?.sessionStatus || selectedSession?.sessionStatus || "unknown").toLowerCase()}>
                          {SESSION_STATUS_LABELS[roster?.sessionStatus || selectedSession?.sessionStatus] || roster?.sessionStatus || "Chưa xác định"}
                        </span>
                      </h2>
                      <p className="coach-attendance-sub">
                        <b>{roster?.courseClassName || selectedSession?.courseClassName || "Lớp học"} · Buổi #{roster?.classSessionId || selectedSessionId}</b>
                        {" · "}{formatDateTime(roster?.startTime || selectedSession?.startTime)} – {formatTime(roster?.endTime || selectedSession?.endTime)}
                      </p>
                    </div>
                    <span className="coach-attendance-code">Mã buổi: #{roster?.classSessionId || selectedSessionId}</span>
                  </div>

                  {rosterLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách học viên…</p>
                    : rosterError ? <p className="coach-attendance-alert is-error" role="alert">{rosterError}</p>
                      : !roster ? <p className="coach-attendance-empty">Chưa có dữ liệu danh sách điểm danh.</p>
                        : <>
                          <div className="coach-attendance-stats">
                            <div className="stat stat-total"><span>Tổng học viên</span><strong>{members.length}</strong></div>
                            <div className="stat stat-present"><span>Có mặt</span><strong>{stats.present}</strong></div>
                            <div className="stat stat-absent"><span>Vắng mặt</span><strong>{stats.absent}</strong></div>
                            <div className="stat stat-excused"><span>Có phép</span><strong>{stats.excused}</strong></div>
                            <div className="stat stat-pending"><span>Chưa điểm danh</span><strong>{stats.notMarked}</strong></div>
                          </div>
                          {sessionCancelled && <p className="coach-attendance-alert is-warning"><b>Lưu ý:</b> Buổi học đã hủy. Danh sách chỉ xem được, không thể đánh dấu hoặc điều chỉnh.</p>}
                          {!sessionCancelled && roster.markingAllowed !== true && <p className="coach-attendance-alert is-warning"><b>Lưu ý:</b> Chưa đến giờ bắt đầu buổi học. Huấn luyện viên có thể xem danh sách nhưng chưa thể điểm danh.</p>}
                        </>}
                </section>

                {roster && !rosterLoading && !rosterError && <section className="coach-attendance-panel coach-attendance-roster">
                  {members.length === 0 ? <p className="coach-attendance-empty">Không có học viên đăng ký CONFIRMED cho buổi học này.</p> : <>
                    <div className="coach-attendance-toolbar">
                      <input type="search" className="coach-attendance-search" placeholder="Tìm kiếm tên, mã học viên…"
                        aria-label="Tìm học viên" value={search} onChange={(event) => setSearch(event.target.value)} />
                      <button type="button" className="coach-attendance-history-button" onClick={markAllPresent} disabled={!canMark || busy}>✓ Đánh dấu tất cả có mặt</button>
                      <button type="button" className="coach-attendance-history-button" onClick={exportCsv}>⭳ Xuất file</button>
                    </div>
                    <div className="coach-attendance-table-wrap">
                      <table className="coach-attendance-table">
                        <thead><tr><th>Học viên</th><th>Trạng thái điểm danh</th><th>Cập nhật gần nhất</th><th>Ghi chú buổi học</th><th>Thao tác</th></tr></thead>
                        <tbody>{visibleMembers.length === 0 ? <tr><td colSpan={5}>Không tìm thấy học viên phù hợp.</td></tr> : visibleMembers.map((member) => {
                          const memberId = member?.memberId;
                          const { rowKey, chosenStatus, correcting, changed } = rowState(member);
                          const canSave = canMark && changed && (!correcting || String(reasons[rowKey] || "").trim().length > 0);
                          const attendanceId = member?.attendanceId ?? attendanceIds[rowKey];
                          return <tr key={memberId ?? member?.memberName}>
                            <td>
                              <div className="coach-attendance-member">
                                <span className="coach-attendance-avatar">{initials(member?.memberName)}</span>
                                <span><strong>{member?.memberName || "Học viên"}</strong><small>#{memberId ?? "—"}</small></span>
                              </div>
                            </td>
                            <td>
                              <div className="coach-attendance-segment" role="group" aria-label={"Trạng thái điểm danh " + (member?.memberName || memberId)}>
                                {["PRESENT", "ABSENT", "EXCUSED"].map((status) => <label
                                  key={status}
                                  className={"seg seg-" + status.toLowerCase() + (chosenStatus === status ? " is-active" : "") + (!canMark || busy ? " is-disabled" : "")}
                                >
                                  <input type="radio" name={"attendance-" + memberId} value={status}
                                    checked={chosenStatus === status}
                                    disabled={!roster.markingAllowed || sessionCancelled || busy}
                                    onChange={(event) => setRowValue(setSelectedStatuses, rowKey, event.target.value)} />
                                  {statusLabel(status)}
                                </label>)}
                              </div>
                              {correcting && changed && <label className="coach-attendance-reason">
                                <span>Lý do điều chỉnh <b aria-hidden="true">*</b></span>
                                <input maxLength={500} value={reasons[rowKey] ?? ""}
                                  onChange={(event) => setRowValue(setReasons, rowKey, event.target.value)}
                                  disabled={busy} placeholder="Nhập lý do điều chỉnh" />
                              </label>}
                            </td>
                            <td>{member?.markedAt
                              ? <><strong>{formatTime(member.markedAt)}</strong><small>{formatDateTime(member.markedAt)}</small></>
                              : <em className="coach-attendance-muted">Chưa có dữ liệu</em>}</td>
                            <td><input className="coach-attendance-note" aria-label={"Ghi chú điểm danh học viên " + (memberId ?? "")} maxLength={500}
                              value={notes[rowKey] ?? member?.note ?? ""}
                              onChange={(event) => setRowValue(setNotes, rowKey, event.target.value)}
                              disabled={!canMark || busy} placeholder="Ghi chú…" /></td>
                            <td>
                              <div className="coach-attendance-actions">
                                <button type="button" className="coach-attendance-save" disabled={!canSave || busy} onClick={() => void saveAttendance(member)}>
                                  {savingMemberId === memberId ? "Đang lưu…" : "Lưu"}
                                </button>
                                {attendanceId != null
                                  ? <button type="button" className="coach-attendance-history-button" title="Lịch sử điểm danh" aria-label="Lịch sử điểm danh"
                                    disabled={historyLoading} onClick={() => void openHistory(memberId, attendanceId)}>⟲</button>
                                  : correcting && <small className="coach-attendance-history-unavailable">Chưa có mã bản ghi.</small>}
                              </div>
                              {rowErrors[rowKey] && <small className="coach-attendance-row-error" role="alert">{rowErrors[rowKey]}</small>}
                            </td>
                          </tr>;
                        })}</tbody>
                      </table>
                    </div>
                    <div className="coach-attendance-footer">
                      <span>Hiển thị {visibleMembers.length} / {members.length} học viên trong danh sách lớp</span>
                      <button type="button" className="coach-attendance-save coach-attendance-save-all" disabled={!canMark || busy || pendingMembers.length === 0} onClick={() => void saveAll()}>
                        {savingAll ? "Đang lưu…" : "Lưu toàn bộ điểm danh" + (pendingMembers.length ? " (" + pendingMembers.length + ")" : "")}
                      </button>
                    </div>
                  </>}
                </section>}

                {historyForMemberId != null && <section className="coach-attendance-panel coach-attendance-history" aria-labelledby="attendance-history-title">
                  <div className="coach-attendance-section-heading">
                    <h2 id="attendance-history-title">Lịch sử điểm danh · Học viên #{historyMemberId}</h2>
                    <button type="button" className="coach-attendance-history-button" onClick={() => setHistoryForMemberId(null)}>Đóng</button>
                  </div>
                  {historyLoading ? <p role="status">Đang tải lịch sử…</p>
                    : historyError ? <p className="coach-attendance-alert is-error" role="alert">{historyError}</p>
                      : history.length === 0 ? <p className="coach-attendance-empty">Chưa có sự kiện lịch sử.</p>
                        : <ol className="coach-attendance-history-list">{history.map((event) => <li key={event?.id ?? String(event?.changedAt) + "-" + String(event?.newStatus)}>
                          <strong>{event?.oldStatus ? statusLabel(event.oldStatus) + " → " + statusLabel(event.newStatus) : "Đánh dấu ban đầu: " + statusLabel(event?.newStatus)}</strong>
                          <span>{formatDateTime(event?.changedAt)} · Người thực hiện ID: {event?.changedById ?? "—"}</span>
                          {event?.reason && <p>Lý do: {event.reason}</p>}
                        </li>)}</ol>}
                </section>}

              </>}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
