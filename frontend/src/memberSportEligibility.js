const VALID_LEVELS = new Set(["BEGINNER", "INTERMEDIATE", "ADVANCED"]);

function positiveId(value) {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function validAssessmentTimestamp(value) {
  if (typeof value !== "string" || !value.trim()) return false;
  const local = /^(\d{4})-(\d{2})-(\d{2})T/.exec(value);
  if (local) {
    const [, year, month, day] = local;
    const calendarDate = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
    if (calendarDate.getUTCFullYear() !== Number(year)
      || calendarDate.getUTCMonth() !== Number(month) - 1
      || calendarDate.getUTCDate() !== Number(day)) return false;
  }
  const localDateTime = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?)$/.test(value);
  const normalized = localDateTime ? `${value}+07:00` : value;
  return Number.isFinite(Date.parse(normalized));
}

/** Return catalog sports supported by this Member's confirmed registration or genuine Coach assessment. */
export function getMemberRelevantSports({ sports, registrations, assessments, memberId }) {
  if (![sports, registrations, assessments].every(Array.isArray)) return [];

  const currentMemberId = positiveId(memberId);
  const registeredSportIds = new Set(registrations
    .filter((registration) => String(registration.status).toUpperCase() === "CONFIRMED"
      && (currentMemberId == null || registration.memberId == null
        || positiveId(registration.memberId) === currentMemberId))
    .map((registration) => positiveId(registration.sportId))
    .filter(Boolean));
  const assessedSportIds = new Set(assessments
    .filter((assessment) => String(assessment.status).toUpperCase() === "COMPLETED"
      && currentMemberId != null
      && positiveId(assessment.memberId) === currentMemberId
      && VALID_LEVELS.has(String(assessment.assessedLevel || "").trim().toUpperCase())
      && validAssessmentTimestamp(assessment.assessedAt))
    .map((assessment) => positiveId(assessment.sportId))
    .filter(Boolean));

  const relevantIds = new Set([...registeredSportIds, ...assessedSportIds]);
  const seen = new Set();
  return sports.filter((sport) => {
    const id = positiveId(sport.id);
    if (!id || !relevantIds.has(id) || seen.has(id)) return false;
    seen.add(id);
    return true;
  });
}
