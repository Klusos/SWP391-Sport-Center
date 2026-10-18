import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import { getPublicSportsApi, getPublicCoursesBySportApi } from "../api";
const list = (d) => Array.isArray(d) ? d : d?.content ?? [];
export default function MemberCourses({ role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]), [sportId, setSportId] = useState(null), [courses, setCourses] = useState([]);
  const [loadingSports, setLoadingSports] = useState(true), [loadingCourses, setLoadingCourses] = useState(false), [error, setError] = useState("");
  useEffect(() => { let active = true; getPublicSportsApi().then((d) => { const rows = list(d); if (active) { setSports(rows); setSportId(rows[0]?.id ?? null); } })
    .catch((e) => { if (active) setError(e.message || "Không tải được bộ môn."); }).finally(() => { if (active) setLoadingSports(false); }); return () => { active = false; }; }, []);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { if (sportId == null) return undefined; let active = true; setLoadingCourses(true); setError("");
    getPublicCoursesBySportApi(sportId).then((d) => { if (active) setCourses(list(d)); }).catch((e) => { if (active) { setCourses([]); setError(e.message || "Không tải được khóa học."); } })
    .finally(() => { if (active) setLoadingCourses(false); }); return () => { active = false; }; }, [sportId]);
  return <div className="layout"><Sidebar role={role} activePage="member-courses" onLogout={onLogout} onNavigate={onNavigate}/><main className="main">
    <h1>Khóa học</h1><p className="subtitle">Chọn bộ môn để xem khóa học. Chi tiết khóa học là nơi chọn lớp và đăng ký.</p>
    {error && <div className="banner error" role="alert">{error}</div>}
    {loadingSports ? <div className="queue-state">Đang tải bộ môn…</div> : sports.length === 0 ? <p className="dash-empty">Chưa có bộ môn công khai.</p> :
      <div className="pf-chips" role="tablist" aria-label="Bộ môn">{sports.map((s) => <button key={s.id} type="button" role="tab" aria-selected={s.id === sportId} className={"pf-chip"+(s.id === sportId ? " active" : "")} onClick={() => setSportId(s.id)}>{s.name}</button>)}</div>}
    {loadingCourses ? <div className="queue-state">Đang tải khóa học…</div> : !loadingSports && sportId != null && courses.length === 0 ? <p className="dash-empty">Bộ môn này chưa có khóa học.</p> :
      <div className="pf-courses">{courses.map((c) => <Link key={c.id} className="pf-course" to={"/khoa-hoc/"+c.id}><b>{c.name}</b>{c.description && <span>{c.description}</span>}<span className="pf-primary">Xem chi tiết</span></Link>)}</div>}
  </main></div>;
}