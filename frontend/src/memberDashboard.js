export function resolveMemberLevelSportName(level, sports) {
  const embeddedName = level?.sportName || level?.sport?.name;
  if (typeof embeddedName === "string" && embeddedName.trim()) return embeddedName.trim();

  const sportId = Number(level?.sportId);
  if (!Number.isFinite(sportId)) return null;

  const sport = (Array.isArray(sports) ? sports : []).find((item) => Number(item?.id) === sportId);
  return typeof sport?.name === "string" && sport.name.trim() ? sport.name.trim() : null;
}
