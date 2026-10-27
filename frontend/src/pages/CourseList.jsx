import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { getCoursesApi, getSportsApi } from "../api";
import { filterCourses } from "../courseManagementModel";
import "./Portal.css";
import "./Admin.css";
import "./CourseManagement.css";

const rows = (data) => Array.isArray(data) ? data : data?.content || [];

export default function CourseList({ token, role, onLogout, onNavigate }) {
  const navigate = useNavigate();
  const [courses, setCourses] = useState([]);
  const [sports, setSports] = useState([]);
  const [search, setSearch] = useState("");
  const [sportId, setSportId] = useState("");
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    Promise.all([getCoursesApi(token), getSportsApi(token)])
      .then(([courseData, sportData]) => {
        if (!active) return;
        setCourses(rows(courseData));
        setSports(rows(sportData));
        setError("");
      })
      .catch((e) => active && setError(e.message || "Không thể tải danh sách khóa học."))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [token]);

  const filtered = useMemo(() => filterCourses(courses, { search, sportId, status }), [courses, search, sportId, status]);
  const refresh = () => {
    setLoading(true);
    getCoursesApi(token).then((data) => setCourses(rows(data))).catch((e) => setError(e.message)).finally(() => setLoading(false));
  };

  return (
    <div className="layout">
      <Sidebar role={role} activePage="courses" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main">
        <header className="course-list-heading">
          <div><p className="eyebrow">QUẢN TRỊ ĐÀO TẠO</p><h1>Quản lý khóa học</h1><p className="subtitle">Danh sách khóa học theo tất cả bộ môn.</p></div>
          <button type="button" className="adm-primary course-create-button" onClick={() => navigate("/staff/courses/new")}>＋ Tạo khóa học</button>
        </header>
        {error && <div className="banner error" role="alert">{error} <button type="button" onClick={refresh}>Thử lại</button></div>}
        <section className="adm-block">
          <div className="course-filters">
            <label>Tìm theo tên<input type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Nhập tên khóa học" /></label>
            <label>Sport<select value={sportId} onChange={(e) => setSportId(e.target.value)}><option value="">Tất cả Sport</option>{sports.map((sport) => <option key={sport.id} value={sport.id}>{sport.name}</option>)}</select></label>
            <label>Trạng thái<select value={status} onChange={(e) => setStatus(e.target.value)}><option value="">Tất cả trạng thái</option><option value="ACTIVE">ACTIVE</option><option value="INACTIVE">INACTIVE</option></select></label>
          </div>
          {loading ? <p role="status">Đang tải khóa học…</p> : filtered.length === 0 ? <p className="dash-empty">Không tìm thấy khóa học phù hợp.</p> : (
            <div className="queue-table-wrap">
              <table className="queue-table">
                <thead><tr><th>Khóa học</th><th>Sport</th><th>Trình độ</th><th>Độ tuổi</th><th>Trạng thái</th><th /></tr></thead>
                <tbody>{filtered.map((course) => <tr key={course.id}>
                  <td><strong>{course.name}</strong></td><td>{course.sportName || "—"}</td><td>{course.level || "—"}</td>
                  <td>{course.targetAgeMin ?? "—"} – {course.targetAgeMax ?? "—"}</td><td><span className={"status-pill status-" + String(course.status || "").toLowerCase()}>{course.status || "—"}</span></td>
                  <td><div className="adm-actions"><button type="button" onClick={() => navigate("/staff/courses/" + course.id)}>Quản lý</button><button type="button" onClick={() => navigate("/staff/courses/" + course.id + "/edit")}>Sửa</button></div></td>
                </tr>)}</tbody>
              </table>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
