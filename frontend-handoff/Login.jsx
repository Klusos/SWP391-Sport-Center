import { useState } from "react";
import { loginApi } from "../api";

export default function Login({ onLoginSuccess }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleLogin() {
    setError("");
    setLoading(true);
    try {
      const data = await loginApi(email, password);
      if (data.role !== "RECEPTIONIST" && data.role !== "ADMIN") {
        setError("Tài khoản này không có quyền đăng ký thành viên.");
        return;
      }
      onLoginSuccess(data);
    } catch (e) {
      setError(e.message || "Không kết nối được tới server.");
    } finally {
      setLoading(false);
    }
  }

  function handleKeyDown(e) {
    if (e.key === "Enter") handleLogin();
  }

  return (
    <div className="login-screen">
      <div className="login-card">
        <h1>Đăng nhập</h1>
        <p>Dành cho nhân viên lễ tân (Receptionist)</p>

        <div className="field">
          <label htmlFor="loginEmail">Email</label>
          <input
            id="loginEmail"
            type="email"
            placeholder="ten@sportcenter.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>

        <div className="field">
          <label htmlFor="loginPassword">Mật khẩu</label>
          <input
            id="loginPassword"
            type="password"
            placeholder="Nhập mật khẩu"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={handleKeyDown}
          />
        </div>

        <button className="btn-primary" onClick={handleLogin} disabled={loading}>
          {loading ? "Đang đăng nhập..." : "Đăng nhập"}
        </button>

        <div className="error-text">{error}</div>
      </div>
    </div>
  );
}
