import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { formatTargetAgeRange } from "../memberCoursePresentation.js";
import "./Portal.css";
import "./MemberCourses.css";
import { cancelCourseRegistrationApi, getMyRegistrationsApi, getPublicSportsApi, getPublicCoursesBySportApi } from "../api";
const list = (d) => Array.isArray(d) ? d : d?.content ?? [];
function formatVietnamDateTime(value) {
  if (!value) return "deadline unavailable";
  const local = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value);
  const date = new Date(local ? `${value}+07:00` : value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh",
  }).format(date);
}
export default function MemberCourses({ token, role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]), [sportId, setSportId] = useState(null), [courses, setCourses] = useState([]);
  const [loadingSports, setLoadingSports] = useState(true), [loadingCourses, setLoadingCourses] = useState(false), [error, setError] = useState("");
  const [registrations, setRegistrations] = useState([]), [registrationsLoading, setRegistrationsLoading] = useState(true), [registrationsError, setRegistrationsError] = useState("");
  const [cancellingId, setCancellingId] = useState(null), [cancelError, setCancelError] = useState("");
  async function cancelHold(id) {
    setCancellingId(id); setCancelError("");
    try {
      await cancelCourseRegistrationApi(token, id);
      setRegistrations(await getMyRegistrationsApi(token));
    } catch (requestError) { setCancelError(requestError.message || "Could not cancel this pending registration."); }
    finally { setCancellingId(null); }
  }
  useEffect(() => { let active = true; getPublicSportsApi().then((d) => { const rows = list(d); if (active) { setSports(rows); setSportId(rows[0]?.id ?? null); } })
    .catch((e) => { if (active) setError(e.message || "Không tải được bộ môn."); }).finally(() => { if (active) setLoadingSports(false); }); return () => { active = false; }; }, []);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { if (sportId == null) return undefined; let active = true; setLoadingCourses(true); setError("");
    getPublicCoursesBySportApi(sportId).then((d) => { if (active) setCourses(list(d)); }).catch((e) => { if (active) { setCourses([]); setError(e.message || "Không tải được khóa học."); } })
    .finally(() => { if (active) setLoadingCourses(false); }); return () => { active = false; }; }, [sportId]);
  useEffect(() => { let active = true;
    getMyRegistrationsApi(token).then((data) => { if (active) setRegistrations(list(data)); })
      .catch((e) => { if (active) { setRegistrations([]); setRegistrationsError(e.message || "Không tải được lịch sử đăng ký."); } })
      .finally(() => { if (active) setRegistrationsLoading(false); }); return () => { active = false; };
  }, [token]);
  useEffect(() => {
    let active = true;
    const refreshRegistrations = () => getMyRegistrationsApi(token)
      .then((data) => { if (active) { setRegistrations(list(data)); setRegistrationsError(""); } })
      .catch((requestError) => { if (active) setRegistrationsError(requestError.message || "Could not refresh registration status."); });
    const interval = window.setInterval(refreshRegistrations, 60_000);
    window.addEventListener("focus", refreshRegistrations);
    return () => { active = false; window.clearInterval(interval); window.removeEventListener("focus", refreshRegistrations); };
  }, [token]);
  return <div className="layout"><Sidebar role={role} activePage="member-courses" onLogout={onLogout} onNavigate={onNavigate}/><main className="main member-courses-page">
    <div className="member-courses-shell">
      <header className="member-courses-hero">
        <div className="member-courses-hero-copy"><span className="member-courses-eyebrow">SPORTS CENTER / HỌC VIÊN</span>
          <h1>Khám phá khóa học</h1><p>Khám phá chương trình theo bộ môn, chọn lớp phù hợp và theo dõi các đăng ký của bạn.</p></div>
        <div className="member-courses-hero-summary" aria-label="Bộ môn đang chọn"><span className="member-courses-summary-label">Đang xem</span>
          <strong>{sports.find((sport) => sport.id === sportId)?.name || "Khóa học thể thao"}</strong>
          <small>{loadingCourses ? "Đang cập nhật danh sách…" : `${courses.length} khóa học hiện có`}</small></div>
      </header>

      {error && <div className="banner error member-courses-error" role="alert">{error}</div>}

      <section className="member-course-filter-card" aria-labelledby="member-sport-filter-title">
        <div className="member-course-section-head"><div><span className="member-courses-eyebrow">DANH MỤC</span>
          <h2 id="member-sport-filter-title">Bộ môn</h2><p>Chọn môn thể thao để xem khóa học đang mở.</p></div>
          {!loadingSports && sports.length > 0 && <span className="member-course-total">{sports.length} bộ môn</span>}</div>
        {loadingSports ? <div className="member-course-inline-loading" role="status"><span className="member-course-spinner" />Đang tải bộ môn…</div>
          : sports.length === 0 ? <div className="member-course-empty compact">Chưa có bộ môn công khai.</div>
            : <div className="member-sport-tabs" role="tablist" aria-label="Bộ môn">{sports.map((sport) => <button key={sport.id} type="button" role="tab" aria-selected={sport.id === sportId}
              className={`member-sport-tab ${sport.id === sportId ? "is-active" : ""}`} onClick={() => setSportId(sport.id)}><span className="member-sport-tab-dot" aria-hidden="true" />{sport.name}</button>)}</div>}
      </section>

      <section className="member-course-list-card" aria-labelledby="member-course-list-title">
        <div className="member-course-section-head"><div><span className="member-courses-eyebrow">CHƯƠNG TRÌNH</span>
          <h2 id="member-course-list-title">Danh sách khóa học</h2><p>{sports.find((sport) => sport.id === sportId)?.name ? `Các khóa học thuộc bộ môn ${sports.find((sport) => sport.id === sportId).name}.` : "Khám phá khóa học hiện có."}</p></div>
          {!loadingCourses && courses.length > 0 && <span className="member-course-count">{courses.length} khóa học</span>}</div>
        {loadingCourses ? <div className="member-course-loading-panel" role="status"><span className="member-course-spinner large" /><strong>Đang tải khóa học</strong><small>Danh sách sẽ xuất hiện khi dữ liệu sẵn sàng.</small></div>
          : !loadingSports && sportId != null && courses.length === 0 ? <div className="member-course-empty"><span className="member-course-empty-icon" aria-hidden="true">○</span><h3>Chưa có khóa học</h3><p>Bộ môn này hiện chưa có khóa học.</p></div>
            : <div className="member-course-grid">{courses.map((course, index) => <article className="member-course-card" key={course.id}>
              <div className="member-course-card-accent" aria-hidden="true" /><div className="member-course-card-top"><span className="member-course-badge">Khóa học</span><span className="member-course-number" aria-hidden="true">{String(index + 1).padStart(2, "0")}</span></div>
              <div className="member-course-card-body"><h3>{course.name}</h3>
                {(course.level || course.targetAgeMin != null || course.targetAgeMax != null) && <div className="member-course-card-meta" aria-label="Trình độ và độ tuổi">
                  {course.level && <span className="member-course-card-meta-badge">{course.level}</span>}
                  {formatTargetAgeRange(course.targetAgeMin, course.targetAgeMax) && <span className="member-course-card-meta-badge age">{formatTargetAgeRange(course.targetAgeMin, course.targetAgeMax)}</span>}
                </div>}
                <p>{course.description || "Khám phá khóa học và lựa chọn lớp phù hợp với bạn."}</p></div>
              <div className="member-course-card-footer"><Link className="member-course-detail-btn" to={`/member/courses/${course.id}`}><span><small>Tìm hiểu thêm</small><strong>Xem chi tiết khóa học</strong></span><span className="member-course-arrow" aria-hidden="true">→</span></Link></div>
            </article>)}</div>}
      </section>

      <section className="member-course-history-card" aria-labelledby="member-course-history-title">
        <div className="member-course-section-head member-course-history-heading"><div><span className="member-courses-eyebrow">THEO DÕI</span><h2 id="member-course-history-title">Lớp học đã đăng ký</h2>
          <p>Trạng thái giữ chỗ, thanh toán và lớp học của bạn.</p></div><Link className="member-course-schedule-link" to="/member/schedule">Xem lịch học <span aria-hidden="true">→</span></Link></div>
        {registrationsLoading ? <p className="member-course-history-state" role="status">Đang tải đăng ký…</p>
          : registrationsError ? <p className="banner error" role="alert">{registrationsError}</p>
            : registrations.length === 0 ? <p className="member-course-history-state">Bạn chưa có đăng ký CONFIRMED nào.</p>
              : <div className="member-registration-list">{registrations.map((item) => <article className="member-registration-row" key={item.id}>
                <div className="member-registration-details"><strong>{item.className || `Lớp #${item.classId}`}</strong><span>{[item.courseName, item.courseOfferingName].filter(Boolean).join(" · ") || "Thông tin khóa học không khả dụng"}</span></div>
                <span className={`member-registration-status ${item.status === "PENDING_PAYMENT" ? "pending" : ""}`}>{item.status === "PENDING_PAYMENT" ? "Payment pending" : item.status === "CONFIRMED" ? "Confirmed" : item.status === "EXPIRED" ? "Expired" : item.status}</span>
                {item.status === "PENDING_PAYMENT" && <span className="member-registration-deadline">Thanh toán trước {formatVietnamDateTime(item.paymentDeadline)} (giờ Việt Nam).</span>}
                <div className="member-registration-actions"><Link to={`/member/course-checkout/${item.id}`}>{item.status === "PENDING_PAYMENT" ? "Thanh toán / theo dõi" : "Xem thanh toán"}</Link>
                  {item.status === "PENDING_PAYMENT" && <button type="button" disabled={cancellingId !== null} onClick={() => void cancelHold(item.id)}>{cancellingId === item.id ? "Đang hủy…" : "Hủy giữ chỗ"}</button>}
                  {item.courseId != null && <Link to={`/member/courses/${item.courseId}`}>Xem khóa học</Link>}</div>
              </article>)}</div>}
        {cancelError && <p className="banner error" role="alert">{cancelError}</p>}
      </section>
    </div>
  </main></div>;
}
