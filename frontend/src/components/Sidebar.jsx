export default function Sidebar({ onLogout, onNavigate, activePage, role }) {
  const canAccessConsultations = role === "RECEPTIONIST" || role === "ADMIN";

  return (
    <aside className="sidebar">
      <div className="brand">
        <strong>SPORTS CENTER</strong>
        <span>{role === "ADMIN" ? "Quản trị viên" : "Lễ tân"}</span>
      </div>
      <nav aria-label="Staff navigation">
        <div className="nav-item">Tổng quan</div>
        {canAccessConsultations && (
          <button className={"nav-item clickable nav-button " + (activePage === "members" ? "active" : "")}
            aria-current={activePage === "members" ? "page" : undefined} type="button" onClick={() => onNavigate("members")}>
            Thành viên
          </button>
        )}
        {canAccessConsultations && (
          <button className={"nav-item clickable nav-button " + (activePage === "membership-plans" ? "active" : "")}
            aria-current={activePage === "membership-plans" ? "page" : undefined} type="button" onClick={() => onNavigate("membership-plans")}>
            {"G\u00f3i h\u1ed9i vi\u00ean"}
          </button>
        )}
        {role === "ADMIN" && (
          <button className={"nav-item clickable nav-button " + (activePage === "staff" ? "active" : "")}
            aria-current={activePage === "staff" ? "page" : undefined} type="button" onClick={() => onNavigate("staff")}>
            Quản lý nhân sự
          </button>
        )}
        <button className={"nav-item clickable nav-button " + (activePage === "register" ? "active" : "")}
          aria-current={activePage === "register" ? "page" : undefined} type="button" onClick={() => onNavigate("register")}>
          Đăng ký thành viên
        </button>
        {canAccessConsultations && (
          <button className={"nav-item clickable nav-button " + (activePage === "consultations" ? "active" : "")}
            aria-current={activePage === "consultations" ? "page" : undefined} type="button" onClick={() => onNavigate("consultations")}>
            Yêu cầu tư vấn
          </button>
        )}
        <div className="nav-item">Điểm danh</div>
        <div className="nav-item">Lớp học</div>
        <div className="nav-item">Thanh toán</div>
        <div className="nav-item">Hỗ trợ</div>
      </nav>
      <div className="nav-footer">
        <div className="nav-item">Cài đặt</div>
        <button className="nav-item clickable logout-button" type="button" onClick={onLogout}>Đăng xuất</button>
      </div>
    </aside>
  );
}
