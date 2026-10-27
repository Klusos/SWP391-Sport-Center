function Item({ page, activePage, onNavigate, children }) {
  return (
    <button
      className={`nav-item clickable nav-button ${activePage === page ? "active" : ""}`}
      aria-current={activePage === page ? "page" : undefined}
      type="button"
      onClick={() => onNavigate(page)}
    >
      {children}
    </button>
  );
}

export default function Sidebar({ onLogout, onNavigate, activePage, role }) {
  const memberLinks = [
    ["dashboard", "Tổng quan"],
    ["member-courses", "Khóa học"],
    ["member-membership", "Gói thành viên"],
    ["member-schedule", "Lịch trình"],
    ["member-levels", "Trình độ & đánh giá"],
    ["member-training", "K\u1ebft qu\u1ea3 t\u1eadp luy\u1ec7n"],
    ["member-attendance", "Lịch sử điểm danh"],
    ["member-pt-booking", "Đặt buổi huấn luyện"],
    ["support-tickets", "Hỗ trợ"],
    ["member-profile", "Hồ sơ cá nhân"],
  ];
  const coachLinks = [
    ["dashboard", "Tổng quan"],
    ["coach-schedule", "Lịch trình"],
    ["coach-pt-sessions", "Buổi huấn luyện cá nhân"],
    ["coach-assessments", "Đánh giá kỹ năng"],
    ["coach-training", "Gi\u00e1o \u00e1n & k\u1ebft qu\u1ea3"],
    ["coach-attendance", "Điểm danh"],
  ];
  const receptionistLinks = [
    ["dashboard", "Tổng quan"],
    ["members", "Thành viên"],
    ["membership-sales", "Bán gói hội viên"],
    ["course-registration", "Đăng ký khóa học tại quầy"],
    ["course-payments", "Thanh toán khóa học"],
    ["support-tickets", "Yêu cầu hỗ trợ"],
    ["consultations", "Yêu cầu tư vấn"],
  ];
  const adminLinks = [
    ["dashboard", "Tổng quan"],
    ["members", "Thành viên"],
    ["membership-plans", "Gói hội viên"],
    ["course-payments", "Thanh toán khóa học"],
    ["course-registration", "Đăng ký khóa học tại quầy"],
    ["support-tickets", "Yêu cầu hỗ trợ"],
    ["attendance", "Điểm danh lớp học"],
    ["staff", "Quản lý nhân sự"],
    ["sports", "Quản lý bộ môn"],
    ["courses", "Quản lý khóa học"],
    ["facilities", "Quản lý cơ sở vật chất"],
    ["notification-broadcast", "Gửi thông báo"],
    ["admin-training", "Quản lý tập luyện"],
    ["admin-membership-sales", "Quản lý gói thành viên"],
    ["audit-logs", "Audit Logs"],
    ["register", "Đăng ký thành viên"],
    ["consultations", "Yêu cầu tư vấn"],
  ];
  const links = role === "MEMBER"
    ? memberLinks
    : role === "COACH"
      ? coachLinks
      : role === "RECEPTIONIST"
        ? receptionistLinks
        : adminLinks;
  const label = role === "ADMIN"
    ? "Quản trị viên"
    : role === "RECEPTIONIST"
      ? "Lễ tân"
      : role === "COACH"
        ? "Huấn luyện viên"
        : "Thành viên";

  return (
    <aside className="sidebar">
      <div className="brand"><strong>SPORTS CENTER</strong><span>{label}</span></div>
      <nav aria-label={`${role} navigation`}>
        {links.map(([page, text]) => (
          <Item key={page} page={page} activePage={activePage} onNavigate={onNavigate}>
            {text}
          </Item>
        ))}
      </nav>
      <div className="nav-footer">
        <button className="nav-item clickable logout-button" type="button" onClick={onLogout}>
          Đăng xuất
        </button>
      </div>
    </aside>
  );
}
