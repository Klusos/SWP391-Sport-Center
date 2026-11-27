export function resolveMemberLevelSportName(level, sports) {
  const embeddedName = level?.sportName || level?.sport?.name;
  if (typeof embeddedName === "string" && embeddedName.trim()) return embeddedName.trim();

  const sportId = Number(level?.sportId);
  if (!Number.isFinite(sportId)) return null;

  const sport = (Array.isArray(sports) ? sports : []).find((item) => Number(item?.id) === sportId);
  return typeof sport?.name === "string" && sport.name.trim() ? sport.name.trim() : null;
}

const VN_ZONE = "Asia/Ho_Chi_Minh";
export function memberLevelLabel(value) {
  const labels = { BEGINNER: "Cơ bản", INTERMEDIATE: "Trung cấp", ADVANCED: "Nâng cao" };
  return labels[String(value || "").trim().toUpperCase()] || "Chưa xác định";
}
function localDateParts(date) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: VN_ZONE, year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(date);
  return Object.fromEntries(parts.filter((part) => part.type !== "literal").map(({ type, value }) => [type, Number(value)]));
}

function asVietnamInstant(value) {
  if (!value) return NaN;
  const text = String(value);
  return new Date(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(text)
    ? `${text}+07:00` : text).getTime();
}

export function memberDashboardPreviewBounds(now = new Date()) {
  const { year, month, day } = localDateParts(now);
  const localMidnightUtc = Date.UTC(year, month - 1, day);
  const weekday = new Date(localMidnightUtc).getUTCDay();
  const sinceMonday = (weekday + 6) % 7;
  const start = localMidnightUtc - sinceMonday * 86_400_000 - 7 * 3_600_000;
  return { start, end: start + 14 * 86_400_000 };
}

export function isMemberDashboardPreviewSession(value, now = new Date()) {
  const instant = asVietnamInstant(value);
  const { start, end } = memberDashboardPreviewBounds(now);
  return Number.isFinite(instant) && instant > now.getTime() && instant < end && instant >= start;
}

export function paginateItems(items, page, pageSize) {
  const rows = Array.isArray(items) ? items : [];
  const pages = Math.max(1, Math.ceil(rows.length / pageSize));
  const currentPage = Math.min(Math.max(1, Number(page) || 1), pages);
  return { items: rows.slice((currentPage - 1) * pageSize, currentPage * pageSize), page: currentPage, pages, total: rows.length };
}

export function memberRecentAttendancePreview(records) {
  return (Array.isArray(records) ? records : [])
    .map((record, index) => ({ record, index, at: asVietnamInstant(record?.startTime || record?.when || record?.sessionStartTime) }))
    .filter(({ record, at }) => Number.isFinite(at)
      && record?.validForStatistics === true
      && ["PRESENT", "ABSENT", "EXCUSED"].includes(String(record?.status || "").toUpperCase())
      && !/CANCEL/i.test(String(record?.sessionStatus || record?.classSessionStatus || "")))
    .sort((a, b) => b.at - a.at || a.index - b.index)
    .slice(0, 4)
    .map(({ record }) => record);
}

export function sortMemberSchedule(items) {
  return (Array.isArray(items) ? items : []).slice().sort((a, b) => asVietnamInstant(a?.start) - asVietnamInstant(b?.start));
}
