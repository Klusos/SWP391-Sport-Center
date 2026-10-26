export const API_BASE_URL = import.meta.env?.VITE_API_BASE_URL || "http://localhost:8080";
const API_BASE = API_BASE_URL.replace(/\/$/, "");
let unauthorizedHandler = null;

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = typeof handler === "function" ? handler : null;
}

function getBearerToken(headers) {
  const authorization = headers instanceof Headers
    ? headers.get("Authorization")
    : headers?.Authorization ?? headers?.authorization;
  const match = typeof authorization === "string"
    ? authorization.match(/^Bearer\s+(.+)$/i)
    : null;
  return match?.[1] || null;
}

async function request(path, options = {}) {
  const requestToken = getBearerToken(options.headers);
  const response = await fetch(API_BASE + path, options);
  const text = await response.text();
  let data = {};
  if (text) {
    try { data = JSON.parse(text); } catch { data = {}; }
  }
  if (!response.ok) {
    if (response.status === 401 && requestToken) {
      unauthorizedHandler?.(requestToken);
    }
    const error = new Error(data.message || data.detail || ("Request failed (" + response.status + ")"));
    error.status = response.status;
    throw error;
  }
  return data;
}

function authenticatedHeaders(token) {
  return { Authorization: "Bearer " + token };
}

export function loginApi(email, password) {
  return request("/api/auth/login", {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
}

export function createMemberApi(token, payload) {
  return request("/api/members", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...authenticatedHeaders(token),
    },
    body: JSON.stringify(payload),
  });
}

export function updateMemberApi(token, memberId, payload) {
  return request("/api/members/" + encodeURIComponent(memberId), {
    method: "PUT",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function deactivateMemberApi(token, memberId) {
  return request("/api/members/" + encodeURIComponent(memberId) + "/deactivate", {
    method: "PATCH",
    headers: authenticatedHeaders(token),
  });
}

export function createStaffCoachApi(token, payload) {
  return request("/api/staff/coaches", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function createStaffReceptionistApi(token, payload) {
  return request("/api/staff/receptionists", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateStaffApi(token, userId, payload) {
  return request("/api/staff/" + encodeURIComponent(userId), {
    method: "PUT",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateStaffStatusApi(token, userId, status) {
  return request("/api/staff/" + encodeURIComponent(userId) + "/status", {
    method: "PATCH",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
}

export function getCurrentUserApi(token) {
  return request("/api/users/me", { headers: authenticatedHeaders(token) });
}

export function getUsersApi(token) {
  return request("/api/users", { headers: authenticatedHeaders(token) });
}

export function getUserApi(token, userId) {
  return request("/api/users/" + encodeURIComponent(userId), {
    headers: authenticatedHeaders(token),
  });
}

export function getMemberProfilesApi(token) {
  return request("/api/member-profiles", { headers: authenticatedHeaders(token) });
}

export function getMemberSportLevelsApi(token, memberId) {
  return request("/api/member-sport-levels/member/" + encodeURIComponent(memberId), {
    headers: authenticatedHeaders(token),
  });
}

export function getMyMemberSportLevelsApi(token) {
  return request("/api/member-sport-levels/me", { headers: authenticatedHeaders(token) });
}

export function getMySkillAssessmentsApi(token) {
  return request("/api/skill-assessments/me", { headers: authenticatedHeaders(token) });
}

export function getMyConsultationRequestsApi(token) {
  return request("/api/consultation-requests/me", { headers: authenticatedHeaders(token) });
}

export function createMemberProfileApi(token, payload) {
  return request("/api/member-profiles", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateMemberProfileApi(token, profileId, payload) {
  return request("/api/member-profiles/" + encodeURIComponent(profileId), {
    method: "PUT",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getConsultationRequestsApi(token, { status = "", page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);
  return request("/api/consultation-requests?" + params.toString(), {
    headers: authenticatedHeaders(token),
  });
}

export function takeConsultationRequestApi(token, requestId) {
  return request("/api/consultation-requests/" + encodeURIComponent(requestId) + "/take", {
    method: "PATCH",
    headers: authenticatedHeaders(token),
  });
}

export function getConsultationRequestApi(token, requestId) {
  return request("/api/consultation-requests/" + encodeURIComponent(requestId), {
    headers: authenticatedHeaders(token),
  });
}

export function linkConsultationMemberApi(token, requestId, memberId) {
  return request("/api/consultation-requests/" + encodeURIComponent(requestId) + "/link-member", {
    method: "PATCH",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ memberId }),
  });
}

export function createConsultationMemberApi(token, requestId, payload) {
  return request("/api/consultation-requests/" + encodeURIComponent(requestId) + "/create-member", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function completeConsultationRequestApi(token, requestId, resolutionNote) {
  return request("/api/consultation-requests/" + encodeURIComponent(requestId) + "/complete", {
    method: "PATCH",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ resolutionNote: resolutionNote || null }),
  });
}

export function getPublicSportsApi() {
  return request("/api/public/sports", {
    headers: { Accept: "application/json" },
  });
}

export function getPtEligibleCoachesApi(token, sportId, startTime) {
  const params = new URLSearchParams({ sportId: String(sportId), startTime });
  return request("/api/pt-bookings/eligible-coaches?" + params.toString(), {
    headers: authenticatedHeaders(token),
  });
}

export function createPtBookingApi(token, payload) {
  return request("/api/pt-bookings", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getMyPtBookingsApi(token) {
  return request("/api/pt-bookings/me", { headers: authenticatedHeaders(token) });
}

export function getAssignedPtBookingsApi(token) {
  return request("/api/pt-bookings/assigned", { headers: authenticatedHeaders(token) });
}

export function getMyAttendanceApi(token) {
  return request("/api/attendance/me", { headers: authenticatedHeaders(token) });
}
export function getStaffAttendanceSessionsApi(token) {
  return request("/api/attendance/sessions", { headers: authenticatedHeaders(token) });
}
export function getCoachAttendanceSessionsApi(token) {
  return request("/api/attendance/coach/sessions", { headers: authenticatedHeaders(token) });
}

export function getAttendanceRosterApi(token, sessionId) {
  return request("/api/attendance/sessions/" + encodeURIComponent(sessionId) + "/roster", {
    headers: authenticatedHeaders(token),
  });
}

export function markAttendanceApi(token, sessionId, memberId, payload) {
  return request("/api/attendance/sessions/" + encodeURIComponent(sessionId) + "/members/" + encodeURIComponent(memberId), {
    method: "PUT",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getAttendanceHistoryApi(token, attendanceId) {
  return request("/api/attendance/" + encodeURIComponent(attendanceId) + "/history", {
    headers: authenticatedHeaders(token),
  });
}

export function getPublicCoursesBySportApi(sportId) {
  return request("/api/public/sports/" + encodeURIComponent(sportId) + "/courses", {
    headers: { Accept: "application/json" },
  });
}

export function getPublicCourseApi(courseId) {
  return request("/api/public/courses/" + encodeURIComponent(courseId), {
    headers: { Accept: "application/json" },
  });
}

export function getCourseOfferingsByCourseApi(token, courseId) {
  return request("/api/course-offerings/course/" + encodeURIComponent(courseId), {
    headers: authenticatedHeaders(token),
  });
}

export function getClassesByOfferingApi(token, offeringId) {
  return request("/api/classes/offering/" + encodeURIComponent(offeringId), {
    headers: authenticatedHeaders(token),
  });
}

export function getClassSessionsByClassApi(token, classId) {
  return request("/api/class-sessions/class/" + encodeURIComponent(classId), {
    headers: authenticatedHeaders(token),
  });
}

export function createCourseRegistrationApi(token, classId) {
  return request("/api/course-registrations", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ classId }),
  });
}

export function createStaffCourseRegistrationApi(token, memberId, classId) {
  return request("/api/course-registrations/staff", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ memberId, classId }),
  });
}

export function getAllCourseRegistrationsApi(token) {
  return request("/api/course-registrations", { headers: authenticatedHeaders(token) });
}

export function cancelCourseRegistrationApi(token, registrationId) {
  return request(`/api/course-registrations/${encodeURIComponent(registrationId)}/cancel`, {
    method: "POST",
    headers: authenticatedHeaders(token),
  });
}

export function createMemberConsultationRequestApi(token, payload) {
  return request("/api/consultation-requests", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getCoachesApi(token) {
  return request("/api/coaches", { headers: authenticatedHeaders(token) });
}

export function getSportsApi(token) {
  return request("/api/sports", { headers: authenticatedHeaders(token) });
}

export function createSportApi(token, payload) {
  return request("/api/sports", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateSportApi(token, sportId, payload) {
  return request("/api/sports/" + encodeURIComponent(sportId), {
    method: "PUT",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getCoachSportsApi(token, coachId) {
  return request("/api/coaches/" + encodeURIComponent(coachId) + "/sports", {
    headers: authenticatedHeaders(token),
  });
}

export function assignCoachSportApi(token, coachId, payload) {
  return request("/api/coaches/" + encodeURIComponent(coachId) + "/sports", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateCoachSportApi(token, coachId, sportId, payload) {
  return request("/api/coaches/" + encodeURIComponent(coachId) + "/sports/" + encodeURIComponent(sportId), {
    method: "PUT",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function createSkillAssessmentApi(token, payload) {
  return request("/api/skill-assessments", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getSkillAssessmentsApi(token) {
  return request("/api/skill-assessments", { headers: authenticatedHeaders(token) });
}

export function getMySkillAssessmentAssignmentsApi(token) {
  return request("/api/skill-assessments/my-assignments", { headers: authenticatedHeaders(token) });
}

export function getAssessmentMemberLevelApi(token, assessmentId) {
  return request("/api/skill-assessments/" + encodeURIComponent(assessmentId) + "/member-level", {
    headers: authenticatedHeaders(token),
  });
}

export function completeSkillAssessmentApi(token, assessmentId, payload) {
  return request("/api/skill-assessments/" + encodeURIComponent(assessmentId) + "/complete", {
    method: "PATCH",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function createPublicConsultationRequestApi(payload) {
  return request("/api/consultation-requests", {
    method: "POST",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}



export function getPublicMembershipPlansApi() {
  return request("/api/public/membership-plans", {
    headers: { Accept: "application/json" },
  });
}

export function getMembershipPlansApi(token) {
  return request("/api/membership-plans", { headers: authenticatedHeaders(token) });
}

export function createPaymentApi(token, payload) {
  return request("/api/payments", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function processCounterPaymentApi(token, paymentId, success, note = null) {
  return request("/api/payments/" + encodeURIComponent(paymentId) + "/counter", {
    method: "PATCH",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ success, note }),
  });
}

export function getPendingMembershipPaymentsApi(token, page = 0, size = 20) {
  return request(`/api/payments/pending-memberships?page=${page}&size=${size}`, {
    headers: authenticatedHeaders(token),
  });
}

export function getPendingPtPaymentsApi(token, page = 0, size = 20) {
  return request(`/api/payments/pending-pt?page=${page}&size=${size}`, {
    headers: authenticatedHeaders(token),
  });
}

export function getMyPaymentsApi(token) {
  return request("/api/payments/me", { headers: authenticatedHeaders(token) });
}

export function getMemberPaymentsApi(token, memberId) {
  return request("/api/payments/member/" + encodeURIComponent(memberId), {
    headers: authenticatedHeaders(token),
  });
}

export function cancelPaymentApi(token, paymentId) {
  return request("/api/payments/" + encodeURIComponent(paymentId) + "/cancel", {
    method: "PATCH",
    headers: authenticatedHeaders(token),
  });
}

export function createMembershipPlanApi(token, payload) {
  return request("/api/membership-plans", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateMembershipPlanApi(token, planId, payload) {
  return request("/api/membership-plans/" + encodeURIComponent(planId), {
    method: "PUT",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateMembershipPlanStatusApi(token, planId, status) {
  return request("/api/membership-plans/" + encodeURIComponent(planId) + "/status", {
    method: "PATCH",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
}

export function resolveImageUrl(imageUrl) {
  if (!imageUrl || typeof imageUrl !== "string") return "";
  try {
    return new URL(imageUrl, API_BASE_URL.endsWith("/") ? API_BASE_URL : API_BASE_URL + "/").toString();
  } catch {
    return "";
  }
}

export function getPublicCoachesApi() {
  return request("/api/public/coaches", { headers: { Accept: "application/json" } });
}

export function createFacilityApi(token, payload) {
  return request("/api/facilities", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateFacilityApi(token, facilityId, payload) {
  return request("/api/facilities/" + encodeURIComponent(facilityId), {
    method: "PUT",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}
export function getFacilitiesApi(token) {
  return request("/api/facilities", { headers: authenticatedHeaders(token) });
}

async function uploadImage(path, token, file) {
  const formData = new FormData();
  formData.append("file", file);
  return request(path, { method: "PUT", headers: authenticatedHeaders(token), body: formData });
}

export function uploadSportImageApi(token, id, file) {
  return uploadImage("/api/sports/" + encodeURIComponent(id) + "/image", token, file);
}

export function uploadCoachImageApi(token, id, file) {
  return uploadImage("/api/coaches/" + encodeURIComponent(id) + "/image", token, file);
}

export function updateCoachProfileApi(token, id, payload) {
  return request("/api/coaches/" + encodeURIComponent(id) + "/profile", {
    method: "PUT",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

// Member self-service profile and membership reads.
export function getMyMemberProfileApi(token) {
  return request("/api/member-profiles/me", { headers: authenticatedHeaders(token) });
}

export function saveMyMemberProfileApi(token, payload, exists) {
  return request("/api/member-profiles/me", {
    method: exists ? "PUT" : "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getMembershipsByMemberApi(token, memberId) {
  return request("/api/memberships/member/" + encodeURIComponent(memberId), {
    headers: authenticatedHeaders(token),
  });
}

export function getCoursesApi(token) {
  return request("/api/courses", { headers: authenticatedHeaders(token) });
}

export function getCourseApi(token, courseId) {
  return request("/api/courses/" + encodeURIComponent(courseId), { headers: authenticatedHeaders(token) });
}

// Course administration endpoints. Server authorization remains ADMIN-only.
export function getCoursesBySportApi(token, sportId) {
  return request("/api/courses/sport/" + encodeURIComponent(sportId), { headers: authenticatedHeaders(token) });
}

export function getPendingCoursePaymentsApi(token, page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return request("/api/payments/pending-courses?" + params.toString(), { headers: authenticatedHeaders(token) });
}

export function saveCourseApi(token, courseId, payload) {
  return request(courseId ? "/api/courses/" + encodeURIComponent(courseId) : "/api/courses", {
    method: courseId ? "PUT" : "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getOfferingsApi(token, courseId) {
  return request("/api/course-offerings/course/" + encodeURIComponent(courseId), { headers: authenticatedHeaders(token) });
}

export function saveOfferingApi(token, offeringId, payload) {
  return request(offeringId ? "/api/course-offerings/" + encodeURIComponent(offeringId) : "/api/course-offerings", {
    method: offeringId ? "PUT" : "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getClassesApi(token, offeringId) {
  return request("/api/classes/offering/" + encodeURIComponent(offeringId), { headers: authenticatedHeaders(token) });
}

export function saveClassApi(token, classId, payload) {
  return request(classId ? "/api/classes/" + encodeURIComponent(classId) : "/api/classes", {
    method: classId ? "PUT" : "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getSessionsByClassApi(token, classId) {
  return request("/api/class-sessions/class/" + encodeURIComponent(classId), { headers: authenticatedHeaders(token) });
}

export function saveSessionApi(token, sessionId, payload) {
  return request(sessionId ? "/api/class-sessions/" + encodeURIComponent(sessionId) : "/api/class-sessions", {
    method: sessionId ? "PUT" : "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function setSessionStatusApi(token, sessionId, status) {
  return request("/api/class-sessions/" + encodeURIComponent(sessionId) + "/status", {
    method: "PATCH",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
}

export function previewClassSessionGenerationApi(token, payload) {
  return request("/api/class-sessions/generation/preview", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function confirmClassSessionGenerationApi(token, payload) {
  return request("/api/class-sessions/generation/confirm", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getMyRegistrationsApi(token) {
  return request("/api/course-registrations/me", { headers: authenticatedHeaders(token) });
}

export function getMyClassSessionsApi(token) {
  return request("/api/course-registrations/me/class-sessions", { headers: authenticatedHeaders(token) });
}
export function getNotificationsApi(token, page = 0, size = 20) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return request("/api/notifications?" + query.toString(), { headers: authenticatedHeaders(token) });
}

export function getUnreadNotificationCountApi(token) {
  return request("/api/notifications/unread-count", { headers: authenticatedHeaders(token) });
}

export function markNotificationReadApi(token, id) {
  return request("/api/notifications/" + encodeURIComponent(id) + "/read", {
    method: "PATCH",
    headers: authenticatedHeaders(token),
  });
}

export function markAllNotificationsReadApi(token) {
  return request("/api/notifications/read-all", {
    method: "PATCH",
    headers: authenticatedHeaders(token),
  });
}

export function broadcastNotificationsApi(token, payload) {
  return request("/api/admin/notifications/broadcast", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function getAdminAuditLogsApi(token, { page = 0, size = 20, actorUserId, action, entityType, entityId, from, to } = {}, signal) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  for (const [key, value] of Object.entries({ actorUserId, action, entityType, entityId, from, to })) {
    if (value !== undefined && value !== null && String(value).trim() !== "") params.set(key, String(value).trim());
  }
  return request("/api/admin/audit-logs?" + params.toString(), {
    headers: authenticatedHeaders(token),
    signal,
  });
}

export function getAdminRevenueReportApi(token, { from, to, groupBy = "MONTH" }, signal) {
  const params = new URLSearchParams({ from, to, groupBy });
  return request("/api/reports/revenue?" + params.toString(), {
    headers: authenticatedHeaders(token),
    signal,
  });
}

export function getMySupportTicketsApi(token, { page = 0, size = 20 } = {}, signal) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return request("/api/support-tickets/me?" + params.toString(), { headers: authenticatedHeaders(token), signal });
}

export function getStaffSupportTicketsApi(token, { page = 0, size = 20, status = "", category = "", search = "" } = {}, signal) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);
  if (category) params.set("category", category);
  if (search.trim()) params.set("search", search.trim());
  return request("/api/support-tickets?" + params.toString(), { headers: authenticatedHeaders(token), signal });
}

export function getSupportTicketApi(token, ticketId, signal) {
  return request("/api/support-tickets/" + encodeURIComponent(ticketId), { headers: authenticatedHeaders(token), signal });
}

export function createSupportTicketApi(token, payload) {
  return request("/api/support-tickets", {
    method: "POST", headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function replySupportTicketApi(token, ticketId, message) {
  return request("/api/support-tickets/" + encodeURIComponent(ticketId) + "/messages", {
    method: "POST", headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
  });
}

export function updateSupportTicketStatusApi(token, ticketId, status) {
  return request("/api/support-tickets/" + encodeURIComponent(ticketId) + "/status", {
    method: "PATCH", headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ status }),
  });
}

export function assignSupportTicketToMeApi(token, ticketId) {
  return request("/api/support-tickets/" + encodeURIComponent(ticketId) + "/assign-to-me", {
    method: "PATCH", headers: authenticatedHeaders(token),
  });
}

// ---- Flow 4: training plans, results and progress ----
const trainingJson = (token, method, body) => ({
  method,
  headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export function getTrainingClassesApi(token) {
  return request("/api/training/classes", { headers: authenticatedHeaders(token) });
}

export function getTrainingPlanApi(token, classId) {
  return request(`/api/training/classes/${encodeURIComponent(classId)}/plan`, { headers: authenticatedHeaders(token) });
}

export function saveTrainingPlanApi(token, classId, payload) {
  return request(`/api/training/classes/${encodeURIComponent(classId)}/plan`, trainingJson(token, "PUT", payload));
}

export function addTrainingExerciseApi(token, classId, payload) {
  return request(`/api/training/classes/${encodeURIComponent(classId)}/plan/exercises`, trainingJson(token, "POST", payload));
}

export function updateTrainingExerciseApi(token, exerciseId, payload) {
  return request(`/api/training/exercises/${encodeURIComponent(exerciseId)}`, trainingJson(token, "PUT", payload));
}

export function deleteTrainingExerciseApi(token, exerciseId) {
  return request(`/api/training/exercises/${encodeURIComponent(exerciseId)}`, {
    method: "DELETE",
    headers: authenticatedHeaders(token),
  });
}

export function getTrainingSessionsApi(token, classId) {
  return request(`/api/training/classes/${encodeURIComponent(classId)}/sessions`, { headers: authenticatedHeaders(token) });
}

export function completeTrainingSessionApi(token, sessionId) {
  return request(`/api/training/sessions/${encodeURIComponent(sessionId)}/complete`, {
    method: "POST",
    headers: authenticatedHeaders(token),
  });
}

export function getTrainingResultSheetApi(token, sessionId) {
  return request(`/api/training/sessions/${encodeURIComponent(sessionId)}/results`, { headers: authenticatedHeaders(token) });
}

export function recordTrainingResultApi(token, sessionId, memberId, payload) {
  return request(`/api/training/sessions/${encodeURIComponent(sessionId)}/results/${encodeURIComponent(memberId)}`,
    trainingJson(token, "PUT", payload));
}

export function getTrainingProgressApi(token, classId) {
  return request(`/api/training/classes/${encodeURIComponent(classId)}/progress`, { headers: authenticatedHeaders(token) });
}

export function evaluateProgressApi(token, classId, memberId, payload) {
  return request(`/api/training/classes/${encodeURIComponent(classId)}/members/${encodeURIComponent(memberId)}/evaluations`,
    trainingJson(token, "POST", payload));
}

export function getMyTrainingApi(token) {
  return request("/api/training/me", { headers: authenticatedHeaders(token) });
}
