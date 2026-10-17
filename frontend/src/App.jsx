import { useState } from "react";
import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from "react-router-dom";
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
import MemberLevelsAssessments from "./pages/MemberLevelsAssessments";
import PublicLayout from "./public-site/PublicLayout";
import Admission from "./public-site/pages/Admission";
import Coaches from "./public-site/pages/Coaches";
import Disciplines from "./public-site/pages/Disciplines";
import Facilities from "./public-site/pages/Facilities";
import Home from "./public-site/pages/Home";
import CourseDetail from "./public-site/pages/CourseDetail";
import "./public-site/styles.css";

const STAFF_ROLES = ["ADMIN", "RECEPTIONIST"];

function StaffRoute({ auth }) {
  const location = useLocation();
  if (!auth) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!STAFF_ROLES.includes(auth.role)) return <Navigate to={auth.role === "COACH" ? "/coach/pt-sessions" : "/member/pt-booking"} replace />;
  return <Outlet />;
}

function MemberRoute({ auth }) {
  const location = useLocation();
  if (!auth) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (auth.role !== "MEMBER") return <Navigate to={auth.role === "COACH" ? "/coach/pt-sessions" : "/staff/register-member"} replace />;
  return <Outlet />;
}

function CoachRoute({ auth }) {
  const location = useLocation();
  if (!auth) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (auth.role !== "COACH") return <Navigate to={auth.role === "MEMBER" ? "/member/pt-booking" : "/staff/register-member"} replace />;
  return <Outlet />;
}

function homeForRole(role) {
  if (role === "MEMBER") return "/member/pt-booking";
  if (role === "COACH") return "/coach/pt-sessions";
  return "/staff/register-member";
}

function AdminRoute({ auth }) {
  if (!auth) return <Navigate to="/login" replace />;
  if (auth.role !== "ADMIN") return <Navigate to="/staff/register-member" replace />;
  return <Outlet />;
}

export default function App() {
  const [auth, setAuth] = useState(null);
  const navigate = useNavigate();
  const location = useLocation();

  function handleLogin(data) {
    setAuth(data);
    const returnTo = location.state?.from;
    const memberCourseReturn = data.role === "MEMBER" && typeof returnTo === "string"
      && /^\/khoa-hoc\/[1-9]\d*$/.test(returnTo);
    navigate(memberCourseReturn ? returnTo : homeForRole(data.role), { replace: true });
  }

  function handleLogout(returnTo) {
    setAuth(null);
    navigate("/login", { replace: true, state: returnTo ? { from: returnTo } : undefined });
  }

  const staffProps = {
    token: auth?.accessToken,
    role: auth?.role,
    onLogout: handleLogout,
    onNavigate: (page) => navigate(page === "consultations"
      ? "/staff/consultations"
      : page === "members" ? "/staff/members"
        : page === "staff" ? "/staff/staff"
        : page === "sports" ? "/staff/sports"
        : page === "facilities" ? "/staff/facilities"
        : page === "membership-plans" ? "/staff/membership-plans" : "/staff/register-member"),
  };
  const coachProps = {
    token: auth?.accessToken,
    role: auth?.role,
    onLogout: handleLogout,
    onNavigate: (page) => navigate(page === "coach-assessments" ? "/coach/skill-assessments" : "/coach/pt-sessions"),
  };
  const memberProps = {
    token: auth?.accessToken,
    role: auth?.role,
    onLogout: handleLogout,
    onNavigate: (page) => navigate(page === "member-levels" ? "/member/levels-assessments" : "/member/pt-booking"),
  };

  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/bo-mon" element={<Disciplines />} />
        <Route path="/bo-mon/:sportId" element={<Disciplines />} />
        <Route path="/khoa-hoc/:courseId" element={<CourseDetail auth={auth} onLogout={handleLogout} />} />
        <Route path="/huan-luyen-vien" element={<Coaches />} />
        <Route path="/co-so-vat-chat" element={<Facilities />} />
        <Route path="/tuyen-sinh" element={<Admission />} />
        <Route path="/membership-plans" element={<Navigate to="/tuyen-sinh" replace />} />
      </Route>
      <Route path="/login" element={auth ? <Navigate to={homeForRole(auth.role)} replace /> : <Login onLoginSuccess={handleLogin} />} />
      <Route element={<StaffRoute auth={auth} />}>
        <Route path="/staff" element={<Navigate to="/staff/register-member" replace />} />
        <Route path="/staff/register-member" element={<RegisterMember {...staffProps} />} />
        <Route path="/staff/consultations" element={<ConsultationQueue {...staffProps} />} />
        <Route path="/staff/membership-plans" element={<MembershipPlans {...staffProps} />} />
        <Route path="/staff/members" element={<MemberList {...staffProps} />} />
        <Route element={<AdminRoute auth={auth} />}>
          <Route path="/staff/staff" element={<StaffManagement {...staffProps} />} />
          <Route path="/staff/sports" element={<SportManagement {...staffProps} />} />
          <Route path="/staff/facilities" element={<FacilityManagement {...staffProps} />} />
        </Route>
      </Route>
      <Route element={<MemberRoute auth={auth} />}>
        <Route path="/member/pt-booking" element={<PtBooking token={auth?.accessToken} onLogout={handleLogout} />} />
        <Route path="/member/levels-assessments" element={<MemberLevelsAssessments {...memberProps} />} />
      </Route>
      <Route element={<CoachRoute auth={auth} />}>
        <Route path="/coach/pt-sessions" element={<CoachPtSessions {...coachProps} />} />
        <Route path="/coach/skill-assessments" element={<CoachSkillAssessments {...coachProps} />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
