import { useState } from "react";
import AdminTopBar from "../components/AdminTopBar";
import Sidebar from "../components/Sidebar";
import { createMemberApi } from "../api";
import "./RegisterMember.css";

const EMPTY_FORM = {
  name: "",
  email: "",
  phone: "",
  dateOfBirth: "",
  gender: "",
  password: "",
};

export default function RegisterMember({ token, role, currentUserName, onLogout, onNavigate }) {
  const [form, setForm] = useState(EMPTY_FORM);
  const [successMsg, setSuccessMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(false);

  function handleChange(field, value) {
    setForm((previous) => ({ ...previous, [field]: value }));
  }

  function handleInvalid(event) {
    event.preventDefault();
    const { type, validity } = event.currentTarget;
    if (type === "email" && validity.typeMismatch) {
      setErrorMsg("Vui lòng nhập địa chỉ email hợp lệ.");
    } else if (validity.tooShort) {
      setErrorMsg("Mật khẩu cần có ít nhất 8 ký tự.");
    } else {
      setErrorMsg("Vui lòng điền đầy đủ các trường bắt buộc.");
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSuccessMsg("");
    setErrorMsg("");
    setLoading(true);
    try {
      await createMemberApi(token, form);
      setSuccessMsg("Đăng ký thành viên thành công.");
      setForm(EMPTY_FORM);
    } catch (requestError) {
      setErrorMsg(requestError.message || "Không thể đăng ký thành viên. Vui lòng thử lại.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="register" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main register-member-page">
        <AdminTopBar
          pageLabel="ĐĂNG KÝ THÀNH VIÊN"
          displayName={currentUserName}
          avatarName={currentUserName}
        />
        <div className="register-member-content">
        <header className="register-member-header">
          <div>
            <h1>Đăng ký thành viên</h1>
            <p className="subtitle">Tạo tài khoản thành viên mới trong hệ thống SportCenter.</p>
          </div>
          <button className="register-member-back" type="button" onClick={() => onNavigate("members")}>
            <i className="bi bi-arrow-left" aria-hidden="true" />
            Quay lại danh sách thành viên
          </button>
        </header>
        <form className="form-card register-member-form" onSubmit={handleSubmit}>
          {successMsg && <div className="banner success" role="status">{successMsg}</div>}
          {errorMsg && <div className="banner error" role="alert">{errorMsg}</div>}
          <div className="grid-2">
            <div className="field">
              <label htmlFor="name">Họ và tên</label>
              <input id="name" type="text" value={form.name} onChange={(event) => handleChange("name", event.target.value)} onInvalid={handleInvalid} required />
            </div>
            <div className="field">
              <label htmlFor="phone">Số điện thoại <span className="register-member-optional">(không bắt buộc)</span></label>
              <input id="phone" type="tel" autoComplete="tel" value={form.phone} onChange={(event) => handleChange("phone", event.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="email">Email</label>
              <input id="email" type="email" autoComplete="email" value={form.email} onChange={(event) => handleChange("email", event.target.value)} onInvalid={handleInvalid} required />
            </div>
            <div className="field">
              <label htmlFor="dob">Ngày sinh</label>
              <input id="dob" type="date" value={form.dateOfBirth} onChange={(event) => handleChange("dateOfBirth", event.target.value)} onInvalid={handleInvalid} required />
            </div>
            <div className="field">
              <label htmlFor="gender">Giới tính <span className="register-member-optional">(không bắt buộc)</span></label>
              <select id="gender" value={form.gender} onChange={(event) => handleChange("gender", event.target.value)}>
                <option value="">Chọn giới tính</option>
                <option value="F">Nữ</option>
                <option value="M">Nam</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="password">Mật khẩu tạm thời</label>
              <input id="password" type="password" autoComplete="new-password" minLength={8} value={form.password} onChange={(event) => handleChange("password", event.target.value)} onInvalid={handleInvalid} required />
              <small className="register-member-hint">Mật khẩu cần có ít nhất 8 ký tự.</small>
            </div>
          </div>
          <div className="form-actions">
            <button className="btn-primary" type="submit" disabled={loading}>
              {loading ? "Đang đăng ký…" : "Đăng ký thành viên"}
            </button>
          </div>
        </form>
        </div>
      </main>
    </div>
  );
}
