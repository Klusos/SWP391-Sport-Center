const asList = (value) => Array.isArray(value) ? value : value?.content ?? [];

export function confirmedEnrollmentCount(classes) {
  return asList(classes).reduce((sum, item) => sum + Math.max(0, Number(item?.confirmedMembers) || 0), 0);
}

export function upcomingTeachingSessions(sessions, now = Date.now()) {
  return asList(sessions)
    .filter((session) => {
      const start = new Date(session?.startTime).getTime();
      return Number.isFinite(start) && start > now && !/CANCEL|COMPLETED/i.test(session?.sessionStatus || "");
    })
    .sort((a, b) => new Date(a.startTime) - new Date(b.startTime)
      || Number(a.classSessionId || 0) - Number(b.classSessionId || 0));
}
