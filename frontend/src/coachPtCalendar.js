import { vietnamInstant } from "./ptBooking.js";

const BUSINESS_TIME_ZONE = "Asia/Ho_Chi_Minh";

export function vietnamDateKey(value) {
  const instant = value instanceof Date ? value : vietnamInstant(value);
  if (instant && Number.isNaN(instant.getTime())) return null;
  if (!instant) return null;
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: BUSINESS_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(instant);
  const part = Object.fromEntries(parts.map(({ type, value: partValue }) => [type, partValue]));
  return `${part.year}-${part.month}-${part.day}`;
}

function dateFromKey(key) {
  const [year, month, day] = key.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

function keyFromDate(date) {
  return [date.getUTCFullYear(), String(date.getUTCMonth() + 1).padStart(2, "0"), String(date.getUTCDate()).padStart(2, "0")].join("-");
}

export function coachPtWeekDays(now = new Date(), offset = 0) {
  const today = vietnamDateKey(now);
  if (!today) return [];
  const monday = dateFromKey(today);
  const mondayOffset = (monday.getUTCDay() + 6) % 7;
  monday.setUTCDate(monday.getUTCDate() - mondayOffset + offset * 7);
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(monday);
    day.setUTCDate(monday.getUTCDate() + index);
    return { key: keyFromDate(day), date: day };
  });
}

export function coachPtStatusClass(status) {
  if (status === "PENDING_PAYMENT") return "pending-payment";
  return String(status || "unknown").toLowerCase().replaceAll("_", "-");
}

export function coachPtSessionGroups(rows, days, selectedDay = null, statusFilter = "ALL") {
  const dayKeys = new Set(days.map((day) => day.key));
  const filtered = (Array.isArray(rows) ? rows : [])
    .filter((booking) => {
      const dayKey = vietnamDateKey(booking?.startTime);
      return dayKey && dayKeys.has(dayKey) && (!selectedDay || dayKey === selectedDay)
        && (statusFilter === "ALL" || booking?.status === statusFilter);
    })
    .sort((left, right) => {
      const timeDifference = vietnamInstant(left.startTime) - vietnamInstant(right.startTime);
      return timeDifference || Number(left.id) - Number(right.id);
    });

  const groups = new Map();
  for (const booking of filtered) {
    const key = vietnamDateKey(booking.startTime);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(booking);
  }
  return [...groups].map(([key, sessions]) => ({ key, sessions }));
}

export function coachPtDayCounts(rows, days, statusFilter = "ALL") {
  return Object.fromEntries(days.map(({ key }) => [key, (Array.isArray(rows) ? rows : []).filter((booking) =>
    vietnamDateKey(booking?.startTime) === key && (statusFilter === "ALL" || booking?.status === statusFilter)
  ).length]));
}
