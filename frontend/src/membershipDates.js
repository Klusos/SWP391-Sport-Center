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

// Every Membership is assigned to exactly one display group; queued Memberships are
// persisted ACTIVE records whose start date is still in the future.
export function membershipSections(memberships, today = todayInVietnam()) {
  const current = [];
  const upcoming = [];
  const history = [];
  for (const membership of memberships || []) {
    const status = String(membership?.status || "").toUpperCase();
    const startDate = dateOnly(membership?.startDate);
    const endDate = dateOnly(membership?.endDate);
    if (isMembershipEffective(membership, today)) {
      current.push(membership);
    } else if (status === "ACTIVE" && startDate && endDate && startDate > today && endDate > today) {
      upcoming.push(membership);
    } else {
      history.push(membership);
    }
  }
  const byStartAscending = (a, b) => String(a?.startDate || "").localeCompare(String(b?.startDate || ""));
  current.sort(byStartAscending);
  upcoming.sort(byStartAscending);
  history.sort((a, b) => byStartAscending(b, a) || Number(b?.id || 0) - Number(a?.id || 0));
  return { current, upcoming, history };
}

export function hasOverlappingEffectiveMemberships(sections) {
  return (sections?.current?.length || 0) > 1;
}
