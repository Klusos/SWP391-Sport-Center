export function filterCourses(courses, { search = "", sportId = "", status = "" } = {}) {
  const query = search.trim().toLocaleLowerCase("vi-VN");
  return (Array.isArray(courses) ? courses : []).filter((course) => {
    const matchesName = !query || String(course.name || "").toLocaleLowerCase("vi-VN").includes(query);
    const matchesSport = !sportId || String(course.sportId) === String(sportId);
    const matchesStatus = !status || String(course.status).toUpperCase() === status.toUpperCase();
    return matchesName && matchesSport && matchesStatus;
  });
}

export function buildCoursePayload(form, editing = false) {
  const sportId = Number(form.sportId);
  if (!Number.isInteger(sportId) || sportId <= 0) {
    throw new Error("Vui lòng chọn Sport cho khóa học.");
  }
  const agePolicy = String(form.agePolicy || "RECOMMENDED").toUpperCase();
  if (!["RECOMMENDED", "REQUIRED"].includes(agePolicy)) throw new Error("Chính sách độ tuổi không hợp lệ.");
  const targetAgeMin = form.targetAgeMin === "" || form.targetAgeMin == null ? null : Number(form.targetAgeMin);
  const targetAgeMax = form.targetAgeMax === "" || form.targetAgeMax == null ? null : Number(form.targetAgeMax);
  if ([targetAgeMin, targetAgeMax].some((age) => age != null && (!Number.isInteger(age) || age < 0))) {
    throw new Error("Độ tuổi phải là số nguyên không âm.");
  }
  if (targetAgeMin != null && targetAgeMax != null && targetAgeMin > targetAgeMax) {
    throw new Error("Tuổi tối thiểu không được lớn hơn tuổi tối đa.");
  }
  if (agePolicy === "REQUIRED" && (targetAgeMin == null || targetAgeMax == null)) {
    throw new Error("Độ tuổi bắt buộc cần có cả tuổi tối thiểu và tối đa.");
  }
  const payload = {
    name: String(form.name || "").trim(),
    description: form.description || "",
    targetAgeMin,
    targetAgeMax,
    agePolicy,
    level: form.level,
    learningOutcome: form.learningOutcome || "",
  };
  if (!editing) payload.sportId = sportId;
  if (editing) payload.status = form.status;
  return payload;
}

export function vietnamBusinessDate(value = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(value);
  const dateParts = Object.fromEntries(parts.map(({ type, value: part }) => [type, part]));
  return `${dateParts.year}-${dateParts.month}-${dateParts.day}`;
}

export function offeringTimeStatus(offering, businessDate = vietnamBusinessDate()) {
  const startDate = offering?.startDate;
  const endDate = offering?.endDate;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate || "") || !/^\d{4}-\d{2}-\d{2}$/.test(endDate || "") || startDate > endDate) return null;
  if (businessDate < startDate) return "UPCOMING";
  if (businessDate <= endDate) return "ONGOING";
  return "PAST";
}

export function filterCourseOfferings(offerings, { search = "", timeStatus = "", status = "", sort = "NEWEST", businessDate } = {}) {
  const query = search.trim().toLocaleLowerCase("vi-VN");
  return (Array.isArray(offerings) ? offerings : [])
    .filter((offering) => !query || String(offering.name || "").toLocaleLowerCase("vi-VN").includes(query))
    .filter((offering) => !timeStatus || offeringTimeStatus(offering, businessDate) === timeStatus)
    .filter((offering) => !status || String(offering.status || "").toUpperCase() === status.toUpperCase())
    .sort((left, right) => {
      const byDate = String(right.startDate || "").localeCompare(String(left.startDate || ""));
      return (sort === "OLDEST" ? -byDate : byDate) || Number(right.id || 0) - Number(left.id || 0);
    });
}

export function filterCourseClasses(classes, { search = "", status = "" } = {}) {
  const query = search.trim().toLocaleLowerCase("vi-VN");
  return (Array.isArray(classes) ? classes : [])
    .filter((item) => !query || String(item.name || "").toLocaleLowerCase("vi-VN").includes(query))
    .filter((item) => !status || String(item.status || "").toUpperCase() === status.toUpperCase())
    .sort((left, right) => String(left.name || "").localeCompare(String(right.name || ""), "vi-VN"));
}

export function classSessionVietnamDate(value) {
  if (typeof value !== "string" || !value) return "";
  const localMatch = /^(\d{4}-\d{2}-\d{2})T\d{2}:\d{2}/.exec(value);
  if (localMatch && !/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)) {
    const [, year, month, day] = /^(\d{4})-(\d{2})-(\d{2})$/.exec(localMatch[1]) || [];
    const date = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day)));
    if (date.getUTCFullYear() !== Number(year) || date.getUTCMonth() !== Number(month) - 1
      || date.getUTCDate() !== Number(day)) return "";
    return localMatch[1];
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map(({ type, value: part }) => [type, part]));
  return `${values.year}-${values.month}-${values.day}`;
}

export function formatClassSessionDate(value) {
  const date = classSessionVietnamDate(value);
  if (!date) return "—";
  const [, year, month, day] = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date) || [];
  return year ? `${day}/${month}/${year}` : "—";
}

export function formatClassSessionTime(value) {
  if (typeof value !== "string" || !value) return "—";
  if (!classSessionVietnamDate(value)) return "—";
  const localMatch = /T(\d{2}):(\d{2})/.exec(value);
  if (localMatch && !/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)) {
    const hour = Number(localMatch[1]);
    const minute = Number(localMatch[2]);
    return hour <= 23 && minute <= 59 ? `${localMatch[1]}:${localMatch[2]}` : "—";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Ho_Chi_Minh", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).format(date);
}

function classSessionTimestamp(value) {
  if (typeof value !== "string" || !value) return Number.POSITIVE_INFINITY;
  const localMatch = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?)$/.exec(value);
  const timestamp = localMatch
    ? Date.parse(`${localMatch[1].replace(/\.(\d{3})\d+$/, ".$1")}+07:00`)
    : Date.parse(value);
  return Number.isNaN(timestamp) ? Number.POSITIVE_INFINITY : timestamp;
}

export function filterClassSessions(sessions, { status = "", startDate = "", endDate = "", sort = "ASC" } = {}) {
  const direction = String(sort).toUpperCase() === "DESC" ? -1 : 1;
  return (Array.isArray(sessions) ? sessions : [])
    .filter((session) => !status || String(session.status || "").toUpperCase() === status.toUpperCase())
    .filter((session) => {
      const date = classSessionVietnamDate(session.startTime);
      if ((startDate || endDate) && !date) return false;
      return (!startDate || date >= startDate) && (!endDate || date <= endDate);
    })
    .sort((left, right) => direction * (classSessionTimestamp(left.startTime) - classSessionTimestamp(right.startTime))
      || direction * (Number(left.id || 0) - Number(right.id || 0)));
}

export function paginateItems(items, page = 0, pageSize = 10) {
  const safePageSize = Math.max(1, Math.floor(pageSize));
  const pageCount = Math.max(1, Math.ceil(items.length / safePageSize));
  const pageIndex = Math.min(Math.max(0, Math.floor(page)), pageCount - 1);
  return {
    items: items.slice(pageIndex * safePageSize, (pageIndex + 1) * safePageSize),
    page: pageIndex,
    pageCount,
    total: items.length,
  };
}

export function qualifiedClassCoaches(coaches, coachSports, sportId) {
  if (sportId == null) return [];
  const qualifiedIds = new Set((coachSports || [])
    .filter((assignment) => Number(assignment?.sportId) === Number(sportId))
    .map((assignment) => Number(assignment?.coachId)));
  return (coaches || []).filter((coach) =>
    String(coach?.status || "").toUpperCase() === "ACTIVE" && qualifiedIds.has(Number(coach?.id)));
}
