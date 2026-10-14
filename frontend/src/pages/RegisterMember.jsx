import { useState } from "react";
import Sidebar from "../components/Sidebar";
import { createMemberApi } from "../api";

const EMPTY_FORM = {
  name: "",
  email: "",
  phone: "",
  dateOfBirth: "",
  gender: "",
  password: "",
};

export default function RegisterMember({ token, onLogout }) {
  const [form, setForm] = useState(EMPTY_FORM);
  const [successMsg, setSuccessMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(false);

  function handleChange(field, value) {
    setForm((previous) => ({ ...previous, [field]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSuccessMsg("");
    setErrorMsg("");
    setLoading(true);
    try {
      await createMemberApi(token, form);
      setSuccessMsg("Member registered successfully.");
      setForm(EMPTY_FORM);
    } catch (requestError) {
      setErrorMsg(requestError.message || "Member registration failed.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="layout">
      <Sidebar onLogout={onLogout} />
      <main className="main">
        <h1>Register a member</h1>
        <p className="subtitle">Create a new SportCenter member account.</p>

        <form className="form-card" onSubmit={handleSubmit}>
          {successMsg && <div className="banner success" role="status">{successMsg}</div>}
          {errorMsg && <div className="banner error" role="alert">{errorMsg}</div>}

          <div className="grid-2">
            <div className="field">
              <label htmlFor="name">Full name</label>
              <input id="name" type="text" value={form.name} onChange={(event) => handleChange("name", event.target.value)} required />
            </div>
            <div className="field">
              <label htmlFor="phone">Phone</label>
              <input id="phone" type="tel" value={form.phone} onChange={(event) => handleChange("phone", event.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="email">Email</label>
              <input id="email" type="email" autoComplete="email" value={form.email} onChange={(event) => handleChange("email", event.target.value)} required />
            </div>
            <div className="field">
              <label htmlFor="dob">Date of birth</label>
              <input id="dob" type="date" value={form.dateOfBirth} onChange={(event) => handleChange("dateOfBirth", event.target.value)} required />
            </div>
            <div className="field">
              <label htmlFor="gender">Gender</label>
              <select id="gender" value={form.gender} onChange={(event) => handleChange("gender", event.target.value)}>
                <option value="">Choose gender</option>
                <option value="F">Female</option>
                <option value="M">Male</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="password">Temporary password</label>
              <input id="password" type="password" autoComplete="new-password" minLength={8} value={form.password} onChange={(event) => handleChange("password", event.target.value)} required />
            </div>
          </div>

          <div className="form-actions">
            <button className="btn-primary" type="submit" disabled={loading}>
              {loading ? "Registering…" : "Register member"}
            </button>
          </div>
        </form>
      </main>
    </div>
  );
}
