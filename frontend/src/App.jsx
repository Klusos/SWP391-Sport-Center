import { useState } from "react";
import {
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";

import NotificationBell from "./components/NotificationBell";
import AdminNotificationBroadcast from "./pages/AdminNotificationBroadcast";
import AdminAuditLogs from "./pages/AdminAuditLogs";
import ConsultationQueue from "./pages/ConsultationQueue";
import Login from "./pages/Login";
import RegisterMember from "./pages/RegisterMember";
import MemberList from "./pages/MemberList";
import StaffManagement from "./pages/StaffManagement";
import SportManagement from "./pages/SportManagement";
import FacilityManagement from "./pages/FacilityManagement";
import MembershipPlans from "./pages/MembershipPlans";
import PtBooking from "./pages/PtBooking";
import CoachPtSessions from "./pages/CoachPtSessions";
import CoachSkillAssessments from "./pages/CoachSkillAssessments";
import CoachAttendance from "./pages/CoachAttendance";
import StaffAttendance from "./pages/StaffAttendance";
import MemberAttendance from "./pages/MemberAttendance";
import MemberLevelsAssessments from "./pages/MemberLevelsAssessments";
import Dashboard from "./pages/Dashboard";
import CourseManagement from "./pages/CourseManagement";
import MemberCourses from "./pages/MemberCourses";
import MemberMembership from "./pages/MemberMembership";
import MemberProfile from "./pages/MemberProfile";
import Schedule from "./pages/Schedule";
import Layout from "./components/Layout";

// Các trang bổ sung
import CrudPage from "./pages/CrudPage";
import AttendancePage from "./pages/AttendancePage";
import SupportPage from "./pages/SupportPage";
import ReportPage from "./pages/ReportPage";
import MemberPage from "./pages/MemberPage";

// Public website
import PublicLayout from "./public-site/PublicLayout";
import Admission from "./public-site/pages/Admission";
import Coaches from "./public-site/pages/Coaches";
import Disciplines from "./public-site/pages/Disciplines";
import Facilities from "./public-site/pages/Facilities";
import Home from "./public-site/pages/Home";
import CourseDetail from "./public-site/pages/CourseDetail";

import "./public-site/styles.css";

const STAFF_ROLES = ["ADMIN", "RECEPTIONIST"];

const home = (role) => {
  if (role === "MEMBER") return "/member/dashboard";
  if (role === "COACH") return "/coach/dashboard";
  return "/staff/dashboard";
};

function StaffRoute({ auth }) {
  const location = useLocation();

  if (!auth) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname }}
      />
    );
  }

  return STAFF_ROLES.includes(auth.role) ? (
    <Outlet />
  ) : (
    <Navigate to={home(auth.role)} replace />
  );
}

function MemberRoute({ auth }) {
  const location = useLocation();

  if (!auth) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname }}
      />
    );
  }

  return auth.role === "MEMBER" ? (
    <Outlet />
  ) : (
    <Navigate to={home(auth.role)} replace />
  );
}

function CoachRoute({ auth }) {
  const location = useLocation();

  if (!auth) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname }}
      />
    );
  }

  return auth.role === "COACH" ? (
    <Outlet />
  ) : (
    <Navigate to={home(auth.role)} replace />
  );
}

function ReceptionistRoute({ auth }) {
  if (!auth) {
    return <Navigate to="/login" replace />;
  }

  return auth.role === "RECEPTIONIST" ? (
    <Outlet />
  ) : (
    <Navigate to={home(auth.role)} replace />
  );
}

function AdminRoute({ auth }) {
  if (!auth) {
    return <Navigate to="/login" replace />;
  }

  return auth.role === "ADMIN" ? (
    <Outlet />
  ) : (
    <Navigate to={home(auth.role)} replace />
  );
}

export default function App() {
  const [auth, setAuth] = useState(null);
  const navigate = useNavigate();
  const location = useLocation();

  function handleLogin(data) {
    setAuth(data);

    const returnPath = location.state?.from;

    // Quay lại trang chi tiết khóa học nếu hội viên đăng nhập từ đó.
    const courseReturn =
      data.role === "MEMBER" &&
      typeof returnPath === "string" &&
      /^\/khoa-hoc\/[^/]+$/.test(returnPath);

    navigate(
      courseReturn ? returnPath : home(data.role),
      { replace: true }
    );
  }

  function handleLogout(returnTo) {
    setAuth(null);

    navigate("/login", {
      replace: true,
      state: returnTo ? { from: returnTo } : undefined,
    });
  }

  const staff = {
    token: auth?.accessToken,
    role: auth?.role,
    onLogout: handleLogout,
    onNavigate: (page) =>
      navigate(
        {
          dashboard: "/staff/dashboard",
          consultations: "/staff/consultations",
          members: "/staff/members",
          staff: "/staff/staff",
          sports: "/staff/sports",
          facilities: "/staff/facilities",
          courses: "/staff/courses",
          "admin-courses": "/admin/courses",
          "notification-broadcast": "/admin/notifications/broadcast",
          "audit-logs": "/admin/audit-logs",
          "membership-plans": "/staff/membership-plans",
          attendance:
            auth?.role === "ADMIN"
              ? "/admin/attendance"
              : "/receptionist/attendance",
          register: "/staff/register-member",
          classes: "/admin/classes",
          reports: "/admin/reports",
          support: "/receptionist/support",
        }[page] || "/staff/dashboard"
      ),
  };

  const coach = {
    token: auth?.accessToken,
    role: auth?.role,
    onLogout: handleLogout,
    onNavigate: (page) =>
      navigate(
        {
          dashboard: "/coach/dashboard",
          "coach-schedule": "/coach/schedule",
          "coach-pt-sessions": "/coach/pt-sessions",
          "coach-assessments": "/coach/skill-assessments",
          "coach-attendance": "/coach/attendance",
        }[page] || "/coach/dashboard"
      ),
  };

  const member = {
    token: auth?.accessToken,
    role: auth?.role,
    onLogout: handleLogout,
    onNavigate: (page) =>
      navigate(
        {
          dashboard: "/member/dashboard",
          "member-courses": "/member/courses",
          "member-membership": "/member/membership",
          "member-schedule": "/member/schedule",
          "member-profile": "/member/profile",
          "member-levels": "/member/levels-assessments",
          "member-attendance": "/member/attendance",
          "member-pt-booking": "/member/pt-booking",
          "member-classes": "/member/classes",
        }[page] || "/member/dashboard"
      ),
  };

  return (
    <>
      <Routes>
        {/* Website công khai */}
        <Route element={<PublicLayout />}>
          <Route path="/" element={<Home />} />
          <Route path="/bo-mon" element={<Disciplines />} />
          <Route path="/bo-mon/:sportId" element={<Disciplines />} />

          <Route
            path="/khoa-hoc/:courseId"
            element={
              <CourseDetail
                auth={auth}
                onLogout={handleLogout}
              />
            }
          />

          <Route path="/huan-luyen-vien" element={<Coaches />} />
          <Route path="/co-so-vat-chat" element={<Facilities />} />
          <Route path="/tuyen-sinh" element={<Admission />} />

          <Route
            path="/membership-plans"
            element={<Navigate to="/tuyen-sinh" replace />}
          />
        </Route>

        {/* Đăng nhập */}
        <Route
          path="/login"
          element={
            auth ? (
              <Navigate to={home(auth.role)} replace />
            ) : (
              <Login onLoginSuccess={handleLogin} />
            )
          }
        />

        {/* Khu vực nhân viên */}
        <Route element={<StaffRoute auth={auth} />}>
          <Route
            path="/staff"
            element={<Navigate to="/staff/dashboard" replace />}
          />

          <Route
            path="/staff/attendance"
            element={
              <Navigate
                to={
                  auth?.role === "ADMIN"
                    ? "/admin/attendance"
                    : "/receptionist/attendance"
                }
                replace
              />
            }
          />

          <Route
            path="/staff/dashboard"
            element={<Dashboard {...staff} />}
          />

          <Route
            path="/staff/register-member"
            element={<RegisterMember {...staff} />}
          />

          <Route
            path="/staff/consultations"
            element={<ConsultationQueue {...staff} />}
          />

          <Route
            path="/staff/membership-plans"
            element={<MembershipPlans {...staff} />}
          />

          <Route
            path="/staff/members"
            element={<MemberList {...staff} />}
          />

          {/* Chỉ Admin */}
          <Route element={<AdminRoute auth={auth} />}>
            <Route
              path="/admin/attendance"
              element={<StaffAttendance {...staff} />}
            />

            <Route
              path="/staff/staff"
              element={<StaffManagement {...staff} />}
            />

            <Route
              path="/staff/sports"
              element={<SportManagement {...staff} />}
            />

            <Route
              path="/staff/facilities"
              element={<FacilityManagement {...staff} />}
            />

            <Route
              path="/staff/courses"
              element={<CourseManagement {...staff} />}
            />

            <Route
              path="/admin/notifications/broadcast"
              element={<AdminNotificationBroadcast {...staff} />}
            />

            <Route
              path="/admin/audit-logs"
              element={<AdminAuditLogs {...staff} />}
            />

            {/* Các trang mới của Admin — bọc trong Layout (sidebar + .app-shell) */}
            <Route element={<Layout {...staff} />}>
              <Route
                path="/admin"
                element={<Navigate to="/admin/courses" replace />}
              />

              <Route
                path="/admin/courses"
                element={
                  <CrudPage
                    kind="courses"
                    title="Quản lý khóa học"
                    subtitle="Tạo, cập nhật và quản lý khóa học."
                  />
                }
              />

              <Route
                path="/admin/classes"
                element={
                  <CrudPage
                    kind="classes"
                    title="Quản lý lớp học"
                    subtitle="Theo dõi lịch học, huấn luyện viên và sức chứa."
                  />
                }
              />

              <Route
                path="/admin/reports"
                element={<ReportPage />}
              />
            </Route>
          </Route>

          {/* Chỉ Receptionist — bọc trong Layout */}
          <Route element={<ReceptionistRoute auth={auth} />}>
            <Route element={<Layout {...staff} />}>
              <Route
                path="/receptionist/attendance"
                element={<AttendancePage />}
              />

              <Route
                path="/receptionist/support"
                element={<SupportPage />}
              />
            </Route>
          </Route>
        </Route>

        {/* Khu vực hội viên */}
        <Route element={<MemberRoute auth={auth} />}>
          <Route
            path="/member"
            element={<Navigate to="/member/dashboard" replace />}
          />

          <Route
            path="/member/dashboard"
            element={<Dashboard {...member} />}
          />

          <Route
            path="/member/attendance"
            element={<MemberAttendance {...member} />}
          />

          <Route
            path="/member/pt-booking"
            element={
              <PtBooking
                token={auth?.accessToken}
                onLogout={handleLogout}
              />
            }
          />

          <Route
            path="/member/levels-assessments"
            element={<MemberLevelsAssessments {...member} />}
          />

          <Route
            path="/member/courses"
            element={<MemberCourses {...member} />}
          />

          {/* Trang mới của hội viên — bọc trong Layout */}
          <Route element={<Layout {...member} />}>
            <Route
              path="/member/classes"
              element={
                <MemberPage
                  kind="member-classes"
                  title="Lớp học"
                  subtitle="Xem lịch và thông tin các lớp học."
                />
              }
            />
          </Route>

          <Route
            path="/member/membership"
            element={<MemberMembership {...member} />}
          />

          <Route
            path="/member/schedule"
            element={<Schedule {...member} />}
          />

          <Route
            path="/member/profile"
            element={<MemberProfile {...member} />}
          />
        </Route>

        {/* Khu vực huấn luyện viên */}
        <Route element={<CoachRoute auth={auth} />}>
          <Route
            path="/coach"
            element={<Navigate to="/coach/dashboard" replace />}
          />

          <Route
            path="/coach/dashboard"
            element={<Dashboard {...coach} />}
          />

          <Route
            path="/coach/schedule"
            element={<Schedule {...coach} />}
          />

          <Route
            path="/coach/pt-sessions"
            element={<CoachPtSessions {...coach} />}
          />

          <Route
            path="/coach/skill-assessments"
            element={<CoachSkillAssessments {...coach} />}
          />

          <Route
            path="/coach/attendance"
            element={<CoachAttendance {...coach} />}
          />
        </Route>

        {/* Trang không tồn tại */}
        <Route
          path="*"
          element={<Navigate to="/" replace />}
        />
      </Routes>

      {auth?.accessToken && (
        <NotificationBell
          token={auth.accessToken}
          role={auth.role}
        />
      )}
    </>
  );
}
