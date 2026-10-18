import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import { getCurrentUserApi, getCoachesApi, getMyPtBookingsApi, getAssignedPtBookingsApi } from "../api";
import { getMyClassSessionsApi, getMyRegistrationsApi, getAllClassesApi, getAllSessionsApi } from "../apiExtra";

const list = (d) => (Array.isArray(d) ? d : d?.content ?? []);
const DAYS = ["Chủ nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];
const monday = (d) => { const x = new Date(d); x.setHours(0, 0, 0, 0); x.setDate(x.getDate() - ((x.getDay() + 6) % 7)); return x; };
const hhmm = (s) => new Date(s).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
const short = (d) => d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
const ok = (r) => (r.status === "fulfilled" ? list(r.value) : []);

const fromPt = (b, who) => ({ id: "pt" + b.id, startTime: b.startTime, endTime: b.endTime, title: "PT • " + (b.sportName || b.sport?.name || "Buổi 1-1"), sub: who(b), status: b.status });
const fromSession = (s) => ({ id: "cs" + s.id, startTime: s.startTime, endTime: s.endTime, title: "Lớp • " + (s.className || "#" + s.classId), sub: s.facilityName || "", status: s.status });

export default function Schedule({ token, role, onLogout, onNavigate }) {
  const isCoach = role === "COACH";
  const [items, setItems] = useState(null);
  const [regs, setRegs] = useState([]);
  const [error, setError] = useState("");
  const [start, setStart] = useState(() => monday(new Date()));

  useEffect(() => {
    let off = false;
    (async () => {
      try {
        let pt, sessions = [];
        if (isCoach) {
          const [me, coaches, bookings, classes, all] = await Promise.allSettled([
            getCurrentUserApi(token), getCoachesApi(token), getAssignedPtBookingsApi(token), getAllClassesApi(token), getAllSessionsApi(token)]);
          const coach = ok(coaches).find((c) => me.status === "fulfilled" && c.userId === me.value.id);
          const mine = new Set(ok(classes).filter((c) => coach && c.coachId === coach.id).map((c) => c.id));
          sessions = ok(all).filter((s) => mine.has(s.classId));
          pt = ok(bookings).map((b) => fromPt(b, (x) => x.memberName || x.memberFullName || x.member?.fullName || ""));
        } else {
          const [bookings, cs, rg] = await Promise.allSettled([getMyPtBookingsApi(token), getMyClassSessionsApi(token), getMyRegistrationsApi(token)]);
          sessions = ok(cs);
          pt = ok(bookings).map((b) => fromPt(b, (x) => x.coachName || x.coachFullName || x.coach?.fullName || ""));
          if (!off) setRegs(ok(rg));
        }
        if (!off) setItems([...pt, ...sessions.map(fromSession)]);
      } catch (e) { if (!off) { setError(e.message); setItems([]); } }
    })();
    return () => { off = true; };
  }, [token, isCoach]);

  const days = Array.from({ length: 7 }, (_, i) => { const d = new Date(start); d.setDate(d.getDate() + i); return d; });
  const shift = (n) => setStart((s) => { const d = new Date(s); d.setDate(d.getDate() + n * 7); return d; });
  const forDay = (d) => (items || [])
    .filter((b) => b.startTime && new Date(b.startTime).toDateString() === d.toDateString())
    .sort((a, b) => new Date(a.startTime) - new Date(b.startTime));

  return (
    <div className="layout">
      <Sidebar role={role} activePage={isCoach ? "coach-schedule" : "member-schedule"} onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main">
        <h1>Lịch trình</h1>
        <p className="subtitle">{isCoach ? "Buổi lớp và buổi huấn luyện cá nhân bạn phụ trách." : "Buổi lớp đã đăng ký và buổi huấn luyện cá nhân của bạn."}</p>
        {error && <div className="banner error" role="alert">{error}</div>}

        <div className="pf-weekbar">
          <button type="button" onClick={() => shift(-1)}>Tuần trước</button>
          <button type="button" onClick={() => setStart(monday(new Date()))}>Tuần này</button>
          <button type="button" onClick={() => shift(1)}>Tuần sau</button>
          <strong>{short(days[0])} – {short(days[6])}/{days[6].getFullYear()}</strong>
        </div>

        {items === null ? <div className="queue-state">Đang tải lịch…</div> : (
          <div className="pf-week">
            {days.map((d) => {
              const rows = forDay(d);
              return (
                <section key={d.toISOString()} className={"pf-day" + (d.toDateString() === new Date().toDateString() ? " today" : "")}>
                  <h2>{DAYS[d.getDay()]}<small>{short(d)}</small></h2>
                  {rows.length === 0 ? <p className="pf-none">Không có buổi</p> : rows.map((b) => (
                    <div key={b.id} className={"pf-session" + (/CANCEL/i.test(b.status || "") ? " off" : "")}>
                      <small>{hhmm(b.startTime)}{b.endTime ? " – " + hhmm(b.endTime) : ""}</small>
                      <b>{b.title}</b>
                      {b.sub && <span>{b.sub}</span>}
                      {b.status && <em>{b.status}</em>}
                    </div>
                  ))}
                </section>
              );
            })}
          </div>
        )}

        {!isCoach && (
          <section className="dash-card" style={{ marginTop: 24 }}>
            <h2>Lớp đã đăng ký</h2>
            {regs.length === 0 ? <p className="dash-empty">Bạn chưa đăng ký lớp nào.</p> : (
              <ul className="dash-rows">
                {regs.map((r) => (
                  <li key={r.id}>
                    <span><b>{r.className}</b><br /><small>{r.courseName} · {r.courseOfferingName}</small></span>
                    <em>{r.status}</em>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
      </main>
    </div>
  );
}