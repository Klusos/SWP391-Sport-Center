const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

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

export function getPublicSportsApi() {
  return request("/api/public/sports", {
    headers: { Accept: "application/json" },
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

