export function todayInVietnam(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  return `${values.year}-${values.month}-${values.day}`;
}
function dateOnly(value) {
  const date = typeof value === "string" ? value.slice(0, 10) : "";
  return /^\d{4}-\d{2}-\d{2}$/.test(date) ? date : null;
}
export function isMembershipEffective(membership, today = todayInVietnam()) {
  const startDate = dateOnly(membership?.startDate);
  const endDate = dateOnly(membership?.endDate);
  return String(membership?.status || "").toUpperCase() === "ACTIVE" && Boolean(startDate && endDate) && startDate <= today && today < endDate;
}
