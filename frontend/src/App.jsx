import { useState } from "react";
import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import ConsultationQueue from "./pages/ConsultationQueue";
import Login from "./pages/Login";
import RegisterMember from "./pages/RegisterMember";
import MemberList from "./pages/MemberList";
import StaffManagement from "./pages/StaffManagement";
import MembershipPlans from "./pages/MembershipPlans";
import PublicLayout from "./public-site/PublicLayout";
import Admission from "./public-site/pages/Admission";
import PublicMembershipPlans from "./public-site/pages/PublicMembershipPlans";
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
  if (!STAFF_ROLES.includes(auth.role)) return <Navigate to="/" replace />;
  return <Outlet />;
}

function AdminRoute({ auth }) {
  if (!auth) return <Navigate to="/login" replace />;
  if (auth.role !== "ADMIN") return <Navigate to="/staff/register-member" replace />;
  return <Outlet />;
}

export default function App() {
  const [auth, setAuth] = useState(null);
  const navigate = useNavigate();

  function handleLogin(data) {
    setAuth(data);
    navigate("/staff/register-member", { replace: true });
  }

  function handleLogout() {
    setAuth(null);
    navigate("/login", { replace: true });
  }

  const staffProps = {
    token: auth?.accessToken,
    role: auth?.role,
    onLogout: handleLogout,
    onNavigate: (page) => navigate(page === "consultations"
      ? "/staff/consultations"
      : page === "members" ? "/staff/members"
        : page === "staff" ? "/staff/staff"
        : page === "membership-plans" ? "/staff/membership-plans" : "/staff/register-member"),
  };

  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/bo-mon" element={<Disciplines />} />
        <Route path="/bo-mon/:sportId" element={<Disciplines />} />
        <Route path="/khoa-hoc/:courseId" element={<CourseDetail />} />
        <Route path="/huan-luyen-vien" element={<Coaches />} />
        <Route path="/co-so-vat-chat" element={<Facilities />} />
        <Route path="/tuyen-sinh" element={<Admission />} />
        <Route path="/membership-plans" element={<PublicMembershipPlans />} />
      </Route>
      <Route path="/login" element={auth ? <Navigate to="/staff/register-member" replace /> : <Login onLoginSuccess={handleLogin} />} />
      <Route element={<StaffRoute auth={auth} />}>
        <Route path="/staff" element={<Navigate to="/staff/register-member" replace />} />
        <Route path="/staff/register-member" element={<RegisterMember {...staffProps} />} />
        <Route path="/staff/consultations" element={<ConsultationQueue {...staffProps} />} />
        <Route path="/staff/membership-plans" element={<MembershipPlans {...staffProps} />} />
        <Route path="/staff/members" element={<MemberList {...staffProps} />} />
        <Route element={<AdminRoute auth={auth} />}>
          <Route path="/staff/staff" element={<StaffManagement {...staffProps} />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
