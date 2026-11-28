import { useId, useMemo, useState } from "react";
import { defaultOpenTrainingWeek, formatTrainingPlanDate, trainingWeekKey } from "./trainingPlanAccordion.js";

export default function TrainingPlanWeeksAccordion({ weeks = [], unassignedExercises = [] }) {
  const orderedWeeks = useMemo(() => [...weeks].sort((left, right) => Number(left.weekNumber) - Number(right.weekNumber)), [weeks]);
  const weekSignature = orderedWeeks.map((week, index) => trainingWeekKey(week, index)).join("|");
  const [selection, setSelection] = useState({ signature: null, key: null });
  const openKey = selection.signature === weekSignature ? selection.key : defaultOpenTrainingWeek(orderedWeeks);
  const instanceId = useId().replace(/[^a-zA-Z0-9_-]/g, "");

  return <div className="training-plan-readonly-weeks">
    {unassignedExercises.length > 0 && <section className="individual-plan-week-view">
      <h5>Bài tập chưa phân tuần</h5>
      <ExerciseList exercises={unassignedExercises}/>
    </section>}
    {orderedWeeks.map((week, index) => {
      const key = trainingWeekKey(week, index);
      const expanded = key === openKey;
      const headerId = `${instanceId}-week-header-${index}`;
      const panelId = `${instanceId}-week-panel-${index}`;
      const exercises = week.exercises || [];
      const goal = week.weekGoal || week.goal;
      return <section className="training-plan-accordion-item" key={key}>
        <h5 className="training-plan-accordion-heading">
          <button type="button" id={headerId} className="training-plan-accordion-trigger"
            aria-expanded={expanded} aria-controls={panelId}
            onClick={() => setSelection({ signature: weekSignature, key: expanded ? null : key })}>
            <span className="training-plan-accordion-title">
              <strong>Tuần {week.weekNumber}{week.title ? ` · ${week.title}` : ""}</strong>
              {week.startDate && week.endDate && <small>{formatTrainingPlanDate(week.startDate)} – {formatTrainingPlanDate(week.endDate)}</small>}
            </span>
            <span className="training-plan-accordion-count">{exercises.length} bài tập</span>
            <i className={`bi ${expanded ? "bi-chevron-up" : "bi-chevron-down"}`} aria-hidden="true"/>
          </button>
        </h5>
        {expanded && <div id={panelId} role="region" aria-labelledby={headerId} className="training-plan-accordion-panel">
          {week.outsideOffering && <p className="member-training-week-warning">Tuần này nằm ngoài thời hạn đợt học hiện tại; nội dung vẫn được giữ.</p>}
          {goal && <p><strong>Mục tiêu tuần:</strong> {goal}</p>}
          {week.description && <p>{week.description}</p>}
          {exercises.length ? <ExerciseList exercises={exercises}/> : <p className="training-muted">Tuần này chưa có bài tập.</p>}
        </div>}
      </section>;
    })}
  </div>;
}

function ExerciseList({ exercises }) {
  return <ul className="training-exercises">{exercises.map((exercise, index) => {
    const quantities = [
      exercise.sets != null && exercise.sets !== "" && `${exercise.sets} hiệp`,
      exercise.reps != null && exercise.reps !== "" && `${exercise.reps} lần`,
      exercise.durationMinutes != null && exercise.durationMinutes !== "" && `${exercise.durationMinutes} phút`,
    ].filter(Boolean);
    return <li key={exercise.id ?? exercise._key ?? `${index}-${exercise.name}`}><div>
      <b>{exercise.name}</b>{quantities.length > 0 && <small>{quantities.join(" · ")}</small>}
      {exercise.notes && <small>{exercise.notes}</small>}
    </div></li>;
  })}</ul>;
}
