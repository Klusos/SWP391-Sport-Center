export const CONSULTATION_EMAIL_ERROR = "Vui lòng nhập địa chỉ email hợp lệ để đăng ký tư vấn.";

export function normalizeConsultationEmail(value) {
  if (typeof value !== "string") return "";
  const email = value.trim();
  if (email.length > 150 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return "";
  return email;
}
