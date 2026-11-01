/* ---------- SVG icon helper (dùng riêng cho sidebar) ---------- */
function NavIc({ d, size = 20 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      style={{ flexShrink: 0 }}
    >
      {d.split(" M").map((p, i) => (
        <path key={i} d={(i ? "M" : "") + p} />
      ))}
    </svg>
  );
}

/* ---------- Icon paths cho từng mục nav lễ tân (Chuẩn Material Symbols / Design tokens) ---------- */
const RC_NAV_ICONS = {
  // Dashboard: Grid 4 ô (thay vì icon ngôi nhà)
  dashboard: "M3 3h7v7H3z M14 3h7v7h-7z M14 14h7v7h-7z M3 14h7v7H3z",
  // Thành viên: Group 2 người
  members: "M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 7a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M23 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75",
  // Gói hội viên: Thẻ hội viên (card membership)
  "membership-plans": "M3 5h18a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z M2 10h20 M7 15h4",
  // Bán gói hội viên: Máy POS / Quầy thanh toán (point of sale)
  "membership-sales": "M5 2h14a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z M7 6h10 M7 10h10 M8 14h.01 M12 14h.01 M16 14h.01 M8 18h.01 M12 18h.01 M16 18h.01",
  // Yêu cầu tư vấn: Tai nghe hỗ trợ viên (contact support)
  consultations: "M3 18v-6a9 9 0 0 1 18 0v6 M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3z M3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z",
};

/* Icon logo trên brand lễ tân */
const LOGO_ICON = "M13 2L3 14h9l-1 8 10-12h-9l1-8";
/* Icon đăng xuất */
const LOGOUT_ICON = "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9";

/* ---------- Nav item ---------- */
function Item({ page, activePage, onNavigate, badge, icon, children }) {
  return (
    <button
      className={"nav-item clickable nav-button " + (activePage === page ? "active" : "")}
      aria-current={activePage === page ? "page" : undefined}
      type="button"
      onClick={() => onNavigate(page)}
    >
      {icon && <NavIc d={icon} size={20} />}
      <span>{children}</span>
      {badge > 0 && <span className="nav-badge">{badge}</span>}
    </button>
  );
}

/* ---------- Danh sách nav theo role ---------- */
const MEMBER_LINKS = [
  ["dashboard", "Tổng quan"],
  ["member-courses", "Khóa học"],
  ["member-membership", "Gói thành viên"],
  ["member-schedule", "Lịch trình"],
  ["member-levels", "Trình độ & đánh giá"],
  ["member-attendance", "Lịch sử điểm danh"],
  ["member-pt-booking", "Đặt buổi huấn luyện"],
  ["member-profile", "Hồ sơ cá nhân"],
];

const COACH_LINKS = [
  ["dashboard", "Tổng quan"],
  ["coach-schedule", "Lịch trình"],
  ["coach-pt-sessions", "Buổi huấn luyện cá nhân"],
  ["coach-assessments", "Đánh giá kỹ năng"],
  ["coach-attendance", "Điểm danh"],
];

// Lễ tân chỉ thấy 5 mục theo thiết kế chuẩn
const RECEPTIONIST_LINKS = [
  ["dashboard", "Tổng quan"],
  ["members", "Thành viên"],
  ["membership-plans", "Gói hội viên"],
  ["membership-sales", "Bán gói hội viên"],
  ["consultations", "Yêu cầu tư vấn"],
];

const ADMIN_LINKS = [
  ["dashboard", "Tổng quan"],
  ["members", "Thành viên"],
  ["membership-plans", "Gói hội viên"],
  ["attendance", "Điểm danh lớp học"],
  ["staff", "Quản lý nhân sự"],
  ["sports", "Quản lý bộ môn"],
  ["courses", "Quản lý khóa học"],
  ["facilities", "Quản lý cơ sở vật chất"],
  ["register", "Đăng ký thành viên"],
  ["consultations", "Yêu cầu tư vấn"],
];

const ROLE_LABEL = {
  ADMIN: "Quản trị viên",
  RECEPTIONIST: "Lễ tân",
  COACH: "Huấn luyện viên",
  MEMBER: "Thành viên",
};

/* ---------- Sidebar ---------- */
export default function Sidebar({ onLogout, onNavigate, activePage, role, badges = {} }) {
  const isRC = role === "RECEPTIONIST";

  const links =
    role === "MEMBER"
      ? MEMBER_LINKS
      : role === "COACH"
      ? COACH_LINKS
      : isRC
      ? RECEPTIONIST_LINKS
      : ADMIN_LINKS;

  const label = ROLE_LABEL[role] || "Thành viên";
  const asideClass = "sidebar" + (isRC ? " rc-sidebar" : "");

  return (
    <aside className={asideClass}>
      {/* Brand / Logo */}
      {isRC ? (
        <div className="brand rc-brand">
          <span className="rc-logo" aria-hidden="true">
            <NavIc d={LOGO_ICON} size={22} />
          </span>
          <div className="rc-brand-text">
            <strong>SPORTS CENTER</strong>
            <span>
              {label}
              <i aria-hidden="true" />
              Vận hành
            </span>
          </div>
        </div>
      ) : (
        <div className="brand">
          <strong>SPORTS CENTER</strong>
          <span>{label}</span>
        </div>
      )}

      {/* Navigation */}
      <nav
        aria-label={
          role === "MEMBER"
            ? "Member navigation"
            : role === "COACH"
            ? "Coach navigation"
            : "Staff navigation"
        }
      >
        {links.map(([page, text]) => (
          <Item
            key={page}
            page={page}
            activePage={activePage}
            onNavigate={onNavigate}
            badge={badges[page]}
            icon={isRC ? RC_NAV_ICONS[page] : null}
          >
            {text}
          </Item>
        ))}
      </nav>

      {/* Footer / System Status & Logout */}
      <div className="nav-footer">
        {isRC && (
          <div className="rc-system-status">
            <span className="rc-status-indicator">
              <span className="rc-status-pulse" />
              <span className="rc-status-text">Hệ thống sẵn sàng</span>
            </span>
            <span className="rc-status-ver">v2.4</span>
          </div>
        )}
        <button
          className="nav-item clickable logout-button"
          type="button"
          onClick={onLogout}
        >
          {isRC && <NavIc d={LOGOUT_ICON} />}
          <span>Đăng xuất</span>
        </button>
      </div>
    </aside>
  );
}
