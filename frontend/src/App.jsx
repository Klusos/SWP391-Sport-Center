import { useState } from "react";
import Login from "./pages/Login";
import RegisterMember from "./pages/RegisterMember";
import "./styles.css";

export default function App() {
  const [auth, setAuth] = useState(null);

  if (!auth) {
    return <Login onLoginSuccess={setAuth} />;
  }

  return <RegisterMember token={auth.accessToken} onLogout={() => setAuth(null)} />;
}
