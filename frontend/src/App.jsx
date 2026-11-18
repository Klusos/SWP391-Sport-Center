import { lazy, Suspense, useCallback, useEffect, useRef, useState } from "react";
import {
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
} from "react-router-dom";

import NotificationBell from "./components/NotificationBell";
import { PortalIdentityProvider } from "./components/PortalIdentityContext.jsx";
import { notificationIdentityKey } from "./notificationIdentity.js";
import { shouldShowNotificationBell } from "./notificationVisibility.js";
import { loginApi, logoutApi, setActiveAuthToken, setUnauthorizedHandler } from "./api";
import {
  clearLegacyTabAuth,
  clearSharedAuthIfToken,
  getSafeLogoutReturnTo,
  readSharedAuth,
  subscribeToSharedAuth,
  withAuthTransitionLock,
  writeSharedAuth,
} from "./authStorage";
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
import ReceptionistPtPayments from "./pages/ReceptionistPtPayments";
import ReceptionistCourseRegistration from "./pages/ReceptionistCourseRegistration";
import PtBooking from "./pages/PtBooking";
import CoachPtSessions from "./pages/CoachPtSessions";
import CoachSkillAssessments from "./pages/CoachSkillAssessments";
import CoachAttendance from "./pages/CoachAttendance";
import CoachTraining from "./pages/CoachTraining";
import MemberTraining from "./pages/MemberTraining";
import StaffAttendance from "./pages/StaffAttendance";
import MemberAttendance from "./pages/MemberAttendance";
import MemberLevelsAssessments from "./pages/MemberLevelsAssessments";
import Dashboard from "./pages/Dashboard";
import CourseManagementPages from "./pages/CourseManagementPages";
import CourseList from "./pages/CourseList";
import CourseForm from "./pages/CourseForm";
import CourseCheckout from "./pages/CourseCheckout";
import SupportTickets from "./pages/SupportTickets";
import MemberCourses from "./pages/MemberCourses";
import MemberMembership from "./pages/MemberMembership";
import MemberProfile from "./pages/MemberProfile";
import Schedule from "./pages/Schedule";
import Layout from "./components/Layout";

// Các trang bổ sung
import ReportPage from "./pages/ReportPage";

// Public website
import PublicLayout from "./public-site/PublicLayout";
import { ThemeProvider } from "./public-site/PublicThemeContext.jsx";
import Disciplines from "./public-site/pages/Disciplines";
import Facilities from "./public-site/pages/Facilities";
import CourseDetail from "./public-site/pages/CourseDetail";

import "./public-site/publicTheme.css";
import "./public-site/styles.css";

const LandingPage = lazy(() => import("./landing/LandingPage.jsx"));

function AdminCourseRouteRedirect({ depth }) {
  const { courseId, offeringId, classId } = useParams();
  let destination = `/staff/courses/${courseId}`;
  if (depth >= 2) destination += `/offerings/${offeringId}`;
  if (depth >= 3) destination += `/classes/${classId}`;
  return <Navigate to={destination} replace />;
}

function LandingSectionRedirect({ sectionId }) {
  const location = useLocation();
  return <Navigate to={{ pathname: "/", search: location.search, hash: `#${sectionId}` }} replace />;
}

const STAFF_ROLES = ["ADMIN", "RECEPTIONIST"];
function initializeAuth() {
  clearLegacyTabAuth();
  const stored = readSharedAuth();
  setActiveAuthToken(stored?.accessToken ?? null);
  return stored;
}

const home = (role) => {
  if (role === "MEMBER") return "/member/dashboard";
  if (role === "COACH") return "/coach/dashboard";
  return "/staff/dashboard";
};

function redactLogoutDiagnostic(value) {
  return String(value || "")
    .replace(/\bBearer\s+[^\s"'`]+/gi, "Bearer [REDACTED]")
    .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, "[REDACTED_JWT]")
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "[REDACTED_EMAIL]");
}

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
  const [auth, setAuth] = useState(initializeAuth);
  const navigate = useNavigate();
  const location = useLocation();
  const authRef = useRef(auth);
  const handledUnauthorizedToken = useRef(null);
  const [logoutError, setLogoutError] = useState("");

  const handleUnauthorized = useCallback((token) => {
    void withAuthTransitionLock(async () => {
      const currentAuth = authRef.current;
      if (!currentAuth || currentAuth.accessToken !== token || handledUnauthorizedToken.current === token) {
        return;
      }

      const sharedAuth = readSharedAuth();
      if (sharedAuth?.accessToken !== token) {
        if (sharedAuth?.accessToken !== currentAuth.accessToken) {
          authRef.current = sharedAuth;
          setActiveAuthToken(sharedAuth?.accessToken ?? null);
          setAuth(sharedAuth);
        }
        return;
      }

      handledUnauthorizedToken.current = token;
      if (clearSharedAuthIfToken(token)) {
        authRef.current = null;
        setActiveAuthToken(null);
        setAuth(null);
        navigate("/login", { replace: true, state: { sessionExpired: true } });
      }
    }).catch(() => {});
  }, [navigate]);

  useEffect(() => {
    setUnauthorizedHandler(handleUnauthorized);
    return () => setUnauthorizedHandler(null);
  }, [handleUnauthorized]);

  useEffect(() => subscribeToSharedAuth((nextAuth) => {
    const currentAuth = authRef.current;
    const currentToken = currentAuth?.accessToken ?? null;
    const nextToken = nextAuth?.accessToken ?? null;
    if (currentToken === nextToken) return;

    authRef.current = nextAuth;
    setLogoutError("");
    handledUnauthorizedToken.current = null;
    setActiveAuthToken(nextToken);
    setAuth(nextAuth);
  }), []);

  useEffect(() => {
    authRef.current = auth;
    setActiveAuthToken(auth?.accessToken ?? null);
  }, [auth]);

  async function handleLogin(email, password) {
    setLogoutError("");
    return withAuthTransitionLock(async () => {
      const previousAuth = readSharedAuth();
      const data = await loginApi(email, password);
      if (!data?.accessToken || typeof data.accessToken !== "string") {
        throw new Error("Phản hồi đăng nhập không có access token hợp lệ.");
      }
      if (!["ADMIN", "RECEPTIONIST", "COACH", "MEMBER"].includes(data.role)) {
        throw new Error("Tài khoản không có vai trò được hỗ trợ.");
      }

      if (previousAuth?.accessToken && previousAuth.accessToken !== data.accessToken) {
        try {
          await logoutApi(previousAuth.accessToken);
        } catch (revokeError) {
          // Do not publish the new identity when the old token could not be revoked.
          try { await logoutApi(data.accessToken); } catch { /* best-effort cleanup only */ }
          if (revokeError.status === 401) {
            // The old credential is already unusable; account switching can proceed.
          } else {
            const error = new Error("Không thể thu hồi phiên đăng nhập trước. Vui lòng thử lại; tài khoản hiện tại chưa thay đổi.");
            error.status = revokeError.status;
            throw error;
          }
        }
      }

      const currentShared = readSharedAuth();
      if ((currentShared?.accessToken ?? null) !== (previousAuth?.accessToken ?? null)) {
        try { await logoutApi(data.accessToken); } catch { /* avoid retaining an unpublished credential where possible */ }
        throw new Error("Phiên đăng nhập đã thay đổi ở tab khác. Vui lòng thử đăng nhập lại.");
      }
      if (!writeSharedAuth(data)) {
        try { await logoutApi(data.accessToken); } catch { /* best-effort cleanup only */ }
        throw new Error("Không thể lưu phiên đăng nhập dùng chung. Vui lòng thử lại.");
      }

      authRef.current = data;
      handledUnauthorizedToken.current = null;
      setActiveAuthToken(data.accessToken);
      setAuth(data);

      const returnPath = location.state?.from;

      // Return Members to the requested course when login began there.
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
      return data;
    });
  }

  async function handleLogout(returnTo) {
    setLogoutError("");
    let stage = "acquire-transition-lock";
    try {
      return await withAuthTransitionLock(async () => {
        stage = "read-shared-auth";
        const previousAuth = readSharedAuth() ?? authRef.current;
        const token = previousAuth?.accessToken;
        if (token) {
          try {
            stage = "revoke-token";
            await logoutApi(token);
          } catch (error) {
            if (error.status !== 401) {
              setLogoutError("Không thể xác nhận đăng xuất với máy chủ. Phiên hiện tại vẫn được giữ để bạn có thể thử lại.");
              return false;
            }
            // A 401 means this credential is already unusable, not that revocation succeeded.
          }
          stage = "clear-shared-auth";
          clearSharedAuthIfToken(token);
        }

        stage = "read-latest-auth";
        const latestAuth = readSharedAuth();
        if (latestAuth) {
          stage = "restore-newer-shared-auth";
          authRef.current = latestAuth;
          setActiveAuthToken(latestAuth.accessToken);
          setAuth(latestAuth);
          return false;
        }
        stage = "clear-local-auth-state";
        authRef.current = null;
        setActiveAuthToken(null);
        setAuth(null);
        stage = "prepare-login-navigation";
        const safeReturnTo = getSafeLogoutReturnTo(returnTo);
        stage = "navigate-to-login";
        navigate("/login", {
          replace: true,
          state: safeReturnTo ? { from: safeReturnTo } : undefined,
        });
        return true;
      });
    } catch (caught) {
      if (import.meta.env.DEV) {
        const error = caught instanceof Error ? caught : new Error(String(caught));
        console.error("[SportCenter logout diagnostic]", {
          stage,
          name: redactLogoutDiagnostic(error.name),
          message: redactLogoutDiagnostic(error.message),
          stack: redactLogoutDiagnostic(error.stack),
        });
      }
      if (stage === "acquire-transition-lock") {
        setLogoutError("Không thể đồng bộ đăng xuất giữa các tab. Vui lòng thử lại.");
      } else {
        setLogoutError("Có lỗi khi hoàn tất đăng xuất. Vui lòng kiểm tra trạng thái đăng nhập trước khi thử lại.");
      }
      return false;
    }
  }

  const staff = {
    token: auth?.accessToken,
    role: auth?.role,
    currentUserName: auth?.name,
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
          "admin-membership-sales": "/admin/membership-sales",
          "membership-plans": "/staff/membership-plans",
          "membership-sales": "/receptionist/membership-sales",
          "course-payments": "/receptionist/course-payments",
          "pt-payments": "/receptionist/pt-payments",
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
    currentUserName: auth?.name,
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
        }[page] || "/member/dashboard"
      ),
  };

  const landingRoute = (
    <Suspense fallback={<p className="landing-preview-loading" role="status">Đang mở trang chủ…</p>}>
      <LandingPage auth={auth} dashboardPath={home(auth?.role)} />
    </Suspense>
  );

  return (
    <ThemeProvider>
    <PortalIdentityProvider currentUserName={auth?.name}>
    <>
      {logoutError && <div className="error-text global-auth-error" role="alert">{logoutError}</div>}
      <Routes key={auth?.accessToken || "anonymous"}>
        {/* The approved Landing is the canonical public Home. */}
        <Route path="/" element={landingRoute} />
        <Route
          path="/landing-preview"
          element={<Navigate to={{ pathname: "/", search: location.search, hash: location.hash }} replace />}
        />

        {/* Website công khai */}
        <Route element={<PublicLayout auth={auth} dashboardPath={home(auth?.role)} />}>
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

          <Route path="/huan-luyen-vien" element={<LandingSectionRedirect sectionId="huan-luyen-vien" />} />
          <Route path="/co-so-vat-chat" element={<Facilities />} />
          <Route path="/tuyen-sinh" element={<LandingSectionRedirect sectionId="tuyen-sinh" />} />

          <Route
            path="/membership-plans"
            element={<LandingSectionRedirect sectionId="tuyen-sinh" />}
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
                onLogin={handleLogin}
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
          <Route path="/receptionist/pt-payments" element={<ReceptionistPtPayments {...staff} userId={auth?.userId} />} />
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
            <Route path="/staff/courses/:courseId/offerings/:offeringId/classes/:classId" element={<CourseManagementPages {...staff} />} />
            <Route path="/staff/courses/:courseId/offerings/:offeringId" element={<CourseManagementPages {...staff} />} />
            <Route path="/staff/courses/:courseId" element={<CourseManagementPages {...staff} />} />

            <Route
              path="/admin/notifications/broadcast"
              element={<AdminNotificationBroadcast {...staff} />}
            />

            <Route
              path="/admin/audit-logs"
              element={<AdminAuditLogs {...staff} />}
            />

            <Route path="/admin/training" element={<CoachTraining {...staff} />} />
            <Route path="/admin/membership-sales" element={<ReceptionistMembershipSales {...staff} />} />

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

              <Route path="/admin/courses/:courseId/offerings/:offeringId/classes/:classId" element={<AdminCourseRouteRedirect depth={3} />} />
              <Route path="/admin/courses/:courseId/offerings/:offeringId" element={<AdminCourseRouteRedirect depth={2} />} />
              <Route path="/admin/courses/:courseId" element={<AdminCourseRouteRedirect depth={1} />} />

              <Route
                path="/admin/classes"
                element={<Navigate to="/staff/courses" replace />}
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
          <Route path="/member/classes" element={<Navigate to="/member/schedule" replace />} />
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
            path="/member/training/:classId"
            element={<MemberTraining {...member} />}
          />

          <Route
            path="/member/training"
            element={<MemberTraining {...member} />}
          />

          <Route
            path="/member/pt-booking"
            element={<PtBooking {...member} />}
          />

          <Route
            path="/member/levels-assessments"
            element={<MemberLevelsAssessments {...member} />}
          />


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

      {shouldShowNotificationBell(location.pathname, auth) && (
        <NotificationBell
          key={notificationIdentityKey(auth)}
          token={auth.accessToken}
          role={auth.role}
        />
      )}
    </>
    </PortalIdentityProvider>
    </ThemeProvider>
  );
}
