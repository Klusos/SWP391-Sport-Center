const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/;
const DAY_MS = 24 * 60 * 60 * 1000;
const VIETNAM_PARTS = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Ho_Chi_Minh",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

const WEEKDAY_LABELS = ["Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"];

function validParts(parts) {
  if (!parts || parts.month < 1 || parts.month > 12 || parts.day < 1 || parts.day > 31
    || parts.hour < 0 || parts.hour > 23 || parts.minute < 0 || parts.minute > 59) return false;
  const check = new Date(Date.UTC(parts.year, parts.month - 1, parts.day));
  return check.getUTCFullYear() === parts.year && check.getUTCMonth() === parts.month - 1
    && check.getUTCDate() === parts.day;
}

function parseDateTime(value) {
  if (typeof value !== "string") return null;
  const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value);
  let parts;
  if (hasZone) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return null;
    try {
      const fields = Object.fromEntries(VIETNAM_PARTS.formatToParts(date)
        .filter((part) => part.type !== "literal").map((part) => [part.type, Number(part.value)]));
      parts = { year: fields.year, month: fields.month, day: fields.day, hour: fields.hour, minute: fields.minute };
    } catch { return null; }
  } else {
    const match = LOCAL_DATE_TIME.exec(value);
    if (!match) return null;
    parts = { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]), hour: Number(match[4]), minute: Number(match[5]) };
  }
  if (!validParts(parts)) return null;
  return { ...parts, epoch: Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute) };
}

function dateParts(epoch) {
  const date = new Date(epoch);
  return { year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, day: date.getUTCDate() };
}

function dateText(parts) {
  if (!parts) return "—";
  return `${String(parts.day).padStart(2, "0")}/${String(parts.month).padStart(2, "0")}/${parts.year}`;
}

function shortDateText(parts) {
  if (!parts) return "—";
  return `${String(parts.day).padStart(2, "0")}/${String(parts.month).padStart(2, "0")}`;
}

function timeText(parts) {
  if (!parts) return "—";
  return `${String(parts.hour).padStart(2, "0")}:${String(parts.minute).padStart(2, "0")}`;
}

function mondayEpoch(parts) {
  const dayEpoch = Date.UTC(parts.year, parts.month - 1, parts.day);
  const weekday = new Date(dayEpoch).getUTCDay();
  return dayEpoch - ((weekday + 6) % 7) * DAY_MS;
}

function weekdayIndex(parts) {
  return (new Date(Date.UTC(parts.year, parts.month - 1, parts.day)).getUTCDay() + 6) % 7;
}

function slotKey(session) {
  return `${session.weekday}|${session.startMinutes}|${session.endMinutes}`;
}

function weeklyCounts(week) {
  const counts = new Map();
  for (const session of week.sessions) {
    const key = slotKey(session);
    counts.set(key, (counts.get(key) || 0) + 1);
  }
  return counts;
}

function detectPattern(weeks, allSessionsValid) {
  if (!allSessionsValid || weeks.length < 2) return null;
  const slotKeys = new Set(weeks.flatMap((week) => [...weeklyCounts(week).keys()]));
  const slots = [];
  for (const key of slotKeys) {
    const counts = weeks.map((week) => weeklyCounts(week).get(key) || 0);
    const frequencies = new Map();
    for (const count of counts) if (count > 0) frequencies.set(count, (frequencies.get(count) || 0) + 1);
    const expectedCount = [...frequencies.entries()].sort((a, b) => b[1] - a[1] || b[0] - a[0])[0]?.[0] || 0;
    const supportingWeeks = counts.filter((count) => count >= expectedCount).length;
    if (expectedCount && supportingWeeks >= 2) {
      const [weekday, startMinutes, endMinutes] = key.split("|").map(Number);
      slots.push({ key, weekday, startMinutes, endMinutes, expectedCount });
    }
  }
  if (!slots.length) return null;
  const completeWeeks = weeks.filter((week) => {
    const counts = weeklyCounts(week);
    return slots.every((slot) => (counts.get(slot.key) || 0) >= slot.expectedCount);
  });
  return completeWeeks.length >= 2 ? slots.sort((a, b) => a.weekday - b.weekday
    || a.startMinutes - b.startMinutes || a.endMinutes - b.endMinutes) : null;
}

function slotLabel(slot) {
  const timeRange = formatScheduleTimeRange(slot);
  return `${WEEKDAY_LABELS[slot.weekday]} ${timeRange}${slot.expectedCount > 1 ? ` · ${slot.expectedCount} buổi` : ""}`;
}

function actualSlots(week) {
  const counts = weeklyCounts(week);
  return [...counts.entries()].map(([key, count]) => {
    const [weekday, startMinutes, endMinutes] = key.split("|").map(Number);
    return { key, weekday, startMinutes, endMinutes, count };
  }).sort((a, b) => a.weekday - b.weekday || a.startMinutes - b.startMinutes || a.endMinutes - b.endMinutes);
}

function annotateWeek(week, pattern, isBoundary) {
  if (!pattern) return { ...week, exceptional: false, actualSlots: actualSlots(week), missingSlots: [] };
  const counts = weeklyCounts(week);
  const missingSlots = pattern.map((slot) => ({
    ...slot,
    missingCount: Math.max(0, slot.expectedCount - (counts.get(slot.key) || 0)),
  })).filter((slot) => slot.missingCount > 0);
  const extras = actualSlots(week).filter((slot) => {
    const expected = pattern.find((item) => item.key === slot.key)?.expectedCount || 0;
    return slot.count > expected;
  });
  const exceptional = extras.length > 0 || (!isBoundary && missingSlots.length > 0);
  return { ...week, exceptional, actualSlots: actualSlots(week), missingSlots: isBoundary && extras.length === 0 ? [] : missingSlots };
}

export function formatRegistrationSessionDate(value) {
  return dateText(parseDateTime(value));
}

export function formatRegistrationSessionTime(value) {
  return timeText(parseDateTime(value));
}

export function buildRegistrationScheduleSummary(sessions) {
  const source = Array.isArray(sessions) ? sessions : [];
  const normalized = source.map((session, index) => {
    const start = parseDateTime(session?.startTime);
    const end = parseDateTime(session?.endTime);
    const weekday = start ? weekdayIndex(start) : null;
    const startMinutes = start ? start.hour * 60 + start.minute : null;
    const endMinutes = end ? end.hour * 60 + end.minute : null;
    return {
      session,
      sourceIndex: index,
      start,
      end,
      weekday,
      startMinutes,
      endMinutes,
      weekStart: start ? mondayEpoch(start) : null,
      order: start?.epoch ?? Number.MAX_SAFE_INTEGER,
      dateLabel: dateText(start),
      startLabel: timeText(start),
      endLabel: timeText(end),
    };
  }).sort((a, b) => a.order - b.order || a.sourceIndex - b.sourceIndex);

  const dated = normalized.filter((item) => item.start && item.weekStart != null);
  const undatedSessions = normalized.filter((item) => !item.start);
  const weeksByStart = new Map();
  if (dated.length) {
    const firstWeek = Math.min(...dated.map((item) => item.weekStart));
    const lastWeek = Math.max(...dated.map((item) => item.weekStart));
    for (let start = firstWeek; start <= lastWeek; start += 7 * DAY_MS) {
      const monday = dateParts(start);
      const sunday = dateParts(start + 6 * DAY_MS);
      weeksByStart.set(start, {
        weekStart: start,
        number: Math.floor((start - firstWeek) / (7 * DAY_MS)) + 1,
        startDate: monday,
        endDate: sunday,
        rangeLabel: `${shortDateText(monday)}–${shortDateText(sunday)}`,
        sessions: [],
      });
    }
    for (const item of dated) weeksByStart.get(item.weekStart).sessions.push(item);
  }
  const rawWeeks = [...weeksByStart.values()];
  const allSessionsValid = normalized.every((item) => item.start && item.end);
  const pattern = detectPattern(rawWeeks, allSessionsValid);
  const weeks = rawWeeks.map((week, index) => annotateWeek(week, pattern, index === 0 || index === rawWeeks.length - 1));
  const sessionRows = normalized.map((item, index) => ({ ...item, number: index + 1 }));
  const sessionsBySourceIndex = new Map(sessionRows.map((item) => [item.sourceIndex, item]));
  const numberedWeeks = weeks.map((week) => ({
    ...week,
    sessions: week.sessions.map((item) => sessionsBySourceIndex.get(item.sourceIndex)),
  }));
  const patternWeekdays = pattern ? [...new Set(pattern.map((slot) => slot.weekday))] : [];
  const startDate = dated.length ? dateParts(dated[0].start.epoch) : null;
  const endDate = dated.length ? dateParts(dated[dated.length - 1].start.epoch) : null;

  return {
    totalSessions: normalized.length,
    weekCount: rawWeeks.length,
    startDate,
    endDate,
    pattern: pattern ? {
      slots: pattern,
      weekdays: patternWeekdays.map((weekday) => ({ index: weekday, label: WEEKDAY_LABELS[weekday] })),
      slotLabels: pattern.map(slotLabel),
    } : null,
    weeks: numberedWeeks,
    undatedSessions: undatedSessions.map((item) => sessionsBySourceIndex.get(item.sourceIndex)),
    sessions: sessionRows,
  };
}

export function formatScheduleDateRange(start, end) {
  if (!start || !end) return "—";
  return `${dateText(start)} – ${dateText(end)}`;
}

export function formatScheduleWeekRange(start, end) {
  if (!start || !end) return "—";
  const yearsDiffer = start.year !== end.year;
  return yearsDiffer
    ? `${dateText(start)}–${dateText(end)}`
    : `${shortDateText(start)}–${shortDateText(end)}`;
}

export function formatScheduleActualSlot(slot) {
  return `${WEEKDAY_LABELS[slot.weekday]}: ${formatScheduleTimeRange(slot)}${slot.count > 1 ? ` · ${slot.count} buổi` : ""}`;
}

export function formatScheduleMissingSlot(slot) {
  return slot.missingCount > 1
    ? `Thiếu ${slot.missingCount} buổi ${WEEKDAY_LABELS[slot.weekday]} ${formatScheduleTimeRange(slot)} so với lịch lặp đã xác lập.`
    : `Không có buổi ${WEEKDAY_LABELS[slot.weekday]} ${formatScheduleTimeRange(slot)} trong lịch đã công bố tuần này.`;
}

export function formatScheduleTimeRange(slot) {
  const timeFor = (minutes) => Number.isFinite(minutes)
    ? timeText({ hour: Math.floor(minutes / 60), minute: minutes % 60 })
    : "—";
  return `${timeFor(slot.startMinutes)}–${timeFor(slot.endMinutes)}`;
}
