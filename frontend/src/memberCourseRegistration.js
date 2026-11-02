const ACTIVE_REGISTRATION_STATUSES = new Set(["CONFIRMED", "PENDING_PAYMENT"]);

/** Returns the strongest active course-level status represented by registration DTOs. */
export function courseRegistrationStatus(registrations, courseId) {
  if (courseId == null || !Array.isArray(registrations)) return null;
  const statuses = registrations
    .filter((registration) => Number(registration.courseId) === Number(courseId))
    .map((registration) => String(registration.status || "").toUpperCase());
  if (statuses.includes("PENDING_PAYMENT")) return "PENDING_PAYMENT";
  if (statuses.includes("CONFIRMED")) return "CONFIRMED";
  return null;
}

/** Only live registrations suppress a new offering selection; pending payment takes display priority. */
export function activeOfferingRegistration(registrations, offeringId) {
  if (offeringId == null || !Array.isArray(registrations)) return null;
  return registrations
    .filter((registration) => Number(registration.courseOfferingId) === Number(offeringId)
      && ACTIVE_REGISTRATION_STATUSES.has(String(registration.status || "").toUpperCase()))
    .sort((left, right) => {
      const leftPending = String(left.status).toUpperCase() === "PENDING_PAYMENT";
      const rightPending = String(right.status).toUpperCase() === "PENDING_PAYMENT";
      return Number(rightPending) - Number(leftPending);
    })[0] || null;
}

export function courseRegistrationStatusLabel(status) {
  return status === "PENDING_PAYMENT" ? "Chờ thanh toán"
    : status === "CONFIRMED" ? "Đã xác nhận" : "Không còn hiệu lực";
}
