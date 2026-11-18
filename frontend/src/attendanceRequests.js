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

export async function applyAttendanceRequestIfCurrent(promise, { isCurrent, onSuccess, onError, onSettled }) {
  let result;
  let failure;
  let failed = false;
  try {
    result = await promise;
  } catch (error) {
    failure = error;
    failed = true;
  }

  if (!isCurrent()) return false;
  if (failed) onError?.(failure);
  else onSuccess?.(result);
  if (isCurrent()) onSettled?.();
  return true;
}
