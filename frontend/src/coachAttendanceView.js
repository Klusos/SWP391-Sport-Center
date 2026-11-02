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
