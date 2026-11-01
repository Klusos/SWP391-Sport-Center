import { useState } from "react";
import { loginApi } from "../api";
import { Link } from "react-router-dom";

export default function Login({ onLoginSuccess, sessionExpired = false }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      const data = await loginApi(email, password);
      if (!data?.accessToken || typeof data.accessToken !== "string") {
        setError("The login response did not include an access token. Please contact support.");
        return;
      }
      if (!["ADMIN", "RECEPTIONIST", "MEMBER", "COACH"].includes(data.role)) {
        setError("This account does not have a supported application role.");
        return;
      }
      onLoginSuccess(data);
    } catch (requestError) {
      if (requestError.status === 415) {
        setError("The server rejected the login content type. This frontend sends application/json; verify the running frontend and backend endpoint versions.");
      } else {
        setError(requestError.message || "Could not connect to the server.");
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-screen">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1>Sign in</h1>
        {sessionExpired && <p className="error-text" role="status">Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.</p>}
        <p><Link to="/">← Quay về trang chủ</Link></p>
        <p>For SportCenter members and staff</p>

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
          <label htmlFor="loginPassword">Password</label>
          <input
            id="loginPassword"
            type="password"
            autoComplete="current-password"
            placeholder="Enter your password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>

        <button className="btn-primary" type="submit" disabled={loading}>
          {loading ? "Signing in…" : "Sign in"}
        </button>
        <div className="error-text" role="alert">{error}</div>
      </form>
    </div>
  );
}

