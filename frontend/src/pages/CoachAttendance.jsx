import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  getAttendanceHistoryApi,
  getAttendanceRosterApi,
  getCoachAttendanceSessionsApi,
  markAttendanceApi,
} from "../api";
import { applyAttendanceRequestIfCurrent, attendanceRowKey, isCurrentAttendanceRosterRequest } from "../attendanceRequests";
import {
  attendanceClassOptions,
  attendanceSessionsForClass,
  paginateAttendanceSessions,
} from "../coachAttendanceView.js";
import { buildAttendanceCsv } from "../attendanceCsv.js";
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
  const [view, setView] = useState("list");
  const [sessionPage, setSessionPage] = useState(1);
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
  const historyRequestRef = useRef(0);
  const sessionsRequestSequenceRef = useRef(0);
  const mountedRef = useRef(false);
  const detailHeadingRef = useRef(null);
  const selectedSessionButtonRef = useRef(null);
  const returnFocusSessionIdRef = useRef("");

  const selectedSession = useMemo(
    () => sessions.find((session) => String(session.classSessionId) === String(selectedSessionId)) || null,
    [sessions, selectedSessionId],
  );
  const classOptions = useMemo(() => attendanceClassOptions(sessions), [sessions]);
  const filteredSessions = useMemo(() => attendanceSessionsForClass(sessions, classFilter), [sessions, classFilter]);
  const sessionPageData = useMemo(
    () => paginateAttendanceSessions(filteredSessions, sessionPage),
    [filteredSessions, sessionPage],
  );
  const members = Array.isArray(roster?.members) ? roster.members : [];
  const sessionCancelled = String(roster?.sessionStatus || selectedSession?.sessionStatus || "").toUpperCase() === "CANCELLED";
  const canMark = Boolean(roster) && roster.markingAllowed === true && !sessionCancelled;
  const busy = savingMemberId !== null || savingAll;
  const rowState = (member) => {
    const rowKey = attendanceRowKey(selectedSessionId, member?.memberId);
    const currentStatus = member?.attendanceStatus || "NOT_MARKED";
    const chosenStatus = selectedStatuses[rowKey] ?? currentStatus;
    return { rowKey, currentStatus, chosenStatus, correcting: currentStatus !== "NOT_MARKED", changed: chosenStatus !== currentStatus };
  };
  const stats = members.reduce((counts, member) => {
    const status = rowState(member).chosenStatus;
    if (status === "PRESENT") counts.present += 1;
    else if (status === "ABSENT") counts.absent += 1;
    else if (status === "EXCUSED") counts.excused += 1;
    else counts.pending += 1;
    return counts;
  }, { present: 0, absent: 0, excused: 0, pending: 0 });
  const keyword = search.trim().toLocaleLowerCase("vi");
  const visibleMembers = keyword
    ? members.filter((member) => `${member?.memberName || ""} ${member?.memberId ?? ""}`.toLocaleLowerCase("vi").includes(keyword))
    : members;
  const pendingMembers = members.filter((member) => {
    const state = rowState(member);
    return state.changed && ["PRESENT", "ABSENT", "EXCUSED"].includes(state.chosenStatus);
  });

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      rosterRequestSequenceRef.current += 1;
      sessionsRequestSequenceRef.current += 1;
      historyRequestRef.current += 1;
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

  useEffect(() => {
    if (view === "detail") {
      detailHeadingRef.current?.focus();
      return;
    }
    if (returnFocusSessionIdRef.current) selectedSessionButtonRef.current?.focus();
  }, [view]);

  function chooseSession(sessionId) {
    historyRequestRef.current += 1;
    const nextSessionId = String(sessionId);
    const changedSession = nextSessionId !== String(selectedSessionIdRef.current);
    returnFocusSessionIdRef.current = nextSessionId;
    if (changedSession) {
      selectedSessionIdRef.current = nextSessionId;
      rosterRequestSequenceRef.current += 1;
      setRoster(null);
      setRosterLoading(false);
      setRosterError("");
      setRowErrors({});
      setNotice("");
      setHistoryForMemberId(null);
      setHistory([]);
      setHistoryLoading(false);
    }
    setSelectedSessionId(nextSessionId);
    setView("detail");
  }

  function chooseClass(classKey) {
    historyRequestRef.current += 1;
    if (String(selectedSessionIdRef.current) !== "") {
      selectedSessionIdRef.current = "";
      rosterRequestSequenceRef.current += 1;
    }
    returnFocusSessionIdRef.current = "";
    setView("list");
    setSessionPage(1);
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
    setHistoryLoading(false);
    setHistoryError("");
  }

  function returnToSessionList() {
    setView("list");
  }

  function setRowValue(setter, memberId, value) {
    setter((current) => ({ ...current, [memberId]: value }));
    setRowErrors((current) => ({ ...current, [memberId]: "" }));
  }

  async function saveAttendance(member) {
    if (!roster || !selectedSessionId || busy || sessionCancelled || roster.markingAllowed !== true) return;
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
    const missingReasons = pendingMembers.filter((member) => {
      const state = rowState(member);
      return state.correcting && !String(reasons[state.rowKey] || "").trim();
    });
    if (missingReasons.length > 0) {
      setRowErrors((current) => {
        const next = { ...current };
        missingReasons.forEach((member) => { next[rowState(member).rowKey] = "Vui lòng nhập lý do điều chỉnh."; });
        return next;
      });
      return;
    }
    const correctionCount = pendingMembers.filter((member) => rowState(member).correcting).length;
    if (correctionCount > 0 && !window.confirm(`Xác nhận điều chỉnh điểm danh của ${correctionCount} học viên đã được đánh dấu?`)) return;

    const sessionId = selectedSessionId;
    const batch = pendingMembers.slice();
    setSavingAll(true);
    setNotice("");
    let saved = 0;
    let sessionChanged = false;
    for (const member of batch) {
      if (String(selectedSessionIdRef.current) !== String(sessionId)) { sessionChanged = true; break; }
      const { rowKey, chosenStatus, correcting } = rowState(member);
      setRowErrors((current) => ({ ...current, [rowKey]: "" }));
      try {
        const response = await markAttendanceApi(token, sessionId, member.memberId, {
          status: chosenStatus,
          note: String(notes[rowKey] ?? member.note ?? "").trim() || null,
          reason: correcting ? String(reasons[rowKey] || "").trim() : null,
        });
        if (!mountedRef.current) return;
        if (String(selectedSessionIdRef.current) !== String(sessionId)) { sessionChanged = true; break; }
        if (response?.id != null) setAttendanceIds((current) => ({ ...current, [rowKey]: response.id }));
        saved += 1;
      } catch (error) {
        if (mountedRef.current) setRowErrors((current) => ({ ...current, [rowKey]: errorMessage(error, "Không lưu được điểm danh.") }));
      }
    }
    if (!mountedRef.current) return;
    setSavingAll(false);
    setNotice(sessionChanged
      ? `Đã dừng khi chuyển buổi. Đã lưu ${saved}/${batch.length} học viên; hãy kiểm tra buổi trước khi rời trang.`
      : saved === batch.length
      ? `Đã lưu điểm danh cho ${saved} học viên.`
      : `Đã lưu ${saved}/${batch.length} học viên; các dòng báo lỗi cần được kiểm tra.`);
    if (String(selectedSessionIdRef.current) === String(sessionId)) await loadRoster(sessionId);
  }

  function exportCsv() {
    if (!roster || members.length === 0) return;
    const rows = [["Mã học viên", "Họ tên", "Trạng thái đã lưu", "Thời điểm đánh dấu", "Ghi chú đã lưu"]]
      .concat(members.map((member) => [
        member?.memberId,
        member?.memberName,
        statusLabel(member?.attendanceStatus || "NOT_MARKED"),
        member?.markedAt ? formatDateTime(member.markedAt) : "",
        member?.note || "",
      ]));
    const blob = new Blob([buildAttendanceCsv(rows)], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `diem-danh-buoi-${selectedSessionId}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  async function openHistory(memberId, attendanceId) {
    if (attendanceId == null) return;
    const sessionId = selectedSessionIdRef.current;
    const requestId = ++historyRequestRef.current;
    const isCurrent = () => mountedRef.current
      && requestId === historyRequestRef.current
      && String(selectedSessionIdRef.current) === String(sessionId);
    setHistoryForMemberId(memberId);
    setHistoryMemberId(memberId);
    setHistory([]);
    setHistoryError("");
    setHistoryLoading(true);
    await applyAttendanceRequestIfCurrent(getAttendanceHistoryApi(token, attendanceId), {
      isCurrent,
      onSuccess: (result) => setHistory(Array.isArray(result) ? result : []),
      onError: (error) => setHistoryError(errorMessage(error, "Không tải được lịch sử điểm danh.")),
      onSettled: () => setHistoryLoading(false),
    });
  }

  function closeHistory() {
    historyRequestRef.current += 1;
    setHistoryForMemberId(null);
    setHistoryLoading(false);
  }

  function sessionAvailability(session) {
    if (String(session?.sessionStatus || "").toUpperCase() === "CANCELLED") return "Buổi học đã hủy";
    const start = parseLocalDateTime(session?.startTime);
    if (!start) return "Chưa xác định thời điểm điểm danh";
    if (Date.now() < start.getTime()) return "Chưa đến giờ điểm danh";
    if (String(session?.sessionStatus || "").toUpperCase() === "COMPLETED") return "Có thể điểm danh bổ sung";
    return "Có thể điểm danh";
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="coach-attendance" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main coach-attendance-main">
        <div className="coach-attendance-page">
          <header className="coach-attendance-header">
            <div>
              <p className="pt-eyebrow">SPORTCENTER / COACH</p>
              <h1>Quản lý điểm danh</h1>
              <p>{view === "list"
                ? "Chọn lớp và buổi học được phân công để xem danh sách Member đã đăng ký."
                : "Xem thông tin và điểm danh Member của buổi học đã chọn."}</p>
            </div>
            <button type="button" className="coach-attendance-refresh" onClick={() => void loadSessions()} disabled={sessionsLoading}>
              {sessionsLoading ? "Đang tải…" : "Làm mới"}
            </button>
          </header>

          {sessionsError && <p className="coach-attendance-alert is-error" role="alert">{sessionsError}</p>}
          {notice && <p className="coach-attendance-alert is-success" role="status">{notice}</p>}

          {view === "list" ? <section className="coach-attendance-panel coach-attendance-session-list-view" aria-labelledby="coach-attendance-sessions-title">
            <div className="coach-attendance-section-heading">
              <div>
                <h2 id="coach-attendance-sessions-title">Buổi học được phân công</h2>
                <p>Chọn lớp trước, sau đó chọn buổi cần điểm danh.</p>
              </div>
              <span>{sessionPageData.total} buổi</span>
            </div>
            {sessionsLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách buổi học…</p>
              : sessionsError ? null
                : sessions.length === 0 ? <p className="coach-attendance-empty">Hiện chưa có buổi học nào được phân công.</p>
                  : <>
                    <label className="coach-attendance-class-filter">
                      <span>Chọn lớp</span>
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
                    {sessionPageData.items.map((session) => {
                      const id = session?.classSessionId;
                      const active = String(id) === String(selectedSessionId);
                      return <button
                        type="button"
                        className={"coach-attendance-session" + (active ? " is-selected" : "")}
                        key={id ?? String(session?.courseClassId) + "-" + String(session?.startTime)}
                        ref={String(id) === returnFocusSessionIdRef.current ? selectedSessionButtonRef : undefined}
                        onClick={() => id != null && chooseSession(id)}
                        disabled={id == null}
                        aria-pressed={active}
                      >
                        <span className="coach-attendance-session-title">
                          <strong>{session?.courseName || session?.courseOfferingName || "Khóa học"}</strong>
                          <span>{session?.courseClassName || (session?.courseClassId != null ? "Lớp #" + session.courseClassId : "Chưa có tên lớp")}</span>
                          {(session?.sportName || session?.sportId != null) && <span>{session?.sportName || "Môn thể thao"}{session?.sportId != null ? " · Sport #" + session.sportId : ""}</span>}
                        </span>
                        <span className="coach-attendance-session-time">
                          <strong>{formatDateTime(session?.startTime)}</strong>
                          <span>{formatTime(session?.startTime)} – {formatTime(session?.endTime)}</span>
                        </span>
                        <span className={"coach-attendance-badge status-" + String(session?.sessionStatus || "unknown").toLowerCase()}>
                          {SESSION_STATUS_LABELS[session?.sessionStatus] || session?.sessionStatus || "Chưa xác định"}
                        </span>
                        <span className="coach-attendance-availability">{sessionAvailability(session)}</span>
                      </button>;
                    })}
                  </div>}
                    {sessionPageData.totalPages > 1 && <nav className="coach-attendance-session-pagination" aria-label="Phân trang buổi học">
                      <button type="button" className="coach-attendance-history-button" onClick={() => setSessionPage(sessionPageData.page - 1)} disabled={sessionPageData.page <= 1}>Trước</button>
                      <span aria-live="polite">Trang {sessionPageData.page}/{sessionPageData.totalPages} · {sessionPageData.total} buổi</span>
                      <button type="button" className="coach-attendance-history-button" onClick={() => setSessionPage(sessionPageData.page + 1)} disabled={sessionPageData.page >= sessionPageData.totalPages}>Sau</button>
                    </nav>}
                  </>}
          </section> : selectedSessionId ? <section className="coach-attendance-panel coach-attendance-detail-view" aria-labelledby="coach-attendance-roster-title">
            <div className="coach-attendance-detail-toolbar">
              <button type="button" className="coach-attendance-history-button" onClick={returnToSessionList}>Quay lại danh sách</button>
            </div>
            <div className="coach-attendance-section-heading">
              <div>
                <h2 id="coach-attendance-roster-title" ref={detailHeadingRef} tabIndex={-1}>Danh sách điểm danh</h2>
                <p>{roster?.courseClassName || selectedSession?.courseClassName || "Lớp học"} · Buổi #{roster?.classSessionId || selectedSessionId}</p>
              </div>
              <span className="coach-attendance-count">{members.length} học viên</span>
            </div>

            {rosterLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách Member…</p>
              : rosterError ? <p className="coach-attendance-alert is-error" role="alert">{rosterError}</p>
                : !roster ? <p className="coach-attendance-empty">Chưa có dữ liệu danh sách điểm danh.</p>
                  : <>
                    <div className="coach-attendance-session-meta">
                      <span>{formatDateTime(roster.startTime)}</span>
                      <span>{formatTime(roster.startTime)} – {formatTime(roster.endTime)}</span>
                      <span className={"coach-attendance-badge status-" + String(roster.sessionStatus || "unknown").toLowerCase()}>
                        {SESSION_STATUS_LABELS[roster.sessionStatus] || roster.sessionStatus || "Chưa xác định"}
                      </span>
                    </div>
                    {sessionCancelled && <p className="coach-attendance-alert is-warning">Buổi học đã hủy. Danh sách chỉ xem được, không thể đánh dấu hoặc điều chỉnh.</p>}
                    {!sessionCancelled && roster.markingAllowed !== true && <p className="coach-attendance-alert is-warning">Chưa đến giờ bắt đầu buổi học. Có thể xem danh sách nhưng chưa thể điểm danh.</p>}
                    {members.length === 0 ? <p className="coach-attendance-empty">Không có Member đăng ký CONFIRMED cho buổi học này.</p>
                      : <>
                        <div className="coach-attendance-stats" aria-label="Tổng hợp trạng thái điểm danh">
                          <div className="stat stat-total"><span>Tổng sĩ số</span><strong>{members.length}</strong></div>
                          <div className="stat stat-present"><span>Có mặt</span><strong>{stats.present}</strong></div>
                          <div className="stat stat-absent"><span>Vắng mặt</span><strong>{stats.absent}</strong></div>
                          <div className="stat stat-excused"><span>Có phép</span><strong>{stats.excused}</strong></div>
                          <div className="stat stat-pending"><span>Chưa điểm danh</span><strong>{stats.pending}</strong></div>
                        </div>
                        <p className="coach-attendance-stats-note" role="status">
                          {pendingMembers.length > 0
                            ? `Thống kê tính theo lựa chọn hiện tại; ${pendingMembers.length} thay đổi chưa được lưu.`
                            : "Thống kê phản ánh điểm danh đã lưu."}
                        </p>
                        <div className="coach-attendance-toolbar">
                          <label className="coach-attendance-search-label">Tìm học viên
                            <input className="coach-attendance-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tên hoặc mã học viên" />
                          </label>
                          <div className="coach-attendance-toolbar-actions">
                            <button type="button" className="coach-attendance-history-button" onClick={markAllPresent} disabled={!canMark || busy || members.length === 0}>Đánh dấu tất cả có mặt</button>
                            <button type="button" className="coach-attendance-history-button" onClick={exportCsv} disabled={members.length === 0}>Xuất CSV</button>
                            <button type="button" className="coach-attendance-save-all" onClick={() => void saveAll()} disabled={!canMark || busy || pendingMembers.length === 0}>
                              {savingAll ? "Đang lưu…" : `Lưu tất cả${pendingMembers.length ? ` (${pendingMembers.length})` : ""}`}
                            </button>
                          </div>
                        </div>
                        <div className="coach-attendance-table-wrap">
                          <table className="coach-attendance-table">
                            <thead><tr><th>Học viên</th><th>Trạng thái</th><th>Lần đánh dấu gần nhất</th><th>Ghi chú</th><th>Thao tác</th></tr></thead>
                            <tbody>{visibleMembers.length === 0 ? <tr><td colSpan={5}>Không tìm thấy học viên phù hợp.</td></tr> : visibleMembers.map((member) => {
                              const memberId = member?.memberId;
                              const rowKey = attendanceRowKey(selectedSessionId, memberId);
                              const currentStatus = member?.attendanceStatus || "NOT_MARKED";
                              const chosenStatus = selectedStatuses[rowKey] ?? currentStatus;
                              const correcting = currentStatus !== "NOT_MARKED";
                              const changed = chosenStatus !== currentStatus;
                              const canSave = roster.markingAllowed === true && !sessionCancelled && changed
                                && (!correcting || String(reasons[rowKey] || "").trim().length > 0);
                              const attendanceId = member?.attendanceId ?? attendanceIds[rowKey];
                              const historyAvailable = attendanceId != null;
                              return <tr key={memberId ?? member?.memberName}>
                                <td><strong>{member?.memberName || "Member"}</strong><small>ID: {memberId ?? "—"}</small></td>
                                <td>
                                  <span className={"coach-attendance-badge attendance-" + String(currentStatus).toLowerCase()}>{statusLabel(currentStatus)}</span>
                                  <label className="coach-attendance-select-label" htmlFor={"attendance-" + memberId}>Trạng thái cập nhật</label>
                                  <select id={"attendance-" + memberId} value={chosenStatus}
                                    onChange={(event) => setRowValue(setSelectedStatuses, rowKey, event.target.value)}
                                     disabled={!roster.markingAllowed || sessionCancelled || busy}>
                                    {["NOT_MARKED", "PRESENT", "ABSENT", "EXCUSED"].map((status) =>
                                      <option key={status} value={status} disabled={status === "NOT_MARKED"}>{statusLabel(status)}</option>)}
                                  </select>
                                  {correcting && changed && <label className="coach-attendance-reason">
                                    <span>Lý do điều chỉnh <b aria-hidden="true">*</b></span>
                                    <input maxLength={500} value={reasons[rowKey] ?? ""}
                                      onChange={(event) => setRowValue(setReasons, rowKey, event.target.value)}
                                       disabled={busy} placeholder="Nhập lý do điều chỉnh" />
                                  </label>}
                                </td>
                                <td>{member?.markedAt ? <><span>{formatDateTime(member.markedAt)}</span><small>Nhân viên ID: {member?.markedById ?? "—"}</small></> : <span>Chưa đánh dấu</span>}</td>
                                <td><textarea aria-label={"Ghi chú điểm danh Member " + (memberId ?? "")} maxLength={500}
                                  value={notes[rowKey] ?? member?.note ?? ""}
                                  onChange={(event) => setRowValue(setNotes, rowKey, event.target.value)}
                                   disabled={!roster.markingAllowed || sessionCancelled || busy}
                                  placeholder="Ghi chú không bắt buộc" /></td>
                                <td>
                                  <button type="button" className="coach-attendance-save"
                                     disabled={!canSave || busy}
                                    onClick={() => void saveAttendance(member)}>
                                    {savingMemberId === memberId ? "Đang lưu…" : correcting ? "Lưu điều chỉnh" : "Lưu điểm danh"}
                                  </button>
                                  {historyAvailable
                                    ? <button type="button" className="coach-attendance-history-button"
                                       onClick={() => void openHistory(memberId, attendanceId)}>Lịch sử</button>
                                    : correcting && <small className="coach-attendance-history-unavailable">API roster chưa trả mã bản ghi để mở lịch sử.</small>}
                                  {rowErrors[rowKey] && <small className="coach-attendance-row-error" role="alert">{rowErrors[rowKey]}</small>}
                                </td>
                              </tr>;
                            })}</tbody>
                          </table>
                        </div>
                      </>}
                  </>}
          </section> : null}

          {view === "detail" && historyForMemberId != null && <section className="coach-attendance-panel coach-attendance-history" aria-labelledby="attendance-history-title">
            <div className="coach-attendance-section-heading">
              <h2 id="attendance-history-title">Lịch sử điểm danh · Member #{historyMemberId}</h2>
              <button type="button" className="coach-attendance-history-button" onClick={closeHistory}>Đóng</button>
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
        </div>
      </main>
    </div>
  );
}
