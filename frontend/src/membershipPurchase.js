export function membershipPurchaseErrorMessage(error) {
  if (error?.status === 409) {
    const message = String(error.message || "").toLowerCase();
    if (message.includes("effective or future membership")) {
      return "Thành viên đã có Membership đang hiệu lực hoặc sắp có hiệu lực. Không thể tạo giao dịch mua thông thường.";
    }
    if (message.includes("pending membership upgrade")) {
      return "Đang có yêu cầu nâng cấp Membership chờ xử lý. Hãy hoàn tất hoặc hủy yêu cầu đó trước.";
    }
    if (message.includes("pending membership payment")) {
      return "Đang có thanh toán Membership chờ xử lý. Hãy tiếp tục hoặc hủy giao dịch đó trước.";
    }
    return error.message || "Không thể tạo thanh toán Membership do xung đột trạng thái.";
  }
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Bạn chỉ có thể tạo thanh toán cho tài khoản của mình.";
  if (error?.status === 503) {
    return "Thanh toán trực tuyến hiện chưa được bật. Không có thanh toán mới nào được tạo; vui lòng chọn thanh toán tại quầy hoặc liên hệ quản trị viên.";
  }
  return error?.message || "Không thể tạo yêu cầu thanh toán.";
}
