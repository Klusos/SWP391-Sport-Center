export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const API_BASE = API_BASE_URL.replace(/\/$/, "");

async function request(path, options = {}) {
  const response = await fetch(API_BASE + path, options);
  const text = await response.text();
  let data = {};
  if (text) {
    try { data = JSON.parse(text); } catch { data = {}; }
  }
  if (!response.ok) {
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

export function createCourseRegistrationApi(token, classId) {
  return request("/api/course-registrations", {
    method: "POST",
    headers: { ...authenticatedHeaders(token), "Content-Type": "application/json" },
    body: JSON.stringify({ classId }),
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
