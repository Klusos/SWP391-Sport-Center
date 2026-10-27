import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import RevenueBars from "../components/RevenueBars";
import { defaultRevenueRange, formatVnd } from "../revenueReport";
import { upcomingPtBookings } from "../ptBooking";
import "./Dashboard.css";
import "./Membertheme.css";
import {
  getUsersApi, getSportsApi, getCoachesApi, getConsultationRequestsApi,
  getPublicMembershipPlansApi, getAssignedPtBookingsApi, getMySkillAssessmentAssignmentsApi,
  getMyPtBookingsApi, getMyMemberSportLevelsApi, getMySkillAssessmentsApi,
  getMyConsultationRequestsApi, getCurrentUserApi, getMembershipsByMemberApi, getMyClassSessionsApi,
  getMyRegistrationsApi, getMyAttendanceApi, getMyPaymentsApi, getAdminRevenueReportApi,
} from "../api";

/* ---------- helpers ---------- */
const list = (d) => (Array.isArray(d) ? d : d?.content ?? []);
const total = (d) => (d == null ? "—" : d?.totalElements ?? list(d).length);
const count = (d, fn) => (d == null ? "—" : list(d).filter(fn).length);
const fmt = (s) => (s ? new Date(s).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" }) : "—");
// PT bookings only: expired holds and cancelled sessions are not upcoming.
const upcoming = (d) => upcomingPtBookings(list(d));
const today = new Date().toLocaleDateString("vi-VN");

const memberUsers = (t) => getUsersApi(t).then((u) => list(u).filter((x) => x.role === "MEMBER"));

const LOADERS = {
  ADMIN: {
    members: memberUsers, sports: getSportsApi, coaches: getCoachesApi,
    requests: (t) => getConsultationRequestsApi(t, { size: 100 }),
  },
  RECEPTIONIST: {
    members: memberUsers, sports: getSportsApi, plans: getPublicMembershipPlansApi,
    requests: (t) => getConsultationRequestsApi(t, { size: 100 }),
  },
  COACH: { bookings: getAssignedPtBookingsApi, assessments: getMySkillAssessmentAssignmentsApi },
  MEMBER: {
    bookings: getMyPtBookingsApi, sessions: getMyClassSessionsApi, regs: getMyRegistrationsApi,
    levels: getMyMemberSportLevelsApi, attendance: getMyAttendanceApi, payments: getMyPaymentsApi,
    profile: (t) => getCurrentUserApi(t).then(async (user) => ({
      user, memberships: list(await getMembershipsByMemberApi(t, user.id).catch(() => [])),
    })),
  },
};

function useLoad(token, role) {
  const [state, setState] = useState({ data: {}, loading: true });
  useEffect(() => {
    let off = false;
    const loaders = LOADERS[role] || {};
    const keys = Object.keys(loaders);
    Promise.allSettled(keys.map((k) => loaders[k](token))).then((rs) => {
      if (off) return;
      const data = {};
      keys.forEach((k, i) => { data[k] = rs[i].status === "fulfilled" ? rs[i].value : null; });
      setState({ data, loading: false });
    });
    return () => { off = true; };
  }, [token, role]);
  return state;
}

/* ---------- small pieces ---------- */
function Stat({ label, value, color }) {
  return (
    <div className="dash-stat">
      <div><span>{label}</span><strong>{value}</strong></div>
      <i className={"dash-dot " + color} aria-hidden="true" />
    </div>
  );
}
const Stats = ({ items }) => <div className="dash-stats">{items.map((s) => <Stat key={s.label} {...s} />)}</div>;

function Card({ title, note, className = "", children }) {
  return (
    <section className={"dash-card " + className}>
      <h2>{title}{note && <small>{note}</small>}</h2>
      {children}
    </section>
  );
}
const Empty = ({ children }) => <p className="dash-empty">{children}</p>;

/* ---------- role views ---------- */
function AdminRevenueCard({ token }) {
  const [state, setState] = useState({ loading: true, report: null, error: "" });
  useEffect(() => {
    const controller = new AbortController();
    const range = defaultRevenueRange();
    getAdminRevenueReportApi(token, { ...range, groupBy: "MONTH" }, controller.signal)
      .then((report) => { if (!controller.signal.aborted) setState({ loading: false, report, error: "" }); })
      .catch((error) => {
        if (!controller.signal.aborted) setState({ loading: false, report: null, error: error.message || "Không thể tải doanh thu." });
      });
    return () => controller.abort();
  }, [token]);
  return (
    <Card title="Doanh thu theo thời gian" note={state.loading ? "Đang tải" : "Giao dịch SUCCESS"}>
      {state.loading ? <Empty>Đang tải dữ liệu doanh thu…</Empty> : state.error ? <Empty>{state.error}</Empty> : (
        <>
          <p className="admin-revenue-total">Tổng 8 tháng gần nhất: <strong>{formatVnd(state.report?.totalRevenue)}</strong></p>
          <RevenueBars periods={state.report?.periods || []} groupBy="MONTH" compact />
          <Link className="admin-revenue-link" to="/admin/reports">Mở báo cáo doanh thu</Link>
        </>
      )}
    </Card>
  );
}

function Admin({ d, token }) {
  const reqs = list(d.requests);
  const by = (s) => reqs.filter((r) => r.status === s).length;
  const rows = [["Chờ xử lý", by("PENDING"), "orange"], ["Đang xử lý", by("IN_PROGRESS"), "blue"], ["Hoàn thành", by("COMPLETED"), "green"]];
  const sum = rows.reduce((a, r) => a + r[1], 0) || 1;
  return (<>
    <Stats items={[
      { label: "Tổng thành viên", value: total(d.members), color: "blue" },
      { label: "Bộ môn", value: total(d.sports), color: "green" },
      { label: "Yêu cầu tư vấn chờ", value: count(d.requests, (r) => r.status === "PENDING"), color: "orange" },
      { label: "Tổng HLV", value: total(d.coaches), color: "blue" },
    ]} />
    <div className="dash-grid wide">
      <AdminRevenueCard token={token} />
      <Card title="Yêu cầu tư vấn">
        <ul className="dash-rows">
          {rows.map(([n, v, c]) => (
            <li key={n}><span>{n}</span><b className={"txt-" + c}>{Math.round((v / sum) * 100)}% ({v})</b></li>
          ))}
        </ul>
      </Card>
    </div>
    <Card title="Bộ môn">
      {list(d.sports).length === 0 ? <Empty>Chưa có bộ môn nào.</Empty> : (
        <ul className="dash-rows">
          {list(d.sports).slice(0, 6).map((s) => <li key={s.id}><span>{s.name}</span><em>{s.status}</em></li>)}
        </ul>
      )}
    </Card>
  </>);
}

function Receptionist({ d, onNavigate }) {
  const actions = [
    ["Yêu cầu tư vấn", "Tiếp nhận / tạo thành viên", "consultations"],
    ["Bán gói hội viên", "Thanh toán tại quầy", "membership-sales"],
    ["Danh sách thành viên", "Tra cứu", "members"],
  ];
  return (<>
    <Stats items={[
      { label: "Tổng thành viên", value: total(d.members), color: "blue" },
      { label: "Gói hội viên", value: total(d.plans), color: "green" },
      { label: "Bộ môn", value: total(d.sports), color: "orange" },
      { label: "Yêu cầu chờ xử lý", value: count(d.requests, (r) => r.status === "PENDING"), color: "red" },
    ]} />
    <Card title="Tìm kiếm thành viên" className="narrow">
      <div className="dash-search">
        <input placeholder="Nhập tên, SĐT, mã thành viên..." aria-label="Tìm thành viên" />
        <button className="dash-primary" type="button" onClick={() => onNavigate("members")}>Tìm kiếm</button>
      </div>
      <h3>Thao tác nhanh</h3>
      <div className="dash-actions">
        {actions.map(([t, s, p]) => (
          <button key={p} type="button" onClick={() => onNavigate(p)}><b>{t}</b><span>{s}</span></button>
        ))}
      </div>
    </Card>
  </>);
}

function BookingList({ items, who }) {
  if (!items.length) return <Empty>Chưa có buổi huấn luyện sắp tới.</Empty>;
  return (
    <ul className="dash-sessions">
      {items.slice(0, 5).map((b) => (
        <li key={b.id}>
          <div><small>{fmt(b.startTime)}</small><b>{b.sportName || b.sport?.name || "Buổi PT"}</b></div>
          <span>{who(b)}</span>
          <em>{b.status}</em>
        </li>
      ))}
    </ul>
  );
}

function Coach({ d }) {
  const up = upcoming(d.bookings);
  const pending = list(d.assessments).filter((a) => !/COMPLETED|CANCEL/i.test(a.status || ""));
  return (<>
    <Stats items={[
      { label: "Buổi PT được giao", value: total(d.bookings), color: "blue" },
      { label: "Sắp diễn ra", value: d.bookings == null ? "—" : up.length, color: "green" },
      { label: "Đánh giá cần làm", value: d.assessments == null ? "—" : pending.length, color: "orange" },
      { label: "Tổng đánh giá", value: total(d.assessments), color: "blue" },
    ]} />
    <div className="dash-grid wide">
      <Card title="Buổi huấn luyện sắp tới">
        <BookingList items={up} who={(b) => b.memberName || b.memberFullName || b.member?.fullName || ""} />
      </Card>
      <Card title="Đánh giá kỹ năng cần làm">
        {pending.length === 0 ? <Empty>Không có đánh giá nào đang chờ.</Empty> : (
          <ul className="dash-rows">
            {pending.slice(0, 5).map((a) => (
              <li key={a.id}><span>{a.memberName || a.memberFullName || "Học viên"}</span><em>{a.sportName || a.sport?.name || ""}</em></li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  </>);
}

const ICON = {
  pt: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M19 8v6 M22 11h-6",
  cls: "M22 12h-4l-3 9L9 3l-3 9H2",
  ok: "M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4 12 14.01l-3-3",
  bill: "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8 M12 17.5v-11",
  cal: "M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
  clock: "M12 6v6l4 2 M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20",
  renew: "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16 M8 16H3v5",
};
const Ic = ({ d, size = 20 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {d.split(" M").map((p, i) => <path key={i} d={(i ? "M" : "") + p} />)}
  </svg>
);

const pick = (o, ...keys) => { for (const k of keys) if (o?.[k] != null && o[k] !== "") return o[k]; return ""; };
const WD = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
const hm = (s) => new Date(s).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
const vnd = (v) => Number(v || 0).toLocaleString("vi-VN") + "đ";
const SESSION_STATUS = { SCHEDULED: "Đã lên lịch", CONFIRMED: "Đã xác nhận", PENDING_PAYMENT: "Chờ thanh toán", COMPLETED: "Hoàn thành" };
const ATT = { PRESENT: ["Có mặt", "ok"], ON_TIME: ["Đúng giờ", "ok"], LATE: ["Trễ giờ", "warn"], EXCUSED: ["Có phép", "warn"], ABSENT: ["Vắng", "bad"] };

function MStat({ icon, tone, label, value, sub, action, onAction }) {
  return (
    <article className={"md-stat " + tone}>
      <header><span>{label}</span><i><Ic d={icon} /></i></header>
      <strong>{value}</strong>
      {action ? <button type="button" className="md-link" onClick={onAction}>{action} →</button> : <small>{sub}</small>}
    </article>
  );
}

function Member({ d, onNavigate }) {
  const user = d.profile?.user;
  const memberships = d.profile?.memberships || [];
  const active = memberships.filter((m) => m.status === "ACTIVE").sort((a, b) => String(b.endDate).localeCompare(String(a.endDate)))[0];
  const now = new Date();

  let left = 0, totalDays = 1;
  if (active) {
    totalDays = Math.max(1, Math.round((new Date(active.endDate) - new Date(active.startDate)) / 86400000));
    left = Math.max(0, Math.ceil((new Date(active.endDate) - now) / 86400000));
  }

  const ptUp = upcoming(d.bookings);
  const classUp = list(d.sessions)
    .filter((s) => s.startTime && new Date(s.startTime) > now && !/CANCEL/i.test(s.status || ""));
  const schedule = [
    ...ptUp.map((b) => ({ id: "pt" + b.id, start: b.startTime, end: b.endTime, title: "PT 1-1 " + (b.sportName || b.sport?.name || ""), tag: "PT 1-1", place: "", person: b.coachName || b.coachFullName || b.coach?.fullName || "", status: b.status })),
    ...classUp.map((s) => ({ id: "cs" + s.id, start: s.startTime, end: s.endTime, title: s.className || "Lớp #" + s.classId, tag: "Lớp học", place: s.facilityName || "", person: "", status: s.status })),
  ].sort((a, b) => new Date(a.start) - new Date(b.start)).slice(0, 4);

  const regs = list(d.regs).filter((r) => !r.status || r.status === "CONFIRMED");
  const att = list(d.attendance).map((a) => ({ ...a, when: pick(a, "startTime", "sessionStartTime", "markedAt", "date", "createdAt"), name: pick(a, "className", "sessionName", "courseName") }))
    .sort((a, b) => new Date(b.when) - new Date(a.when));
  const month = att.filter((a) => a.when && new Date(a.when).getMonth() === now.getMonth() && new Date(a.when).getFullYear() === now.getFullYear());
  const attended = month.filter((a) => /PRESENT|ON_TIME|LATE/i.test(a.status || "")).length;
  const pendingBills = list(d.payments).filter((p) => p.status === "PENDING");
  const billTotal = pendingBills.reduce((a, p) => a + Number(p.amount || 0), 0);
  const levels = list(d.levels);
  const nextOne = schedule[0];

  return (<>
    <header className="md-greet">
      <div>
        <h1>Xin chào, {user?.name || "bạn"} 👋 {active && <span className="md-badge">{active.planName}</span>}</h1>
        <p>Hôm nay: {now.toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" })} • Sẵn sàng bứt phá giới hạn hôm nay!</p>
      </div>
      <button type="button" className="md-cta" onClick={() => onNavigate("member-pt-booking")}><Ic d={ICON.cal} size={18} /> Đặt buổi huấn luyện</button>
    </header>

    <section className="md-hero">
      {active ? (<>
        <div className="md-hero-top">
          <span className="md-pill">GÓI {String(active.planName).toUpperCase()}</span>
          <span className="md-pill live">● Đang hoạt động</span>
          <span className="md-pill">Tự gia hạn: {active.autoRenew ? "Bật" : "Tắt"}</span>
        </div>
        <h2>Tập luyện cùng SPORTS CENTER</h2>
        <p>Gói có hiệu lực từ {active.startDate} đến {active.endDate}.</p>
        <div className="md-hero-bottom">
          <div className="md-progress-wrap">
            <div className="md-progress-label"><span>Thời hạn sử dụng (Còn <b>{left} ngày</b> / {totalDays} ngày)</span><span>Hết hạn: {active.endDate}</span></div>
            <div className="md-progress"><i style={{ width: Math.min(100, (left / totalDays) * 100) + "%" }} /></div>
          </div>
          <button type="button" className="md-ghost" onClick={() => onNavigate("member-membership")}><Ic d={ICON.renew} size={18} /> Gia hạn gói</button>
        </div>
      </>) : (<>
        <h2>Bạn chưa có gói thành viên</h2>
        <p>Chọn một gói để mở khóa ưu đãi khóa học và huấn luyện viên cá nhân.</p>
        <div className="md-hero-bottom"><span /><button type="button" className="md-ghost" onClick={() => onNavigate("member-membership")}>Xem các gói</button></div>
      </>)}
    </section>

    <div className="md-stats">
      <MStat icon={ICON.pt} tone="blue" label="Buổi PT sắp tới" value={ptUp.length}
        sub={ptUp[0] ? "Buổi tiếp theo: " + hm(ptUp[0].startTime) + " " + WD[new Date(ptUp[0].startTime).getDay()] : "Chưa có buổi nào"} />
      <MStat icon={ICON.cls} tone="green" label="Lớp đang học" value={regs.length}
        sub={regs.slice(0, 3).map((r) => r.className).filter(Boolean).join(", ") || "Chưa đăng ký lớp"} />
      <MStat icon={ICON.ok} tone="purple" label="Điểm danh tháng này" value={<>{attended}<em> / {month.length}</em></>}
        sub={month.length ? "Đạt " + Math.round((attended / month.length) * 100) + "% số buổi" : "Chưa có buổi nào trong tháng"} />
      <MStat icon={ICON.bill} tone={pendingBills.length ? "orange" : "gray"} label="Hóa đơn chờ thanh toán"
        value={<>{pendingBills.length}{pendingBills.length > 0 && <em> ({vnd(billTotal)})</em>}</>}
        sub="Không có hóa đơn nào đang chờ" action={pendingBills.length ? "Thanh toán ngay" : ""} onAction={() => onNavigate("member-payments")} />
    </div>

    <div className="md-grid">
      <section className="md-card">
        <div className="md-card-head"><h2><Ic d={ICON.cal} size={20} /> Lịch tập sắp tới</h2>
          <button type="button" className="md-link" onClick={() => onNavigate("member-schedule")}>Xem tất cả lịch →</button></div>
        {schedule.length === 0 ? (
          <div className="md-empty"><Ic d={ICON.cal} size={28} /><p>Chưa có buổi tập nào sắp tới.</p>
            <button type="button" className="md-cta" onClick={() => onNavigate("member-pt-booking")}>Đặt buổi đầu tiên</button></div>
        ) : (
          <ul className="md-timeline">
            {schedule.map((x) => (
              <li key={x.id}>
                <div className="md-date"><small>{WD[new Date(x.start).getDay()]}</small><b>{new Date(x.start).getDate()}</b><span>{hm(x.start)}</span></div>
                <div className="md-info">
                  <h3>{x.title} <span className={"md-tag " + (x.tag === "PT 1-1" ? "pt" : "cls")}>{x.tag}</span></h3>
                  <p>Thời gian: {hm(x.start)}{x.end ? " - " + hm(x.end) : ""}{x.place ? " • " + x.place : ""}</p>
                  {x.person && <p className="md-who">Coach {x.person}</p>}
                </div>
                <span className={"md-chip " + (x.status === "PENDING_PAYMENT" ? "warn" : "ok")}>{SESSION_STATUS[x.status] || x.status || "Đã lên lịch"}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="md-card">
        <div className="md-card-head"><h2><Ic d={ICON.clock} size={20} /> Điểm danh gần đây</h2></div>
        {att.length === 0 ? (
          <div className="md-empty"><Ic d={ICON.ok} size={28} /><p>Chưa có lịch sử điểm danh.</p></div>
        ) : (
          <table className="md-table">
            <thead><tr><th>Ngày</th><th>Môn tập</th><th>Trạng thái</th></tr></thead>
            <tbody>
              {att.slice(0, 4).map((a, i) => {
                const [label, tone] = ATT[String(a.status).toUpperCase()] || [a.status || "—", "warn"];
                return (<tr key={a.id ?? i}>
                  <td>{a.when ? new Date(a.when).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" }) : "—"}<small>{a.when ? hm(a.when) : ""}</small></td>
                  <td>{a.name || "Buổi tập"}</td><td><span className={"md-chip " + tone}>{label}</span></td></tr>);
              })}
            </tbody>
          </table>
        )}
        <button type="button" className="md-wide" onClick={() => onNavigate("member-attendance")}>Xem toàn bộ lịch sử điểm danh</button>
      </section>
    </div>

    <section className="md-card">
      <div className="md-card-head"><h2>Trình độ của tôi</h2>
        <button type="button" className="md-link" onClick={() => onNavigate("member-levels")}>Chi tiết →</button></div>
      {levels.length === 0 ? <p className="dash-empty">Bạn chưa được đánh giá trình độ.</p> : (
        <div className="md-levels">
          {levels.map((l, i) => <div key={l.id ?? i}><span>{l.sportName || l.sport?.name || "Bộ môn #" + l.sportId}</span><b>{l.level || "—"}</b></div>)}
        </div>
      )}
    </section>
  </>);
}

/* ---------- page ---------- */
const TITLES = {
  ADMIN: ["Tổng quan trung tâm", null],
  RECEPTIONIST: ["Tổng quan lễ tân", "Quản lý thành viên, tư vấn và gói hội viên"],
  COACH: ["Tổng quan", null],
  MEMBER: ["Tổng quan", "Lịch tập, trình độ và đánh giá của bạn"],
};

export default function Dashboard({ token, role, onLogout, onNavigate }) {
  const { data, loading } = useLoad(token, role);
  const [title, sub] = TITLES[role] || TITLES.MEMBER;
  const View = { ADMIN: Admin, RECEPTIONIST: Receptionist, COACH: Coach, MEMBER: Member }[role] || Member;

  return (
    <div className="layout">
      <Sidebar role={role} activePage="dashboard" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main dash-main">
        {role !== "MEMBER" && <><h1>{title}</h1>
        <p className="subtitle">{sub || "Hôm nay: " + today}</p></>}
        {loading ? <div className="queue-state">Đang tải dữ liệu…</div> : <View d={data} onNavigate={onNavigate} token={token} />}
      </main>
    </div>
  );
}