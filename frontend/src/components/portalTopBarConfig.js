export const PORTAL_ROLE_PRESENTATION = {
  RECEPTIONIST: { breadcrumb: "LỄ TÂN", account: "Lễ tân" },
  COACH: { breadcrumb: "HUẤN LUYỆN VIÊN", account: "Huấn luyện viên" },
  MEMBER: { breadcrumb: "THÀNH VIÊN", account: "Thành viên" },
};

const PAGE_LABELS = [
  [/^\/staff\/dashboard\/?$/, "TỔNG QUAN"],
  [/^\/staff\/consultations\/?$/, "YÊU CẦU TƯ VẤN"],
  [/^\/staff\/support-tickets\/?$/, "YÊU CẦU HỖ TRỢ"],
  [/^\/staff\/members\/?$/, "THÀNH VIÊN"],
  [/^\/receptionist\/course-registration\/?$/, "ĐĂNG KÝ KHÓA HỌC"],
  [/^\/receptionist\/course-payments\/?$/, "THANH TOÁN KHÓA HỌC"],
  [/^\/receptionist\/pt-payments\/?$/, "THANH TOÁN PT"],
  [/^\/receptionist\/membership-sales\/?$/, "QUẢN LÝ GÓI THÀNH VIÊN"],
  [/^\/coach\/dashboard\/?$/, "TỔNG QUAN"],
  [/^\/coach\/schedule\/?$/, "LỊCH TRÌNH"],
  [/^\/coach\/pt-sessions\/?$/, "BUỔI HUẤN LUYỆN CÁ NHÂN"],
  [/^\/coach\/skill-assessments\/?$/, "ĐÁNH GIÁ KỸ NĂNG"],
  [/^\/coach\/attendance\/?$/, "ĐIỂM DANH"],
  [/^\/coach\/training\/?$/, "GIÁO ÁN & KẾT QUẢ"],
  [/^\/member\/dashboard\/?$/, "TỔNG QUAN"],
  [/^\/member\/courses\/?$/, "KHÓA HỌC CỦA TÔI"],
  [/^\/member\/courses\/[^/]+\/?$/, "CHI TIẾT KHÓA HỌC"],
  [/^\/member\/course-checkout\/[^/]+\/?$/, "THANH TOÁN KHÓA HỌC"],
  [/^\/member\/schedule\/?$/, "LỊCH HỌC"],
  [/^\/member\/attendance\/?$/, "LỊCH SỬ ĐIỂM DANH"],
  [/^\/member\/training(?:\/[^/]+)?\/?$/, "KẾT QUẢ TẬP LUYỆN"],
  [/^\/member\/pt-booking\/?$/, "ĐẶT BUỔI HUẤN LUYỆN"],
  [/^\/member\/levels-assessments\/?$/, "TRÌNH ĐỘ & ĐÁNH GIÁ"],
  [/^\/member\/support-tickets\/?$/, "HỖ TRỢ"],
  [/^\/member\/membership\/?$/, "GÓI THÀNH VIÊN"],
  [/^\/member\/profile\/?$/, "HỒ SƠ CÁ NHÂN"],
];

export function getPortalPageLabel(pathname) {
  return PAGE_LABELS.find(([pattern]) => pattern.test(pathname))?.[1] || "SPORTCENTER";
}
