import { useCallback, useEffect, useRef, useState } from "react";
import { acquireAction } from "../courseCheckout.js";
import { getTrainingPlanApi, saveWeeklyTrainingPlanApi } from "../api.js";
import WeeklyPlanEditor from "./WeeklyPlanEditor.jsx";
import "../pages/Training.css";

const numeric = (value) => value === "" || value == null ? null : Number(value);

export default function GeneralTrainingPlanTab({ token, classId, classInfo, canManageTraining, onSaved, flash, fail, onDraftDirtyChange }) {
  const [plan, setPlan] = useState(null);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [editorVersion, setEditorVersion] = useState(0);
  const actionLock = useRef(false);
  const failRef = useRef(fail);
  useEffect(() => { failRef.current = fail; }, [fail]);
  const load = useCallback(async () => {
    setLoadError(false);
    try {
      setPlan(await getTrainingPlanApi(token, classId));
    } catch (error) {
      if (error?.status === 404) setPlan(null);
      else { setLoadError(true); failRef.current(error, "Không tải được giáo án."); }
    } finally { setLoaded(true); }
  }, [token, classId]);

  useEffect(() => { let active = true; Promise.resolve().then(() => active && load()); return () => { active = false; }; }, [load]);

  async function save(draft) {
    if (!canManageTraining || !acquireAction(actionLock)) return;
    setBusy(true);
    const payload = {
      expectedUpdatedAt: plan?.updatedAt ?? null,
      goal: draft.goal,
      description: draft.description || null,
      unassignedExerciseIds: (draft.unassignedExercises || []).map((exercise) => exercise.id),
      weeks: draft.weeks.map((week) => ({
        id: week.id ?? null,
        weekNumber: Number(week.weekNumber),
        title: week.title || null,
        goal: week.weekGoal || null,
        description: week.description || null,
        exercises: week.exercises.map((exercise) => ({
          id: exercise.id ?? null,
          name: exercise.name,
          sets: numeric(exercise.sets),
          reps: numeric(exercise.reps),
          durationMinutes: numeric(exercise.durationMinutes),
          notes: exercise.notes || null,
        })),
      })),
    };
    try {
      const saved = await saveWeeklyTrainingPlanApi(token, classId, payload);
      setPlan(saved);
      onDraftDirtyChange?.(false);
      setEditorVersion((version) => version + 1);
      flash("Đã lưu giáo án theo tuần.");
      onSaved();
    } catch (error) { fail(error, "Không lưu được giáo án. Bản nháp vẫn được giữ lại để thử lại."); }
    finally { actionLock.current = false; setBusy(false); }
  }

  if (!loaded) return <p role="status">Đang tải giáo án…</p>;
  if (loadError && !plan) return <div className="dash-empty"><p>Không tải được giáo án.</p><button type="button" className="secondary" onClick={() => { setLoaded(false); void load(); }}>Thử lại</button></div>;
  const initial = plan || { goal: "", description: "", weeks: [], exercises: [] };
  return <div className="general-training-plan-tab">
    {canManageTraining ? <WeeklyPlanEditor key={`${classId}-${editorVersion}-${plan?.updatedAt || "new"}`} initial={initial} maxWeekNumber={null}
      includeLegacy showWeekGoal offeringDates={classInfo ? { startDate: classInfo.startDate, endDate: classInfo.endDate } : null}
      busy={busy} onDirtyChange={onDraftDirtyChange} onCancel={async () => { await load(); setEditorVersion((version) => version + 1); }} onSubmit={save}/> : <GeneralPlanReadOnly plan={plan}/>}
  </div>;
}

function GeneralPlanReadOnly({ plan }) {
  if (!plan) return <p className="dash-empty">Lớp học chưa có giáo án.</p>;
  return <section className="general-plan-readonly" aria-label="Giáo án chung của lớp">
    <h3>{plan.goal}</h3>{plan.description && <p>{plan.description}</p>}
    {(plan.exercises || []).length > 0 && <section><h4>Bài tập chưa phân tuần</h4><ExerciseList exercises={plan.exercises}/></section>}
    {!(plan.weeks || []).length && !(plan.exercises || []).length && <p className="dash-empty">Giáo án chưa có nội dung.</p>}
    {[...(plan.weeks || [])].sort((a, b) => a.weekNumber - b.weekNumber).map((week) => <details key={week.id} className="individual-plan-week-view" open>
      <summary>Tuần {week.weekNumber}{week.title ? ` · ${week.title}` : ""}<small>{week.startDate && week.endDate ? ` · ${week.startDate} – ${week.endDate}` : ""}</small></summary>
      {week.outsideOffering && <p className="weekly-plan-range warning">Tuần này nằm ngoài thời hạn đợt học hiện tại; nội dung vẫn được giữ.</p>}
      {week.goal && <p><strong>Mục tiêu:</strong> {week.goal}</p>}{week.description && <p>{week.description}</p>}
      <ExerciseList exercises={week.exercises || []}/>
    </details>)}
  </section>;
}

function ExerciseList({ exercises }) {
  if (!exercises.length) return <p className="training-muted">Chưa có bài tập.</p>;
  return <ul className="training-exercises">{exercises.map((exercise) => <li key={exercise.id}><div><b>{exercise.name}</b>
    <small>{[exercise.sets != null && `${exercise.sets} hiệp`, exercise.reps != null && `${exercise.reps} lần`, exercise.durationMinutes != null && `${exercise.durationMinutes} phút`].filter(Boolean).join(" · ")}</small>
    {exercise.notes && <small>{exercise.notes}</small>}</div></li>)}</ul>;
}
