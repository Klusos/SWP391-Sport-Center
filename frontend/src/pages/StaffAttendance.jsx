import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import AdminTopBar from "../components/AdminTopBar";
import { getAttendanceHistoryApi, getAttendanceRosterApi, getStaffAttendanceSessionsApi, markAttendanceApi, submitInitialAttendanceApi } from "../api";
import { buildAttendanceCsv } from "../attendanceCsv.js";
import { applyAttendanceRequestIfCurrent } from "../attendanceRequests.js";
import { attendanceClassKey, attendanceClassOptions } from "../coachAttendanceView.js";
import { attendanceSessionStatusLabel } from "../attendanceStatusPresentation.js";
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
const dateOnly = (value) => dateValue(value)?.toLocaleDateString("vi-VN") || "Chưa có ngày";
const time = (value) => dateValue(value)?.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) || "—";
const requestError = (error, fallback) => {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Bạn không có quyền thực hiện thao tác điểm danh này.";
  if (error?.status === 404) return "Buổi học hoặc dữ liệu điểm danh không còn tồn tại.";
  if (error?.status === 409) return error.message || "Dữ liệu đã thay đổi. Vui lòng tải lại danh sách.";
  return error?.message || fallback;
};
const keyFor = (sessionId, memberId) => `${sessionId}:${memberId}`;
const PAGE_SIZE = 10;

export default function StaffAttendance({ token, role, currentUserName, onLogout, onNavigate }) {
  const [sessions, setSessions] = useState([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [sessionsError, setSessionsError] = useState("");
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [sportFilter, setSportFilter] = useState("");
  const [memberFilter, setMemberFilter] = useState("");
  const [page, setPage] = useState(1);
  const [sessionPage, setSessionPage] = useState(1);
  const [coursePage, setCoursePage] = useState(1);
  const [classListPage, setClassListPage] = useState(1);
  const [selectedCourseId, setSelectedCourseId] = useState("");
  const [classFrom, setClassFrom] = useState("");
  const [classTo, setClassTo] = useState("");
  const [sessionFrom, setSessionFrom] = useState("");
  const [sessionTo, setSessionTo] = useState("");
  const [completenessFilter, setCompletenessFilter] = useState("");
  const [selectedClassKey, setSelectedClassKey] = useState("");
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
  const selectedIdRef = useRef(selectedId);
  const rosterRequestRef = useRef(0);
  const historyRequestRef = useRef(0);
  selectedIdRef.current = selectedId;

  useEffect(() => () => {
    rosterRequestRef.current += 1;
    historyRequestRef.current += 1;
  }, []);

  const selectedSession = useMemo(() => sessions.find((item) => String(item?.classSessionId) === String(selectedId)) || null, [sessions, selectedId]);
  const members = Array.isArray(roster?.members) ? roster.members : [];
  const cancelled = String(roster?.sessionStatus || selectedSession?.sessionStatus || "").toUpperCase() === "CANCELLED";
  const initialSubmission = Boolean(roster) && ["NOT_RECORDED", "PARTIAL"].includes(roster.attendanceCompleteness);
  const showActionColumn = !initialSubmission || members.some((member) => member?.attendanceId != null);
  const busy = Boolean(savingKey) || savingAll;
  const rowState = (member) => {
    const rowKey = keyFor(selectedId, member?.memberId);
    const current = member?.attendanceStatus || "NOT_MARKED";
    const next = choices[rowKey] ?? current;
    return { rowKey, current, next, correction: current !== "NOT_MARKED", changed: next !== current };
  };
  const stats = members.reduce((counts, member) => {
    const status = rowState(member).next;
    if (status === "PRESENT") counts.present += 1;
    else if (status === "ABSENT") counts.absent += 1;
    else if (status === "EXCUSED") counts.excused += 1;
    else counts.pending += 1;
    return counts;
  }, { present: 0, absent: 0, excused: 0, pending: 0 });
  const filteredMembers = memberFilter ? members.filter((member) => rowState(member).next === memberFilter) : members;
  const pageCount = Math.max(1, Math.ceil(filteredMembers.length / PAGE_SIZE));
  const currentPage = Math.min(page, pageCount);
  const pagedMembers = filteredMembers.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
  const pendingMembers = members.filter((member) => {
    const state = rowState(member);
    return state.changed && ["PRESENT", "ABSENT"].includes(state.next);
  });
  const sports = [...new Map(sessions.filter((item) => item?.sportId != null || item?.sportName).map((item) => {
    const value = String(item?.sportId ?? item?.sportName);
    return [value, { value, label: item?.sportName || `Môn #${item.sportId}` }];
  })).values()].sort((left, right) => left.label.localeCompare(right.label, "vi"));
  const visibleSessions = useMemo(() => {
    const term = (role === "ADMIN" && selectedClassKey ? "" : query).trim().toLocaleLowerCase("vi");
    return sessions.filter((item) => {
      const haystack = [item?.sportName, item?.sportId, item?.courseName, item?.courseOfferingName, item?.courseClassName, item?.courseClassId, item?.classSessionId]
        .filter((value) => value != null).join(" ").toLocaleLowerCase("vi");
      return (!term || haystack.includes(term))
        && (!statusFilter || String(item?.sessionStatus || "").toUpperCase() === statusFilter)
        && (!completenessFilter || String(item?.attendanceCompleteness || "") === completenessFilter)
        && (Boolean(selectedCourseId) || !sportFilter || String(item?.sportId ?? item?.sportName ?? "") === sportFilter)
        && (!sessionFrom || String(item?.startTime || "").slice(0, 10) >= sessionFrom)
        && (!sessionTo || String(item?.startTime || "").slice(0, 10) <= sessionTo);
    });
  }, [sessions, query, role, selectedClassKey, selectedCourseId, statusFilter, completenessFilter, sportFilter, sessionFrom, sessionTo]);
  const classOptions = useMemo(() => attendanceClassOptions(sessions), [sessions]);
  const courseOptions = [...new Map(sessions.filter((item) => item?.courseId != null).map((item) => [String(item.courseId), {
    id: String(item.courseId), name: item.courseName || `Khóa học #${item.courseId}`, sportId: String(item.sportId ?? ""),
    sportName: item.sportName || "", classes: new Set(sessions.filter((row) => String(row.courseId) === String(item.courseId)).map((row) => String(row.courseClassId))),
  }])).values()];
  const term = query.trim().toLocaleLowerCase("vi");
  const visibleCourses = courseOptions.filter((course) => (!term || `${course.name} ${course.id}`.toLocaleLowerCase("vi").includes(term))
    && (!sportFilter || course.sportId === sportFilter));
  const coursePageCount = Math.max(1, Math.ceil(visibleCourses.length / PAGE_SIZE));
  const currentCoursePage = Math.min(coursePage, coursePageCount);
  const pagedCourses = visibleCourses.slice((currentCoursePage - 1) * PAGE_SIZE, currentCoursePage * PAGE_SIZE);
  const visibleClassOptions = useMemo(() => {
    const visibleKeys = new Set(visibleSessions.map(attendanceClassKey));
    return classOptions.filter((option) => visibleKeys.has(option.key)
      && (!selectedCourseId || sessions.some((session) => attendanceClassKey(session) === option.key && String(session.courseId) === selectedCourseId))
      && sessions.some((session) => attendanceClassKey(session) === option.key
        && (!classFrom || Boolean(session.offeringEndDate && session.offeringEndDate >= classFrom))
        && (!classTo || Boolean(session.offeringStartDate && session.offeringStartDate <= classTo))));
  }, [classOptions, visibleSessions, selectedCourseId, sessions, classFrom, classTo]);
  const classListPageCount = Math.max(1, Math.ceil(visibleClassOptions.length / PAGE_SIZE));
  const currentClassListPage = Math.min(classListPage, classListPageCount);
  const pagedClassOptions = visibleClassOptions.slice((currentClassListPage - 1) * PAGE_SIZE, currentClassListPage * PAGE_SIZE);
  const selectedClass = classOptions.find((option) => option.key === selectedClassKey) || null;
  const classSessions = selectedClassKey
    ? visibleSessions.filter((session) => attendanceClassKey(session) === selectedClassKey)
    : [];
  const sessionPageCount = Math.max(1, Math.ceil(classSessions.length / PAGE_SIZE));
  const currentSessionPage = Math.min(sessionPage, sessionPageCount);
  const pagedSessions = classSessions.slice((currentSessionPage - 1) * PAGE_SIZE, currentSessionPage * PAGE_SIZE);

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
    const requestId = ++rosterRequestRef.current;
    setRosterLoading(true); setRosterError(""); setRoster(null);
    try {
      const result = await getAttendanceRosterApi(token, sessionId);
      if (requestId !== rosterRequestRef.current || String(selectedIdRef.current) !== String(sessionId)) return;
      setRoster(result && typeof result === "object" ? result : null);
    } catch (error) { if (requestId === rosterRequestRef.current && String(selectedIdRef.current) === String(sessionId)) setRosterError(requestError(error, "Không tải được danh sách điểm danh.")); }
    finally {
      if (requestId === rosterRequestRef.current && String(selectedIdRef.current) === String(sessionId)) setRosterLoading(false);
    }
  }, [token]);
  useEffect(() => { if (selectedId) void loadRoster(selectedId); else setRoster(null); }, [selectedId, loadRoster]);

  function selectSession(id) {
    selectedIdRef.current = String(id); rosterRequestRef.current += 1;
    historyRequestRef.current += 1;
    setSelectedId(String(id)); setNotice(""); setRowErrors({}); setHistory([]); setHistoryOpen(false); setHistoryError(""); setHistoryLoading(false);
    setMemberFilter(""); setPage(1);
  }
  function markAllPresent() {
    if (!initialSubmission || busy || !roster?.markingAllowed || cancelled) return;
    setChoices((current) => {
      const next = { ...current };
      members.filter((member) => (member?.attendanceStatus || "NOT_MARKED") === "NOT_MARKED")
        .forEach((member) => { next[keyFor(selectedId, member.memberId)] = "PRESENT"; });
      return next;
    });
  }
  function returnToClassSelection() {
    selectedIdRef.current = "";
    rosterRequestRef.current += 1;
    setSelectedId("");
    setSelectedClassKey("");
    setClassListPage(1);
    setSessionPage(1);
  }
  function returnToCourseSelection() {
    selectedIdRef.current = ""; rosterRequestRef.current += 1;
    setSelectedId(""); setSelectedClassKey(""); setSelectedCourseId("");
    setCoursePage(1); setClassListPage(1); setSessionPage(1);
    setStatusFilter(""); setCompletenessFilter(""); setSessionFrom(""); setSessionTo(""); setClassFrom(""); setClassTo("");
  }
  function returnToSessions() {
    selectedIdRef.current = "";
    rosterRequestRef.current += 1;
    setSelectedId("");
    if (role !== "ADMIN") setSelectedClassKey("");
  }
  function editRow(setter, rowKey, value) {
    setter((current) => ({ ...current, [rowKey]: value }));
    setRowErrors((current) => ({ ...current, [rowKey]: "" }));
  }
  async function save(member) {
    if (!roster || initialSubmission || !selectedId || busy || roster.markingAllowed !== true || cancelled) return;
    const memberId = member?.memberId;
    if (memberId == null) return;
    const rowKey = keyFor(selectedId, memberId);
    const current = member?.attendanceStatus || "NOT_MARKED";
    const next = choices[rowKey] ?? current;
    if (!["PRESENT", "ABSENT"].includes(next) || next === current) return;
    const correction = current !== "NOT_MARKED";
    const reason = String(reasons[rowKey] || "").trim();
    if (correction && !reason) { setRowErrors((state) => ({ ...state, [rowKey]: "Vui lòng nhập lý do điều chỉnh." })); return; }
    if (correction && !window.confirm(`Xác nhận đổi trạng thái của ${member?.memberName || `Học viên #${memberId}`} từ “${labelStatus(current)}” sang “${labelStatus(next)}”?`)) return;
    setSavingKey(rowKey); setNotice(""); setRowErrors((state) => ({ ...state, [rowKey]: "" }));
    try {
      await markAttendanceApi(token, selectedId, memberId, {
        status: next,
        note: String(notes[rowKey] ?? member?.note ?? "").trim() || null,
        reason: correction ? reason : null,
      });
      setNotice(`Đã lưu điểm danh cho ${member?.memberName || `Học viên #${memberId}`} .`);
      await Promise.all([loadRoster(selectedId), loadSessions()]);
    } catch (error) { setRowErrors((state) => ({ ...state, [rowKey]: requestError(error, "Không thể lưu điểm danh.") })); }
    finally { setSavingKey(""); }
  }
  async function saveAll() {
    if (initialSubmission) {
      if (!roster || !selectedId || busy || roster.markingAllowed !== true || cancelled) return;
      const unselected = members.filter((member) => {
        const current = member?.attendanceStatus || "NOT_MARKED";
        const next = choices[keyFor(selectedId, member?.memberId)] ?? current;
        return current === "NOT_MARKED" && !["PRESENT", "ABSENT"].includes(next);
      });
      if (unselected.length) { setNotice(`${unselected.length} học viên chưa được chọn trạng thái.`); return; }
      const sessionId = selectedId;
      setSavingAll(true); setNotice(""); setRowErrors({});
      try {
        await submitInitialAttendanceApi(token, sessionId, members.map((member) => {
          const rowKey = keyFor(sessionId, member.memberId);
          return { memberId: member.memberId, status: choices[rowKey] ?? member.attendanceStatus,
            note: String(notes[rowKey] ?? member.note ?? "").trim() || null };
        }));
        setNotice("Đã gửi điểm danh cho toàn bộ học viên.");
        await Promise.all([loadRoster(sessionId), loadSessions()]);
      } catch (error) { setRosterError(requestError(error, "Không thể gửi điểm danh. Hãy tải lại danh sách.")); }
      finally { setSavingAll(false); }
      return;
    }
    if (!roster || !selectedId || busy || roster.markingAllowed !== true || cancelled || pendingMembers.length === 0) return;
    const missing = pendingMembers.filter((member) => {
      const state = rowState(member);
      return state.correction && !String(reasons[state.rowKey] || "").trim();
    });
    if (missing.length) {
      setRowErrors((current) => {
        const next = { ...current };
        missing.forEach((member) => { next[rowState(member).rowKey] = "Vui lòng nhập lý do điều chỉnh."; });
        return next;
      });
      return;
    }
    const correctionCount = pendingMembers.filter((member) => rowState(member).correction).length;
    if (correctionCount && !window.confirm(`Xác nhận điều chỉnh điểm danh của ${correctionCount} học viên đã được đánh dấu?`)) return;
    const sessionId = selectedId;
    const batch = pendingMembers.slice();
    setSavingAll(true); setNotice("");
    let saved = 0;
    let sessionChanged = false;
    for (const member of batch) {
      if (String(selectedIdRef.current) !== String(sessionId)) { sessionChanged = true; break; }
      const { rowKey, next, correction } = rowState(member);
      setRowErrors((current) => ({ ...current, [rowKey]: "" }));
      try {
        await markAttendanceApi(token, sessionId, member.memberId, {
          status: next,
          note: String(notes[rowKey] ?? member?.note ?? "").trim() || null,
          reason: correction ? String(reasons[rowKey] || "").trim() : null,
        });
        if (String(selectedIdRef.current) !== String(sessionId)) { sessionChanged = true; break; }
        saved += 1;
      } catch (error) {
        if (String(selectedIdRef.current) === String(sessionId)) {
          setRowErrors((current) => ({ ...current, [rowKey]: requestError(error, "Không lưu được điểm danh.") }));
        }
      }
    }
    setSavingAll(false);
    setNotice(sessionChanged
      ? `Đã dừng khi chuyển buổi. Đã lưu ${saved}/${batch.length} học viên; hãy kiểm tra buổi trước khi rời trang.`
      : saved === batch.length
        ? `Đã lưu điểm danh cho ${saved} học viên.`
        : `Đã lưu ${saved}/${batch.length} học viên; các dòng báo lỗi cần được kiểm tra.`);
    if (String(selectedIdRef.current) === String(sessionId)) await Promise.all([loadRoster(sessionId), loadSessions()]);
  }

  function exportCsv() {
    if (!roster || members.length === 0) return;
    const rows = [["Mã học viên", "Họ tên", "Trạng thái đã lưu", "Thời điểm đánh dấu", "Ghi chú đã lưu"]]
      .concat(members.map((member) => [member?.memberId, member?.memberName,
        labelStatus(member?.attendanceStatus || "NOT_MARKED"), member?.markedAt ? dateTime(member.markedAt) : "", member?.note || ""]));
    const blob = new Blob([buildAttendanceCsv(rows)], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url; link.download = `diem-danh-buoi-${selectedId}.csv`; link.click();
    URL.revokeObjectURL(url);
  }

  async function openHistory(attendanceId) {
    if (attendanceId == null) return;
    const sessionId = selectedIdRef.current;
    const requestId = ++historyRequestRef.current;
    const isCurrent = () => requestId === historyRequestRef.current
      && String(selectedIdRef.current) === String(sessionId);
    setHistoryLoading(true); setHistoryError(""); setHistory([]); setHistoryOpen(true);
    await applyAttendanceRequestIfCurrent(getAttendanceHistoryApi(token, attendanceId), {
      isCurrent,
      onSuccess: (result) => setHistory(Array.isArray(result) ? result : []),
      onError: (error) => setHistoryError(requestError(error, "Không tải được lịch sử điểm danh.")),
      onSettled: () => setHistoryLoading(false),
    });
  }

  function closeHistory() {
    historyRequestRef.current += 1;
    setHistoryOpen(false);
    setHistoryLoading(false);
  }

  return <div className="layout">
    <Sidebar role={role} activePage="attendance" onNavigate={onNavigate} onLogout={onLogout} />
    <main className={`main coach-attendance-main${role === "ADMIN" ? " staff-attendance-main" : ""}`}>
      {role === "ADMIN" && <AdminTopBar pageLabel="ĐIỂM DANH" displayName={currentUserName} avatarName={currentUserName} />}
      <div className={role === "ADMIN" ? "staff-attendance-content" : undefined}>
      <div className="coach-attendance-page">
        <header className="coach-attendance-header">
          <div>{role !== "ADMIN" && <p className="pt-eyebrow">SPORTCENTER / RECEPTIONIST</p>}<h1>Quản lý điểm danh lớp học</h1><p>Tra cứu buổi học, cập nhật điểm danh và xem lịch sử điều chỉnh.</p></div>
          <button type="button" className="coach-attendance-refresh" onClick={() => void loadSessions()} disabled={sessionsLoading}>{sessionsLoading ? "Đang tải…" : "Làm mới"}</button>
        </header>
        {sessionsError && <p className="coach-attendance-alert is-error" role="alert">{sessionsError}</p>}
        {notice && <p className="coach-attendance-alert is-success" role="status">{notice}</p>}
        {role === "ADMIN" && <section className="coach-attendance-panel staff-attendance-admin-navigation" aria-labelledby="staff-attendance-admin-heading">
          <div className="coach-attendance-section-heading">
            <div><h2 id="staff-attendance-admin-heading">{selectedId ? "Điểm danh" : selectedClass ? "Buổi học" : selectedCourseId ? "Chọn lớp học" : "Chọn khóa học"}</h2>
              <p>{selectedId
                ? <><span><strong>Lớp:</strong> {roster?.courseClassName || selectedClass?.name || "Lớp học"}</span><span><strong>Khóa:</strong> {roster?.courseName || selectedClass?.courseName || "Khóa học"}</span></>
                : selectedClass
                  ? <><span><strong>Lớp:</strong> {selectedClass.name}</span><span><strong>Khóa:</strong> {selectedClass.courseName || "Khóa học"}</span></>
                  : selectedCourseId
                    ? <><span>{courseOptions.find((course) => course.id === selectedCourseId)?.name || "Khóa học"}</span><span>Chọn lớp học để xem các buổi học.</span></>
                    : "Chọn khóa học để xem danh sách lớp học."}</p>
            </div>
            <span>{selectedId ? "Danh sách học viên" : selectedClass ? `${classSessions.length} buổi` : selectedCourseId ? `${visibleClassOptions.length} lớp` : `${visibleCourses.length} khóa học`}</span>
          </div>
          {!selectedId && <>
            {selectedClass && <button type="button" className="coach-attendance-history-button staff-attendance-back" onClick={returnToClassSelection}>Quay lại danh sách lớp</button>}
            {selectedCourseId && !selectedClass && <button type="button" className="coach-attendance-history-button staff-attendance-back" onClick={returnToCourseSelection}>Quay lại khóa học</button>}
            <div className={`staff-attendance-filters${selectedClass ? " is-session-filters" : selectedCourseId ? " is-class-filters" : " is-course-filters"}`}>
                {role === "ADMIN" ? <>
                  {!selectedCourseId && <label>Tìm khóa học<input value={query} onChange={(event) => { setQuery(event.target.value); setCoursePage(1); }} placeholder="Tên hoặc mã khóa học" /></label>}
                  {selectedCourseId && !selectedClass && <label>Tìm lớp học<input value={query} onChange={(event) => { setQuery(event.target.value); setClassListPage(1); }} placeholder="Tên hoặc mã lớp học" /></label>}
                  {selectedClass && <><label>Từ ngày<input type="date" value={sessionFrom} onChange={(event) => { setSessionFrom(event.target.value); setSessionPage(1); }} /></label><label>Đến ngày<input type="date" value={sessionTo} onChange={(event) => { setSessionTo(event.target.value); setSessionPage(1); }} /></label><label>Trạng thái buổi học<select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setSessionPage(1); }}><option value="">Tất cả trạng thái</option><option value="SCHEDULED">Đã lên lịch</option><option value="COMPLETED">Đã hoàn thành</option><option value="CANCELLED">Đã hủy</option></select></label><label>Tình trạng điểm danh<select value={completenessFilter} onChange={(event) => { setCompletenessFilter(event.target.value); setSessionPage(1); }}><option value="">Tất cả</option><option value="NOT_RECORDED">Chưa điểm danh</option><option value="PARTIAL">Chưa hoàn tất</option><option value="COMPLETE">Đã điểm danh</option><option value="NOT_APPLICABLE">Không có học viên</option><option value="NOT_YET_ELIGIBLE">Chưa đến giờ điểm danh</option></select></label></>}
                </> : <label>Tìm buổi học<input value={query} onChange={(event) => { setQuery(event.target.value); setSessionPage(1); }} placeholder="Nhập từ khóa" /></label>}
              {role === "ADMIN" && !selectedCourseId && <label>Bộ môn<select value={sportFilter} onChange={(event) => { setSportFilter(event.target.value); setSessionPage(1); setCoursePage(1); setClassListPage(1); }}><option value="">Tất cả bộ môn</option>{sports.map((sport) => <option key={sport.value} value={sport.value}>{sport.label}</option>)}</select></label>}
              {selectedCourseId && !selectedClass && <><label>Từ ngày<input type="date" value={classFrom} onChange={(event) => { setClassFrom(event.target.value); setClassListPage(1); }} /></label><label>Đến ngày<input type="date" value={classTo} onChange={(event) => { setClassTo(event.target.value); setClassListPage(1); }} /></label></>}
            </div>
            {sessionsLoading ? <p className="coach-attendance-empty" role="status">Đang tải dữ liệu lớp học…</p>
              : sessionsError ? null
                : !selectedCourseId ? visibleCourses.length === 0 ? <p className="coach-attendance-empty">Không tìm thấy khóa học phù hợp.</p>
                  : <><div className="staff-attendance-class-list" aria-label="Các khóa học được phép truy cập">{pagedCourses.map((course) => <button type="button" className="coach-attendance-session staff-attendance-class-card" key={course.id} onClick={() => { setSelectedCourseId(course.id); setClassListPage(1); }}>
                    <span className="coach-attendance-session-title"><strong>{course.name}</strong><span>{course.sportName || "Bộ môn"}</span></span><span className="staff-attendance-class-count">{course.classes.size} lớp học</span>
                  </button>)}</div><nav className="staff-attendance-session-pager" aria-label="Phân trang khóa học"><span>{pagedCourses.length}/{visibleCourses.length} khóa học · Trang {currentCoursePage}/{coursePageCount}</span><div><button type="button" onClick={() => setCoursePage((current) => Math.max(1, current - 1))} disabled={currentCoursePage <= 1}>Trước</button><button type="button" onClick={() => setCoursePage((current) => Math.min(coursePageCount, current + 1))} disabled={currentCoursePage >= coursePageCount}>Sau</button></div></nav></>
                : !selectedClass ? visibleClassOptions.length === 0 ? <p className="coach-attendance-empty">Không tìm thấy lớp học phù hợp.</p>
                  : <><div className="staff-attendance-class-list" aria-label="Các lớp học được phép truy cập">{pagedClassOptions.map((option) => {
                    const optionSessions = visibleSessions.filter((session) => attendanceClassKey(session) === option.key);
                    const sportNames = [...new Set(optionSessions.map((session) => session.sportName).filter(Boolean))];
                    return <button type="button" className="coach-attendance-session staff-attendance-class-card" key={option.key} onClick={() => { setSelectedClassKey(option.key); setSessionPage(1); }}>
                      <span className="coach-attendance-session-title"><strong>{option.name}</strong><span>{option.courseName || "Khóa học"}</span><span>{sportNames.join(" · ") || "Môn thể thao"}</span></span>
                      <span className="staff-attendance-class-count">{optionSessions.length} buổi phù hợp</span>
                    </button>;
                  })}</div><nav className="staff-attendance-session-pager" aria-label="Phân trang lớp học"><span>{pagedClassOptions.length}/{visibleClassOptions.length} lớp · Trang {currentClassListPage}/{classListPageCount}</span><div><button type="button" onClick={() => setClassListPage((current) => Math.max(1, current - 1))} disabled={currentClassListPage <= 1}>Trước</button><button type="button" onClick={() => setClassListPage((current) => Math.min(classListPageCount, current + 1))} disabled={currentClassListPage >= classListPageCount}>Sau</button></div></nav></>
                  : classSessions.length === 0 ? <p className="coach-attendance-empty">Lớp này không có buổi học phù hợp với bộ lọc hiện tại.</p>
                    : <><div className="coach-attendance-sessions staff-attendance-session-list" role="region" aria-label="Buổi học của lớp đã chọn">{pagedSessions.map((session) => {
                      const id = session?.classSessionId;
                      const attendanceLabel = attendanceSessionStatusLabel(session);
                      return <button type="button" className="coach-attendance-session" key={id ?? `${session?.courseClassId}-${session?.startTime}`} onClick={() => id != null && selectSession(id)} disabled={id == null}>
                        <span className="coach-attendance-session-time"><strong>{dateOnly(session?.startTime)}</strong><span>{time(session?.startTime)} – {time(session?.endTime)}</span></span>
                        <span className={`coach-attendance-badge status-${String(session?.sessionStatus || "unknown").toLowerCase()}`}>{SESSION_LABELS[session?.sessionStatus] || session?.sessionStatus || "Chưa xác định"}</span>
                        {attendanceLabel && <span className="coach-attendance-availability">{attendanceLabel}</span>}
                      </button>;
                    })}</div><nav className="staff-attendance-session-pager" aria-label="Phân trang buổi học"><span>Hiển thị {pagedSessions.length}/{classSessions.length} buổi · Trang {currentSessionPage}/{sessionPageCount}</span><div><button type="button" onClick={() => setSessionPage((current) => Math.max(1, current - 1))} disabled={currentSessionPage <= 1}>Trước</button><button type="button" onClick={() => setSessionPage((current) => Math.min(sessionPageCount, current + 1))} disabled={currentSessionPage >= sessionPageCount}>Sau</button></div></nav></>}
          </>}
          {selectedId && <button type="button" className="coach-attendance-history-button staff-attendance-back" onClick={returnToSessions}>Quay lại buổi học</button>}
        </section>}
        <div className={`staff-attendance-workspace${selectedId ? " has-selection" : ""}${role === "ADMIN" && selectedId ? " is-admin-roster" : ""}`}>
        {role !== "ADMIN" && <section className="coach-attendance-panel staff-attendance-session-panel" aria-labelledby="staff-attendance-sessions-title">
          <div className="coach-attendance-section-heading"><div><h2 id="staff-attendance-sessions-title">Danh sách buổi học</h2><p>Hiển thị dữ liệu từ các lớp học hiện có.</p></div><span>{visibleSessions.length}/{sessions.length} buổi</span></div>
          <div className="staff-attendance-filters">
            <label>Tìm theo môn, khóa, lớp hoặc mã buổi<input value={query} onChange={(event) => { setQuery(event.target.value); setSessionPage(1); }} placeholder="Nhập từ khóa…" /></label>
            <label>Trạng thái buổi học<select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setSessionPage(1); }}><option value="">Tất cả</option><option value="SCHEDULED">Đã lên lịch</option><option value="COMPLETED">Đã hoàn thành</option><option value="CANCELLED">Đã hủy</option></select></label>
            <label>Môn thể thao<select value={sportFilter} onChange={(event) => { setSportFilter(event.target.value); setSessionPage(1); }}><option value="">Tất cả môn</option>{sports.map((sport) => <option key={sport.value} value={sport.value}>{sport.label}</option>)}</select></label>
          </div>
          {sessionsLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách buổi học…</p>
            : sessionsError ? null
              : sessions.length === 0 ? <p className="coach-attendance-empty">Chưa có buổi học nào.</p>
                : visibleSessions.length === 0 ? <p className="coach-attendance-empty">Không tìm thấy buổi học phù hợp.</p>
                  : <><div className="coach-attendance-sessions staff-attendance-session-list" role="region" aria-label="Các buổi học có thể chọn">{pagedSessions.map((session) => {
                    const id = session?.classSessionId; const active = String(id) === String(selectedId);
                    return <button type="button" className={`coach-attendance-session${active ? " is-selected" : ""}`} key={id ?? `${session?.courseClassId}-${session?.startTime}`} onClick={() => id != null && selectSession(id)} disabled={id == null} aria-pressed={active}>
                      <span className="coach-attendance-session-title"><strong>{session?.courseName || session?.courseOfferingName || "Khóa học"}</strong><span>{session?.courseClassName || (session?.courseClassId != null ? `Lớp #${session.courseClassId}` : "Chưa có tên lớp")}</span><span>{session?.sportName || (session?.sportId != null ? `Môn #${session.sportId}` : "Chưa có môn")}</span></span>
                      <span className="coach-attendance-session-time"><strong>{dateOnly(session?.startTime)}</strong><span>{time(session?.startTime)} – {time(session?.endTime)}</span></span>
                      <span className={`coach-attendance-badge status-${String(session?.sessionStatus || "unknown").toLowerCase()}`}>{SESSION_LABELS[session?.sessionStatus] || session?.sessionStatus || "Chưa xác định"}</span>
                    </button>;
                  })}</div><nav className="staff-attendance-session-pager" aria-label="Phân trang buổi học"><span>Hiển thị {pagedSessions.length}/{visibleSessions.length} buổi · Trang {currentSessionPage}/{sessionPageCount}</span><div><button type="button" onClick={() => setSessionPage((current) => Math.max(1, current - 1))} disabled={currentSessionPage <= 1}>Trước</button><button type="button" onClick={() => setSessionPage((current) => Math.min(sessionPageCount, current + 1))} disabled={currentSessionPage >= sessionPageCount}>Sau</button></div></nav></>}
        </section>}

        {selectedId && <section className={`coach-attendance-panel staff-attendance-roster-panel${role === "ADMIN" ? " is-admin-dedicated" : ""}`} aria-labelledby="staff-attendance-roster-title">
          <div className="coach-attendance-section-heading"><div><h2 id="staff-attendance-roster-title">Danh sách điểm danh</h2><p>{roster?.courseClassName || selectedSession?.courseClassName || "Lớp học"} · Buổi #{roster?.classSessionId ?? selectedId}</p></div><span>{members.length} thành viên</span></div>
          {rosterLoading ? <p className="coach-attendance-empty" role="status">Đang tải danh sách thành viên…</p>
            : rosterError ? <p className="coach-attendance-alert is-error" role="alert">{rosterError}</p>
              : !roster ? <p className="coach-attendance-empty">Chưa có dữ liệu danh sách điểm danh.</p>
                : <>
                  <div className="coach-attendance-session-meta"><span>{dateOnly(roster.startTime)}</span><span>{time(roster.startTime)} – {time(roster.endTime)}</span><span className={`coach-attendance-badge status-${String(roster.sessionStatus || "unknown").toLowerCase()}`}>{SESSION_LABELS[roster.sessionStatus] || roster.sessionStatus || "Chưa xác định"}</span>{attendanceSessionStatusLabel({ ...roster, attendanceTimeEligible: roster.markingAllowed }) && <span className="coach-attendance-availability">{attendanceSessionStatusLabel({ ...roster, attendanceTimeEligible: roster.markingAllowed })}</span>}</div>
                  {cancelled && <p className="coach-attendance-alert is-warning">Buổi học đã hủy. Chỉ có thể xem danh sách và lịch sử; không thể đánh dấu hoặc điều chỉnh.</p>}
                  {!cancelled && roster.markingAllowed !== true && <p className="coach-attendance-alert is-warning">Máy chủ chưa cho phép điểm danh buổi học này. Bạn chỉ có thể xem dữ liệu.</p>}
                  {members.length === 0 ? <p className="coach-attendance-empty">Không có học viên đã xác nhận đăng ký cho buổi học này.</p>
                    : <>
                      <div className="coach-attendance-stats" aria-label="Tổng hợp trạng thái điểm danh">
                        <div className="stat stat-total"><span>Tổng sĩ số</span><strong>{members.length}</strong></div>
                        <div className="stat stat-present"><span>Có mặt</span><strong>{stats.present}</strong></div>
                        <div className="stat stat-absent"><span>Vắng mặt</span><strong>{stats.absent}</strong></div>
                        {stats.excused > 0 && <div className="stat stat-excused"><span>Có phép</span><strong>{stats.excused}</strong></div>}
                        <div className="stat stat-pending"><span>Chưa điểm danh</span><strong>{stats.pending}</strong></div>
                      </div>
                      <div className="staff-attendance-roster-toolbar">
                        <label>Lọc trạng thái học viên<select value={memberFilter} onChange={(event) => { setMemberFilter(event.target.value); setPage(1); }}>
                          <option value="">Tất cả ({members.length})</option><option value="PRESENT">Có mặt ({stats.present})</option><option value="ABSENT">Vắng mặt ({stats.absent})</option><option value="EXCUSED">Có phép ({stats.excused})</option><option value="NOT_MARKED">Chưa điểm danh ({stats.pending})</option>
                        </select></label>
                        <div className="coach-attendance-toolbar-actions">
                          <button type="button" className="coach-attendance-history-button" onClick={markAllPresent} disabled={!initialSubmission || !roster.markingAllowed || cancelled || busy || members.length === 0}>Đánh dấu tất cả có mặt</button>
                          <button type="button" className="coach-attendance-history-button" onClick={exportCsv} disabled={members.length === 0}>Xuất CSV</button>
                          <button type="button" className="coach-attendance-save-all" onClick={() => void saveAll()} disabled={!roster.markingAllowed || cancelled || busy || (initialSubmission && members.some((member) => {
                            const current = member?.attendanceStatus || "NOT_MARKED";
                            const next = choices[keyFor(selectedId, member?.memberId)] ?? current;
                            return current === "NOT_MARKED" && !["PRESENT", "ABSENT"].includes(next);
                          })) || (!initialSubmission && pendingMembers.length === 0)}>{savingAll ? "Đang gửi…" : initialSubmission ? "Gửi điểm danh" : `Lưu tất cả${pendingMembers.length ? ` (${pendingMembers.length})` : ""}`}</button>
                        </div>
                      </div>
                      <div className="coach-attendance-table-wrap"><table className="coach-attendance-table"><thead><tr><th>Thành viên</th><th>Trạng thái</th><th>Lần đánh dấu</th><th>Ghi chú</th>{showActionColumn && <th>Thao tác</th>}</tr></thead>
                      <tbody>{pagedMembers.length === 0 ? <tr><td colSpan={showActionColumn ? 5 : 4}>Không có thành viên phù hợp bộ lọc.</td></tr> : pagedMembers.map((member) => {
                        const memberId = member?.memberId; const rowKey = keyFor(selectedId, memberId); const current = member?.attendanceStatus || "NOT_MARKED";
                        const next = choices[rowKey] ?? current; const correction = current !== "NOT_MARKED"; const changed = next !== current;
                        const canSave = roster.markingAllowed === true && !initialSubmission && !cancelled && changed && (!correction || String(reasons[rowKey] || "").trim().length > 0);
                        return <Fragment key={memberId ?? member?.memberName}><tr>
                          <td><strong>{member?.memberName || "Thành viên"}</strong><small>ID: {memberId ?? "—"}</small></td>
                          <td><span className={`coach-attendance-badge attendance-${String(current).toLowerCase()}`}>{labelStatus(current)}</span><label className="coach-attendance-select-label" htmlFor={`staff-attendance-${memberId}`}>Trạng thái cập nhật</label>
                            <select id={`staff-attendance-${memberId}`} value={next} onChange={(event) => editRow(setChoices, rowKey, event.target.value)} disabled={!roster.markingAllowed || cancelled || busy || (initialSubmission && correction)}>
                              {[...(current === "EXCUSED" ? ["EXCUSED"] : []), "NOT_MARKED", "PRESENT", "ABSENT"].map((status) => <option key={status} value={status} disabled={status === "NOT_MARKED"}>{labelStatus(status)}</option>)}
                            </select>
                            {correction && changed && <label className="coach-attendance-reason"><span>Lý do điều chỉnh <b aria-hidden="true">*</b></span><input maxLength={500} value={reasons[rowKey] ?? ""} onChange={(event) => editRow(setReasons, rowKey, event.target.value)} disabled={busy} placeholder="Nhập lý do điều chỉnh" /></label>}
                          </td>
                          <td>{member?.markedAt ? <><span>{dateTime(member.markedAt)}</span><small>Người thực hiện ID: {member?.markedById ?? "—"}</small></> : <span>Chưa đánh dấu</span>}</td>
                          <td><textarea aria-label={`Ghi chú điểm danh thành viên ${memberId ?? ""}`} maxLength={500} value={notes[rowKey] ?? member?.note ?? ""} onChange={(event) => editRow(setNotes, rowKey, event.target.value)} disabled={!roster.markingAllowed || cancelled || busy} placeholder="Ghi chú không bắt buộc" /></td>
                            {showActionColumn && <td><div className="coach-attendance-row-actions">{!initialSubmission && <button type="button" className="coach-attendance-save" disabled={!canSave || busy} onClick={() => void save(member)}>{savingKey === rowKey ? "Đang lưu…" : "Lưu điều chỉnh"}</button>}
                            {member?.attendanceId != null && <button type="button" className="coach-attendance-history-button"  onClick={() => void openHistory(member.attendanceId)}>Lịch sử</button>}
                            </div></td>}
                        </tr>{rowErrors[rowKey] && <tr className="coach-attendance-error-row"><td colSpan={showActionColumn ? 5 : 4}><small className="coach-attendance-row-error" role="alert">{rowErrors[rowKey]}</small></td></tr>}</Fragment>;
                      })}</tbody>
                    </table></div>
                    <div className="coach-attendance-footer"><span>Hiển thị {pagedMembers.length}/{filteredMembers.length} học viên</span><nav className="staff-attendance-pager" aria-label="Phân trang danh sách học viên">
                      <button type="button" onClick={() => setPage(currentPage - 1)} disabled={currentPage <= 1}>Trước</button><span>Trang {currentPage}/{pageCount}</span><button type="button" onClick={() => setPage(currentPage + 1)} disabled={currentPage >= pageCount}>Sau</button>
                    </nav></div>
                    </>}
                  <p className="coach-attendance-footnote">Không tự động ghi vắng mặt. Thời gian lịch học được hiển thị theo giờ địa phương của thiết bị; máy chủ quyết định quyền điểm danh.</p>
                </>}
        </section>}
        </div>

        {historyOpen && <section className="coach-attendance-panel coach-attendance-history" aria-labelledby="staff-attendance-history-title"><div className="coach-attendance-section-heading"><h2 id="staff-attendance-history-title">Lịch sử điểm danh</h2><button type="button" className="coach-attendance-history-button" onClick={closeHistory}>Đóng</button></div>
          {historyLoading ? <p role="status">Đang tải lịch sử…</p> : historyError ? <p className="coach-attendance-alert is-error" role="alert">{historyError}</p> : history.length === 0 ? <p className="coach-attendance-empty">Chưa có sự kiện lịch sử.</p> : <ol className="coach-attendance-history-list">{history.map((event) => <li key={event?.id ?? `${event?.changedAt}-${event?.newStatus}`}><strong>{event?.oldStatus ? `${labelStatus(event.oldStatus)} → ${labelStatus(event.newStatus)}` : `Đánh dấu ban đầu: ${labelStatus(event?.newStatus)}`}</strong><span>{dateTime(event?.changedAt)} · Người thực hiện ID: {event?.changedById ?? "—"}</span>{event?.reason && <p>Lý do: {event.reason}</p>}</li>)}</ol>}
        </section>}
      </div>
      </div>
    </main>
  </div>;
}
