import { useState } from "react";
import { Link } from "react-router-dom";

export default function Login({ onLogin, sessionExpired = false }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      await onLogin(email, password);
    } catch (requestError) {
      if (requestError.status === 415) {
        setError("Máy chủ không chấp nhận định dạng đăng nhập. Vui lòng kiểm tra phiên bản ứng dụng và thử lại.");
      } else {
        setError(requestError.message || "Không thể kết nối đến máy chủ.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-screen">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1>Đăng nhập</h1>
        {sessionExpired && <p className="error-text" role="status">Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.</p>}
        <p><Link to="/">← Quay về trang chủ</Link></p>
        <p>Dành cho hội viên và nhân viên SportCenter</p>

        <div className="field">
          <label htmlFor="loginEmail">Email</label>
          <input
            id="loginEmail"
            type="email"
            autoComplete="username"
            placeholder="name@sportcenter.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="loginPassword">Mật khẩu</label>
          <input
            id="loginPassword"
            type="password"
            autoComplete="current-password"
            placeholder="Nhập mật khẩu"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>

        <button className="btn-primary" type="submit" disabled={loading}>
          {loading ? "Đang đăng nhập…" : "Đăng nhập"}
        </button>
        <div className="error-text" role="alert">{error}</div>
      </form>
    </div>
  );
}

