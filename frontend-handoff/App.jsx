import { useState } from "react";
import Login from "./components/Login";
import RegisterMember from "./components/RegisterMember";
import "./styles.css";

export default function App() {
  const [auth, setAuth] = useState(null); // { accessToken, role, name, ... }

  function handleLoginSuccess(data) {
    setAuth(data);
  }

  function handleLogout() {
    setAuth(null);
  }

  if (!auth) {
    return <Login onLoginSuccess={handleLoginSuccess} />;
  }

  return <RegisterMember token={auth.accessToken} onLogout={handleLogout} />;
}
