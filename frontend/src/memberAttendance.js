const validStatuses = new Set(["PRESENT", "ABSENT", "EXCUSED"]);
const instant = (value) => {
  if (!value) return NaN;
  const text = String(value);
  return new Date(/[zZ]|[+-]\d{2}:\d{2}$/.test(text) ? text : `${text}+07:00`).getTime();
};

export function buildMemberAttendanceClasses(trainingRows = [], attendanceRows = [], scheduledRows = [], now = Date.now()) {
  const classes = new Map();
  const ensureClass = (id, courseClass = {}) => {
    if (id == null) return null;
    const key = String(id);
    const definedCourseClass = Object.fromEntries(Object.entries(courseClass).filter(([, value]) => value != null));
    let entry = classes.get(key);
    if (!entry) {
      entry = { classId: Number(id), courseClass: { ...definedCourseClass, classId: Number(id) }, sessions: new Map() };
      classes.set(key, entry);
    } else {
      entry.courseClass = { ...entry.courseClass, ...definedCourseClass, classId: Number(id) };
    }
    return entry;
  };

  for (const item of Array.isArray(trainingRows) ? trainingRows : []) {
    const courseClass = item?.courseClass || {};
    const entry = ensureClass(courseClass.classId, courseClass);
    if (entry) entry.training = item;
  }

  for (const session of Array.isArray(scheduledRows) ? scheduledRows : []) {
    const entry = ensureClass(session?.classId, {
      className: session?.className,
      courseClassId: session?.classId,
    });
    if (entry && session?.id != null) {
      entry.sessions.set(String(session.id), { ...session, classSessionId: session.id, attendanceStatus: "UNRECORDED", attendance: null });
    }
  }

  for (const attendance of Array.isArray(attendanceRows) ? attendanceRows : []) {
    const entry = ensureClass(attendance?.courseClassId, {
      classId: attendance?.courseClassId,
      className: attendance?.courseClassName,
      courseName: attendance?.courseName,
      sportId: attendance?.sportId,
      sportName: attendance?.sportName,
      courseOfferingId: attendance?.courseOfferingId,
      courseOfferingName: attendance?.courseOfferingName,
    });
    if (!entry || attendance?.classSessionId == null) continue;
    const key = String(attendance.classSessionId);
    const scheduled = entry.sessions.get(key) || {};
    entry.sessions.set(key, {
      ...scheduled,
      id: attendance.classSessionId,
      classSessionId: attendance.classSessionId,
      classId: attendance.courseClassId,
      className: attendance.courseClassName,
      startTime: attendance.startTime || scheduled.startTime,
      endTime: attendance.endTime || scheduled.endTime,
      status: attendance.sessionStatus || scheduled.status,
      attendanceStatus: attendance.status || "UNRECORDED",
      attendance,
    });
  }

  return [...classes.values()].map((entry) => {
    const sessions = [...entry.sessions.values()]
      .sort((a, b) => String(a.startTime || "").localeCompare(String(b.startTime || ""))
        || Number(a.classSessionId) - Number(b.classSessionId))
      .map((session) => ({
        ...session,
        future: !session.attendance && Boolean(session.startTime) && instant(session.startTime) > now,
      }));
    const records = sessions.filter((session) => session.attendance
      && String(session.status || "").toUpperCase() !== "CANCELLED"
      && session.attendance.validForStatistics !== false
      && validStatuses.has(String(session.attendanceStatus).toUpperCase()));
    const summary = {
      present: records.filter((session) => String(session.attendanceStatus).toUpperCase() === "PRESENT").length,
      absent: records.filter((session) => String(session.attendanceStatus).toUpperCase() === "ABSENT").length,
      excused: records.filter((session) => String(session.attendanceStatus).toUpperCase() === "EXCUSED").length,
      recorded: records.length,
      future: sessions.filter((session) => String(session.status || "").toUpperCase() !== "CANCELLED"
        && !session.attendance && session.startTime && instant(session.startTime) > now).length,
      unrecorded: sessions.filter((session) => String(session.status || "").toUpperCase() !== "CANCELLED"
        && !session.attendance && (!session.startTime || instant(session.startTime) <= now)).length,
    };
    return {
      ...entry,
      sessions,
      summary,
      attendancePercent: attendancePercentage(summary.present, summary.recorded),
    };
  }).sort((a, b) => String(b.courseClass.startDate || "").localeCompare(String(a.courseClass.startDate || ""))
    || a.classId - b.classId);
}

export function buildMemberAttendanceOverview(rows = [], now = Date.now()) {
  return (Array.isArray(rows) ? rows : []).map((row) => {
    const sessions = (Array.isArray(row.sessions) ? row.sessions : []).map((session) => {
      const attendance = session.attendanceId == null ? null : {
        id: session.attendanceId, status: session.attendanceStatus,
        validForStatistics: session.validForStatistics,
      };
      return { classSessionId: session.classSessionId, startTime: session.startTime,
        endTime: session.endTime, status: session.sessionStatus, attendanceId: session.attendanceId,
        attendanceStatus: session.attendanceStatus || "UNRECORDED", attendance,
        future: Boolean(session.startTime) && instant(session.startTime) > now };
    });
    const summary = { present: Number(row.presentCount || 0), absent: Number(row.absentCount || 0),
      excused: Number(row.excusedCount || 0), recorded: Number(row.recordedElapsedCount || 0),
      eligibleElapsed: Number(row.eligibleElapsedCount || 0), unrecorded: Number(row.unrecordedElapsedCount || 0),
      future: Number(row.futureSessionCount || 0) };
    const courseClass = { classId: Number(row.courseClassId), className: row.courseClassName,
      courseId: row.courseId, courseName: row.courseName, courseOfferingId: row.courseOfferingId,
      courseOfferingName: row.courseOfferingName, startDate: row.offeringStartDate,
      endDate: row.offeringEndDate, sportId: row.sportId, sportName: row.sportName, coachName: row.coachName };
    return { classId: courseClass.classId, courseClass, sessions, summary,
      attendancePercent: attendancePercentage(summary.present, summary.recorded) };
  }).sort((a, b) => String(b.courseClass.startDate || "").localeCompare(String(a.courseClass.startDate || ""))
    || a.classId - b.classId);
}

export function filterMemberAttendanceClasses(classes, { year = "ALL", sportId = "ALL" } = {}) {
  return (Array.isArray(classes) ? classes : []).filter((item) => {
    const courseClass = item?.courseClass || {};
    if (year !== "ALL" && String(courseClass.startDate || "").slice(0, 4) !== year) return false;
    if (sportId !== "ALL" && String(courseClass.sportId) !== sportId) return false;
    return true;
  });
}

export function memberAttendanceYears(classes) {
  return [...new Set((Array.isArray(classes) ? classes : [])
    .map((item) => String(item?.courseClass?.startDate || "").slice(0, 4))
    .filter((year) => /^\d{4}$/.test(year)))].sort((a, b) => b.localeCompare(a));
}

export function paginateAttendance(items, requestedPage, pageSize) {
  const values = Array.isArray(items) ? items : [];
  const totalPages = Math.max(1, Math.ceil(values.length / pageSize));
  const page = Math.min(Math.max(1, Number(requestedPage) || 1), totalPages);
  return { items: values.slice((page - 1) * pageSize, page * pageSize), page, totalPages, total: values.length };
}

export function attendancePercentage(present, validRecorded) {
  const denominator = Number(validRecorded);
  if (!Number.isFinite(denominator) || denominator <= 0) return null;
  return Math.round((Number(present) / denominator) * 100);
}

export function attendanceDonutShares(summary = {}) {
  const recorded = Number(summary.recorded || 0);
  if (!Number.isFinite(recorded) || recorded <= 0) return { present: 0, absent: 0, excused: 0 };
  return {
    present: Number(summary.present || 0) / recorded * 100,
    absent: Number(summary.absent || 0) / recorded * 100,
    excused: Number(summary.excused || 0) / recorded * 100,
  };
}
