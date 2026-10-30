export function attendanceRowKey(sessionId, memberId) {
  return String(sessionId) + ":" + String(memberId);
}

export function isCurrentAttendanceRosterRequest({
  requestId,
  latestRequestId,
  sessionId,
  selectedSessionId,
  mounted,
  effectActive = true,
}) {
  return mounted
    && effectActive
    && requestId === latestRequestId
    && String(sessionId) === String(selectedSessionId);
}
