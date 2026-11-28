export const PHONE_VALIDATION_ERROR = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.";

export function isValidVietnamesePhone(value, { required = false } = {}) {
  if (value == null || String(value).trim() === "") return !required;
  return /^0[0-9]{9}$/.test(String(value).trim());
}
