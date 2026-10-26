import { useCallback, useEffect, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import { acquireAction } from "../courseCheckout";
import {
  addTrainingExerciseApi,
  completeTrainingSessionApi,
  deleteTrainingExerciseApi,
  evaluateProgressApi,
  getTrainingClassesApi,
  getTrainingPlanApi,
  getTrainingProgressApi,
  getTrainingResultSheetApi,
  getTrainingSessionsApi,
  recordTrainingResultApi,
  saveTrainingPlanApi,
  updateTrainingExerciseApi,
} from "../api";
import "./Training.css";

const SESSION_STATUS = { SCHEDULED: "Đã lên lịch", COMPLETED: "Đã hoàn thành", CANCELLED: "Đã hủy" };
const ATTENDANCE = { NOT_MARKED: "Chưa điểm danh", PRESENT: "Có mặt", ABSENT: "Vắng", EXCUSED: "Có phép" };
const EMPTY_EXERCISE = { name: "", sets: "", reps: "", durationMinutes: "", notes: "" };

const formatDateTime = (value) => value
  ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(value))
  : "—";
const numberOrNull = (value) => value === "" || value == null ? null : Number(value);
const errorText = (error, fallback) => error?.status === 403
  ? "Bạn không có quyền thao tác trên lớp này."
  : error?.message || fallback;

export default function CoachTraining({ token, role, onLogout, onNavigate }) {
  const [classes, setClasses] = useState([]);
  const [classId, setClassId] = useState("");
  const [tab, setTab] = useState("plan");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const loadClasses = useCallback(async () => {
    setLoading(true);
    try {
      const rows = await getTrainingClassesApi(token);
      setClasses(Array.isArray(rows) ? rows : []);
      setClassId((current) => current || (rows?.[0]?.classId ? String(rows[0].classId) : ""));
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
  const flash = (message) => { setError(""); setNotice(message); };
  const fail = (requestError, fallback) => { setNotice(""); setError(errorText(requestError, fallback)); };

  return <div className="layout">
    <Sidebar role={role} activePage={role === "ADMIN" ? "admin-training" : "coach-training"} onLogout={onLogout} onNavigate={onNavigate} />
    <main className="main training-page">
      <header className="training-header">
        <div>
          <h1>Giáo án &amp; kết quả</h1>
          <p className="subtitle">Lập giáo án, mục tiêu và bài tập cho lớp được giao; hoàn thành buổi học, ghi kết quả và đánh giá tiến độ.</p>
        </div>
        <label className="training-class-picker">Lớp
          <select value={classId} onChange={(event) => { setClassId(event.target.value); setNotice(""); setError(""); }} disabled={loading || !classes.length}>
            {!classes.length && <option value="">Chưa có lớp được giao</option>}
            {classes.map((item) => <option key={item.classId} value={item.classId}>
              {item.className} · {item.courseName}
            </option>)}
          </select>
        </label>
      </header>
      {error && <div className="banner error" role="alert">{error}</div>}
      {notice && <div className="banner success" role="status">{notice}</div>}

      {loading ? <p role="status">Đang tải…</p> : !selected ? <p className="dash-empty">Bạn chưa được giao lớp nào.</p> : <>
        <section className="training-summary">
          <span><b>{selected.sportName}</b> · {selected.courseOfferingName}</span>
          <span>{selected.confirmedMembers} học viên</span>
          <span>{selected.completedSessions}/{selected.totalSessions} buổi đã hoàn thành</span>
          <span>{selected.hasPlan ? `Mục tiêu: ${selected.planGoal}` : "Chưa có giáo án"}</span>
        </section>
        <nav className="training-tabs" role="tablist">
          {[["plan", "1. Giáo án & bài tập"], ["sessions", "2. Buổi học & kết quả"], ["progress", "3. Đánh giá tiến độ"]].map(([key, label]) =>
            <button key={key} type="button" role="tab" aria-selected={tab === key} className={tab === key ? "active" : ""} onClick={() => setTab(key)}>{label}</button>)}
        </nav>
        {tab === "plan" && <PlanTab key={classId} token={token} classId={classId} onSaved={loadClasses} flash={flash} fail={fail} />}
        {tab === "sessions" && <SessionsTab key={classId} token={token} classId={classId} onChanged={loadClasses} flash={flash} fail={fail} />}
        {tab === "progress" && <ProgressTab key={classId} token={token} classId={classId} flash={flash} fail={fail} />}
      </>}
    </main>
  </div>;
}

function PlanTab({ token, classId, onSaved, flash, fail }) {
  const [plan, setPlan] = useState(null);
  const [form, setForm] = useState({ goal: "", description: "" });
  const [exercise, setExercise] = useState(EMPTY_EXERCISE);
  const [editingId, setEditingId] = useState(null);
  const [busy, setBusy] = useState(false);
  const actionLock = useRef(false);
  const [loaded, setLoaded] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await getTrainingPlanApi(token, classId);
      setPlan(data);
      setForm({ goal: data.goal || "", description: data.description || "" });
    } catch (requestError) {
      if (requestError.status === 404) { setPlan(null); setForm({ goal: "", description: "" }); }
      else fail(requestError, "Không tải được giáo án.");
    } finally {
      setLoaded(true);
    }
  }, [token, classId, fail]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => active && load());
    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token, classId]);

  async function savePlan(event) {
    event.preventDefault();
    if (!acquireAction(actionLock)) return;
    setBusy(true);
    try {
      const saved = await saveTrainingPlanApi(token, classId, { goal: form.goal, description: form.description || null });
      setPlan(saved);
      flash(plan ? "Đã cập nhật giáo án." : "Đã tạo giáo án. Bạn có thể thêm bài tập.");
      onSaved();
    } catch (requestError) { fail(requestError, "Không lưu được giáo án."); }
    finally { actionLock.current = false; setBusy(false); }
  }

  async function saveExercise(event) {
    event.preventDefault();
    if (!acquireAction(actionLock)) return;
    setBusy(true);
    const payload = {
      name: exercise.name,
      sets: numberOrNull(exercise.sets),
      reps: numberOrNull(exercise.reps),
      durationMinutes: numberOrNull(exercise.durationMinutes),
      notes: exercise.notes || null,
      sortOrder: editingId ? plan.exercises.find((item) => item.id === editingId)?.sortOrder : null,
    };
    try {
      if (editingId) await updateTrainingExerciseApi(token, editingId, payload);
      else await addTrainingExerciseApi(token, classId, payload);
      flash(editingId ? "Đã sửa bài tập." : "Đã thêm bài tập.");
      setExercise(EMPTY_EXERCISE);
      setEditingId(null);
      await load();
    } catch (requestError) { fail(requestError, "Không lưu được bài tập."); }
    finally { actionLock.current = false; setBusy(false); }
  }

  async function removeExercise(item) {
    if (!window.confirm(`Xóa bài tập “${item.name}”?`)) return;
    if (!acquireAction(actionLock)) return;
    setBusy(true);
    try {
      await deleteTrainingExerciseApi(token, item.id);
      flash("Đã xóa bài tập.");
      await load();
    } catch (requestError) { fail(requestError, "Không xóa được bài tập."); }
    finally { actionLock.current = false; setBusy(false); }
  }

  function edit(item) {
    setEditingId(item.id);
    setExercise({
      name: item.name, sets: item.sets ?? "", reps: item.reps ?? "",
      durationMinutes: item.durationMinutes ?? "", notes: item.notes ?? "",
    });
  }

  if (!loaded) return <p role="status">Đang tải giáo án…</p>;
  return <div className="training-grid">
    <section className="dash-card">
      <h2>Giáo án &amp; mục tiêu</h2>
      <form className="training-form" onSubmit={savePlan}>
        <label>Mục tiêu tập luyện *
          <input required maxLength={500} value={form.goal} onChange={(event) => setForm({ ...form, goal: event.target.value })}
            placeholder="Ví dụ: Đánh cầu qua lại liên tục 20 lần" />
        </label>
        <label>Mô tả giáo án
          <textarea maxLength={2000} rows={4} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })}
            placeholder="Nội dung chính, phương pháp, lưu ý an toàn…" />
        </label>
        <button type="submit" disabled={busy}>{plan ? "Cập nhật giáo án" : "Tạo giáo án"}</button>
        {plan && <small className="training-muted">Cập nhật lần cuối {formatDateTime(plan.updatedAt)} bởi {plan.updatedByName || `#${plan.updatedById}`}</small>}
      </form>
    </section>

    <section className="dash-card">
      <h2>Bài tập <small>{plan ? `${plan.exercises.length} bài` : ""}</small></h2>
      {!plan ? <p className="dash-empty">Tạo giáo án trước khi thêm bài tập.</p> : <>
        {plan.exercises.length === 0 ? <p className="dash-empty">Chưa có bài tập.</p>
          : <ol className="training-exercises">{plan.exercises.map((item) => <li key={item.id}>
            <div>
              <b>{item.name}</b>
              <small>{[item.sets && `${item.sets} hiệp`, item.reps && `${item.reps} lần`, item.durationMinutes && `${item.durationMinutes} phút`].filter(Boolean).join(" · ") || "—"}</small>
              {item.notes && <small>{item.notes}</small>}
            </div>
            <span>
              <button type="button" className="training-link-button" disabled={busy} onClick={() => edit(item)}>Sửa</button>
              <button type="button" className="training-link-button danger" disabled={busy} onClick={() => void removeExercise(item)}>Xóa</button>
            </span>
          </li>)}</ol>}
        <form className="training-form training-exercise-form" onSubmit={saveExercise}>
          <h3>{editingId ? "Sửa bài tập" : "Thêm bài tập"}</h3>
          <label>Tên bài tập *<input required maxLength={150} value={exercise.name} onChange={(event) => setExercise({ ...exercise, name: event.target.value })} /></label>
          <div className="training-inline">
            <label>Số hiệp<input type="number" min={1} max={100} value={exercise.sets} onChange={(event) => setExercise({ ...exercise, sets: event.target.value })} /></label>
            <label>Số lần<input type="number" min={1} max={1000} value={exercise.reps} onChange={(event) => setExercise({ ...exercise, reps: event.target.value })} /></label>
            <label>Phút<input type="number" min={1} max={240} value={exercise.durationMinutes} onChange={(event) => setExercise({ ...exercise, durationMinutes: event.target.value })} /></label>
          </div>
          <label>Ghi chú<input maxLength={500} value={exercise.notes} onChange={(event) => setExercise({ ...exercise, notes: event.target.value })} /></label>
          <span className="training-actions">
            <button type="submit" disabled={busy}>{editingId ? "Lưu bài tập" : "Thêm bài tập"}</button>
            {editingId && <button type="button" className="secondary" onClick={() => { setEditingId(null); setExercise(EMPTY_EXERCISE); }}>Hủy sửa</button>}
          </span>
        </form>
      </>}
    </section>
  </div>;
}

function SessionsTab({ token, classId, onChanged, flash, fail }) {
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
            {session.canComplete && <button type="button" disabled={busy} onClick={() => void complete(session)}>Hoàn thành</button>}
            <button type="button" className="secondary" disabled={!session.canRecordResults} onClick={() => void openSheet(session.sessionId)}>
              {session.canRecordResults ? "Ghi kết quả" : "Chưa diễn ra"}
            </button>
          </span>
        </li>)}</ul>}
    </section>

    <section className="dash-card">
      <h2>Kết quả buổi học</h2>
      {!sheet ? <p className="dash-empty">Chọn một buổi đã diễn ra để ghi kết quả.</p> : <>
        <p className="training-muted">{formatDateTime(sheet.startTime)} – {formatDateTime(sheet.endTime)} · {SESSION_STATUS[sheet.status] || sheet.status}</p>
        {sheet.members.length === 0 ? <p className="dash-empty">Lớp chưa có học viên đã xác nhận.</p>
          : <table className="training-table">
            <thead><tr><th>Học viên</th><th>Điểm danh</th><th>Điểm (1–10)</th><th>Nhận xét</th><th /></tr></thead>
            <tbody>{sheet.members.map((row) => {
              const present = row.attendanceStatus === "PRESENT";
              const draft = drafts[row.memberId] || { score: "", notes: "" };
              return <tr key={row.memberId}>
                <td>{row.memberName}</td>
                <td><span className={`training-badge ${row.attendanceStatus.toLowerCase()}`}>{ATTENDANCE[row.attendanceStatus] || row.attendanceStatus}</span></td>
                <td><input type="number" min={1} max={10} disabled={!present || busy} value={draft.score}
                  onChange={(event) => setDrafts({ ...drafts, [row.memberId]: { ...draft, score: event.target.value } })} /></td>
                <td><input maxLength={1000} disabled={!present || busy} value={draft.notes}
                  onChange={(event) => setDrafts({ ...drafts, [row.memberId]: { ...draft, notes: event.target.value } })} /></td>
                <td>{present
                  ? <button type="button" disabled={busy || !draft.score} onClick={() => void saveResult(row)}>{row.resultId ? "Cập nhật" : "Lưu"}</button>
                  : <small className="training-muted">Cần có mặt</small>}</td>
              </tr>;
            })}</tbody>
          </table>}
      </>}
    </section>
  </div>;
}

function ProgressTab({ token, classId, flash, fail }) {
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
        ? <p><Stars value={row.latestEvaluation.rating} /> {row.latestEvaluation.comment} <small className="training-muted">({formatDateTime(row.latestEvaluation.evaluatedAt)})</small></p>
        : <p className="training-muted">Chưa có đánh giá tiến độ.</p>}
      {row.evaluationHistory.length > 1 && <details><summary>Lịch sử đánh giá ({row.evaluationHistory.length})</summary>
        <ul>{row.evaluationHistory.map((item) => <li key={item.id}><Stars value={item.rating} /> {item.comment} — {formatDateTime(item.evaluatedAt)}</li>)}</ul>
      </details>}
      <form className="training-inline" onSubmit={(event) => { event.preventDefault(); void evaluate(row); }}>
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
      </form>
    </section>;
  })}</div>;
}

export function Stars({ value }) {
  return <span className="training-stars" aria-label={`${value}/5`}>{"★".repeat(value)}{"☆".repeat(5 - value)}</span>;
}
