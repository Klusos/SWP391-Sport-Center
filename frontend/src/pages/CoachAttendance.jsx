import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  getAttendanceHistoryApi,
  getAttendanceRosterApi,
  getCoachAttendanceSessionsApi,
  markAttendanceApi,
} from "../api";
import "./CoachAttendance.css";

const BUSINESS_ZONE = "Asia/Ho_Chi_Minh";
const MARKED_STATUSES = ["PRESENT", "ABSENT", "EXCUSED"];

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
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value;
  }

  if (typeof value !== "string" || !value.trim()) return null;

  const input = value.trim();
  const hasTimezone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(input);
  const normalized = hasTimezone ? input : `${input}+07:00`;
  const date = new Date(normalized);

  return Number.isNaN(date.getTime()) ? null : date;
}

function businessDateKey(value) {
  const date = value instanceof Date ? value : parseLocalDateTime(value);
  if (!date) return "";

  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: BUSINESS_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);

  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function isSameBusinessDay(value, now = new Date()) {
  const sessionKey = businessDateKey(value);
  return Boolean(sessionKey) && sessionKey === businessDateKey(now);
}

function formatDateTime(value) {
  const date = parseLocalDateTime(value);
  return date
    ? date.toLocaleString("vi-VN", { timeZone: BUSINESS_ZONE })
    : "Chưa có thời gian";
}

function formatTime(value) {
  const date = parseLocalDateTime(value);
  return date
    ? date.toLocaleTimeString("vi-VN", {
        timeZone: BUSINESS_ZONE,
        hour: "2-digit",
        minute: "2-digit",
      })
    : "—";
}

function statusLabel(value) {
  return STATUS_LABELS[value] || value || "Chưa xác định";
}

function attendanceKey(sessionId, memberId) {
  return String(sessionId) + ":" + String(memberId);
}

function classFilterKey(session) {
  if (session?.courseClassId != null) return `id:${session.courseClassId}`;
  return `name:${session?.courseClassName || "unknown"}`;
}

function isMemberMarked(member) {
  return MARKED_STATUSES.includes(String(member?.attendanceStatus || "NOT_MARKED").toUpperCase());
}

function isRosterComplete(roster) {
  const members = Array.isArray(roster?.members) ? roster.members : [];
  return members.length > 0 && members.every(isMemberMarked);
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
  const [completedSessionIds, setCompletedSessionIds] = useState({});

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
    () =>
      sessions.find(
        (session) => String(session.classSessionId) === String(selectedSessionId),
      ) || null,
    [sessions, selectedSessionId],
  );

  const classOptions = useMemo(() => {
    const map = new Map();

    sessions.forEach((session) => {
      const key = classFilterKey(session);
      if (!map.has(key)) {
        map.set(key, {
          key,
          name:
            session?.courseClassName ||
            (session?.courseClassId != null
              ? `Lớp #${session.courseClassId}`
              : "Lớp chưa xác định"),
        });
      }
    });

    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name, "vi"));
  }, [sessions]);

  const filteredSessions = useMemo(() => {
    if (!classFilter) return [];
    return sessions.filter((session) => classFilterKey(session) === classFilter);
  }, [sessions, classFilter]);

  const members = Array.isArray(roster?.members) ? roster.members : [];
  const allMembersMarked = isRosterComplete(roster);

  const sessionCancelled =
    String(roster?.sessionStatus || selectedSession?.sessionStatus || "").toUpperCase() ===
    "CANCELLED";

  const selectedStartTime = roster?.startTime || selectedSession?.startTime;
  const selectedStartDate = parseLocalDateTime(selectedStartTime);
  const selectedSessionIsToday = isSameBusinessDay(selectedStartTime);
  const selectedSessionStarted = Boolean(
    selectedStartDate && Date.now() >= selectedStartDate.getTime(),
  );

  const selectedSessionCompleted =
    allMembersMarked ||
    Boolean(completedSessionIds[String(selectedSessionId)]) ||
    String(roster?.sessionStatus || selectedSession?.sessionStatus || "").toUpperCase() ===
      "COMPLETED";

  // Cho phép chỉnh trong đúng ngày học. Sau ngày đó khóa toàn bộ thao tác ghi.
  // Với session COMPLETED trong ngày, vẫn cho phép sửa theo yêu cầu nghiệp vụ.
  const attendanceEditable =
    Boolean(roster && selectedSessionId) &&
    !sessionCancelled &&
    selectedSessionIsToday &&
    selectedSessionStarted &&
    (roster?.markingAllowed === true || selectedSessionCompleted);

  const sessionStatusFor = useCallback(
    (session) => {
      const raw = String(session?.sessionStatus || "").toUpperCase();
      if (raw === "CANCELLED") return "CANCELLED";
      if (completedSessionIds[String(session?.classSessionId)] || raw === "COMPLETED") {
        return "COMPLETED";
      }
      return raw || "SCHEDULED";
    },
    [completedSessionIds],
  );

  const markSessionCompletedLocally = useCallback((sessionId) => {
    if (sessionId == null) return;
    const id = String(sessionId);

    setCompletedSessionIds((current) => ({ ...current, [id]: true }));
    setSessions((current) =>
      current.map((session) =>
        String(session?.classSessionId) === id
          ? { ...session, sessionStatus: "COMPLETED" }
          : session,
      ),
    );
  }, []);

  const loadSessions = useCallback(async () => {
    setSessionsLoading(true);
    setSessionsError("");

    try {
      const result = await getCoachAttendanceSessionsApi(token);
      const rows = Array.isArray(result) ? result : [];

      // Chỉ lấy danh sách session từ API.
      // Không phụ thuộc completedSessionIds để tránh refetch và làm giao diện
      // bị giật mỗi khi một session được đánh dấu COMPLETED cục bộ.
      setSessions(rows);
    } catch (error) {
      setSessions([]);
      setSessionsError(
        errorMessage(error, "Không tải được danh sách buổi học được phân công."),
      );
    } finally {
      setSessionsLoading(false);
    }
  }, [token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadSessions(), 0);
    return () => window.clearTimeout(timer);
  }, [loadSessions]);

  const loadRoster = useCallback(
    async (sessionId, active = () => true) => {
      setRosterLoading(true);
      setRosterError("");

      try {
        const result = await getAttendanceRosterApi(token, sessionId);
        const nextRoster = result && typeof result === "object" ? result : null;

        if (active()) {
          setRoster(nextRoster);

          if (isRosterComplete(nextRoster)) {
            markSessionCompletedLocally(sessionId);
          }
        }

        return nextRoster;
      } catch (error) {
        if (active()) {
          setRoster(null);
          setRosterError(errorMessage(error, "Không tải được danh sách điểm danh."));
        }
        return null;
      } finally {
        if (active()) setRosterLoading(false);
      }
    },
    [token, markSessionCompletedLocally],
  );

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
    if (!selectedSessionId || !classFilter) return;

    const stillVisible = filteredSessions.some(
      (session) => String(session?.classSessionId) === String(selectedSessionId),
    );

    if (!stillVisible) {
      setSelectedSessionId("");
      setRoster(null);
      setHistoryForMemberId(null);
      setHistory([]);
    }
  }, [classFilter, filteredSessions, selectedSessionId]);

  function chooseClass(nextClassKey) {
    setClassFilter(nextClassKey);
    setSelectedSessionId("");
    setRoster(null);
    setRosterError("");
    setSelectedStatuses({});
    setNotes({});
    setReasons({});
    setRowErrors({});
    setNotice("");
    setAttendanceIds({});
    setHistoryForMemberId(null);
    setHistoryMemberId(null);
    setHistory([]);
    setHistoryError("");
  }

  function chooseSession(sessionId) {
    setSelectedSessionId(String(sessionId));
    setRoster(null);
    setRowErrors({});
    setNotice("");
    setHistoryForMemberId(null);
    setHistory([]);
  }

  function setRowValue(setter, rowKey, value) {
    setter((current) => ({ ...current, [rowKey]: value }));
    setRowErrors((current) => ({ ...current, [rowKey]: "" }));
  }

  async function saveAttendance(member) {
    if (
      !roster ||
      !selectedSessionId ||
      savingMemberId !== null ||
      !attendanceEditable
    ) {
      return;
    }

    const memberId = member?.memberId;
    if (memberId == null) return;

    const rowKey = attendanceKey(selectedSessionId, memberId);
    const currentStatus = member.attendanceStatus || "NOT_MARKED";
    const nextStatus = selectedStatuses[rowKey] ?? currentStatus;

    if (!MARKED_STATUSES.includes(nextStatus) || nextStatus === currentStatus) return;

    const correcting = currentStatus !== "NOT_MARKED";
    const reason = String(reasons[rowKey] || "").trim();

    if (correcting && !reason) {
      setRowErrors((current) => ({
        ...current,
        [rowKey]: "Vui lòng nhập lý do điều chỉnh.",
      }));
      return;
    }

    if (
      correcting &&
      !window.confirm(
        "Xác nhận điều chỉnh điểm danh của " +
          (member.memberName || "Member #" + memberId) +
          " từ “" +
          statusLabel(currentStatus) +
          "” sang “" +
          statusLabel(nextStatus) +
          "”?",
      )
    ) {
      return;
    }

    setSavingMemberId(memberId);
    setRowErrors((current) => ({ ...current, [rowKey]: "" }));
    setNotice("");

    try {
      const response = await markAttendanceApi(token, selectedSessionId, memberId, {
        status: nextStatus,
        note: String(notes[rowKey] ?? member.note ?? "").trim() || null,
        reason: correcting ? reason : null,
      });

      if (response?.id != null) {
        setAttendanceIds((current) => ({ ...current, [rowKey]: response.id }));
      }

      setSelectedStatuses((current) => ({ ...current, [rowKey]: nextStatus }));

      const refreshedRoster = await loadRoster(selectedSessionId);
      const nowCompleted = isRosterComplete(refreshedRoster);

      if (nowCompleted) {
        markSessionCompletedLocally(selectedSessionId);
        setNotice(
          "Đã lưu điểm danh. Tất cả học viên đã được điểm danh nên buổi học được đánh dấu Đã hoàn thành. Bạn vẫn có thể điều chỉnh trong hôm nay.",
        );
      } else {
        setNotice(
          "Đã lưu điểm danh cho " + (member.memberName || "Member #" + memberId) + ".",
        );
      }
    } catch (error) {
      setRowErrors((current) => ({
        ...current,
        [rowKey]: errorMessage(error, "Không lưu được điểm danh."),
      }));
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
    const status = sessionStatusFor(session);

    if (status === "CANCELLED") return "Buổi học đã hủy";

    const start = parseLocalDateTime(session?.startTime);
    if (!start) return "Chưa xác định thời điểm điểm danh";

    if (Date.now() < start.getTime()) return "Chưa đến giờ điểm danh";

    const today = isSameBusinessDay(session?.startTime);

    if (!today) {
      return status === "COMPLETED"
        ? "Đã hoàn thành · Đã khóa chỉnh sửa"
        : "Đã qua ngày điểm danh · Đã khóa";
    }

    if (status === "COMPLETED") {
      return "Đã hoàn thành · Có thể chỉnh sửa hôm nay";
    }

    return "Có thể điểm danh";
  }

  const selectedSessionStatus = sessionCancelled
    ? "CANCELLED"
    : selectedSessionCompleted
      ? "COMPLETED"
      : String(roster?.sessionStatus || selectedSession?.sessionStatus || "SCHEDULED").toUpperCase();

  return (
    <div className="layout">
      <Sidebar
        role={role}
        activePage="coach-attendance"
        onNavigate={onNavigate}
        onLogout={onLogout}
      />

      <main className="main coach-attendance-main">
        <div className="coach-attendance-page">
          <header className="coach-attendance-header">
            <div>
              <p className="pt-eyebrow">SPORTCENTER / COACH</p>
              <h1>Quản lý điểm danh</h1>
              <p>
                Lọc theo lớp, chọn buổi học và điểm danh Member. Điểm danh chỉ được
                chỉnh sửa trong ngày diễn ra buổi học.
              </p>
            </div>

            <button
              type="button"
              className="coach-attendance-refresh"
              onClick={() => void loadSessions()}
              disabled={sessionsLoading}
            >
              {sessionsLoading ? "Đang tải…" : "Làm mới"}
            </button>
          </header>

          {sessionsError && (
            <p className="coach-attendance-alert is-error" role="alert">
              {sessionsError}
            </p>
          )}

          {notice && (
            <p className="coach-attendance-alert is-success" role="status">
              {notice}
            </p>
          )}

          <section
            className="coach-attendance-panel coach-attendance-selection-panel"
            aria-labelledby="coach-attendance-sessions-title"
          >
            <div className="coach-attendance-section-heading">
              <div>
                <h2 id="coach-attendance-sessions-title">Chọn lớp và buổi học</h2>
                <p>Chọn lớp trước, sau đó chọn buổi cần điểm danh.</p>
              </div>
            </div>

            {sessionsLoading ? (
              <p className="coach-attendance-empty" role="status">
                Đang tải danh sách lớp và buổi học…
              </p>
            ) : sessionsError ? null : sessions.length === 0 ? (
              <p className="coach-attendance-empty">
                Hiện chưa có buổi học nào được phân công.
              </p>
            ) : (
              <>
                <div className="coach-attendance-selector-grid">
                  <label className="coach-attendance-class-filter">
                    <span>1. Chọn lớp</span>
                    <select
                      value={classFilter}
                      onChange={(event) => chooseClass(event.target.value)}
                    >
                      <option value="">-- Chọn lớp --</option>
                      {classOptions.map((option) => (
                        <option key={option.key} value={option.key}>
                          {option.name}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label
                    className={
                      "coach-attendance-session-filter" +
                      (!classFilter ? " is-disabled" : "")
                    }
                  >
                    <span>2. Chọn buổi học</span>
                    <select
                      value={selectedSessionId}
                      onChange={(event) => {
                        const nextSessionId = event.target.value;

                        if (!nextSessionId) {
                          setSelectedSessionId("");
                          setRoster(null);
                          setRosterError("");
                          setNotice("");
                          setHistoryForMemberId(null);
                          setHistory([]);
                          return;
                        }

                        chooseSession(nextSessionId);
                      }}
                      disabled={!classFilter}
                    >
                      <option value="">
                        {classFilter ? "-- Chọn buổi học --" : "-- Chọn lớp trước --"}
                      </option>

                      {filteredSessions.map((session) => {
                        const id = session?.classSessionId;
                        const effectiveStatus = sessionStatusFor(session);

                        return (
                          <option key={id} value={id}>
                            Buổi #{id} · {formatDateTime(session?.startTime)}
                            {" · "}
                            {formatTime(session?.startTime)} – {formatTime(session?.endTime)}
                            {" · "}
                            {SESSION_STATUS_LABELS[effectiveStatus] || effectiveStatus}
                          </option>
                        );
                      })}
                    </select>
                  </label>
                </div>

                {!classFilter && (
                  <div className="coach-attendance-selection-hint">
                    Hãy chọn một lớp để xem các buổi học được phân công.
                  </div>
                )}

                {classFilter && filteredSessions.length === 0 && (
                  <div className="coach-attendance-selection-hint is-warning">
                    Lớp này hiện chưa có buổi học nào.
                  </div>
                )}

                {selectedSession && (
                  <div className="coach-attendance-selected-session">
                    <div>
                      <span className="coach-attendance-selected-session-label">
                        Buổi đang chọn
                      </span>
                      <strong>
                        {selectedSession?.courseClassName || "Lớp học"} · Buổi #
                        {selectedSession?.classSessionId}
                      </strong>
                      <small>
                        {formatDateTime(selectedSession?.startTime)} ·{" "}
                        {formatTime(selectedSession?.startTime)} –{" "}
                        {formatTime(selectedSession?.endTime)}
                      </small>
                    </div>

                    <span
                      className={
                        "coach-attendance-badge status-" +
                        sessionStatusFor(selectedSession).toLowerCase()
                      }
                    >
                      {SESSION_STATUS_LABELS[sessionStatusFor(selectedSession)] ||
                        sessionStatusFor(selectedSession)}
                    </span>
                  </div>
                )}
              </>
            )}
          </section>

          {selectedSessionId && (
            <section
              className="coach-attendance-panel"
              aria-labelledby="coach-attendance-roster-title"
            >
              <div className="coach-attendance-section-heading">
                <div>
                  <h2 id="coach-attendance-roster-title">Danh sách điểm danh</h2>
                  <p>
                    {roster?.courseClassName ||
                      selectedSession?.courseClassName ||
                      "Lớp học"}{" "}
                    · Buổi #{roster?.classSessionId || selectedSessionId}
                  </p>
                </div>
                <span>{members.length} Member</span>
              </div>

              {rosterLoading ? (
                <p className="coach-attendance-empty" role="status">
                  Đang tải danh sách Member…
                </p>
              ) : rosterError ? (
                <p className="coach-attendance-alert is-error" role="alert">
                  {rosterError}
                </p>
              ) : !roster ? (
                <p className="coach-attendance-empty">
                  Chưa có dữ liệu danh sách điểm danh.
                </p>
              ) : (
                <>
                  <div className="coach-attendance-session-meta">
                    <span>{formatDateTime(roster.startTime)}</span>
                    <span>
                      {formatTime(roster.startTime)} – {formatTime(roster.endTime)}
                    </span>
                    <span
                      className={
                        "coach-attendance-badge status-" +
                        selectedSessionStatus.toLowerCase()
                      }
                    >
                      {SESSION_STATUS_LABELS[selectedSessionStatus] || selectedSessionStatus}
                    </span>
                  </div>

                  {sessionCancelled && (
                    <p className="coach-attendance-alert is-warning">
                      Buổi học đã hủy. Danh sách chỉ xem được, không thể đánh dấu hoặc
                      điều chỉnh.
                    </p>
                  )}

                  {!sessionCancelled && selectedStartDate && !selectedSessionIsToday && (
                    <p className="coach-attendance-alert is-warning">
                      Buổi học này không diễn ra trong hôm nay. Điểm danh đã bị khóa;
                      bạn chỉ có thể xem dữ liệu và lịch sử.
                    </p>
                  )}

                  {!sessionCancelled &&
                    selectedSessionIsToday &&
                    !selectedSessionStarted && (
                      <p className="coach-attendance-alert is-warning">
                        Chưa đến giờ bắt đầu buổi học. Có thể xem danh sách nhưng chưa
                        thể điểm danh.
                      </p>
                    )}

                  {!sessionCancelled &&
                    selectedSessionIsToday &&
                    selectedSessionStarted &&
                    selectedSessionCompleted && (
                      <p className="coach-attendance-alert is-success">
                        Tất cả học viên đã được điểm danh. Buổi học đã hoàn thành; bạn
                        vẫn có thể điều chỉnh điểm danh đến hết hôm nay.
                      </p>
                    )}

                  {!sessionCancelled &&
                    selectedSessionIsToday &&
                    selectedSessionStarted &&
                    !selectedSessionCompleted &&
                    roster.markingAllowed !== true && (
                      <p className="coach-attendance-alert is-warning">
                        Máy chủ hiện không cho phép điểm danh buổi này.
                      </p>
                    )}

                  {members.length === 0 ? (
                    <p className="coach-attendance-empty">
                      Không có Member đăng ký CONFIRMED cho buổi học này.
                    </p>
                  ) : (
                    <>
                      <div className="coach-attendance-table-wrap">
                        <table className="coach-attendance-table">
                          <thead>
                            <tr>
                              <th>Member</th>
                              <th>Trạng thái</th>
                              <th>Lần đánh dấu gần nhất</th>
                              <th>Ghi chú</th>
                              <th>Thao tác</th>
                            </tr>
                          </thead>

                          <tbody>
                            {members.map((member) => {
                              const memberId = member?.memberId;
                              const rowKey = attendanceKey(selectedSessionId, memberId);
                              const currentStatus = member?.attendanceStatus || "NOT_MARKED";
                              const chosenStatus =
                                selectedStatuses[rowKey] ?? currentStatus;
                              const correcting = currentStatus !== "NOT_MARKED";
                              const changed = chosenStatus !== currentStatus;
                              const canSave =
                                attendanceEditable &&
                                changed &&
                                (!correcting ||
                                  String(reasons[rowKey] || "").trim().length > 0);
                              const attendanceId =
                                member?.attendanceId ?? attendanceIds[rowKey];
                              const historyAvailable = attendanceId != null;

                              return (
                                <tr key={memberId ?? member?.memberName}>
                                  <td>
                                    <strong>{member?.memberName || "Member"}</strong>
                                    <small>ID: {memberId ?? "—"}</small>
                                  </td>

                                  <td>
                                    <span
                                      className={
                                        "coach-attendance-badge attendance-" +
                                        String(currentStatus).toLowerCase()
                                      }
                                    >
                                      {statusLabel(currentStatus)}
                                    </span>

                                    <label
                                      className="coach-attendance-select-label"
                                      htmlFor={"attendance-" + memberId}
                                    >
                                      Trạng thái cập nhật
                                    </label>

                                    <select
                                      id={"attendance-" + memberId}
                                      value={chosenStatus}
                                      onChange={(event) =>
                                        setRowValue(
                                          setSelectedStatuses,
                                          rowKey,
                                          event.target.value,
                                        )
                                      }
                                      disabled={
                                        !attendanceEditable || savingMemberId !== null
                                      }
                                    >
                                      {["NOT_MARKED", "PRESENT", "ABSENT", "EXCUSED"].map(
                                        (status) => (
                                          <option
                                            key={status}
                                            value={status}
                                            disabled={status === "NOT_MARKED"}
                                          >
                                            {statusLabel(status)}
                                          </option>
                                        ),
                                      )}
                                    </select>

                                    {correcting && changed && (
                                      <label className="coach-attendance-reason">
                                        <span>
                                          Lý do điều chỉnh <b aria-hidden="true">*</b>
                                        </span>
                                        <input
                                          maxLength={500}
                                          value={reasons[rowKey] ?? ""}
                                          onChange={(event) =>
                                            setRowValue(
                                              setReasons,
                                              rowKey,
                                              event.target.value,
                                            )
                                          }
                                          disabled={
                                            !attendanceEditable || savingMemberId !== null
                                          }
                                          placeholder="Nhập lý do điều chỉnh"
                                        />
                                      </label>
                                    )}
                                  </td>

                                  <td>
                                    {member?.markedAt ? (
                                      <>
                                        <span>{formatDateTime(member.markedAt)}</span>
                                        <small>
                                          Nhân viên ID: {member?.markedById ?? "—"}
                                        </small>
                                      </>
                                    ) : (
                                      <span>Chưa đánh dấu</span>
                                    )}
                                  </td>

                                  <td>
                                    <textarea
                                      aria-label={
                                        "Ghi chú điểm danh Member " + (memberId ?? "")
                                      }
                                      maxLength={500}
                                      value={notes[rowKey] ?? member?.note ?? ""}
                                      onChange={(event) =>
                                        setRowValue(setNotes, rowKey, event.target.value)
                                      }
                                      disabled={
                                        !attendanceEditable || savingMemberId !== null
                                      }
                                      placeholder="Ghi chú không bắt buộc"
                                    />
                                  </td>

                                  <td>
                                    <button
                                      type="button"
                                      className="coach-attendance-save"
                                      disabled={!canSave || savingMemberId !== null}
                                      onClick={() => void saveAttendance(member)}
                                    >
                                      {savingMemberId === memberId
                                        ? "Đang lưu…"
                                        : correcting
                                          ? "Lưu điều chỉnh"
                                          : "Lưu điểm danh"}
                                    </button>

                                    {historyAvailable ? (
                                      <button
                                        type="button"
                                        className="coach-attendance-history-button"
                                        disabled={historyLoading}
                                        onClick={() =>
                                          void openHistory(memberId, attendanceId)
                                        }
                                      >
                                        Lịch sử
                                      </button>
                                    ) : (
                                      correcting && (
                                        <small className="coach-attendance-history-unavailable">
                                          API roster chưa trả mã bản ghi để mở lịch sử.
                                        </small>
                                      )
                                    )}

                                    {rowErrors[rowKey] && (
                                      <small
                                        className="coach-attendance-row-error"
                                        role="alert"
                                      >
                                        {rowErrors[rowKey]}
                                      </small>
                                    )}
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>

                      <p className="coach-attendance-footnote">
                        Quy tắc chỉnh sửa: Coach chỉ được thay đổi điểm danh trong
                        đúng ngày diễn ra buổi học (múi giờ Việt Nam). Từ ngày kế
                        tiếp, dữ liệu bị khóa nhưng lịch sử vẫn xem được.
                      </p>
                    </>
                  )}
                </>
              )}
            </section>
          )}

          {historyForMemberId != null && (
            <section
              className="coach-attendance-panel coach-attendance-history"
              aria-labelledby="attendance-history-title"
            >
              <div className="coach-attendance-section-heading">
                <h2 id="attendance-history-title">
                  Lịch sử điểm danh · Member #{historyMemberId}
                </h2>
                <button
                  type="button"
                  className="coach-attendance-history-button"
                  onClick={() => setHistoryForMemberId(null)}
                >
                  Đóng
                </button>
              </div>

              {historyLoading ? (
                <p role="status">Đang tải lịch sử…</p>
              ) : historyError ? (
                <p className="coach-attendance-alert is-error" role="alert">
                  {historyError}
                </p>
              ) : history.length === 0 ? (
                <p className="coach-attendance-empty">Chưa có sự kiện lịch sử.</p>
              ) : (
                <ol className="coach-attendance-history-list">
                  {history.map((event) => (
                    <li
                      key={
                        event?.id ??
                        String(event?.changedAt) + "-" + String(event?.newStatus)
                      }
                    >
                      <strong>
                        {event?.oldStatus
                          ? statusLabel(event.oldStatus) +
                            " → " +
                            statusLabel(event.newStatus)
                          : "Đánh dấu ban đầu: " + statusLabel(event?.newStatus)}
                      </strong>
                      <span>
                        {formatDateTime(event?.changedAt)} · Người thực hiện ID:{" "}
                        {event?.changedById ?? "—"}
                      </span>
                      {event?.reason && <p>Lý do: {event.reason}</p>}
                    </li>
                  ))}
                </ol>
              )}
            </section>
          )}
        </div>
      </main>
    </div>
  );
}
