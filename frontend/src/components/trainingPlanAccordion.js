export function trainingWeekKey(week, index) {
  return String(week?.id ?? week?.weekNumber ?? index);
}

export function vietnamTodayIso(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  return `${values.year}-${values.month}-${values.day}`;
}

export function defaultOpenTrainingWeek(weeks, today = vietnamTodayIso()) {
  if (!weeks.length) return null;
  const current = weeks.find((week) => week.startDate && week.endDate
    && week.startDate <= today && week.endDate >= today);
  const index = current ? weeks.indexOf(current) : 0;
  return trainingWeekKey(weeks[index], index);
}

export function formatTrainingPlanDate(value) {
  if (!value) return "";
  const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})/);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : String(value);
}
