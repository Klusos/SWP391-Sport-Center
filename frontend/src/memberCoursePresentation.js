export function formatTargetAgeRange(min, max) {
  if (min != null && max != null) return `${min}–${max} tuổi`;
  if (min != null) return `Từ ${min} tuổi`;
  if (max != null) return `Đến ${max} tuổi`;
  return "";
}

export function formatClassScheduleSummary(sessions, formatDateTime) {
  if (!Array.isArray(sessions) || sessions.length === 0) return "Lịch chưa được công bố";
  const firstStart = sessions[0]?.startTime;
  const time = firstStart ? formatDateTime(firstStart) : "";
  return time && time !== "—"
    ? `${sessions.length} buổi được công bố · ${time}`
    : `${sessions.length} buổi được công bố`;
}

export function toggleExpandedClass(currentClassId, nextClassId) {
  return currentClassId === nextClassId ? null : nextClassId;
}
