import { useState } from "react";
import Sidebar from "../components/Sidebar";
import { broadcastNotificationsApi } from "../api";
import "./AdminNotificationBroadcast.css";

const TITLE_LIMIT = 150;
const MESSAGE_LIMIT = 5000;

export default function AdminNotificationBroadcast({ token, role, onLogout, onNavigate }) {
  const [title, setTitle] = useState("");
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    setSuccess("");
    if (!title.trim() || !message.trim()) {
      setError("Vui lòng nhập tiêu đề và nội dung thông báo.");
      return;
    }
    if (!window.confirm("Gửi thông báo này đến tất cả tài khoản đang hoạt động?")) return;

    setPending(true);
    try {
      const result = await broadcastNotificationsApi(token, { title: title.trim(), message: message.trim() });
      setSuccess(`Đã gửi thông báo đến ${result.recipientCount} tài khoản đang hoạt động.`);
      setTitle("");
      setMessage("");
    } catch (requestError) {
      setError(requestError.message || "Không thể gửi thông báo. Vui lòng thử lại.");
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="layout">
      <Sidebar role={role} activePage="notification-broadcast" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main notification-broadcast-main">
        <h1>Gửi thông báo toàn trung tâm</h1>
        <p className="subtitle">Thông báo sẽ được gửi đến tất cả tài khoản đang hoạt động, bao gồm cả tài khoản quản trị viên của bạn.</p>
        <section className="form-card notification-broadcast-card">
          <div className="notification-audience"><i className="bi bi-people" aria-hidden="true" /><span><strong>Đối tượng nhận</strong>Tất cả ADMIN, RECEPTIONIST, COACH và MEMBER đang hoạt động.</span></div>
          {error && <div className="banner error" role="alert">{error}</div>}
          {success && <div className="banner success" role="status">{success}</div>}
          <form onSubmit={submit}>
            <div className="notification-field">
              <label htmlFor="broadcast-title">Tiêu đề <span aria-hidden="true">*</span></label>
              <input id="broadcast-title" value={title} onChange={(event) => setTitle(event.target.value)} maxLength={TITLE_LIMIT} required disabled={pending} />
              <small>{title.length}/{TITLE_LIMIT}</small>
            </div>
            <div className="notification-field">
              <label htmlFor="broadcast-message">Nội dung <span aria-hidden="true">*</span></label>
              <textarea id="broadcast-message" value={message} onChange={(event) => setMessage(event.target.value)} maxLength={MESSAGE_LIMIT} rows={7} required disabled={pending} />
              <small>{message.length}/{MESSAGE_LIMIT}</small>
            </div>
            <button className="btn-primary notification-submit" type="submit" disabled={pending || !title.trim() || !message.trim()}>
              {pending ? "Đang gửi…" : "Gửi thông báo"}
            </button>
          </form>
        </section>
      </main>
    </div>
  );
}
