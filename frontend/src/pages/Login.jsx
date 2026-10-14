import { useState } from "react";
import { loginApi } from "../api";

export default function Login({ onLoginSuccess }) {
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
      if (data.role !== "RECEPTIONIST" && data.role !== "ADMIN") {
        setError("This account cannot register members.");
        return;
      }
      onLoginSuccess(data);
    } catch (requestError) {
      setError(requestError.message || "Could not connect to the server.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-screen">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1>Sign in</h1>
        <p>For SportCenter staff</p>

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
