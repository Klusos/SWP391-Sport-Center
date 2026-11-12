import { useEffect, useId, useRef } from "react";
import "./CourseConsultationModal.css";

export default function CourseConsultationModal({
  course,
  values,
  onChange,
  profileError,
  error,
  onEmailInvalid,
  submitting,
  onClose,
  onSubmit,
}) {
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef(null);
  const firstFieldRef = useRef(null);
  const closeRef = useRef(onClose);
  const submittingRef = useRef(submitting);

  useEffect(() => {
    closeRef.current = onClose;
    submittingRef.current = submitting;
  }, [onClose, submitting]);

  useEffect(() => {
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    firstFieldRef.current?.focus();

    function handleKeyDown(event) {
      if (event.key === "Escape" && !submittingRef.current) {
        event.preventDefault();
        closeRef.current?.();
        return;
      }
      if (event.key !== "Tab") return;
      const focusable = [...(dialogRef.current?.querySelectorAll(
        "button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), a[href], [tabindex]:not([tabindex='-1'])",
      ) || [])];
      if (!focusable.length) {
        event.preventDefault();
        dialogRef.current?.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);

  function closeFromBackdrop(event) {
    if (event.target === event.currentTarget && !submitting) onClose?.();
  }

  function update(field) {
    return (event) => onChange((previous) => ({ ...previous, [field]: event.target.value }));
  }

  return <div className="course-consult-modal-backdrop" onMouseDown={closeFromBackdrop}>
    <section
      ref={dialogRef}
      className="course-consult-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      aria-busy={submitting}
      tabIndex={-1}
    >
      <header className="course-consult-modal-header">
        <div>
          <p className="course-consult-modal-eyebrow">SPORTCENTER / TƯ VẤN KHÓA HỌC</p>
          <h2 id={titleId}>Yêu cầu tư vấn</h2>
          <p className="course-consult-modal-context">
            <strong>{course.name}</strong><span aria-hidden="true"> · </span>{course.sportName || (course.sportId != null ? `Bộ môn #${course.sportId}` : "Bộ môn")}
          </p>
        </div>
        <button type="button" className="course-consult-modal-close" aria-label="Đóng yêu cầu tư vấn" onClick={onClose} disabled={submitting}>×</button>
      </header>

      <form className="course-consult-modal-form" onSubmit={onSubmit}>
        <div className="course-consult-modal-body" id={descriptionId}>
          <p className="course-consult-modal-help">
            {profileError ? "Không tải được hồ sơ; vui lòng nhập thông tin liên hệ." : "Thông tin liên hệ được lấy từ hồ sơ nếu có; vui lòng kiểm tra trước khi gửi."}
          </p>
          <div className="course-consult-modal-fields">
            <label htmlFor="course-consult-name">Họ và tên <span aria-hidden="true">*</span>
              <input ref={firstFieldRef} id="course-consult-name" required maxLength={100} autoComplete="name" value={values.name} onChange={update("name")} />
            </label>
            <label htmlFor="course-consult-phone">Số điện thoại <span aria-hidden="true">*</span>
              <input id="course-consult-phone" required maxLength={20} autoComplete="tel" value={values.phone} onChange={update("phone")} />
            </label>
            <label htmlFor="course-consult-email">Email <span aria-hidden="true">*</span>
              <input id="course-consult-email" type="email" maxLength={150} autoComplete="email" value={values.email} onChange={update("email")} required onInvalid={(event) => { event.preventDefault(); onEmailInvalid?.(); }} />
            </label>
            <label htmlFor="course-consult-age">Tuổi (không bắt buộc)
              <input id="course-consult-age" type="number" min="1" max="120" value={values.age} onChange={update("age")} />
            </label>
            <label htmlFor="course-consult-experience" className="wide">Kinh nghiệm thể thao
              <textarea id="course-consult-experience" rows="3" value={values.experience} onChange={update("experience")} />
            </label>
            <label htmlFor="course-consult-note" className="wide">Ghi chú
              <textarea id="course-consult-note" rows="3" maxLength={1000} value={values.note} onChange={update("note")} />
            </label>
          </div>
          {error && <p className="course-consult-modal-error" role="alert">{error}</p>}
        </div>
        <footer className="course-consult-modal-actions">
          <button type="button" className="course-consult-modal-cancel" onClick={onClose} disabled={submitting}>Hủy</button>
          <button type="submit" className="course-consult-modal-submit" disabled={submitting}>
            {submitting ? "Đang gửi…" : "Gửi yêu cầu tư vấn"}
          </button>
        </footer>
      </form>
    </section>
  </div>;
}
