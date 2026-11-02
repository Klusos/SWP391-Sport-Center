import { useCallback, useEffect, useMemo, useState } from "react";
import {
  createIndividualTrainingPlanApi,
  getClassIndividualTrainingPlansApi,
  getIndividualTrainingPlanApi,
  getIndividualTrainingPlanRevisionsApi,
  getMyIndividualTrainingPlansApi,
  getTrainingProgressApi,
  updateIndividualTrainingPlanApi,
} from "../api";
import {
  addWeekWithUnusedNumber,
  createLatestRequestGuard,
  savePlanAndRefreshOpenHistory,
  toIndividualTrainingPlanPayload,
} from "./individualTrainingPlanHelpers";

const blankWeek = (weekNumber = 1) => ({ weekNumber, title: "", description: "", exercises: [] });
const blankExercise = () => ({ name: "", sets: "", reps: "", durationMinutes: "", notes: "" });
const toForm = (revision) => ({
  goal: revision?.goal || "",
  description: revision?.description || "",
  weeks: revision?.weeks?.length ? revision.weeks.map((week) => ({
    weekNumber: week.weekNumber,
    title: week.title || "",
    description: week.description || "",
    exercises: (week.exercises || []).map((exercise) => ({
      name: exercise.name || "", sets: exercise.sets ?? "", reps: exercise.reps ?? "",
      durationMinutes: exercise.durationMinutes ?? "", notes: exercise.notes || "",
    })),
  })) : [blankWeek()],
});

function messageFor(error, fallback) {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Không đủ quyền cho thao tác này. Việc tạo hoặc chỉnh sửa yêu cầu bạn là HLV được giao lớp và học viên có gói hội viên đủ điều kiện hiện tại.";
  if (error?.status === 404) return "Không tìm thấy giáo án hoặc lớp học.";
  if (error?.status === 409) return error.message || "Học viên chưa đủ điều kiện đăng ký hoặc đã có giáo án cá nhân.";
  if (error?.status === 400) return error.message || "Thông tin chưa hợp lệ. Kiểm tra mục tiêu, tuần và bài tập.";
  return error?.message || fallback;
}

export default function IndividualTrainingPlans({ token, mode, classId }) {
  const isCoach = mode === "coach";
  const [plans, setPlans] = useState([]);
  const [members, setMembers] = useState([]);
  const [selected, setSelected] = useState(null);
  const [history, setHistory] = useState(null);
  const [historyPlanId, setHistoryPlanId] = useState(null);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [busy, setBusy] = useState(false);
  const [creatingFor, setCreatingFor] = useState(null);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [historyRequests] = useState(() => createLatestRequestGuard());

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      if (isCoach) {
        const [classPlans, progress] = await Promise.all([
          getClassIndividualTrainingPlansApi(token, classId),
          getTrainingProgressApi(token, classId),
        ]);
        setPlans(Array.isArray(classPlans) ? classPlans : []);
        setMembers(Array.isArray(progress) ? progress : []);
      } else {
        const ownPlans = await getMyIndividualTrainingPlansApi(token);
        setPlans(Array.isArray(ownPlans) ? ownPlans : []);
      }
    } catch (requestError) {
      setError(messageFor(requestError, "Không tải được danh sách giáo án cá nhân."));
    } finally {
      setLoading(false);
    }
  }, [token, isCoach, classId]);

  useEffect(() => {
    Promise.resolve().then(() => load());
  }, [load]);

  const planByMember = useMemo(() => new Map(plans.map((plan) => [plan.memberId, plan])), [plans]);

  async function openPlan(plan) {
    historyRequests.invalidate();
    setSelected(null);
    setHistory(null);
    setHistoryPlanId(null);
    setLoadingHistory(false);
    setEditing(false);
    setLoadingDetail(true);
    setError("");
    try {
      setSelected(await getIndividualTrainingPlanApi(token, plan.id));
    } catch (requestError) {
      setError(messageFor(requestError, "Không tải được chi tiết giáo án."));
    } finally {
      setLoadingDetail(false);
    }
  }

  function clearHistory() {
    historyRequests.invalidate();
    setHistory(null);
    setHistoryPlanId(null);
    setLoadingHistory(false);
  }

  async function showHistory(planId) {
    const requestId = historyRequests.start();
    setHistoryPlanId(planId);
    setError("");
    setLoadingHistory(true);
    try {
      const rows = await getIndividualTrainingPlanRevisionsApi(token, planId);
      if (historyRequests.isCurrent(requestId)) setHistory(Array.isArray(rows) ? rows : []);
    } catch (requestError) {
      if (historyRequests.isCurrent(requestId)) {
        setError(messageFor(requestError, "Không tải được lịch sử chỉnh sửa."));
      }
    } finally {
      if (historyRequests.isCurrent(requestId)) setLoadingHistory(false);
    }
  }

  async function submit(memberId, form, planId = null) {
    if (busy) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const payload = toIndividualTrainingPlanPayload(form);
      const saved = await savePlanAndRefreshOpenHistory(
        () => planId
          ? updateIndividualTrainingPlanApi(token, planId, payload)
          : createIndividualTrainingPlanApi(token, classId, memberId, payload),
        {
          planId,
          historyIsOpen: planId != null && historyPlanId === planId,
          refreshHistory: showHistory,
        },
      );
      setNotice(planId ? "Đã lưu phiên bản giáo án mới." : "Đã tạo giáo án cá nhân.");
      setCreatingFor(null);
      if (planId == null) clearHistory();
      setEditing(false);
      setSelected(saved);
      await load();
    } catch (requestError) {
      setError(messageFor(requestError, "Không lưu được giáo án cá nhân."));
    } finally {
      setBusy(false);
    }
  }

  return <section className="individual-plans" aria-labelledby={`individual-plan-heading-${mode}`}>
    <div className="individual-plans-heading">
      <div>
        <h2 id={`individual-plan-heading-${mode}`}>Giáo án cá nhân</h2>
        <p className="training-muted">Kế hoạch riêng theo từng học viên; các phiên bản cũ được lưu trong lịch sử.</p>
      </div>
      <button type="button" className="secondary" onClick={() => void load()} disabled={loading}>Làm mới</button>
    </div>
    {error && <div className="banner error" role="alert">{error}</div>}
    {notice && <div className="banner success" role="status">{notice}</div>}
    {loading ? <p role="status">Đang tải giáo án cá nhân…</p>
      : isCoach ? <>
        {members.length === 0 ? <p className="dash-empty">Lớp hiện chưa có học viên đăng ký đã xác nhận.</p> : <div className="individual-plan-list">
          {members.map((member) => {
            const plan = planByMember.get(member.memberId);
            return <article className="individual-plan-row" key={member.memberId}>
              <div><strong>{member.memberName}</strong><small>{plan ? `Phiên bản ${plan.currentRevision?.revisionNumber ?? "—"}` : "Chưa có giáo án cá nhân"}</small></div>
              <div className="training-actions">
                {plan ? <button type="button" className="secondary" onClick={() => void openPlan(plan)}>Mở giáo án</button>
                  : <button type="button" onClick={() => { clearHistory(); setCreatingFor(member.memberId); setSelected(null); }}>Tạo giáo án</button>}
              </div>
            </article>;
          })}
        </div>}
        {creatingFor != null && <PlanEditor key={`create-${creatingFor}`} busy={busy} onCancel={() => setCreatingFor(null)}
          onSubmit={(form) => submit(creatingFor, form)} />}
      </> : plans.length === 0 ? <p className="dash-empty">Bạn chưa có giáo án cá nhân. Giáo án sẽ hiển thị tại đây khi huấn luyện viên tạo cho bạn.</p>
        : <div className="individual-plan-list">{plans.map((plan) => <article className="individual-plan-row" key={plan.id}>
          <div><strong>{plan.className}</strong><small>Phiên bản {plan.currentRevision?.revisionNumber ?? "—"}</small></div>
          <button type="button" className="secondary" onClick={() => void openPlan(plan)}>Xem giáo án</button>
        </article>)}</div>}

    {loadingDetail && <p role="status">Đang tải chi tiết…</p>}
    {selected && <section className="individual-plan-detail" aria-label="Chi tiết giáo án cá nhân">
      <div className="individual-plans-heading">
        <div><h3>{selected.className} · {selected.memberName}</h3>
          <p className="training-muted">Phiên bản {selected.currentRevision?.revisionNumber ?? "—"} · cập nhật {formatDate(selected.updatedAt)}</p></div>
        {isCoach && <button type="button" onClick={() => setEditing(true)}>Chỉnh sửa</button>}
      </div>
      {editing && isCoach ? <PlanEditor key={`edit-${selected.id}-${selected.currentRevision?.id}`} initial={selected.currentRevision}
        busy={busy} onCancel={() => setEditing(false)} onSubmit={(form) => submit(selected.memberId, form, selected.id)} />
        : <PlanRevision revision={selected.currentRevision} />}
      <div className="training-actions">
        <button type="button" className="secondary" onClick={() => void showHistory(selected.id)}>Lịch sử phiên bản</button>
        {isCoach && <button type="button" className="secondary" onClick={() => { clearHistory(); setSelected(null); }}>Đóng chi tiết</button>}
      </div>
      {historyPlanId === selected.id && history && <div className="individual-plan-history"><h4>Lịch sử chỉnh sửa</h4>
        {history.length === 0 ? <p className="training-muted">Chưa có lịch sử phiên bản.</p> : history.map((revision) => <details key={revision.id}>
          <summary>Phiên bản {revision.revisionNumber} · {formatDate(revision.createdAt)} · {revision.revisedByName}</summary>
          <PlanRevision revision={revision} />
        </details>)}
      </div>}
      {loadingHistory && <p role="status">Đang tải lịch sử phiên bản…</p>}
    </section>}
  </section>;
}

function PlanEditor({ initial, busy, onCancel, onSubmit }) {
  const [form, setForm] = useState(() => toForm(initial));
  function updateWeek(index, patch) {
    setForm((current) => ({ ...current, weeks: current.weeks.map((week, i) => i === index ? { ...week, ...patch } : week) }));
  }
  function updateExercise(weekIndex, exerciseIndex, patch) {
    setForm((current) => ({ ...current, weeks: current.weeks.map((week, i) => i !== weekIndex ? week : {
      ...week, exercises: week.exercises.map((exercise, j) => j === exerciseIndex ? { ...exercise, ...patch } : exercise),
    }) }));
  }
  return <form className="individual-plan-editor" onSubmit={(event) => { event.preventDefault(); void onSubmit(form); }}>
    <label>Mục tiêu *<input required maxLength={500} value={form.goal} onChange={(event) => setForm({ ...form, goal: event.target.value })} /></label>
    <label>Mô tả<textarea maxLength={2000} rows={3} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
    <div className="individual-plan-weeks-heading"><h4>Nội dung theo tuần</h4>
      <button type="button" className="secondary" disabled={form.weeks.length >= 52} onClick={() => setForm((current) => ({ ...current, weeks: addWeekWithUnusedNumber(current.weeks) }))}>Thêm tuần</button></div>
    {form.weeks.map((week, weekIndex) => <fieldset className="individual-plan-week" key={weekIndex}>
      <legend>Tuần {week.weekNumber}</legend>
      <div className="individual-plan-editor-grid">
        <label>Số tuần<input required type="number" min="1" max="52" value={week.weekNumber} onChange={(event) => updateWeek(weekIndex, { weekNumber: event.target.value })} /></label>
        <label>Tiêu đề<input maxLength={150} value={week.title} onChange={(event) => updateWeek(weekIndex, { title: event.target.value })} /></label>
        <label className="wide">Mô tả tuần<textarea maxLength={1000} rows={2} value={week.description} onChange={(event) => updateWeek(weekIndex, { description: event.target.value })} /></label>
      </div>
      {week.exercises.map((exercise, exerciseIndex) => <div className="individual-plan-exercise" key={exerciseIndex}>
        <label>Bài tập *<input required maxLength={150} value={exercise.name} onChange={(event) => updateExercise(weekIndex, exerciseIndex, { name: event.target.value })} /></label>
        <label>Hiệp<input type="number" min="1" max="100" value={exercise.sets} onChange={(event) => updateExercise(weekIndex, exerciseIndex, { sets: event.target.value })} /></label>
        <label>Lần<input type="number" min="1" max="1000" value={exercise.reps} onChange={(event) => updateExercise(weekIndex, exerciseIndex, { reps: event.target.value })} /></label>
        <label>Phút<input type="number" min="1" max="240" value={exercise.durationMinutes} onChange={(event) => updateExercise(weekIndex, exerciseIndex, { durationMinutes: event.target.value })} /></label>
        <label className="wide">Ghi chú<input maxLength={500} value={exercise.notes} onChange={(event) => updateExercise(weekIndex, exerciseIndex, { notes: event.target.value })} /></label>
        <button type="button" className="training-link-button danger" onClick={() => updateWeek(weekIndex, { exercises: week.exercises.filter((_, i) => i !== exerciseIndex) })}>Xóa bài tập</button>
      </div>)}
      <button type="button" className="training-link-button" disabled={week.exercises.length >= 30}
        onClick={() => updateWeek(weekIndex, { exercises: [...week.exercises, blankExercise()] })}>Thêm bài tập</button>
      {form.weeks.length > 1 && <button type="button" className="training-link-button danger" onClick={() => setForm({ ...form, weeks: form.weeks.filter((_, i) => i !== weekIndex) })}>Xóa tuần</button>}
    </fieldset>)}
    <div className="training-actions"><button type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu giáo án"}</button>
      <button type="button" className="secondary" disabled={busy} onClick={onCancel}>Hủy</button></div>
  </form>;
}

function PlanRevision({ revision }) {
  if (!revision) return <p className="training-muted">Chưa có nội dung giáo án.</p>;
  return <div className="individual-plan-revision"><h4>{revision.goal}</h4>
    {revision.description && <p>{revision.description}</p>}
    {(revision.weeks || []).map((week) => <section className="individual-plan-week-view" key={`${week.weekNumber}-${week.title}`}>
      <h5>Tuần {week.weekNumber}{week.title ? ` · ${week.title}` : ""}</h5>
      {week.description && <p>{week.description}</p>}
      {!week.exercises?.length ? <p className="training-muted">Chưa có bài tập trong tuần này.</p> : <ul className="training-exercises">
        {week.exercises.map((exercise) => <li key={exercise.id ?? `${exercise.sortOrder}-${exercise.name}`}><div><b>{exercise.name}</b>
          <small>{[exercise.sets != null && `${exercise.sets} hiệp`, exercise.reps != null && `${exercise.reps} lần`, exercise.durationMinutes != null && `${exercise.durationMinutes} phút`].filter(Boolean).join(" · ") || "Chưa đặt số lượng"}</small>
          {exercise.notes && <small>{exercise.notes}</small>}</div></li>)}
      </ul>}
    </section>)}
  </div>;
}

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(date);
}
