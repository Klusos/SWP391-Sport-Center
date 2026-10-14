import { useState } from "react";
import Sidebar from "./Sidebar";
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
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit() {
    setSuccessMsg("");
    setErrorMsg("");

    if (!form.name || !form.email || !form.phone || !form.dateOfBirth || !form.password) {
      setErrorMsg("Vui lòng điền đầy đủ thông tin bắt buộc.");
      return;
    }

    setLoading(true);
    try {
      await createMemberApi(token, form);
      setSuccessMsg("Tạo thành viên thành công.");
      setForm(EMPTY_FORM);
    } catch (e) {
      setErrorMsg(e.message || "Tạo thành viên thất bại.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="layout">
      <Sidebar onLogout={onLogout} />

      <div className="main">
        <h1>Đăng ký thành viên mới</h1>
        <p className="subtitle">Tạo hồ sơ thành viên tại quầy</p>

        <div className="form-card">
          {successMsg && <div className="banner success">{successMsg}</div>}
          {errorMsg && <div className="banner error">{errorMsg}</div>}

          <div className="grid-2">
            <div className="field">
              <label htmlFor="name">Họ và tên</label>
              <input
                id="name"
                type="text"
                placeholder="Nhập họ tên"
                value={form.name}
                onChange={(e) => handleChange("name", e.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="phone">Số điện thoại</label>
              <input
                id="phone"
                type="text"
                placeholder="Nhập SĐT"
                value={form.phone}
                onChange={(e) => handleChange("phone", e.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                placeholder="Nhập email"
                value={form.email}
                onChange={(e) => handleChange("email", e.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="dob">Ngày sinh</label>
              <input
                id="dob"
                type="date"
                value={form.dateOfBirth}
                onChange={(e) => handleChange("dateOfBirth", e.target.value)}
              />
            </div>

            <div className="field">
              <label htmlFor="gender">Giới tính</label>
              <select
                id="gender"
                value={form.gender}
                onChange={(e) => handleChange("gender", e.target.value)}
              >
                <option value="">Chọn giới tính</option>
                <option value="F">Nữ</option>
                <option value="M">Nam</option>
              </select>
            </div>

            <div className="field">
              <label htmlFor="password">Mật khẩu tạm thời</label>
              <input
                id="password"
                type="text"
                placeholder="Tối thiểu 8 ký tự"
                value={form.password}
                onChange={(e) => handleChange("password", e.target.value)}
              />
            </div>
          </div>

          <div className="form-actions">
            <button className="btn-primary" onClick={handleSubmit} disabled={loading}>
              {loading ? "Đang tạo..." : "Tạo thành viên"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
