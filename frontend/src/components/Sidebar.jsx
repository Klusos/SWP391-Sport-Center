import { useState } from "react";
import { useLocation } from "react-router-dom";
import AdminTopBar from "./AdminTopBar.jsx";
import { usePortalIdentity } from "./PortalIdentityContext.jsx";
import { getPortalPageLabel, PORTAL_ROLE_PRESENTATION } from "./portalTopBarConfig.js";
import { roleLabel } from "../uiPresentation.js";

function Item({ page, activePage, onNavigate, children, icon }) {
  return (
    <button
      className={`nav-item clickable nav-button ${icon ? "admin-nav-item " : ""}${activePage === page ? "active" : ""}`}
      aria-current={activePage === page ? "page" : undefined}
      type="button"
      onClick={() => onNavigate(page)}
    >
      {icon && <i className={`bi ${icon} admin-nav-icon`} aria-hidden="true" />}
      {icon ? <span className="admin-nav-label">{children}</span> : children}
    </button>
  );
}
const adminNavigationIcons = {
  consultations: "bi-chat-dots",
  "support-tickets": "bi-life-preserver",
  "course-registration": "bi-journal-plus",
  "admin-membership-sales": "bi-bag-check",
  "course-payments": "bi-credit-card",
  attendance: "bi-calendar-check",
  "admin-training": "bi-clipboard2-pulse",
  sports: "bi-trophy",
  courses: "bi-journal-bookmark",
  facilities: "bi-building",
  "membership-plans": "bi-card-list",
  members: "bi-people",
  staff: "bi-person-badge",
  reports: "bi-bar-chart-line",
  "audit-logs": "bi-journal-text",
  "notification-broadcast": "bi-megaphone-fill",
};

const adminGroups = [
  {
    id: "operational-support",
    title: "HỖ TRỢ VẬN HÀNH",
    pages: [
      ["consultations", "Yêu cầu tư vấn"],
      ["support-tickets", "Yêu cầu hỗ trợ"],
      ["course-registration", "Đăng ký khóa học tại quầy"],
      ["admin-membership-sales", "Quản lý gói hội viên"],
      ["course-payments", "Thanh to\u00e1n kh\u00f3a h\u1ecdc & PT"],
      ["attendance", "Điểm danh lớp học"],
      ["admin-training", "Giám sát huấn luyện"],
    ],
  },
  {
    id: "catalog-management",
    title: "Quản lý danh mục",
    pages: [
      ["sports", "Quản lý bộ môn"],
      ["courses", "Quản lý khóa học"],
      ["facilities", "Quản lý cơ sở vật chất"],
      ["membership-plans", "Gói hội viên"],
    ],
  },
  {
    id: "administration",
    title: "Quản trị hệ thống & báo cáo",
    pages: [
      ["members", "Hội viên"],
      ["staff", "Quản lý nhân sự"],
      ["reports", "Báo cáo và thống kê"],
      ["audit-logs", "Nhật ký hoạt động"],
      ["notification-broadcast", "Gửi thông báo"],
    ],
  },
];

const adminPageByGroup = new Map(
  adminGroups.flatMap((group) => group.pages.map(([page]) => [page, group.id])),
);

function AdminNavigation({ activePage, onNavigate }) {
  const navigationActivePage = activePage === "pt-payments" ? "course-payments" : activePage;
  const activeAdminGroup = adminPageByGroup.get(navigationActivePage);
  const [expandedAdminGroup, setExpandedAdminGroup] = useState(activeAdminGroup);

  return (
    <>
      <Item page="dashboard" activePage={navigationActivePage} onNavigate={onNavigate}>
        Tổng quan
      </Item>
      {adminGroups.map((group) => {
        const expanded = expandedAdminGroup === group.id;
        return (
          <section className="admin-nav-group" key={group.id}>
            <button
              className={`admin-nav-group-toggle${activeAdminGroup === group.id ? " active-group" : ""}`}
              type="button"
              aria-expanded={expanded}
              aria-controls={`admin-nav-${group.id}`}
              onClick={() => setExpandedAdminGroup(expanded ? null : group.id)}
            >
              <span>{group.title}</span>
              <i className={`bi ${expanded ? "bi-chevron-down" : "bi-chevron-right"} admin-nav-chevron`} aria-hidden="true" />
            </button>
            <div
              className="admin-nav-group-items"
              id={`admin-nav-${group.id}`}
              hidden={!expanded}
            >
              {group.pages.map(([page, text]) => (
                <Item key={page} page={page} activePage={navigationActivePage} onNavigate={onNavigate} icon={adminNavigationIcons[page]}>
                  {text}
                </Item>
              ))}
            </div>
          </section>
        );
      })}
    </>
  );
}

export default function Sidebar({ onLogout, onNavigate, activePage, role }) {
  const { pathname } = useLocation();
  const { currentUserName } = usePortalIdentity();
  const portalRole = PORTAL_ROLE_PRESENTATION[role];
  const memberLinks = [
    ["dashboard", "Tổng quan"],
    ["member-courses", "Khóa học"],
    ["member-membership", "Gói hội viên"],
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
    ["members", "Hội viên"],
    ["membership-sales", "Bán gói hội viên"],
    ["course-registration", "Đăng ký khóa học tại quầy"],
    ["course-payments", "Thanh to\u00e1n kh\u00f3a h\u1ecdc & PT"],
    ["support-tickets", "Yêu cầu hỗ trợ"],
    ["consultations", "Yêu cầu tư vấn"],
  ];
  const adminLinks = [
    ["dashboard", "Tổng quan"],
    ["members", "Hội viên"],
    ["membership-plans", "Gói hội viên"],
    ["course-payments", "Thanh toán khóa học"],
    ["pt-payments", "Thanh toán PT"],
    ["course-registration", "Đăng ký khóa học tại quầy"],
    ["attendance", "Điểm danh lớp học"],
    ["staff", "Quản lý nhân sự"],
    ["sports", "Quản lý bộ môn"],
    ["courses", "Quản lý khóa học"],
    ["facilities", "Quản lý cơ sở vật chất"],
    ["notification-broadcast", "Gửi thông báo"],
    ["admin-membership-sales", "Quản lý gói hội viên"],
    ["audit-logs", "Nhật ký hoạt động"],
  ];
  const links = role === "MEMBER"
    ? memberLinks
    : role === "COACH"
      ? coachLinks
      : role === "RECEPTIONIST"
        ? receptionistLinks
        : adminLinks;
  const label = roleLabel(role);

  return (
    <>
    {portalRole && <AdminTopBar
      pageLabel={getPortalPageLabel(pathname)}
      displayName={currentUserName}
      avatarName={currentUserName}
      roleLabel={portalRole.account}
      breadcrumbRoleLabel={portalRole.breadcrumb}
      className={`portal-topbar portal-topbar--${role.toLowerCase()}`}
    />}
    <aside className="sidebar">
      <div className="brand"><strong>SPORTS CENTER</strong><span>{label}</span></div>
      <nav className={role === "ADMIN" ? "admin-navigation" : undefined} aria-label={`Điều hướng ${label}`}>
        {role === "ADMIN" ? (
          <AdminNavigation
            key={activePage}
            activePage={activePage}
            onNavigate={onNavigate}
          />
        ) : (
          links.map(([page, text]) => (
            <Item key={page} page={page} activePage={activePage} onNavigate={onNavigate}>
              {text}
            </Item>
          ))
        )}
      </nav>
      <div className="nav-footer">
        <button className="nav-item clickable logout-button" type="button" onClick={() => onLogout?.()}>
          Đăng xuất
        </button>
      </div>
    </aside>
    </>
  );
}
