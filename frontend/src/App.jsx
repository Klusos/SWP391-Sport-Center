import { useCallback, useEffect, useRef, useState } from "react";
import {
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";

import NotificationBell from "./components/NotificationBell";
import { setUnauthorizedHandler } from "./api";
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
import ReceptionistMembershipSales from "./pages/ReceptionistMembershipSales";
import ReceptionistCoursePayments from "./pages/ReceptionistCoursePayments";
import ReceptionistCourseRegistration from "./pages/ReceptionistCourseRegistration";
import PtBooking from "./pages/PtBooking";
import CoachPtSessions from "./pages/CoachPtSessions";
import CoachSkillAssessments from "./pages/CoachSkillAssessments";
import CoachAttendance from "./pages/CoachAttendance";
import StaffAttendance from "./pages/StaffAttendance";
import MemberAttendance from "./pages/MemberAttendance";
import MemberLevelsAssessments from "./pages/MemberLevelsAssessments";
import Dashboard from "./pages/Dashboard";
import CourseManagement from "./pages/CourseManagement";
import CourseList from "./pages/CourseList";
import CourseForm from "./pages/CourseForm";
import CourseCheckout from "./pages/CourseCheckout";
import SupportTickets from "./pages/SupportTickets";
import MemberCourses from "./pages/MemberCourses";
import MemberMembership from "./pages/MemberMembership";
import CoachTraining from "./pages/CoachTraining";
import MemberTraining from "./pages/MemberTraining";
import MemberProfile from "./pages/MemberProfile";
import Schedule from "./pages/Schedule";
import Layout from "./components/Layout";

// Các trang bổ sung
import CrudPage from "./pages/CrudPage";
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
const AUTH_STORAGE_KEY = "sportcenter.auth";

function readStoredAuth() {
  try {
    const value = JSON.parse(sessionStorage.getItem(AUTH_STORAGE_KEY) || "null");
    return value?.accessToken && ["ADMIN", "RECEPTIONIST", "COACH", "MEMBER"].includes(value.role)
      ? value
      : null;
  } catch {
    return null;
  }
}

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
        state={{ from: location.pathname + location.search }}
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
        state={{ from: location.pathname + location.search }}
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
  const [auth, setAuth] = useState(readStoredAuth);
  const navigate = useNavigate();
  const location = useLocation();
  const authRef = useRef(auth);
  const handledUnauthorizedToken = useRef(null);

  const handleUnauthorized = useCallback((token) => {
    const currentAuth = authRef.current;
    if (!currentAuth || currentAuth.accessToken !== token || handledUnauthorizedToken.current === token) {
      return;
    }

    handledUnauthorizedToken.current = token;
    authRef.current = null;
    try {
      sessionStorage.removeItem(AUTH_STORAGE_KEY);
    } catch {
      // Ignore unavailable browser storage.
    }
    setAuth(null);
    navigate("/login", { replace: true, state: { sessionExpired: true } });
  }, [navigate]);

  useEffect(() => {
    setUnauthorizedHandler(handleUnauthorized);
    return () => setUnauthorizedHandler(null);
  }, [handleUnauthorized]);

  function handleLogin(data) {
    authRef.current = data;
    handledUnauthorizedToken.current = null;
    setAuth(data);
    try {
      sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(data));
    } catch {
      // Keep this login active in memory if session storage is unavailable.
    }

    const returnPath = location.state?.from;

    // Quay lại trang chi tiết khóa học nếu hội viên đăng nhập từ đó.
    const courseReturn =
      data.role === "MEMBER" &&
      typeof returnPath === "string" &&
      (/^\/member\/(?:course-checkout\/[1-9]\d*|membership)(?:\?[^#]*)?$/.test(returnPath) ||
        /^\/member\/courses\/[1-9]\d*$/.test(returnPath) ||
        /^\/khoa-hoc\/[1-9]\d*$/.test(returnPath));

    const memberCoursePath = courseReturn
      ? returnPath.replace(/^\/khoa-hoc\//, "/member/courses/")
      : returnPath;

    navigate(
      courseReturn ? memberCoursePath : home(data.role),
      { replace: true }
    );
  }

  function handleLogout(returnTo) {
    authRef.current = null;
    setAuth(null);
    try {
      sessionStorage.removeItem(AUTH_STORAGE_KEY);
    } catch {
      // Ignore unavailable browser storage.
    }

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
          "admin-training": "/admin/training",
          "membership-plans": "/staff/membership-plans",
          "membership-sales": "/receptionist/membership-sales",
          "course-payments": "/receptionist/course-payments",
          "course-registration": "/receptionist/course-registration",
          "support-tickets": "/staff/support-tickets",
          attendance: "/admin/attendance",
          register: "/staff/register-member",
          classes: "/admin/classes",
          reports: "/admin/reports",
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
          "coach-training": "/coach/training",
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
          "member-training": "/member/training",
          "member-attendance": "/member/attendance",
          "member-pt-booking": "/member/pt-booking",
          "support-tickets": "/member/support-tickets",
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
              auth?.role === "MEMBER" ? (
                <Navigate
                  to={`/member/courses/${location.pathname.split("/").at(-1)}`}
                  replace
                />
              ) : (
                <CourseDetail auth={auth} onLogout={handleLogout} />
              )
            }
          />

          <Route path="/huan-luyen-vien" element={<Coaches />} />
          <Route path="/co-so-vat-chat" element={<Facilities />} />
          <Route path="/tuyen-sinh" element={<Admission auth={auth} />} />

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
              <Login
                onLoginSuccess={handleLogin}
                sessionExpired={Boolean(location.state?.sessionExpired)}
              />
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
                    : "/staff/dashboard"
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
            element={auth?.role === "ADMIN" ? <RegisterMember {...staff} /> : <Navigate to="/staff/consultations" replace />}
          />

          <Route
            path="/staff/consultations"
            element={<ConsultationQueue {...staff} />}
          />

          <Route path="/staff/support-tickets" element={<SupportTickets {...staff} />} />

          <Route
            path="/staff/members"
            element={<MemberList {...staff} />}
          />

          <Route element={<ReceptionistRoute auth={auth} />}>
            <Route
              path="/receptionist/membership-sales"
              element={<ReceptionistMembershipSales {...staff} />}
            />
          </Route>

          <Route path="/receptionist/course-payments" element={<ReceptionistCoursePayments {...staff} userId={auth?.userId} />} />
          <Route path="/receptionist/course-registration" element={<ReceptionistCourseRegistration {...staff} />} />

          {/* Chỉ Admin */}
          <Route element={<AdminRoute auth={auth} />}>
            <Route
              path="/staff/membership-plans"
              element={<MembershipPlans {...staff} />}
            />
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
              element={<CourseList {...staff} />}
            />
            <Route path="/staff/courses/new" element={<CourseForm {...staff} />} />
            <Route path="/staff/courses/:courseId/edit" element={<CourseForm {...staff} />} />
            <Route path="/staff/courses/:courseId" element={<CourseManagement {...staff} />} />

            <Route
              path="/admin/notifications/broadcast"
              element={<AdminNotificationBroadcast {...staff} />}
            />

            <Route
              path="/admin/audit-logs"
              element={<AdminAuditLogs {...staff} />}
            />

            <Route path="/admin/training" element={<CoachTraining {...staff} />} />

            {/* Các trang mới của Admin — bọc trong Layout (sidebar + .app-shell) */}
            <Route element={<Layout {...staff} />}>
              <Route
                path="/admin"
                element={<Navigate to="/staff/dashboard" replace />}
              />

              <Route
                path="/admin/courses"
                element={<Navigate to="/staff/courses" replace />}
              />

              <Route
                path="/admin/classes"
                element={
                  <CrudPage
                    kind="classes"
                    title="Demo — Quản lý lớp học"
                    subtitle="Giao diện mẫu với dữ liệu minh họa; không sửa dữ liệu SportCenter."
                  />
                }
              />

              <Route
                path="/admin/reports"
                element={<ReportPage {...staff} />}
              />
            </Route>
          </Route>

          {/* Chỉ Receptionist — bọc trong Layout */}
          <Route element={<ReceptionistRoute auth={auth} />}>
            <Route element={<Layout {...staff} />}>
              <Route path="/receptionist/attendance" element={<Navigate to="/staff/dashboard" replace />} />

              <Route
                path="/receptionist/support"
                element={<Navigate to="/staff/support-tickets" replace />}
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
            element={<PtBooking {...member} />}
          />

          <Route
            path="/member/levels-assessments"
            element={<MemberLevelsAssessments {...member} />}
          />

          <Route path="/member/training" element={<MemberTraining {...member} />} />

          <Route
            path="/member/courses"
            element={<MemberCourses {...member} />}
          />

          <Route
            path="/member/courses/:courseId"
            element={<CourseDetail auth={auth} memberRoute onLogout={handleLogout} onNavigate={member.onNavigate} />}
          />
          <Route path="/member/course-checkout/:registrationId" element={<CourseCheckout auth={auth} {...member} />} />
          <Route path="/member/support-tickets" element={<SupportTickets {...member} />} />

          {/* Trang mới của hội viên — bọc trong Layout */}
          <Route element={<Layout {...member} />}>
            <Route
              path="/member/classes"
              element={
                <MemberPage
                  kind="member-classes"
                title="Demo — Lớp học"
                subtitle="Giao diện mẫu với dữ liệu minh họa; lịch thật xem tại mục Lịch trình."
                />
              }
            />
          </Route>

          <Route
            path="/member/membership"
            element={new URLSearchParams(location.search).has("paymentId")
                || new URLSearchParams(location.search).has("paymentStatus")
                || new URLSearchParams(location.search).has("reason")
              ? <CourseCheckout auth={auth} {...member} />
              : <MemberMembership {...member} />}
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

          <Route path="/coach/training" element={<CoachTraining {...coach} />} />
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
