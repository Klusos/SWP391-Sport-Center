export function offeringVisibility(offering) {
  if (offering?.registrationAvailable === true
      && Array.isArray(offering.registrationReadyClassIds)
      && offering.registrationReadyClassIds.length > 0) return "REGISTRABLE";
  return "HISTORY";
}

/** Member discovery trusts backend registration/readiness validation as its authority. */
export function memberOfferingLists(offerings) {
  const current = (Array.isArray(offerings) ? offerings : [])
    .filter((offering) => offeringVisibility(offering) === "REGISTRABLE")
    .map((offering) => ({ ...offering, visibility: "REGISTRABLE" }))
    .sort((left, right) => String(left.startDate || "").localeCompare(String(right.startDate || "")));
  return { current, history: [] };
}
