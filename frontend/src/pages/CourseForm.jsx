import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { getCourseApi, getSportsApi, saveCourseApi } from "../api";
import { buildCoursePayload } from "../courseManagementModel";
import "./Portal.css";
import "./Admin.css";
import "./CourseManagement.css";

const rows = (data) => Array.isArray(data) ? data : data?.content || [];
const empty = { sportId: "", name: "", level: "", targetAgeMin: "", targetAgeMax: "", description: "", learningOutcome: "", status: "ACTIVE" };

export default function CourseForm({ token, role, onLogout, onNavigate }) {
  const { courseId } = useParams();
  const editing = Boolean(courseId);
  const navigate = useNavigate();
  const [sports, setSports] = useState([]);
  const [form, setForm] = useState(empty);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    Promise.all([getSportsApi(token), editing ? getCourseApi(token, courseId) : Promise.resolve(null)])
      .then(([sportData, course]) => {
        if (!active) return;
        setSports(rows(sportData));
        if (course) setForm({ ...empty, ...course, sportId: String(course.sportId), targetAgeMin: course.targetAgeMin ?? "", targetAgeMax: course.targetAgeMax ?? "" });
        setError("");
      })
      .catch((e) => active && setError(e.message || "Không thể tải dữ liệu khóa học."))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [courseId, editing, token]);

  const change = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setError("");
    try {
      const payload = buildCoursePayload(form, editing);
      const saved = await saveCourseApi(token, editing ? courseId : null, payload);
      navigate(editing ? "/staff/courses/" + courseId : "/staff/courses/" + saved.id, { replace: true });
    } catch (e) {
      setError(e.message || "Không thể lưu khóa học.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="layout">
      <Sidebar role={role} activePage="courses" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main">
        <button type="button" className="adm-back" onClick={() => navigate(editing ? "/staff/courses/" + courseId : "/staff/courses")}>← Quay lại</button>
        <p className="eyebrow">QUẢN TRỊ ĐÀO TẠO</p><h1>{editing ? "Chỉnh sửa khóa học" : "Tạo khóa học"}</h1>
        <p className="subtitle">Chọn rõ Sport trước khi lưu để tránh gán khóa học nhầm bộ môn.</p>
        {error && <div className="banner error" role="alert">{error}</div>}
        {loading ? <p role="status">Đang tải biểu mẫu…</p> : (
          <form className="adm-form course-form adm-block" onSubmit={submit}>
            <div className="grid-2">
              <div className="field course-sport-field">
                <label htmlFor="course-sport">Sport <span aria-hidden="true">*</span></label>
                <select id="course-sport" name="sportId" value={form.sportId} onChange={change} required disabled={editing}>
                  <option value="" disabled>Chọn Sport</option>
                  {sports.map((sport) => <option key={sport.id} value={sport.id}>{sport.name}</option>)}
                </select>
                {editing && <small>Sport hiện tại được đọc từ khóa học đã lưu. API cập nhật hiện tại không hỗ trợ đổi Sport.</small>}
              </div>
              <div className="field"><label htmlFor="course-name">Tên khóa học *</label><input id="course-name" name="name" value={form.name} onChange={change} required maxLength={255} /></div>
              <div className="field"><label htmlFor="course-level">Trình độ *</label><select id="course-level" name="level" value={form.level} onChange={change} required><option value="" disabled>Chọn trình độ</option>{["BEGINNER", "INTERMEDIATE", "ADVANCED"].map((level) => <option key={level}>{level}</option>)}</select></div>
              <div className="field"><label htmlFor="course-status">Trạng thái</label><select id="course-status" name="status" value={form.status} onChange={change} disabled={!editing}><option value="ACTIVE">ACTIVE</option><option value="INACTIVE">INACTIVE</option></select></div>
              <div className="field"><label htmlFor="course-age-min">Tuổi tối thiểu</label><input id="course-age-min" name="targetAgeMin" type="number" min="0" value={form.targetAgeMin} onChange={change} /></div>
              <div className="field"><label htmlFor="course-age-max">Tuổi tối đa</label><input id="course-age-max" name="targetAgeMax" type="number" min="0" value={form.targetAgeMax} onChange={change} /></div>
              <div className="field"><label htmlFor="course-description">Mô tả</label><textarea id="course-description" name="description" value={form.description || ""} onChange={change} /></div>
              <div className="field"><label htmlFor="course-outcome">Kết quả đạt được</label><textarea id="course-outcome" name="learningOutcome" value={form.learningOutcome || ""} onChange={change} /></div>
            </div>
            <div className="form-actions">
              <button type="button" onClick={() => navigate(editing ? "/staff/courses/" + courseId : "/staff/courses")}>Hủy</button>
              <button className="adm-primary" type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu khóa học"}</button>
            </div>
          </form>
        )}
      </main>
    </div>
  );
}
