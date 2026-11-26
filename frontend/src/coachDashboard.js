const asList = (value) => Array.isArray(value) ? value : value?.content ?? [];

export function confirmedEnrollmentCount(classes) {
  return asList(classes).reduce((sum, item) => sum + Math.max(0, Number(item?.confirmedMembers) || 0), 0);
}

export function pendingCoachAssessments(assignments) {
  return asList(assignments).filter((assessment) => assessment?.status === "PENDING");
}

export function coachAssessmentSummary(assessment, sports = []) {
  const sportNames = new Map(asList(sports).map((sport) => [Number(sport.id), sport.name]));
  const memberId = assessment?.memberId;
  const sportId = assessment?.sportId;
  return {
    member: assessment?.memberName || assessment?.memberFullName || assessment?.member?.fullName
      || (memberId != null ? `Học viên #${memberId}` : "Học viên"),
    sport: assessment?.sportName || assessment?.sport?.name || sportNames.get(Number(sportId))
      || (sportId != null ? `Bộ môn #${sportId}` : "Bộ môn"),
    status: ({ PENDING: "Đang chờ", COMPLETED: "Đã hoàn tất", CANCELLED: "Đã hủy" })[assessment?.status] || assessment?.status || "Chưa xác định",
    assignment: assessment?.assignedCoachId != null
      ? `Được phân công cho HLV #${assessment.assignedCoachId}` : "Được phân công cho HLV",
  };
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
