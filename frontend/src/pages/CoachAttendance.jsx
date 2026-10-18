import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  getAttendanceHistoryApi,
  getAttendanceRosterApi,
  getCoachAttendanceSessionsApi,
  markAttendanceApi,
} from "../api";
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

function attendanceKey(sessionId, memberId) {
  return String(sessionId) + ":" + String(memberId);
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
  const [roster, setRoster] = useState(null);
  const [rosterLoading, setRosterLoading] = useState(false);
  const [rosterError, setRosterError] = useState("");
  const [selectedStatuses, setSelectedStatuses] = useState({});
  const [notes, setNotes] = useState({});
  const [reasons, setReasons] = useState({});
  const [savingMemberId, setSavingMemberId] = useState(null);
  const [rowErrors, setRowErrors] = useState({});
  const [notice, setNotice] = useState("");
  const [attendanceIds, setAttendanceIds] = useState({});
  const [historyForMemberId, setHistoryForMemberId] = useState(null);
  const [historyMemberId, setHistoryMemberId] = useState(null);
  const [history, setHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState("");

  const selectedSession = useMemo(
    () => sessions.find((session) => String(session.classSessionId) === String(selectedSessionId)) || null,
    [sessions, selectedSessionId],
  );
  const members = Array.isArray(roster?.members) ? roster.members : [];
  const sessionCancelled = String(roster?.sessionStatus || selectedSession?.sessionStatus || "").toUpperCase() === "CANCELLED";

  const loadSessions = useCallback(async () => {
    setSessionsLoading(true);
    setSessionsError("");
    try {
      const result = await getCoachAttendanceSessionsApi(token);
      setSessions(Array.isArray(result) ? result : []);
    } catch (error) {
      setSessions([]);
      setSessionsError(errorMessage(error, "Không tải được danh sách buổi học được phân công."));
    } finally {
      setSessionsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadSessions(), 0);
    return () => window.clearTimeout(timer);
  }, [loadSessions]);

  const loadRoster = useCallback(async (sessionId, active = () => true) => {
    setRosterLoading(true);
    setRosterError("");
    try {
      const result = await getAttendanceRosterApi(token, sessionId);
      if (active()) setRoster(result && typeof result === "object" ? result : null);
    } catch (error) {
      if (active()) {
        setRoster(null);
        setRosterError(errorMessage(error, "Không tải được danh sách điểm danh."));
      }
    } finally {
      if (active()) setRosterLoading(false);
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
    setSelectedSessionId(String(sessionId));
    setRoster(null);
    setRowErrors({});
    setNotice("");
    setHistoryForMemberId(null);
    setHistory([]);
  }

  function setRowValue(setter, memberId, value) {
    setter((current) => ({ ...current, [memberId]: value }));
    setRowErrors((current) => ({ ...current, [memberId]: "" }));
  }

  async function saveAttendance(member) {
    if (!roster || !selectedSessionId || savingMemberId !== null || sessionCancelled || roster.markingAllowed !== true) return;
    const memberId = member?.memberId;
    if (memberId == null) return;
    const rowKey = attendanceKey(selectedSessionId, memberId);
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
      if (response?.id != null) setAttendanceIds((current) => ({ ...current, [rowKey]: response.id }));
      setSelectedStatuses((current) => ({ ...current, [rowKey]: nextStatus }));
      setNotice("Đã lưu điểm danh cho " + (member.memberName || "Member #" + memberId) + ".");
      await loadRoster(selectedSessionId);
    } catch (error) {
      setRowErrors((current) => ({ ...current, [rowKey]: errorMessage(error, "Không lưu được điểm danh.") }));
    } finally {
      setSavingMemberId(null);
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
      setHistory(Array.isArray(result) ? result : []);
    } catch (error) {
      setHistoryError(errorMessage(error, "Không tải được lịch sử điểm danh."));
    } finally {
      setHistoryLoading(false);
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

  return (
    <div className="layout">
      <Sidebar role={role} activePage="coach-attendance" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main coach-attendance-main">
        <div className="coach-attendance-page">
          <header className="coach-attendance-header">
            <div>
              <p className="pt-eyebrow">SPORTCENTER / COACH</p>
              <h1>Quản lý điểm danh</h1>
              <p>Chọn buổi học được phân công để xem danh sách Member đã đăng ký.</p>
            </div>
            <button type="button" className="coach-attendance-refresh" onClick={() => void loadSessions()} disabled={sessionsLoading}>
              {sessionsLoading ? "Đang tải…" : "Làm mới"}
            </button>
          </header>

          {sessionsError && <p className="coach-attendance-alert is-error" role="alert">{sessionsError}</p>}
          {notice && <p className="coach-attendance-alert is-success" role="status">{notice}</p>}

          <section className="coach-attendance-panel" aria-labelledby="coach-attendance-sessions-title">
            <div className="coach-attendance-section-heading">
              <h2 id="coach-attendance-sessions-title">Buổi học được phân công</h2>
              <span>{sessions.length} buổi</span>
            </div>
            {sessionsLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách buổi học…</p>
              : sessionsError ? null
                : sessions.length === 0 ? <p className="coach-attendance-empty">Hiện chưa có buổi học nào được phân công.</p>
                  : <div className="coach-attendance-sessions">
                    {sessions.map((session) => {
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
          </section>

          {selectedSessionId && <section className="coach-attendance-panel" aria-labelledby="coach-attendance-roster-title">
            <div className="coach-attendance-section-heading">
              <div>
                <h2 id="coach-attendance-roster-title">Danh sách điểm danh</h2>
                <p>{roster?.courseClassName || selectedSession?.courseClassName || "Lớp học"} · Buổi #{roster?.classSessionId || selectedSessionId}</p>
              </div>
              <span>{members.length} Member</span>
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
                        <div className="coach-attendance-table-wrap">
                          <table className="coach-attendance-table">
                            <thead><tr><th>Member</th><th>Trạng thái</th><th>Lần đánh dấu gần nhất</th><th>Ghi chú</th><th>Thao tác</th></tr></thead>
                            <tbody>{members.map((member) => {
                              const memberId = member?.memberId;
                              const rowKey = attendanceKey(selectedSessionId, memberId);
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
                                    onChange={(event) => setRowValue(setSelectedStatuses, memberId, event.target.value)}
                                    disabled={!roster.markingAllowed || sessionCancelled || savingMemberId !== null}>
                                    {["NOT_MARKED", "PRESENT", "ABSENT", "EXCUSED"].map((status) =>
                                      <option key={status} value={status} disabled={status === "NOT_MARKED"}>{statusLabel(status)}</option>)}
                                  </select>
                                  {correcting && changed && <label className="coach-attendance-reason">
                                    <span>Lý do điều chỉnh <b aria-hidden="true">*</b></span>
                                    <input maxLength={500} value={reasons[rowKey] ?? ""}
                                      onChange={(event) => setRowValue(setReasons, rowKey, event.target.value)}
                                      disabled={savingMemberId !== null} placeholder="Nhập lý do điều chỉnh" />
                                  </label>}
                                </td>
                                <td>{member?.markedAt ? <><span>{formatDateTime(member.markedAt)}</span><small>Nhân viên ID: {member?.markedById ?? "—"}</small></> : <span>Chưa đánh dấu</span>}</td>
                                <td><textarea aria-label={"Ghi chú điểm danh Member " + (memberId ?? "")} maxLength={500}
                                  value={notes[rowKey] ?? member?.note ?? ""}
                                  onChange={(event) => setRowValue(setNotes, rowKey, event.target.value)}
                                  disabled={!roster.markingAllowed || sessionCancelled || savingMemberId !== null}
                                  placeholder="Ghi chú không bắt buộc" /></td>
                                <td>
                                  <button type="button" className="coach-attendance-save"
                                    disabled={!canSave || savingMemberId !== null}
                                    onClick={() => void saveAttendance(member)}>
                                    {savingMemberId === memberId ? "Đang lưu…" : correcting ? "Lưu điều chỉnh" : "Lưu điểm danh"}
                                  </button>
                                  {historyAvailable
                                    ? <button type="button" className="coach-attendance-history-button"
                                      disabled={historyLoading} onClick={() => void openHistory(memberId, attendanceId)}>Lịch sử</button>
                                    : correcting && <small className="coach-attendance-history-unavailable">API roster chưa trả mã bản ghi để mở lịch sử.</small>}
                                  {rowErrors[rowKey] && <small className="coach-attendance-row-error" role="alert">{rowErrors[rowKey]}</small>}
                                </td>
                              </tr>;
                            })}</tbody>
                          </table>
                        </div>
                        <p className="coach-attendance-footnote">API trả thời gian buổi học không kèm múi giờ; giao diện hiển thị theo giờ địa phương của thiết bị. Máy chủ vẫn xác nhận thời điểm và quyền điểm danh.</p>
                      </>}
                  </>}
          </section>}

          {historyForMemberId != null && <section className="coach-attendance-panel coach-attendance-history" aria-labelledby="attendance-history-title">
            <div className="coach-attendance-section-heading">
              <h2 id="attendance-history-title">Lịch sử điểm danh · Member #{historyMemberId}</h2>
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
        </div>
      </main>
    </div>
  );
}
