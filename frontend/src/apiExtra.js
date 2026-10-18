// API bổ sung theo API guide. Không cần sửa api.js.
import { API_BASE_URL } from "./api";

const BASE = API_BASE_URL.replace(/\/$/, "");

async function req(path, token, method = "GET", body) {
  const headers = { Authorization: "Bearer " + token };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(BASE + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let data = {};
  if (text) { try { data = JSON.parse(text); } catch { data = {}; } }
  if (!response.ok) {
    const error = new Error(data.message || data.detail || ("Request failed (" + response.status + ")"));
    error.status = response.status;
    throw error;
  }
  return data;
}

// MemberProfile của chính member (GET/POST/PUT /api/member-profiles/me)
export const getMyMemberProfileApi = (token) => req("/api/member-profiles/me", token);
export const saveMyMemberProfileApi = (token, payload, exists) =>
  req("/api/member-profiles/me", token, exists ? "PUT" : "POST", payload);

// Khóa học, đợt học, lớp học, buổi học
export const getCoursesBySportApi = (t, sportId) => req("/api/courses/sport/" + sportId, t);
export const saveCourseApi = (t, id, b) => id ? req("/api/courses/" + id, t, "PUT", b) : req("/api/courses", t, "POST", b);
export const getOfferingsApi = (t, courseId) => req("/api/course-offerings/course/" + courseId, t);
export const saveOfferingApi = (t, id, b) => id ? req("/api/course-offerings/" + id, t, "PUT", b) : req("/api/course-offerings", t, "POST", b);
export const getClassesApi = (t, offeringId) => req("/api/classes/offering/" + offeringId, t);
export const getAllClassesApi = (t) => req("/api/classes", t);
export const saveClassApi = (t, id, b) => id ? req("/api/classes/" + id, t, "PUT", b) : req("/api/classes", t, "POST", b);
export const getSessionsByClassApi = (t, classId) => req("/api/class-sessions/class/" + classId, t);
export const getAllSessionsApi = (t) => req("/api/class-sessions", t);
export const saveSessionApi = (t, id, b) => id ? req("/api/class-sessions/" + id, t, "PUT", b) : req("/api/class-sessions", t, "POST", b);
export const setSessionStatusApi = (t, id, status) => req("/api/class-sessions/" + id + "/status", t, "PATCH", { status });

// Gói thành viên và đăng ký lớp của member
export const getMembershipsByMemberApi = (t, memberId) => req("/api/memberships/member/" + memberId, t);
export const createMembershipApi = (t, b) => req("/api/memberships", t, "POST", b);
export const setAutoRenewApi = (t, id, enable) => req("/api/memberships/" + id + (enable ? "/enable-auto-renew" : "/cancel-renewal"), t, "PATCH");
export const getMyRegistrationsApi = (t) => req("/api/course-registrations/me", t);
export const getMyClassSessionsApi = (t) => req("/api/course-registrations/me/class-sessions", t);