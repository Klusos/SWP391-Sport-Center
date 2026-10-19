function NavButton({ id, activePage, onNavigate, children }) {
  return (
    <button className={"nav-item clickable nav-button " + (activePage === id ? "active" : "")}
      aria-current={activePage === id ? "page" : undefined} type="button" onClick={() => onNavigate(id)}>
      {children}
    </button>
  );
}

export default function Sidebar({ onLogout, onNavigate, activePage, role }) {
  const nav = { activePage, onNavigate };

  if (role === "MEMBER") {
    return (
      <aside className="sidebar">
        <div className="brand"><strong>SPORTS CENTER</strong><span>Thành viên</span></div>
        <nav aria-label="Member navigation">
          <NavButton id="dashboard" {...nav}>Tổng quan</NavButton>
          <NavButton id="member-courses" {...nav}>Khóa học &amp; lớp học</NavButton>
          <NavButton id="member-membership" {...nav}>Gói thành viên</NavButton>
          <NavButton id="member-schedule" {...nav}>Lịch trình</NavButton>
          <NavButton id="member-payments" {...nav}>Hóa đơn</NavButton>
          <NavButton id="member-levels" {...nav}>Trình độ &amp; đánh giá</NavButton>
          <NavButton id="member-pt-booking" {...nav}>Đặt buổi huấn luyện</NavButton>
          <NavButton id="member-profile" {...nav}>Hồ sơ cá nhân</NavButton>
        </nav>
        <div className="nav-footer"><button className="nav-item clickable logout-button" type="button" onClick={onLogout}>Đăng xuất</button></div>
      </aside>
    );
  }

  if (role === "COACH") {
    return (
      <aside className="sidebar">
        <div className="brand"><strong>SPORTS CENTER</strong><span>Huấn luyện viên</span></div>
        <nav aria-label="Coach navigation">
          <NavButton id="dashboard" {...nav}>Tổng quan</NavButton>
          <NavButton id="coach-schedule" {...nav}>Lịch trình</NavButton>
          <NavButton id="coach-pt-sessions" {...nav}>Buổi huấn luyện cá nhân</NavButton>
          <NavButton id="coach-assessments" {...nav}>Đánh giá kỹ năng</NavButton>
        </nav>
        <div className="nav-footer"><button className="nav-item clickable logout-button" type="button" onClick={onLogout}>Đăng xuất</button></div>
      </aside>
    );
  }

  const canAccessConsultations = role === "RECEPTIONIST" || role === "ADMIN";

  return (
    <aside className="sidebar">
      <div className="brand">
        <strong>SPORTS CENTER</strong>
        <span>{role === "ADMIN" ? "Quản trị viên" : "Lễ tân"}</span>
      </div>
      <nav aria-label="Staff navigation">
        <NavButton id="dashboard" {...nav}>Tổng quan</NavButton>
        {canAccessConsultations && <NavButton id="members" {...nav}>Thành viên</NavButton>}
        {canAccessConsultations && <NavButton id="membership-plans" {...nav}>Gói hội viên</NavButton>}
        {role === "ADMIN" && <NavButton id="staff" {...nav}>Quản lý nhân sự</NavButton>}
        {role === "ADMIN" && <NavButton id="sports" {...nav}>Quản lý bộ môn</NavButton>}
        {role === "ADMIN" && <NavButton id="courses" {...nav}>Quản lý khóa học &amp; lớp</NavButton>}
        {role === "ADMIN" && <NavButton id="reports" {...nav}>Báo cáo</NavButton>}
        {role === "ADMIN" && <NavButton id="facilities" {...nav}>Quản lý cơ sở vật chất</NavButton>}
        <NavButton id="register" {...nav}>Đăng ký thành viên</NavButton>
        {canAccessConsultations && <NavButton id="consultations" {...nav}>Yêu cầu tư vấn</NavButton>}
        <NavButton id="attendance" {...nav}>Điểm danh</NavButton>
        <NavButton id="payments" {...nav}>Hóa đơn</NavButton>
        <NavButton id="support" {...nav}>Hỗ trợ</NavButton>
      </nav>
      <div className="nav-footer">
        <div className="nav-item">Cài đặt</div>
        <button className="nav-item clickable logout-button" type="button" onClick={onLogout}>Đăng xuất</button>
      </div>
    </aside>
  );
}