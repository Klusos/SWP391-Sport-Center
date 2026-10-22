
import { useState } from "react";
import Sidebar from "../components/Sidebar";
import { broadcastNotificationsApi } from "../api";
import "./AdminNotificationBroadcast.css";

const TITLE_LIMIT = 150;
const MESSAGE_LIMIT = 5000;

export default function AdminNotificationBroadcast({
  token,
  role,
  onLogout,
  onNavigate,
}) {
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

    if (
      !window.confirm(
        "Gửi thông báo này đến tất cả tài khoản đang hoạt động?"
      )
    ) {
      return;
    }

    setPending(true);

    try {
      const result = await broadcastNotificationsApi(token, {
        title: title.trim(),
        message: message.trim(),
      });

      setSuccess(
        `Đã gửi thông báo đến ${result.recipientCount} tài khoản đang hoạt động.`
      );

      setTitle("");
      setMessage("");
    } catch (requestError) {
      setError(
        requestError.message ||
          "Không thể gửi thông báo. Vui lòng thử lại."
      );
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="layout">
      {/* Giữ nguyên Sidebar hiện tại */}
      <Sidebar
        role={role}
        activePage="notification-broadcast"
        onLogout={onLogout}
        onNavigate={onNavigate}
      />

      <main className="main notification-broadcast-main">
        <div className="nb-page">
          {/* Page header */}
          <header className="nb-header">
            <div className="nb-header-content">
              <div className="nb-eyebrow">
                <span className="nb-eyebrow-dot" />
                NOTIFICATION CENTER
              </div>

              <h1>Gửi thông báo toàn trung tâm</h1>

              <p className="nb-subtitle">
                Truyền tải thông tin quan trọng đến toàn bộ thành viên
                và nhân viên của Sport Center.
              </p>
            </div>

            <div className="nb-header-icon" aria-hidden="true">
              <i className="bi bi-bell-fill" />
              <span />
            </div>
          </header>

          {/* Main form card */}
          <section className="nb-card">
            <div className="nb-card-heading">
              <div className="nb-card-heading-icon">
                <i className="bi bi-send" aria-hidden="true" />
              </div>

              <div>
                <h2>Soạn thông báo mới</h2>
                <p>
                  Vui lòng kiểm tra nội dung trước khi gửi đến toàn trung tâm.
                </p>
              </div>
            </div>

            {/* Audience information */}
            <div className="nb-audience">
              <div className="nb-audience-icon">
                <i className="bi bi-people-fill" aria-hidden="true" />
              </div>

              <div className="nb-audience-content">
                <strong>Đối tượng nhận thông báo</strong>

                <p>
                  Thông báo sẽ được gửi đến tất cả tài khoản đang hoạt động,
                  bao gồm ADMIN, RECEPTIONIST, COACH và MEMBER.
                </p>

                <div className="nb-role-list">
                  <span>ADMIN</span>
                  <span>RECEPTIONIST</span>
                  <span>COACH</span>
                  <span>MEMBER</span>
                </div>
              </div>
            </div>

            {/* Error and success messages */}
            {error && (
              <div className="nb-alert nb-alert-error" role="alert">
                <i
                  className="bi bi-exclamation-circle-fill"
                  aria-hidden="true"
                />

                <span>{error}</span>

                <button
                  type="button"
                  onClick={() => setError("")}
                  aria-label="Đóng thông báo lỗi"
                >
                  ×
                </button>
              </div>
            )}

            {success && (
              <div className="nb-alert nb-alert-success" role="status">
                <i
                  className="bi bi-check-circle-fill"
                  aria-hidden="true"
                />

                <span>{success}</span>

                <button
                  type="button"
                  onClick={() => setSuccess("")}
                  aria-label="Đóng thông báo thành công"
                >
                  ×
                </button>
              </div>
            )}

            <form className="nb-form" onSubmit={submit}>
              {/* Title */}
              <div className="nb-field">
                <div className="nb-label-row">
                  <label htmlFor="broadcast-title">
                    Tiêu đề <span>*</span>
                  </label>

                  <small className={title.length >= TITLE_LIMIT ? "nb-limit" : ""}>
                    {title.length}/{TITLE_LIMIT}
                  </small>
                </div>

                <div className="nb-input-wrap">
                  <i className="bi bi-type" aria-hidden="true" />

                  <input
                    id="broadcast-title"
                    type="text"
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                    maxLength={TITLE_LIMIT}
                    placeholder="Ví dụ: Thông báo lịch nghỉ lễ"
                    required
                    disabled={pending}
                  />
                </div>

                <p className="nb-field-hint">
                  Hãy sử dụng tiêu đề ngắn gọn, dễ hiểu.
                </p>
              </div>

              {/* Message */}
              <div className="nb-field nb-message-field">
                <div className="nb-label-row">
                  <label htmlFor="broadcast-message">
                    Nội dung thông báo <span>*</span>
                  </label>

                  <small
                    className={
                      message.length >= MESSAGE_LIMIT ? "nb-limit" : ""
                    }
                  >
                    {message.length}/{MESSAGE_LIMIT}
                  </small>
                </div>

                <textarea
                  id="broadcast-message"
                  value={message}
                  onChange={(event) => setMessage(event.target.value)}
                  maxLength={MESSAGE_LIMIT}
                  rows={7}
                  placeholder={
                    "Nhập nội dung thông báo tại đây...\n\n" +
                    "Bạn có thể cung cấp thời gian, địa điểm và những lưu ý cần thiết."
                  }
                  required
                  disabled={pending}
                />

                <p className="nb-field-hint">
                  Vui lòng đảm bảo thông tin chính xác trước khi gửi.
                </p>
              </div>

              {/* Submit */}
              <div className="nb-form-footer">
                <div className="nb-security-note">
                  <i
                    className="bi bi-info-circle"
                    aria-hidden="true"
                  />

                  <span>
                    Thông báo sẽ được gửi đến tất cả tài khoản đang hoạt động.
                  </span>
                </div>

                <button
                  className="nb-submit"
                  type="submit"
                  disabled={
                    pending || !title.trim() || !message.trim()
                  }
                >
                  {pending ? (
                    <>
                      <span className="nb-spinner" />
                      Đang gửi thông báo...
                    </>
                  ) : (
                    <>
                      <i className="bi bi-send-fill" aria-hidden="true" />
                      Gửi thông báo
                    </>
                  )}
                </button>
              </div>
            </form>
          </section>

          <footer className="nb-footer">
            <span>SPORT CENTER</span>
            <span>Truyền tải thông tin nhanh chóng và hiệu quả.</span>
          </footer>
        </div>
      </main>
    </div>
  );
}
