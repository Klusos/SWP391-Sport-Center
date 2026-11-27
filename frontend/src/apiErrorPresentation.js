const HAS_VIETNAMESE_DIACRITICS = /[À-ỹ]/;

export function apiErrorMessage(message, status, path = "") {
  const text = String(message || "").trim();
  if (text && HAS_VIETNAMESE_DIACRITICS.test(text)) return text;
  if (/Only the assigned Coach or ADMIN can manage training for this class/i.test(text)) {
    return "Chỉ huấn luyện viên được phân công cho lớp này mới có thể quản lý giáo án.";
  }
  if (/Only the assigned Coach can/i.test(text)) {
    return "Chỉ huấn luyện viên được phân công mới có thể thực hiện thao tác này.";
  }
  if (/^Forbidden$/i.test(text)) return "Bạn không có quyền thực hiện thao tác này.";
  if (path === "/api/auth/login" && status === 401) return "Email hoặc mật khẩu chưa đúng.";
  if (status === 400 || status === 422) return "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các mục đã nhập.";
  if (status === 401) return "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại.";
  if (status === 403) return "Bạn không có quyền thực hiện thao tác này.";
  if (status === 404) return "Không tìm thấy thông tin yêu cầu.";
  if (status === 409) return "Dữ liệu đã thay đổi hoặc đang có xung đột. Hãy làm mới rồi thử lại.";
  if (status >= 500) return "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.";
  return text || "Không thể hoàn tất yêu cầu. Vui lòng thử lại.";
}
