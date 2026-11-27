/** Present the persisted attendance state with authoritative server-side eligibility. */
export function attendanceSessionStatusLabel(session) {
  const lifecycle = String(session?.sessionStatus || "").toUpperCase();
  if (lifecycle === "CANCELLED") return "";

  const completeness = String(session?.attendanceCompleteness || "").toUpperCase();
  if (session?.attendanceTimeEligible === false || completeness === "NOT_YET_ELIGIBLE") {
    return "Chưa đến giờ điểm danh";
  }
  if (completeness === "NOT_APPLICABLE") return "Không có học viên";
  if (completeness === "NOT_RECORDED") return "Chưa điểm danh";
  if (completeness === "PARTIAL") return "Chưa hoàn tất";
  if (completeness === "COMPLETE") return "Đã điểm danh";
  return "";
}
