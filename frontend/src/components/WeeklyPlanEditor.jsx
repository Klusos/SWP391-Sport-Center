import { useEffect, useMemo, useRef, useState } from "react";
import { validatePlanDraft } from "./individualTrainingPlanHelpers.js";
import "../pages/Training.css";

const key = () => globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`;
const blankExercise = () => ({ _key: key(), name: "", sets: "", reps: "", durationMinutes: "", notes: "" });
const blankWeek = (weekNumber) => ({ _key: key(), weekNumber, title: "", weekGoal: "", description: "", exercises: [] });

function toDraft(initial) {
  return {
    goal: initial?.goal || "", description: initial?.description || "",
    weeks: initial?.weeks?.length ? initial.weeks.map((week) => ({
      ...week, _key: key(), weekGoal: week.weekGoal || week.goal || "",
      exercises: (week.exercises || []).map((exercise) => ({ ...exercise, _key: key(),
        sets: exercise.sets ?? "", reps: exercise.reps ?? "", durationMinutes: exercise.durationMinutes ?? "", notes: exercise.notes || "" })),
    })) : [blankWeek(1)],
    unassignedExercises: (initial?.unassignedExercises || initial?.exercises || []).map((exercise) => ({ ...exercise, _key: key() })),
  };
}

export default function WeeklyPlanEditor({ initial, busy, onCancel, onSubmit, onDirtyChange, maxWeekNumber = 52, includeLegacy = false, showWeekGoal = false, offeringDates }) {
  const initialDraft = useMemo(() => toDraft(initial), [initial]);
  const [draft, setDraft] = useState(initialDraft);
  const [activeKey, setActiveKey] = useState(initialDraft.weeks[0]?._key);
  const [preview, setPreview] = useState(false);
  const [issue, setIssue] = useState(null);
  const isDirty = JSON.stringify(draft) !== JSON.stringify(initialDraft);
  const dirtyCallback = useRef(onDirtyChange);
  const activeIndex = draft.weeks.findIndex((week) => week._key === activeKey);
  const active = draft.weeks[activeIndex] || draft.weeks[0];
  const ordered = [...draft.weeks].sort((a, b) => Number(a.weekNumber) - Number(b.weekNumber));

  useEffect(() => {
    dirtyCallback.current = onDirtyChange;
  }, [onDirtyChange]);

  useEffect(() => {
    dirtyCallback.current?.(isDirty);
  }, [isDirty]);

  useEffect(() => {
    if (!isDirty) return undefined;
    const beforeUnload = (event) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", beforeUnload);
    return () => window.removeEventListener("beforeunload", beforeUnload);
  }, [isDirty]);

  function patchWeek(weekKey, patch) {
    setDraft((current) => ({ ...current, weeks: current.weeks.map((week) => week._key === weekKey ? { ...week, ...patch } : week) }));
    setIssue(null);
  }
  function patchExercise(weekKey, exerciseKey, patch) {
    setDraft((current) => ({ ...current, weeks: current.weeks.map((week) => week._key !== weekKey ? week : {
      ...week, exercises: week.exercises.map((exercise) => exercise._key === exerciseKey ? { ...exercise, ...patch } : exercise),
    }) }));
    setIssue(null);
  }
  function addWeek() {
    const number = Math.max(0, ...draft.weeks.map((week) => Number(week.weekNumber) || 0)) + 1;
    if (maxWeekNumber != null && number > maxWeekNumber) return;
    const next = blankWeek(number);
    setDraft((current) => ({ ...current, weeks: [...current.weeks, next] }));
    setActiveKey(next._key);
  }
  function copyWeek() {
    if (!active) return;
    const newNumber = Math.max(0, ...draft.weeks.map((week) => Number(week.weekNumber) || 0)) + 1;
    if (maxWeekNumber != null && newNumber > maxWeekNumber) return;
    const copy = { ...active, id: undefined, _key: key(), weekNumber: newNumber,
      title: active.title ? `${active.title} (bản sao)` : "Bản sao",
      exercises: active.exercises.map((exercise) => ({ ...exercise, id: undefined, _key: key() })) };
    setDraft((current) => ({ ...current, weeks: [...current.weeks, copy] }));
    setActiveKey(copy._key);
  }
  function deleteWeek() {
    if (!active || draft.weeks.length <= 1 || !window.confirm(`Xóa tuần ${active.weekNumber} và các bài tập trong tuần này?`)) return;
    const weeks = draft.weeks.filter((week) => week._key !== active._key);
    setDraft((current) => ({ ...current, weeks }));
    setActiveKey(weeks[0]?._key);
  }
  function assignLegacy(exercise) {
    if (!active) return;
    setDraft((current) => ({ ...current,
      unassignedExercises: current.unassignedExercises.filter((item) => item.id !== exercise.id),
      weeks: current.weeks.map((week) => week._key === active._key
        ? { ...week, exercises: [...week.exercises, { ...exercise, _key: key(), sortOrder: week.exercises.length }] }
        : week),
    }));
  }
  function cancel() {
    if (isDirty && !window.confirm("Bạn có thay đổi chưa lưu. Hủy chỉnh sửa?")) return;
    dirtyCallback.current?.(false);
    onCancel();
  }
  function submit(event) {
    event.preventDefault();
    const validation = validatePlanDraft(draft, { maxWeekNumber });
    if (validation) {
      setIssue(validation);
      if (validation.weekIndex != null) setActiveKey(draft.weeks[validation.weekIndex]?._key);
      return;
    }
    void onSubmit(draft);
  }
  if (preview) return <section className="weekly-plan-preview" aria-label="Xem trước giáo án">
    <div className="weekly-plan-editor-heading"><h3>Xem trước giáo án</h3><button type="button" className="secondary" onClick={() => setPreview(false)}>Quay lại chỉnh sửa</button></div>
    <ReadOnlyPlan revision={draft} />
    <div className="weekly-plan-sticky-actions"><button type="button" className="secondary" onClick={() => setPreview(false)}>Quay lại chỉnh sửa</button><button type="button" className="secondary" onClick={cancel}>Hủy</button></div>
  </section>;

  return <form className="individual-plan-editor weekly-plan-editor" onSubmit={submit}>
    <section className="weekly-plan-overview"><h3>Tổng quan giáo án</h3>
      <label>Mục tiêu *<input required maxLength={500} value={draft.goal} onChange={(event) => { setDraft({ ...draft, goal: event.target.value }); setIssue(null); }} /></label>
      <label>Mô tả<textarea maxLength={2000} rows={2} value={draft.description} onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
    </section>
    {issue?.field === "goal" && <p className="weekly-plan-validation" role="alert">{issue.message}</p>}
    <div className="weekly-plan-workspace">
      <aside className="weekly-plan-sidebar" aria-label="Danh sách tuần">
        <div className="weekly-plan-sidebar-heading"><h3>Các tuần</h3><span>{draft.weeks.length}</span></div>
        <nav className="weekly-plan-week-list">{ordered.map((week) => <button key={week._key} type="button" className={active?._key === week._key ? "selected" : ""}
          aria-current={active?._key === week._key ? "step" : undefined} onClick={() => setActiveKey(week._key)}>
          <strong>Tuần {week.weekNumber}</strong><span>{week.title || "Chưa đặt tiêu đề"}</span><small>{week.exercises.length} bài tập</small>
        </button>)}</nav>
        <button type="button" className="secondary outline weekly-plan-add" disabled={maxWeekNumber != null && Math.max(0, ...draft.weeks.map((week) => Number(week.weekNumber) || 0)) >= maxWeekNumber} onClick={addWeek}><i className="bi bi-plus-lg" aria-hidden="true"/> Thêm tuần</button>
        {includeLegacy && draft.unassignedExercises.length > 0 && <section className="weekly-plan-legacy"><h4>Bài tập chưa phân tuần</h4>
          {draft.unassignedExercises.map((exercise) => <div key={exercise.id}><span>{exercise.name}</span><button type="button" className="secondary" disabled={!active} onClick={() => assignLegacy(exercise)}>Chuyển vào tuần đang chọn</button></div>)}</section>}
      </aside>
      <section className="weekly-plan-week-editor" aria-label={active ? `Chỉnh sửa tuần ${active.weekNumber}` : "Chỉnh sửa tuần"}>
        {active ? <>
          <div className="weekly-plan-editor-heading"><div><p className="training-muted">Đang chỉnh sửa</p><h3>Tuần {active.weekNumber}{active.title ? ` · ${active.title}` : ""}</h3>
            {offeringDates && <WeekDateRange weekNumber={Number(active.weekNumber)} startDate={offeringDates.startDate} endDate={offeringDates.endDate}/>}</div>
            <div className="weekly-plan-actions"><button type="button" className="secondary" onClick={copyWeek}><i className="bi bi-copy" aria-hidden="true"/> Sao chép tuần</button><button type="button" className="secondary danger" disabled={draft.weeks.length <= 1} onClick={deleteWeek} aria-label={`Xóa tuần ${active.weekNumber}`}><i className="bi bi-trash3" aria-hidden="true"/> Xóa tuần</button></div>
          </div>
          {issue?.weekIndex === activeIndex && <p className="weekly-plan-validation" role="alert">{issue.message}</p>}
          <div className="individual-plan-editor-grid weekly-plan-fields">
            <label>Tuần<input required type="number" min="1" max={maxWeekNumber ?? undefined} value={active.weekNumber} onChange={(event) => patchWeek(active._key, { weekNumber: event.target.value })}/></label>
            <label className="wide">Tiêu đề<input maxLength={150} value={active.title || ""} onChange={(event) => patchWeek(active._key, { title: event.target.value })}/></label>
            {showWeekGoal && <label className="wide">Mục tiêu tuần<textarea maxLength={500} rows={2} value={active.weekGoal || ""} onChange={(event) => patchWeek(active._key, { weekGoal: event.target.value })}/></label>}
            <label className="wide">Mô tả tuần<textarea maxLength={1000} rows={2} value={active.description || ""} onChange={(event) => patchWeek(active._key, { description: event.target.value })}/></label>
          </div>
          <div className="weekly-plan-exercise-heading"><h4>Bài tập <span>{active.exercises.length}</span></h4><button type="button" className="secondary outline" onClick={() => patchWeek(active._key, { exercises: [...active.exercises, blankExercise()] })}><i className="bi bi-plus-lg" aria-hidden="true"/> Thêm bài tập</button></div>
          {active.exercises.map((exercise) => <div className="individual-plan-exercise" key={exercise._key}>
            <label>Bài tập *<input required maxLength={150} value={exercise.name || ""} onChange={(event) => patchExercise(active._key, exercise._key, { name: event.target.value })}/></label>
            <label>Hiệp<input type="number" min="1" max="100" value={exercise.sets} onChange={(event) => patchExercise(active._key, exercise._key, { sets: event.target.value })}/></label>
            <label>Lần<input type="number" min="1" max="1000" value={exercise.reps} onChange={(event) => patchExercise(active._key, exercise._key, { reps: event.target.value })}/></label>
            <label>Phút<input type="number" min="1" max="240" value={exercise.durationMinutes} onChange={(event) => patchExercise(active._key, exercise._key, { durationMinutes: event.target.value })}/></label>
            <label className="wide">Ghi chú<input maxLength={500} value={exercise.notes} onChange={(event) => patchExercise(active._key, exercise._key, { notes: event.target.value })}/></label>
            <button type="button" className="training-link-button danger" onClick={() => patchWeek(active._key, { exercises: active.exercises.filter((item) => item._key !== exercise._key) })}>Xóa bài tập</button>
          </div>)}
          {active.exercises.length === 0 && <p className="dash-empty">Tuần này chưa có bài tập.</p>}
        </> : <p className="dash-empty">Chưa có tuần. Hãy thêm tuần để bắt đầu.</p>}
      </section>
    </div>
    <div className="weekly-plan-sticky-actions"><button type="button" className="secondary" onClick={() => setPreview(true)}><i className="bi bi-eye" aria-hidden="true"/> Xem trước</button><span className="weekly-plan-actions-spacer"/>
      <button type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu giáo án"}</button><button type="button" className="secondary" disabled={busy} onClick={cancel}>Hủy</button></div>
  </form>;
}

function WeekDateRange({ weekNumber, startDate, endDate }) {
  if (!Number.isInteger(weekNumber) || weekNumber < 1 || !startDate || !endDate) return null;
  const [startYear, startMonth, startDay] = startDate.split("-").map(Number);
  const [endYear, endMonth, endDay] = endDate.split("-").map(Number);
  const startMs = Date.UTC(startYear, startMonth - 1, startDay) + (weekNumber - 1) * 7 * 86400000;
  const endMs = startMs + 6 * 86400000;
  const offeringEndMs = Date.UTC(endYear, endMonth - 1, endDay);
  const start = new Date(startMs); const end = new Date(Math.min(endMs, offeringEndMs));
  const outOfRange = startMs > offeringEndMs;
  const fmt = (date) => new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeZone: "Asia/Ho_Chi_Minh" }).format(date);
  return <small className={outOfRange ? "weekly-plan-range warning" : "weekly-plan-range"}>{outOfRange ? "Tuần nằm ngoài thời hạn đợt học" : `${fmt(start)} – ${fmt(end)}`}</small>;
}

function ReadOnlyPlan({ revision }) {
  return <div className="individual-plan-revision"><h4>{revision.goal}</h4>{revision.description && <p>{revision.description}</p>}
    {(revision.unassignedExercises || []).length > 0 && <section className="individual-plan-week-view"><h5>Bài tập chưa phân tuần</h5><ul className="training-exercises">{revision.unassignedExercises.map((exercise) => <li key={exercise._key || exercise.id}><div><b>{exercise.name}</b>{exercise.notes && <small>{exercise.notes}</small>}</div></li>)}</ul></section>}
    {[...(revision.weeks || [])].sort((a, b) => Number(a.weekNumber) - Number(b.weekNumber)).map((week) => <section className="individual-plan-week-view" key={week._key || week.id || week.weekNumber}>
      <h5>Tuần {week.weekNumber}{week.title ? ` · ${week.title}` : ""}</h5>{week.weekGoal && <p><strong>Mục tiêu:</strong> {week.weekGoal}</p>}{week.description && <p>{week.description}</p>}
      <ul className="training-exercises">{(week.exercises || []).map((exercise) => <li key={exercise._key || exercise.id || exercise.name}><div><b>{exercise.name}</b><small>{[exercise.sets && `${exercise.sets} hiệp`, exercise.reps && `${exercise.reps} lần`, exercise.durationMinutes && `${exercise.durationMinutes} phút`].filter(Boolean).join(" · ")}</small>{exercise.notes && <small>{exercise.notes}</small>}</div></li>)}</ul>
    </section>)}</div>;
}
