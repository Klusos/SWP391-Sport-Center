const blankWeek = (weekNumber = 1) => ({ weekNumber, title: "", description: "", exercises: [] });
const asOptionalNumber = (value) => value === "" || value == null ? null : Number(value);

export function addWeekWithUnusedNumber(weeks) {
  const used = new Set(weeks.map((week) => Number(week.weekNumber)));
  let weekNumber = 1;
  while (weekNumber <= 52 && used.has(weekNumber)) weekNumber += 1;
  if (weekNumber > 52) return weeks;
  return [...weeks, blankWeek(weekNumber)].sort((left, right) => Number(left.weekNumber) - Number(right.weekNumber));
}

export function toIndividualTrainingPlanPayload(form) {
  return {
    goal: form.goal,
    description: form.description || null,
    weeks: [...form.weeks].sort((left, right) => Number(left.weekNumber) - Number(right.weekNumber)).map((week) => ({
      weekNumber: Number(week.weekNumber), title: week.title || null, description: week.description || null,
      exercises: week.exercises.map((exercise, sortOrder) => ({
        name: exercise.name,
        sets: asOptionalNumber(exercise.sets),
        reps: asOptionalNumber(exercise.reps),
        durationMinutes: asOptionalNumber(exercise.durationMinutes),
        notes: exercise.notes || null,
        sortOrder,
      })),
    })),
  };
}

export function createLatestRequestGuard() {
  let latest = 0;
  return {
    start: () => ++latest,
    isCurrent: (requestId) => requestId === latest,
    invalidate: () => { latest += 1; },
  };
}

export async function savePlanAndRefreshOpenHistory(saveRequest, { planId, historyIsOpen, refreshHistory }) {
  const saved = await saveRequest();
  if (planId != null && historyIsOpen) await refreshHistory(planId);
  return saved;
}
