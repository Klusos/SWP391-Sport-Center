const blankWeek = (weekNumber = 1) => ({ weekNumber, title: "", description: "", exercises: [] });
const asOptionalNumber = (value) => value === "" || value == null ? null : Number(value);

export function canCreateIndividualPlan(member, eligibilityLoaded) {
  return eligibilityLoaded === true && member?.hasQualifyingCompletedAttendance === true;
}

export function individualPlanEligibilityMessage(member, eligibilityLoaded, eligibilityError = false) {
  if (eligibilityError || eligibilityLoaded !== true) {
    return "Không thể xác minh điều kiện điểm danh. Hãy tải lại danh sách trước khi tạo giáo án.";
  }
  return member?.hasQualifyingCompletedAttendance === true
    ? ""
    : "Chỉ có thể tạo giáo án cá nhân sau khi học viên tham gia ít nhất một buổi học đã hoàn thành.";
}

export function addWeekWithUnusedNumber(weeks) {
  const used = new Set(weeks.map((week) => Number(week.weekNumber)));
  const weekNumber = Math.max(0, ...[...used].filter(Number.isFinite)) + 1;
  if (weekNumber > 52) return weeks;
  return [...weeks, blankWeek(weekNumber)].sort((left, right) => Number(left.weekNumber) - Number(right.weekNumber));
}

export function nextAvailableWeekNumber(weeks) {
  return Math.max(0, ...(weeks || []).map((week) => Number(week.weekNumber)).filter(Number.isFinite)) + 1;
}

export function validatePlanDraft(form, { maxWeekNumber = null } = {}) {
  if (!String(form?.goal || "").trim() || String(form.goal).length > 500) return { field: "goal", message: "Mục tiêu giáo án là bắt buộc và không được vượt quá 500 ký tự." };
  if (String(form.description || "").length > 2000) return { field: "description", message: "Mô tả giáo án không được vượt quá 2.000 ký tự." };
  if (!form?.weeks?.length) return { field: "weeks", message: "Giáo án cần có ít nhất một tuần." };
  const seen = new Set();
  for (let index = 0; index < form.weeks.length; index += 1) {
    const number = Number(form.weeks[index].weekNumber);
    if (!Number.isInteger(number) || number < 1 || (maxWeekNumber != null && number > maxWeekNumber)) {
      return { weekIndex: index, field: "weekNumber", message: maxWeekNumber == null
        ? `Tuần ${index + 1} phải có số tuần nguyên dương.`
        : `Tuần phải nằm trong khoảng 1–${maxWeekNumber}.` };
    }
    if (seen.has(number)) return { weekIndex: index, field: "weekNumber", message: `Số tuần ${number} đang bị trùng.` };
    seen.add(number);
    const week = form.weeks[index];
    if (String(week.title || "").length > 150 || String(week.description || "").length > 1000 || String(week.weekGoal || "").length > 500) {
      return { weekIndex: index, field: "week", message: `Nội dung tuần ${number} vượt quá giới hạn ký tự.` };
    }
    for (const exercise of week.exercises || []) {
      if (!String(exercise.name || "").trim() || String(exercise.name).length > 150) {
        return { weekIndex: index, field: "exercise", message: `Nhập tên bài tập trong tuần ${number} (tối đa 150 ký tự).` };
      }
      if (String(exercise.notes || "").length > 500) return { weekIndex: index, field: "exercise", message: `Ghi chú bài tập trong tuần ${number} không được vượt quá 500 ký tự.` };
      for (const [field, min, max, label] of [["sets", 1, 100, "số hiệp"], ["reps", 1, 1000, "số lần"], ["durationMinutes", 1, 240, "thời lượng"]]) {
        if (exercise[field] !== "" && exercise[field] != null) {
          const value = Number(exercise[field]);
          if (!Number.isInteger(value) || value < min || value > max) return { weekIndex: index, field: "exercise", message: `${label[0].toUpperCase()}${label.slice(1)} trong tuần ${number} nằm ngoài giới hạn.` };
        }
      }
    }
  }
  return null;
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
