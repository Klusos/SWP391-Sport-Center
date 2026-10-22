const BUSINESS_TIME_ZONE = "Asia/Ho_Chi_Minh";

export const REVENUE_PURPOSES = [
  ["MEMBERSHIP", "Hội viên"],
  ["COURSE_REGISTRATION", "Đăng ký khóa học"],
  ["PT_BOOKING", "Huấn luyện cá nhân"],
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
