export function coachAssessmentStatusSummary(assignments) {
  const rows = Array.isArray(assignments) ? assignments : [];
  return {
    pending: rows.filter((item) => item?.status === "PENDING").length,
    processed: rows.filter((item) => item?.status === "COMPLETED" || item?.status === "CANCELLED").length,
  };
}
