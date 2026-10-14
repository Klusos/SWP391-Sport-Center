const API_BASE = "http://localhost:8080";

export async function loginApi(email, password) {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || "Email hoặc mật khẩu không đúng.");
  return data; // { accessToken, role, name, email, ... }
}

export async function createMemberApi(token, payload) {
  const res = await fetch(`${API_BASE}/api/members`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || "Tạo thành viên thất bại.");
  return data;
}
