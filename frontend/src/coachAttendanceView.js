export function attendanceClassKey(session) {
  if (session?.courseClassId != null) return `id:${session.courseClassId}`;
  const name = String(session?.courseClassName || "").trim();
  return name ? `name:${name}` : "unknown";
}

export function attendanceClassOptions(sessions) {
  const classes = new Map();
  for (const session of Array.isArray(sessions) ? sessions : []) {
    const key = attendanceClassKey(session);
    if (key === "unknown") continue;
    const current = classes.get(key) || {
      key,
      name: session?.courseClassName || `Lớp #${session.courseClassId}`,
      courseName: session?.courseName || session?.courseOfferingName || "",
      sessionCount: 0,
    };
    current.sessionCount += 1;
    classes.set(key, current);
  }
  return [...classes.values()].sort((left, right) => left.name.localeCompare(right.name, "vi"));
}

export function attendanceSessionsForClass(sessions, classKey) {
  if (!classKey || !Array.isArray(sessions)) return [];
  return sessions
    .filter((session) => attendanceClassKey(session) === classKey)
    .slice()
    .sort((left, right) => String(left?.startTime || "").localeCompare(String(right?.startTime || ""))
      || Number(left?.classSessionId || 0) - Number(right?.classSessionId || 0));
}

export const COACH_ATTENDANCE_SESSION_PAGE_SIZE = 8;

export function paginateAttendanceSessions(sessions, requestedPage, pageSize = COACH_ATTENDANCE_SESSION_PAGE_SIZE) {
  const items = Array.isArray(sessions) ? sessions : [];
  const safePageSize = Number.isFinite(pageSize) && pageSize > 0 ? Math.floor(pageSize) : COACH_ATTENDANCE_SESSION_PAGE_SIZE;
  const totalPages = Math.max(1, Math.ceil(items.length / safePageSize));
  const parsedPage = Number.isFinite(requestedPage) ? Math.floor(requestedPage) : 1;
  const page = Math.min(Math.max(parsedPage, 1), totalPages);
  const start = (page - 1) * safePageSize;
  return {
    items: items.slice(start, start + safePageSize),
    page,
    pageSize: safePageSize,
    total: items.length,
    totalPages,
  };
}
