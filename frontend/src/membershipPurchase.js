export function membershipPurchaseErrorMessage(error) {
  if (error?.status === 409) {
    return "Đã có yêu cầu thanh toán đang chờ cho gói này. Hãy tiếp tục thanh toán hoặc hủy yêu cầu hiện tại.";
  }
  if (error?.status === 401) {
    return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  }
  if (error?.status === 403) {
    return "Bạn chỉ có thể tạo thanh toán cho tài khoản của mình.";
  }
  if (error?.status === 503) {
    return "Thanh toán trực tuyến hiện chưa được bật cho bản demo. Không có thanh toán mới nào được tạo; vui lòng chọn thanh toán tại quầy hoặc liên hệ quản trị viên.";
  }
  return error?.message || "Không thể tạo yêu cầu thanh toán.";
}
