const BUSINESS_TIME_ZONE = "Asia/Ho_Chi_Minh";

export const REVENUE_PURPOSES = [
  ["MEMBERSHIP", "Hội viên"],
  ["COURSE_REGISTRATION", "Đăng ký khóa học"],
  ["PT_BOOKING", "Huấn luyện cá nhân"],
  ["UPGRADE", "Nâng cấp gói"],
];

export function hcmDate(value = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: BUSINESS_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(value);
  const values = Object.fromEntries(parts.map(({ type, value: part }) => [type, part]));
  return `${values.year}-${values.month}-${values.day}`;
}

export function defaultRevenueRange(now = new Date(), months = 8) {
  const today = hcmDate(now);
  const [year, month] = today.split("-").map(Number);
  const firstMonth = new Date(Date.UTC(year, month - months, 1));
  return {
    from: `${firstMonth.getUTCFullYear()}-${String(firstMonth.getUTCMonth() + 1).padStart(2, "0")}-01`,
    to: today,
  };
}

function shiftBusinessDate(date, offsetDays) {
  const [year, month, day] = date.split("-").map(Number);
  const shifted = new Date(Date.UTC(year, month - 1, day + offsetDays));
  return `${shifted.getUTCFullYear()}-${String(shifted.getUTCMonth() + 1).padStart(2, "0")}-${String(shifted.getUTCDate()).padStart(2, "0")}`;
}

export function defaultReportRevenueRange(now = new Date()) {
  const today = hcmDate(now);
  return { from: `${today.slice(0, 7)}-01`, to: today };
}

export const REVENUE_DATE_PRESETS = [
  ["TODAY", "Hôm nay"],
  ["LAST_7_DAYS", "7 ngày gần nhất"],
  ["THIS_MONTH", "Tháng này"],
  ["LAST_30_DAYS", "30 ngày gần nhất"],
];

export function revenueDatePresetRange(preset, now = new Date()) {
  const today = hcmDate(now);
  const firstOfMonth = `${today.slice(0, 7)}-01`;
  switch (preset) {
    case "TODAY": return { from: today, to: today };
    case "LAST_7_DAYS": return { from: shiftBusinessDate(today, -6), to: today };
    case "THIS_MONTH": return { from: firstOfMonth, to: today };
    case "LAST_30_DAYS": return { from: shiftBusinessDate(today, -29), to: today };
    default: return null;
  }
}

export function isValidRevenueRange({ from, to } = {}) {
  return Boolean(from && to && to >= from);
}

export function formatVnd(value) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(Number(value || 0));
}

export function formatRevenuePeriod(value, groupBy) {
  const [year, month, day] = String(value).split("-").map(Number);
  if (groupBy === "YEAR") return String(year);
  if (groupBy === "MONTH") return `${String(month).padStart(2, "0")}/${year}`;
  return `${String(day).padStart(2, "0")}/${String(month).padStart(2, "0")}`;
}
