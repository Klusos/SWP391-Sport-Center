import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import Checkout from "../components/Checkout";
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
  const [checkout, setCheckout] = useState(null);

  useEffect(() => {
    getPublicSportsApi().then((d) => { const s = list(d); setSports(s); if (s[0]) setSportId(s[0].id); })
      .catch((e) => setMsg({ type: "error", text: e.message }));
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
      setOfferings(await Promise.all(offs.map(async (o) => {
        try { return { ...o, classes: list(await getClassesByOfferingApi(token, o.id)) }; }
        catch { return { ...o, classes: [] }; }
      })));
    } catch (e) { setMsg({ type: "error", text: e.message }); } finally { setBusy(false); }
  }

  /* Đăng ký lớp -> nhận bản đăng ký (kèm giá) -> mở hóa đơn để thanh toán */
  async function register(cls, offering) {
    setMsg(null);
    try {
      const reg = await createCourseRegistrationApi(token, cls.id);
      setCheckout({ reg, cls, offering });
    } catch (e) { setMsg({ type: "error", text: e.message }); }
  }

  const lines = (reg, offering) => [
    { label: "Học phí – " + (offering?.name || ""), amount: reg.originalPrice ?? offering?.price },
    ...(Number(reg.membershipDiscount) > 0 ? [{ label: "Giảm giá thành viên", amount: reg.membershipDiscount, neg: true }] : []),
  ];

  return (
    <div className="layout">
      <Sidebar role={role} activePage="member-courses" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main">
        <h1>Khóa học &amp; lớp học</h1>
        <p className="subtitle">Chọn bộ môn, xem khóa học và đăng ký lớp. Bạn sẽ nhận hóa đơn để thanh toán sau khi đăng ký.</p>
        {msg && <div className={"banner " + msg.type} role="status">{msg.text}</div>}

        <div className="pf-chips" role="tablist" aria-label="Bộ môn">
          {sports.map((s) => (
            <button key={s.id} type="button" role="tab" aria-selected={s.id === sportId}
              className={"pf-chip" + (s.id === sportId ? " active" : "")} onClick={() => setSportId(s.id)}>{s.name}</button>
          ))}
        </div>

        {courses.length === 0 ? <p className="dash-empty">Bộ môn này chưa có khóa học.</p> : (
          <div className="pf-courses">
            {courses.map((c) => (
              <button key={c.id} type="button" className={"pf-course" + (course?.id === c.id ? " active" : "")} onClick={() => openCourse(c)}>
                <b>{first(c, "name", "title")}</b><span>{first(c, "description", "summary")}</span>
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
                    <small>{[day(o.startDate), day(o.endDate)].filter(Boolean).join(" – ")} · {Number(o.price).toLocaleString("vi-VN")}đ</small>
                  </h3>
                  {o.classes.length === 0 ? <p className="dash-empty">Chưa có lớp trong đợt này.</p> : (
                    <ul className="pf-classes">
                      {o.classes.map((c) => (
                        <li key={c.id}>
                          <div><b>{first(c, "name") || "Lớp #" + c.id}</b>
                            <span>{[c.coachName, c.capacity ? "Sĩ số " + c.capacity : ""].filter(Boolean).join(" • ")}</span></div>
                          <em>{first(c, "status")}</em>
                          <button type="button" className="pf-primary" onClick={() => register(c, o)}>Đăng ký</button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
          </section>
        )}

        {checkout && (
          <Checkout token={token} title={"Lớp " + checkout.cls.name + " – " + course?.name} purpose="COURSE_REGISTRATION"
            referenceId={checkout.reg.id} lines={lines(checkout.reg, checkout.offering)}
            total={checkout.reg.finalPrice ?? checkout.offering?.price} onClose={() => setCheckout(null)} />
        )}
      </main>
    </div>
  );
}