import { useCallback, useEffect, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import AdminTopBar from "../components/AdminTopBar";
import IndividualTrainingPlans from "../components/IndividualTrainingPlans";
import GeneralTrainingPlanTab from "../components/GeneralTrainingPlanTab.jsx";
import AdminPtOutcomePanel from "./AdminPtOutcomePanel.jsx";
import { acquireAction } from "../courseCheckout";
import { canManageTrainingForRole } from "../trainingCapabilities";
import { progressLevelLabel } from "../trainingPresentation.js";
import {
  completeTrainingSessionApi,
  evaluateProgressApi,
  getTrainingClassesApi,
  getTrainingProgressApi,
  getTrainingResultSheetApi,
  getTrainingSessionsApi,
  recordTrainingResultApi,
} from "../api";
import "./Training.css";

const SESSION_STATUS = { SCHEDULED: "Đã lên lịch", COMPLETED: "Đã hoàn thành", CANCELLED: "Đã hủy" };
const ATTENDANCE = { NOT_MARKED: "Chưa điểm danh", PRESENT: "Có mặt", ABSENT: "Vắng", EXCUSED: "Có phép" };

const formatDateTime = (value) => value
  ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(value))
  : "—";
const errorText = (error, fallback) => error?.status === 403
  ? "Bạn không có quyền thao tác trên lớp này."
  : error?.message || fallback;

export default function CoachTraining({ token, role, currentUserName, onLogout, onNavigate }) {
  const canManageTraining = canManageTrainingForRole(role);
  const isAdmin = role === "ADMIN";
  const [classes, setClasses] = useState([]);
  const [classId, setClassId] = useState("");
  const [selectedCourseId, setSelectedCourseId] = useState("");
  const [courseQuery, setCourseQuery] = useState("");
  const [classQuery, setClassQuery] = useState("");
  const [selectionSport, setSelectionSport] = useState("");
  const [selectionFrom, setSelectionFrom] = useState("");
  const [selectionTo, setSelectionTo] = useState("");
  const [courseSelectionPage, setCourseSelectionPage] = useState(1);
  const [classSelectionPage, setClassSelectionPage] = useState(1);
  const [tab, setTab] = useState("plan");
  const [trainingDraftDirty, setTrainingDraftDirty] = useState(false);
  const [adminTrainingView, setAdminTrainingView] = useState("class");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const loadClasses = useCallback(async () => {
    setLoading(true);
    try {
      const rows = await getTrainingClassesApi(token);
      setClasses(Array.isArray(rows) ? rows : []);
      setClassId((current) => rows?.some((row) => String(row.classId) === current) ? current : "");
    } catch (requestError) {
      setError(errorText(requestError, "Không tải được danh sách lớp."));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => active && loadClasses());
    return () => { active = false; };
  }, [loadClasses]);

  const selected = classes.find((item) => String(item.classId) === classId);
  function mayLeaveTrainingDraft() {
    if (trainingDraftDirty && !window.confirm("Bạn có thay đổi giáo án chưa lưu. Tiếp tục và bỏ các thay đổi này?")) return false;
    setTrainingDraftDirty(false);
    return true;
  }
  function changeWorkspaceTab(nextTab) {
    if (!mayLeaveTrainingDraft()) return;
    setTab(nextTab);
  }
  const selectionPage = selectedCourseId ? classSelectionPage : courseSelectionPage;
  const setSelectionPage = (update) => selectedCourseId ? setClassSelectionPage(update) : setCourseSelectionPage(update);
  const courses = [...new Map(classes.map((item) => [String(item.courseId), item])).values()];
  const sports = [...new Map(classes.map((item) => [String(item.sportId), item])).values()];
  const courseTerm = courseQuery.trim().toLocaleLowerCase("vi");
  const classTerm = classQuery.trim().toLocaleLowerCase("vi");
  const visibleCourses = courses.filter((item) => (!courseTerm || `${item.courseName} ${item.courseId}`.toLocaleLowerCase("vi").includes(courseTerm))
    && (!selectionSport || String(item.sportId) === selectionSport));
  const visibleClasses = classes.filter((item) => String(item.courseId) === selectedCourseId
    && (!classTerm || `${item.className} ${item.classId}`.toLocaleLowerCase("vi").includes(classTerm))
    && (!selectionFrom || !item.endDate || item.endDate >= selectionFrom)
    && (!selectionTo || !item.startDate || item.startDate <= selectionTo));
  const selectionPages = Math.max(1, Math.ceil((selectedCourseId ? visibleClasses.length : visibleCourses.length) / 10));
  const pageRows = (selectedCourseId ? visibleClasses : visibleCourses).slice((Math.min(selectionPage, selectionPages) - 1) * 10, Math.min(selectionPage, selectionPages) * 10);
  const flash = (message) => { setError(""); setNotice(message); };
  const fail = (requestError, fallback) => { setNotice(""); setError(errorText(requestError, fallback)); };

  return <div className="layout">
    <Sidebar role={role} activePage={role === "ADMIN" ? "admin-training" : "coach-training"} onLogout={onLogout} onNavigate={onNavigate} />
    <main className={`main training-page${isAdmin ? " training-page--admin" : ""}`}>
      {isAdmin && <AdminTopBar pageLabel="GIÁM SÁT HUẤN LUYỆN" displayName={currentUserName} avatarName={currentUserName} />}
      <div className={isAdmin ? "training-admin-content" : "training-coach-content"}>
      <header className="training-header">
        <div>
          <h1>Giáo án &amp; kết quả</h1>
          <p className="subtitle">{canManageTraining ? "Lập giáo án, mục tiêu và bài tập cho lớp được giao; hoàn thành buổi học, ghi kết quả và đánh giá tiến độ." : "Chế độ chỉ xem: theo dõi giáo án, buổi học, kết quả và tiến độ của lớp."}</p>
        </div>
      </header>
      {isAdmin && <nav className="training-admin-view-tabs" role="tablist" aria-label="Khu vực giám sát huấn luyện">
        <button type="button" role="tab" aria-selected={adminTrainingView === "class"} className={adminTrainingView === "class" ? "active" : ""} onClick={() => setAdminTrainingView("class")}>Giáo án &amp; kết quả lớp học</button>
        <button type="button" role="tab" aria-selected={adminTrainingView === "pt"} className={adminTrainingView === "pt" ? "active" : ""} onClick={() => setAdminTrainingView("pt")}>Giám sát huấn luyện cá nhân</button>
      </nav>}
      {error && <div className="banner error" role="alert">{error}</div>}
      {notice && <div className="banner success" role="status">{notice}</div>}

      {isAdmin && adminTrainingView === "pt" ? <section className="training-admin-pt-supervision" aria-label="Giám sát kết quả PT">
        <AdminPtOutcomePanel token={token} />
      </section> : null}

      {(!isAdmin || adminTrainingView === "class") && (loading ? <p role="status">Đang tải…</p> : !selected ? <section className="training-selection training-content-frame">
        <div className="training-selection-heading"><div>
          <h2>{selectedCourseId ? "Chọn lớp học" : "Chọn khóa học"}</h2>
          <p>{selectedCourseId ? `Khóa học: ${courses.find((item) => String(item.courseId) === selectedCourseId)?.courseName || "Khóa học đã chọn"}` : "Chọn khóa học để xem danh sách lớp học."}</p>
        </div><span>{selectedCourseId ? `${visibleClasses.length} lớp học` : `${visibleCourses.length} khóa học`}</span></div>
        {selectedCourseId && <div className="training-selection-back-row"><button type="button" className="secondary" onClick={() => setSelectedCourseId("")}>Quay lại khóa học</button></div>}
        <div className={`training-selection-filters${selectedCourseId ? " has-class-filters" : " has-course-filters"}`}>
          <label>{selectedCourseId ? "Tìm lớp học" : "Tìm khóa học"}<input value={selectedCourseId ? classQuery : courseQuery} onChange={(event) => { (selectedCourseId ? setClassQuery : setCourseQuery)(event.target.value); setSelectionPage(1); }} /></label>
          {!selectedCourseId && <label>Bộ môn<select value={selectionSport} onChange={(event) => { setSelectionSport(event.target.value); setSelectionPage(1); }}><option value="">Tất cả bộ môn</option>{sports.map((item) => <option key={item.sportId} value={item.sportId}>{item.sportName}</option>)}</select></label>}
          {selectedCourseId && <><label>Từ ngày<input type="date" value={selectionFrom} onChange={(event) => { setSelectionFrom(event.target.value); setSelectionPage(1); }} /></label><label>Đến ngày<input type="date" value={selectionTo} onChange={(event) => { setSelectionTo(event.target.value); setSelectionPage(1); }} /></label></>}
        </div>
        {pageRows.length === 0 ? <p className="dash-empty">Không tìm thấy dữ liệu phù hợp.</p> : <div className="training-selection-list">{pageRows.map((item) => <button type="button" key={selectedCourseId ? item.classId : item.courseId} onClick={() => { if (selectedCourseId) { setClassId(String(item.classId)); setTab("plan"); } else { setSelectedCourseId(String(item.courseId)); setClassSelectionPage(1); } }}>
          <strong>{selectedCourseId ? item.className : item.courseName}</strong><span>{item.sportName} · {selectedCourseId ? `${item.courseOfferingName || "Đợt học"} · ${item.confirmedMembers} học viên` : `${classes.filter((row) => String(row.courseId) === String(item.courseId)).length} lớp học`}</span>
        </button>)}</div>}
        <nav className="training-selection-pager"><span>{selectedCourseId ? visibleClasses.length : visibleCourses.length} mục · Trang {Math.min(selectionPage, selectionPages)}/{selectionPages}</span><button type="button" disabled={selectionPage <= 1} onClick={() => setSelectionPage((page) => Math.max(1, page - 1))}>Trước</button><button type="button" disabled={selectionPage >= selectionPages} onClick={() => setSelectionPage((page) => Math.min(selectionPages, page + 1))}>Sau</button></nav>
      </section> : <section className="training-workspace-frame training-content-frame">
        <div className="training-workspace-heading"><div><h2>{selected.className}</h2><p><strong>Khóa học:</strong> {selected.courseName} <span>·</span> <strong>Lớp:</strong> {selected.className}</p></div></div>
        <div className="training-workspace-back-row"><button type="button" className="secondary" onClick={() => { if (mayLeaveTrainingDraft()) setClassId(""); }}>Quay lại danh sách lớp</button></div>
        <section className="training-summary">
          <span><b>{selected.sportName}</b> · {selected.courseOfferingName}</span>
          <span>{selected.confirmedMembers} học viên</span>
          <span>{selected.completedSessions}/{selected.totalSessions} buổi đã hoàn thành</span>
          <span>{selected.hasPlan ? `Mục tiêu: ${selected.planGoal}` : "Chưa có giáo án"}</span>
        </section>
        <nav className="training-tabs" role="tablist">
          {[["plan", "1. Giáo án & bài tập"], ["sessions", "2. Buổi học & kết quả"], ["progress", "3. Đánh giá tiến độ"], ["individual", "4. Giáo án cá nhân"]].map(([key, label]) =>
            <button key={key} type="button" role="tab" aria-selected={tab === key} className={tab === key ? "active" : ""} onClick={() => changeWorkspaceTab(key)}>{label}</button>)}
        </nav>
        {tab === "plan" && <PlanTab key={classId} token={token} classId={classId} classInfo={selected} canManageTraining={canManageTraining} onSaved={loadClasses} flash={flash} fail={fail} onDraftDirtyChange={setTrainingDraftDirty} />}
        {tab === "sessions" && <SessionsTab key={classId} token={token} classId={classId} canManageTraining={canManageTraining} onChanged={loadClasses} flash={flash} fail={fail} />}
        {tab === "progress" && <ProgressTab key={classId} token={token} classId={classId} canManageTraining={canManageTraining} flash={flash} fail={fail} />}
        {tab === "individual" && <IndividualTrainingPlans key={classId} mode={canManageTraining ? "coach" : "admin-readonly"} canManageTraining={canManageTraining} token={token} classId={classId} />}
      </section>)}
      </div>
    </main>
  </div>;
}

function PlanTab(props) { return <GeneralTrainingPlanTab {...props}/>; }

function SessionsTab({ token, classId, canManageTraining, onChanged, flash, fail }) {
  const [sessions, setSessions] = useState(null);
  const [sheet, setSheet] = useState(null);
  const [drafts, setDrafts] = useState({});
  const [busy, setBusy] = useState(false);
  const actionLock = useRef(false);

  const loadSessions = useCallback(async () => {
    try { setSessions(await getTrainingSessionsApi(token, classId)); }
    catch (requestError) { setSessions([]); fail(requestError, "Không tải được buổi học."); }
  }, [token, classId, fail]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => active && loadSessions());
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, classId]);

  async function openSheet(sessionId) {
    try {
      const data = await getTrainingResultSheetApi(token, sessionId);
      setSheet(data);
      setDrafts(Object.fromEntries(data.members.map((row) => [row.memberId, { score: row.score ?? "", notes: row.notes ?? "" }])));
    } catch (requestError) { fail(requestError, "Không tải được bảng kết quả."); }
  }

  async function complete(session) {
    if (!canManageTraining) return;
    if (!window.confirm("Xác nhận buổi học đã được tiến hành xong?")) return;
    if (!acquireAction(actionLock)) return;
    setBusy(true);
    try {
      await completeTrainingSessionApi(token, session.sessionId);
      flash("Đã đánh dấu buổi học hoàn thành.");
      await loadSessions();
      onChanged();
    } catch (requestError) { fail(requestError, "Không hoàn thành được buổi học."); }
    finally { actionLock.current = false; setBusy(false); }
  }

  async function saveResult(row) {
    if (!canManageTraining) return;
    const draft = drafts[row.memberId] || {};
    if (!acquireAction(actionLock)) return;
    setBusy(true);
    try {
      await recordTrainingResultApi(token, sheet.sessionId, row.memberId, { score: Number(draft.score), notes: draft.notes || null });
      flash(`Đã lưu kết quả cho ${row.memberName}.`);
      await openSheet(sheet.sessionId);
      await loadSessions();
    } catch (requestError) { fail(requestError, "Không lưu được kết quả."); }
    finally { actionLock.current = false; setBusy(false); }
  }

  if (!sessions) return <p role="status">Đang tải buổi học…</p>;
  return <div className="training-grid">
    <section className="dash-card">
      <h2>Buổi học <small>Điểm danh tại mục “Điểm danh” trước khi ghi kết quả</small></h2>
      {sessions.length === 0 ? <p className="dash-empty">Lớp chưa có buổi học.</p>
        : <ul className="training-sessions">{sessions.map((session) => <li key={session.sessionId} className={sheet?.sessionId === session.sessionId ? "selected" : ""}>
          <div>
            <b>{formatDateTime(session.startTime)}</b>
            <small>{SESSION_STATUS[session.status] || session.status} · {session.presentCount} có mặt · {session.resultCount} kết quả</small>
          </div>
          <span className="training-actions">
            {canManageTraining && session.canComplete && <button type="button" disabled={busy} onClick={() => void complete(session)}>Hoàn thành</button>}
            <button type="button" className="secondary" disabled={canManageTraining && !session.canRecordResults} onClick={() => void openSheet(session.sessionId)}>
              {canManageTraining ? (session.canRecordResults ? "Ghi kết quả" : "Chưa diễn ra") : "Xem kết quả"}
            </button>
          </span>
        </li>)}</ul>}
    </section>

    <section className="dash-card">
      <h2>Kết quả buổi học</h2>
        {!sheet ? <p className="dash-empty">Chọn một buổi học để xem kết quả.</p> : <>
        <p className="training-muted">{formatDateTime(sheet.startTime)} – {formatDateTime(sheet.endTime)} · {SESSION_STATUS[sheet.status] || sheet.status}</p>
        {sheet.members.length === 0 ? <p className="dash-empty">Lớp chưa có học viên đã xác nhận.</p>
          : <table className="training-table">
            <thead><tr><th>Học viên</th><th>Điểm danh</th><th>Điểm (1–10)</th><th>Nhận xét</th>{canManageTraining && <th />}</tr></thead>
            <tbody>{sheet.members.map((row) => {
              const present = row.attendanceStatus === "PRESENT";
              const draft = drafts[row.memberId] || { score: "", notes: "" };
              return <tr key={row.memberId}>
                <td>{row.memberName}</td>
                <td><span className={`training-badge ${row.attendanceStatus.toLowerCase()}`}>{ATTENDANCE[row.attendanceStatus] || row.attendanceStatus}</span></td>
                <td>{canManageTraining ? <input type="number" min={1} max={10} disabled={!present || busy} value={draft.score}
                  onChange={(event) => setDrafts({ ...drafts, [row.memberId]: { ...draft, score: event.target.value } })} /> : row.score ?? "—"}</td>
                <td>{canManageTraining ? <input maxLength={1000} disabled={!present || busy} value={draft.notes}
                  onChange={(event) => setDrafts({ ...drafts, [row.memberId]: { ...draft, notes: event.target.value } })} /> : row.notes || "—"}</td>
                {canManageTraining && <td>{present
                  ? <button type="button" disabled={busy || !draft.score} onClick={() => void saveResult(row)}>{row.resultId ? "Cập nhật" : "Lưu"}</button>
                  : <small className="training-muted">Cần có mặt</small>}</td>
                }
              </tr>;
            })}</tbody>
          </table>}
      </>}
    </section>
  </div>;
}

function ProgressTab({ token, classId, canManageTraining, flash, fail }) {
  const [rows, setRows] = useState(null);
  const [drafts, setDrafts] = useState({});
  const [busy, setBusy] = useState(false);
  const actionLock = useRef(false);

  const load = useCallback(async () => {
    try { setRows(await getTrainingProgressApi(token, classId)); }
    catch (requestError) { setRows([]); fail(requestError, "Không tải được tiến độ."); }
  }, [token, classId, fail]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => active && load());
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, classId]);

  async function evaluate(row) {
    if (!canManageTraining) return;
    const draft = drafts[row.memberId] || {};
    if (!acquireAction(actionLock)) return;
    setBusy(true);
    try {
      await evaluateProgressApi(token, classId, row.memberId, { rating: Number(draft.rating), comment: draft.comment });
      flash(`Đã ghi đánh giá tiến độ cho ${row.memberName}.`);
      setDrafts({ ...drafts, [row.memberId]: { rating: "", comment: "" } });
      await load();
    } catch (requestError) { fail(requestError, "Không lưu được đánh giá."); }
    finally { actionLock.current = false; setBusy(false); }
  }

  if (!rows) return <p role="status">Đang tải tiến độ…</p>;
  if (rows.length === 0) return <p className="dash-empty">Lớp chưa có học viên đã xác nhận.</p>;
  return <div className="training-progress">{rows.map((row) => {
    const draft = drafts[row.memberId] || { rating: "", comment: "" };
    return <section key={row.memberId} className="dash-card">
      <h2>{row.memberName} <small>Có mặt {row.sessionsPresent}/{row.sessionsMarked} buổi đã điểm danh · {row.resultsRecorded} kết quả · Điểm TB {row.averageScore ?? "—"}</small></h2>
      {row.latestEvaluation
        ? <p><strong>{row.latestEvaluation.rating}/5 · {progressLevelLabel(row.latestEvaluation.rating)}</strong> <Stars value={row.latestEvaluation.rating} /> {row.latestEvaluation.comment} <small className="training-muted">({formatDateTime(row.latestEvaluation.evaluatedAt)})</small></p>
        : <p className="training-muted">Chưa có đánh giá tiến độ.</p>}
      {row.evaluationHistory.length > 1 && <details><summary>Lịch sử đánh giá ({row.evaluationHistory.length})</summary>
        <ul>{row.evaluationHistory.map((item) => <li key={item.id}><b>{item.rating}/5 · {progressLevelLabel(item.rating)}</b> <Stars value={item.rating} /> {item.comment} — {formatDateTime(item.evaluatedAt)}</li>)}</ul>
      </details>}
      {canManageTraining && <form className="training-inline" onSubmit={(event) => { event.preventDefault(); void evaluate(row); }}>
        <label>Mức tiến độ
          <select required value={draft.rating} onChange={(event) => setDrafts({ ...drafts, [row.memberId]: { ...draft, rating: event.target.value } })}>
            <option value="">Chọn</option>
            <option value="1">1 · Chưa đạt</option><option value="2">2 · Cần cố gắng</option>
            <option value="3">3 · Đúng tiến độ</option><option value="4">4 · Tốt</option><option value="5">5 · Vượt mục tiêu</option>
          </select>
        </label>
        <label className="grow">Nhận xét
          <input required maxLength={1000} value={draft.comment} onChange={(event) => setDrafts({ ...drafts, [row.memberId]: { ...draft, comment: event.target.value } })} />
        </label>
        <button type="submit" disabled={busy}>Ghi đánh giá</button>
      </form>}
    </section>;
  })}</div>;
}

export function Stars({ value }) {
  return <span className="training-stars" aria-label={`${value}/5`}>{"★".repeat(value)}{"☆".repeat(5 - value)}</span>;
}
