const FOURTEEN_DAYS_MS = 14 * 24 * 60 * 60 * 1000;

export function scheduledOpeningInstant(registrationDeadline) {
  if (!registrationDeadline) return null;
  const localDateTime = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(registrationDeadline);
  const instant = new Date(localDateTime ? `${registrationDeadline}+07:00` : registrationDeadline);
  return Number.isNaN(instant.getTime()) ? null : new Date(instant.getTime() - FOURTEEN_DAYS_MS);
}

export function isOfferingCommercialEditLocked(offering, now = new Date()) {
  const opening = scheduledOpeningInstant(offering?.registrationDeadline);
  return opening != null && now.getTime() >= opening.getTime();
}
