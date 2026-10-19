import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
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
  return <div className="layout"><Sidebar role={role} activePage="member-courses" onLogout={onLogout} onNavigate={onNavigate}/><main className="main">
    <h1>Khóa học</h1><p className="subtitle">Khám phá khóa học, chọn lớp phù hợp và theo dõi các đăng ký đã xác nhận.</p>
    {error && <div className="banner error" role="alert">{error}</div>}
    {loadingSports ? <div className="queue-state">Đang tải bộ môn…</div> : sports.length === 0 ? <p className="dash-empty">Chưa có bộ môn công khai.</p> :
      <div className="pf-chips" role="tablist" aria-label="Bộ môn">{sports.map((s) => <button key={s.id} type="button" role="tab" aria-selected={s.id === sportId} className={"pf-chip"+(s.id === sportId ? " active" : "")} onClick={() => setSportId(s.id)}>{s.name}</button>)}</div>}
    {loadingCourses ? <div className="queue-state">Đang tải khóa học…</div> : !loadingSports && sportId != null && courses.length === 0 ? <p className="dash-empty">Bộ môn này chưa có khóa học.</p> :
      <div className="pf-courses">{courses.map((c) => <Link key={c.id} className="pf-course" to={"/member/courses/"+c.id}><b>{c.name}</b>{c.description && <span>{c.description}</span>}<span className="pf-primary">Xem chi tiết</span></Link>)}</div>}
    <section className="dash-card member-course-history"><div className="member-course-history-heading"><h2>Lớp học đã đăng ký</h2><Link to="/member/schedule">Xem lịch học</Link></div>
      {registrationsLoading ? <p className="queue-state" role="status">Đang tải đăng ký…</p>
        : registrationsError ? <p className="banner error" role="alert">{registrationsError}</p>
          : registrations.length === 0 ? <p className="dash-empty">Bạn chưa có đăng ký CONFIRMED nào.</p>
            : <div className="member-registration-list">{registrations.map((item) => <article key={item.id}>
              <div><strong>{item.className || `Lớp #${item.classId}`}</strong><span>{[item.courseName, item.courseOfferingName].filter(Boolean).join(" · ") || "Thông tin khóa học không khả dụng"}</span></div>
              <span className="member-registration-status">{item.status === "PENDING_PAYMENT" ? "Payment pending" : item.status === "CONFIRMED" ? "Confirmed" : item.status === "EXPIRED" ? "Expired" : item.status}</span>
              {item.status === "PENDING_PAYMENT" && <span>Pay before {formatVietnamDateTime(item.paymentDeadline)} (Vietnam time).</span>}
              {item.status === "PENDING_PAYMENT" && <button type="button" className="btn btn-outline-secondary" disabled={cancellingId !== null} onClick={() => void cancelHold(item.id)}>{cancellingId === item.id ? "Cancelling…" : "Cancel hold"}</button>}
              {item.courseId != null && <Link to={`/member/courses/${item.courseId}`}>Xem khóa học</Link>}
            </article>)}</div>}
      {cancelError && <p className="banner error" role="alert">{cancelError}</p>}
    </section>
  </main></div>;
}
