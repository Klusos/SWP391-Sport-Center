import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import {
  getPublicSportsApi, getPublicCoursesBySportApi, getCourseOfferingsByCourseApi,
  getClassesByOfferingApi, createCourseRegistrationApi,
} from "../api";

const list = (d) => (Array.isArray(d) ? d : d?.content ?? []);
const first = (o, ...keys) => { for (const k of keys) if (o?.[k] != null && o[k] !== "") return o[k]; return ""; };
const day = (s) => (s ? new Date(s).toLocaleDateString("vi-VN") : "");

export default function MemberCourses({ token, role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]);
  const [sportId, setSportId] = useState(null);
  const [courses, setCourses] = useState([]);
  const [course, setCourse] = useState(null);
  const [offerings, setOfferings] = useState([]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState(null);

  useEffect(() => {
    getPublicSportsApi().then((d) => {
      const s = list(d);
      setSports(s);
      if (s[0]) setSportId(s[0].id);
    }).catch((e) => setMsg({ type: "error", text: e.message }));
  }, []);

  useEffect(() => {
    if (sportId == null) return;
    setCourse(null); setOfferings([]);
    getPublicCoursesBySportApi(sportId).then((d) => setCourses(list(d)))
      .catch((e) => { setCourses([]); setMsg({ type: "error", text: e.message }); });
  }, [sportId]);

  async function openCourse(c) {
    setCourse(c); setOfferings([]); setMsg(null); setBusy(true);
    try {
      const offs = list(await getCourseOfferingsByCourseApi(token, c.id));
      const full = await Promise.all(offs.map(async (o) => {
        try { return { ...o, classes: list(await getClassesByOfferingApi(token, o.id)) }; }
        catch { return { ...o, classes: [] }; }
      }));
      setOfferings(full);
    } catch (e) {
      setMsg({ type: "error", text: e.message });
    } finally { setBusy(false); }
  }

  async function register(cls) {
    setMsg(null);
    try {
      await createCourseRegistrationApi(token, cls.id);
      setMsg({ type: "success", text: "Đã đăng ký lớp " + first(cls, "name", "className", "id") + "." });
    } catch (e) {
      setMsg({ type: "error", text: e.message });
    }
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="member-courses" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main">
        <h1>Khóa học &amp; lớp học</h1>
        <p className="subtitle">Chọn bộ môn, xem khóa học và đăng ký lớp phù hợp với bạn.</p>
        {msg && <div className={"banner " + msg.type} role="status">{msg.text}</div>}

        <div className="pf-chips" role="tablist" aria-label="Bộ môn">
          {sports.map((s) => (
            <button key={s.id} type="button" role="tab" aria-selected={s.id === sportId}
              className={"pf-chip" + (s.id === sportId ? " active" : "")} onClick={() => setSportId(s.id)}>
              {s.name}
            </button>
          ))}
        </div>

        {courses.length === 0 ? <p className="dash-empty">Bộ môn này chưa có khóa học.</p> : (
          <div className="pf-courses">
            {courses.map((c) => (
              <button key={c.id} type="button" className={"pf-course" + (course?.id === c.id ? " active" : "")} onClick={() => openCourse(c)}>
                <b>{first(c, "name", "title")}</b>
                <span>{first(c, "description", "summary")}</span>
              </button>
            ))}
          </div>
        )}

        {course && (
          <section className="dash-card pf-detail">
            <h2>{first(course, "name", "title")}</h2>
            {busy ? <p className="dash-empty">Đang tải lớp học…</p>
              : offerings.length === 0 ? <p className="dash-empty">Khóa học này chưa mở đợt học nào.</p>
              : offerings.map((o) => (
                <div key={o.id} className="pf-offering">
                  <h3>{first(o, "name", "title") || "Đợt học #" + o.id}
                    <small>{[day(first(o, "startDate")), day(first(o, "endDate"))].filter(Boolean).join(" – ")}</small>
                  </h3>
                  {o.classes.length === 0 ? <p className="dash-empty">Chưa có lớp trong đợt này.</p> : (
                    <ul className="pf-classes">
                      {o.classes.map((c) => (
                        <li key={c.id}>
                          <div>
                            <b>{first(c, "name", "className") || "Lớp #" + c.id}</b>
                            <span>{[first(c, "coachName"), first(c, "schedule", "scheduleDescription"), first(c, "facilityName", "room")].filter(Boolean).join(" • ")}</span>
                          </div>
                          <em>{first(c, "status")}</em>
                          <button type="button" className="pf-primary" onClick={() => register(c)}>Đăng ký</button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
          </section>
        )}
      </main>
    </div>
  );
}