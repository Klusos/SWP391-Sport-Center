import { Outlet, useLocation } from "react-router-dom";
import Sidebar from "./Sidebar";

// Đường dẫn -> khóa "page" mà Sidebar dùng để tô sáng mục đang mở
const pageByPath = {
  "/staff/dashboard": "dashboard",
  "/member/dashboard": "dashboard",
  "/staff/consultations": "consultations",
  "/staff/members": "members",
  "/staff/staff": "staff",
  "/staff/sports": "sports",
  "/staff/facilities": "facilities",
  "/staff/courses": "courses",
  "/staff/membership-plans": "membership-plans",
  "/staff/register-member": "register",
  "/admin/notifications/broadcast": "notification-broadcast",
  "/admin/audit-logs": "audit-logs",
  "/admin/attendance": "attendance",
  "/receptionist/attendance": "attendance",
  "/admin/courses": "admin-courses",
  "/admin/classes": "classes",
  "/admin/reports": "reports",
  "/member/courses": "member-courses",
  "/member/classes": "member-classes",
  "/member/membership": "member-membership",
  "/member/schedule": "member-schedule",
  "/member/levels-assessments": "member-levels",
  "/member/attendance": "member-attendance",
  "/member/pt-booking": "member-pt-booking",
  "/member/profile": "member-profile",
};

// Dùng cùng cấu trúc .layout > Sidebar + .main như các trang cũ,
// nên mọi trang có chung một sidebar và một kiểu giao diện.
export default function Layout({ role, onLogout, onNavigate }) {
  const { pathname } = useLocation();
  return (
    <div className="layout">
      <Sidebar
        role={role}
        onLogout={onLogout}
        onNavigate={onNavigate}
        activePage={pageByPath[pathname]}
      />
      <main className="main">
        <div className="app-shell">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
