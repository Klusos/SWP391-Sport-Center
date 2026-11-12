function vietnamInstant(value) {
  if (!value) return null;
  const localDateTime = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value);
  const date = new Date(localDateTime ? `${value}+07:00` : value);
  return Number.isNaN(date.getTime()) ? null : date.getTime();
}

function sessionsFor(registration, sessions) {
  return (Array.isArray(sessions) ? sessions : []).filter((session) =>
    Number(session.classId) === Number(registration.classId)
      && String(session.status || "").toUpperCase() !== "CANCELLED");
}

export function memberRegistrationSections(registrations, sessions, now = Date.now()) {
  const current = [];
  const history = [];
  for (const registration of Array.isArray(registrations) ? registrations : []) {
    const status = String(registration.status || "").toUpperCase();
    const classSessions = sessionsFor(registration, sessions);
    const datedSessions = classSessions.map((session) => ({ session, start: vietnamInstant(session.startTime), end: vietnamInstant(session.endTime) }));
    const plannedSessions = Number(registration.plannedSessions);
    const hasReliableSchedule = Number.isInteger(plannedSessions) && plannedSessions > 0
      && classSessions.length === plannedSessions
      && datedSessions.every(({ start, end }) => start != null && end != null);
    const upcomingSessions = datedSessions
      .filter(({ start, end }) => start != null && end != null && end >= now)
      .sort((a, b) => a.start - b.start);
    const actionablePending = status === "PENDING_PAYMENT" && vietnamInstant(registration.paymentDeadline) > now;
    const confirmedCurrent = status === "CONFIRMED" && (upcomingSessions.length > 0 || !hasReliableSchedule);
    const entry = { ...registration, nextClassStart: upcomingSessions[0]?.start ?? null, scheduleUnavailable: status === "CONFIRMED" && !hasReliableSchedule };
    if (actionablePending || confirmedCurrent) current.push(entry);
    else history.push(entry);
  }

  current.sort((a, b) => {
    const aWhen = a.status === "PENDING_PAYMENT" ? vietnamInstant(a.paymentDeadline) : a.nextClassStart;
    const bWhen = b.status === "PENDING_PAYMENT" ? vietnamInstant(b.paymentDeadline) : b.nextClassStart;
    return (aWhen ?? Number.MAX_SAFE_INTEGER) - (bWhen ?? Number.MAX_SAFE_INTEGER)
      || String(a.registeredAt || "").localeCompare(String(b.registeredAt || ""));
  });
  history.sort((a, b) => String(b.registeredAt || b.cancelledAt || "").localeCompare(String(a.registeredAt || a.cancelledAt || ""))
    || Number(b.id) - Number(a.id));
  return { current, history };
}
