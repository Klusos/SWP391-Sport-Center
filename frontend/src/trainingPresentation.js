const PROGRESS_LEVELS = {
  1: "Chưa đạt",
  2: "Cần cố gắng",
  3: "Đúng tiến độ",
  4: "Tốt",
  5: "Vượt mục tiêu",
};

export function progressLevelLabel(value) {
  return PROGRESS_LEVELS[Number(value)] || "Chưa có đánh giá";
}

export function trainingStartYears(rows) {
  return [...new Set((Array.isArray(rows) ? rows : [])
    .map((item) => String(item?.courseClass?.startDate || "").slice(0, 4))
    .filter((year) => /^\d{4}$/.test(year)))].sort((a, b) => b.localeCompare(a));
}

export function filterMemberTrainingRows(rows, { year = "ALL", sportId = "ALL", lifecycle = "ALL", today } = {}) {
  return (Array.isArray(rows) ? rows : []).filter((item) => {
    const courseClass = item?.courseClass || {};
    if (year !== "ALL" && String(courseClass.startDate || "").slice(0, 4) !== year) return false;
    if (sportId !== "ALL" && String(courseClass.sportId) !== sportId) return false;
    if (lifecycle !== "ALL") {
      const endDate = /^\d{4}-\d{2}-\d{2}$/.test(String(courseClass.endDate || "")) ? courseClass.endDate : null;
      if (!endDate || !today) return false;
      if (lifecycle === "NOT_ENDED" && endDate < today) return false;
      if (lifecycle === "COMPLETED" && endDate >= today) return false;
    }
    return true;
  });
}
