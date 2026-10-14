import { useState } from "react";
import Login from "./pages/Login";
import RegisterMember from "./pages/RegisterMember";
import ConsultationQueue from "./pages/ConsultationQueue";
import "./styles.css";

export default function App() {
  const [auth, setAuth] = useState(null);
  const [page, setPage] = useState("register");

  if (!auth) {
    return <Login onLoginSuccess={(data) => { setAuth(data); setPage("register"); }} />;
  }

  const shared = {
    token: auth.accessToken,
    role: auth.role,
    onLogout: () => { setAuth(null); setPage("register"); },
    onNavigate: setPage,
  };

  return page === "consultations"
    ? <ConsultationQueue {...shared} />
    : <RegisterMember {...shared} />;
}
