import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import "./Dashboard.css";
import "./MemberTheme.css";
import "./CoachTheme.css";
import "./ReceptionTheme.css";
import {
  getUsersApi, getSportsApi, getCoachesApi, getConsultationRequestsApi,
  getPublicMembershipPlansApi, getAssignedPtBookingsApi, getMySkillAssessmentAssignmentsApi,
  getMyPtBookingsApi, getMyMemberSportLevelsApi, getMySkillAssessmentsApi,
  getMyConsultationRequestsApi, getCurrentUserApi, getMembershipsByMemberApi, getMyClassSessionsApi,
  getMyRegistrationsApi, getMyAttendanceApi, getMyPaymentsApi, getCoachAttendanceSessionsApi, getCoachSportsApi,
  takeConsultationRequestApi,
} from "../api";

/* ---------- helpers ---------- */
const list = (d) => (Array.isArray(d) ? d : d?.content ?? []);
const total = (d) => (d == null ? "—" : d?.totalElements ?? list(d).length);
const count = (d, fn) => (d == null ? "—" : list(d).filter(fn).length);
const fmt = (s) => (s ? new Date(s).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" }) : "—");
const upcoming = (d) => list(d)
  .filter((b) => b.startTime && new Date(b.startTime) > new Date() && !/CANCEL/i.test(b.status || ""))
  .sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
const today = new Date().toLocaleDateString("vi-VN");

const memberUsers = (t) => getUsersApi(t).then((u) => list(u).filter((x) => x.role === "MEMBER"));

const LOADERS = {
  ADMIN: {
    members: memberUsers, sports: getSportsApi, coaches: getCoachesApi,
    requests: (t) => getConsultationRequestsApi(t, { size: 100 }),
  },
  RECEPTIONIST: {
    me: getCurrentUserApi,
    members: memberUsers, sports: getSportsApi, plans: getPublicMembershipPlansApi,
    requests: (t) => getConsultationRequestsApi(t, { size: 100 }),
  },
  COACH: {
    me: getCurrentUserApi, sports: getSportsApi, classSessions: getCoachAttendanceSessionsApi,
    bookings: getAssignedPtBookingsApi, assessments: getMySkillAssessmentAssignmentsApi,
    coachSports: (t) => Promise.all([getCurrentUserApi(t), getCoachesApi(t)]).then(([u, cs]) => {
      const c = list(cs).find((x) => x.userId === u.id);
      return c ? getCoachSportsApi(t, c.id) : [];
    }),
  },
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
  const [tick, setTick] = useState(0);
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
  }, [token, role, tick]);
  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { ...state, reload };
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
function Admin({ d }) {
  const reqs = list(d.requests);
  const by = (s) => reqs.filter((r) => r.status === s).length;
  const rows = [["Chờ xử lý", by("PENDING"), "orange"], ["Đang xử lý", by("IN_PROGRESS"), "blue"], ["Hoàn thành", by("COMPLETED"), "green"]];
  const sum = rows.reduce((a, r) => a + r[1], 0) || 1;
  const months = [28, 38, 50, 58, 68, 78, 88, 100];
  return (<>
    <Stats items={[
      { label: "Tổng thành viên", value: total(d.members), color: "blue" },
      { label: "Bộ môn", value: total(d.sports), color: "green" },
      { label: "Yêu cầu tư vấn chờ", value: count(d.requests, (r) => r.status === "PENDING"), color: "orange" },
      { label: "Tổng HLV", value: total(d.coaches), color: "blue" },
    ]} />
    <div className="dash-grid wide">
      <Card title="Doanh thu theo thời gian" note="Dữ liệu mẫu">
        <div className="dash-bars" role="img" aria-label="Biểu đồ doanh thu mẫu">
          {months.map((h, i) => <span key={i} style={{ height: h + "%" }} />)}
        </div>
      </Card>
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

/* ---------- Lễ tân ---------- */
// Lưu ý: chỉ dùng chuỗi path thuần ở đây (không tham chiếu hằng khai báo phía dưới file).
const RC = {
  users: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75",
  card: "M3 5h18a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z M2 10h20",
  award: "M12 15a6 6 0 1 0 0-12 6 6 0 0 0 0 12 M8.2 13.9 7 22l5-3 5 3-1.2-8.1",
  pending: "M16 4h2a2 2 0 0 1 2 2v4 M8 4H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h4 M9 2h6v4H9z M17 22a5 5 0 1 0 0-10 5 5 0 0 0 0 10 M17 14.5V17l1.5 1",
  search: "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16 M21 21l-4.35-4.35",
  userSearch: "M10 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M7.5 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M21 21l-3.5-3.5 M17 18a3 3 0 1 0 0-6 3 3 0 0 0 0 6",
  phone: "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z",
  clock: "M12 6v6l4 2 M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20",
  chev: "M9 18l6-6-6-6",
  arrow: "M5 12h14 M12 5l7 7-7 7",
  cal: "M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
  headset: "M3 18v-6a9 9 0 0 1 18 0v6 M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3z M3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z",
  bill: "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8 M12 17.5v-11",
  badge: "M16 2H8a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z M12 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4 M9 16a3 3 0 0 1 6 0",
};
const initialsOf = (name) => (name || "?").trim().split(/\s+/).slice(-2).map((w) => w[0]).join("").toUpperCase();
const whenLabel = (s) => {
  if (!s) return "";
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return "";
  const isToday = d.toDateString() === new Date().toDateString();
  return d.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) + " " +
    (isToday ? "Hôm nay" : d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" }));
};
const planPeriod = (m) => (Number(m) === 12 ? "/ năm" : "/ " + m + " tháng");

function Receptionist({ d, onNavigate, token, reload }) {
  const [q, setQ] = useState("");
  const [busyId, setBusyId] = useState(null);
  const [message, setMessage] = useState("");

  const plans = list(d.plans).filter((p) => !p.status || p.status === "ACTIVE");
  const pending = list(d.requests)
    .filter((r) => r.status === "PENDING")
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
  const requestsOk = d.requests != null;
  const dateLabel = new Date().toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" });

  const kpis = [
    { label: "Tổng thành viên", value: total(d.members), icon: RC.users, tone: "blue", note: "Tài khoản hội viên" },
    { label: "Gói hội viên", value: d.plans == null ? "—" : plans.length, icon: RC.card, tone: "green", note: "Đang mở bán" },
    { label: "Bộ môn", value: total(d.sports), icon: RC.award, tone: "blue", note: "Đang giảng dạy" },
    {
      label: "Yêu cầu chờ xử lý", value: requestsOk ? pending.length : "—", icon: RC.pending,
      tone: pending.length ? "amber" : "green",
      note: !requestsOk ? "Không tải được dữ liệu" : pending.length ? "Cần tiếp nhận" : "Không có yêu cầu chờ",
    },
  ];
  const actions = [
    { title: "Yêu cầu tư vấn", sub: "Tiếp nhận / tạo thành viên", page: "consultations", icon: RC.headset },
    { title: "Bán gói hội viên", sub: "Thanh toán tại quầy", page: "membership-sales", icon: RC.bill },
    { title: "Danh sách thành viên", sub: "Tra cứu", page: "members", icon: RC.badge },
  ];

  function search(event) {
    event.preventDefault();
    onNavigate("members", { q: q.trim() });
  }

  async function take(id) {
    setBusyId(id);
    setMessage("");
    try {
      await takeConsultationRequestApi(token, id);
      onNavigate("consultations");
    } catch (error) {
      setMessage(error.status === 409
        ? "Yêu cầu này đã được nhân viên khác tiếp nhận. Danh sách đã được làm mới."
        : error.status === 403
          ? "Tài khoản không có quyền tiếp nhận yêu cầu này."
          : error.message || "Không thể tiếp nhận yêu cầu.");
      if (error.status === 409) reload();
      setBusyId(null);
    }
  }

  return (<>
    <div className="rc-head">
      <div>
        <h1>Tổng quan lễ tân</h1>
        <p>Quản lý thành viên, tư vấn và gói hội viên</p>
      </div>
      <span className="rc-date"><Ic d={RC.cal} size={18} /><b>{dateLabel}</b><i aria-hidden="true" /><small>Hôm nay</small></span>
    </div>

    <section className="rc-kpis" aria-label="Chỉ số nhanh">
      {kpis.map((k) => (
        <article key={k.label} className="rc-kpi">
          <header><span>{k.label}</span><i className={"rc-tile " + k.tone}><Ic d={k.icon} size={21} /></i></header>
          <strong>{k.value}</strong>
          <p className={k.tone}><span aria-hidden="true" />{k.note}</p>
        </article>
      ))}
    </section>

    <div className="rc-grid">
      <div className="rc-col">
        <section className="rc-card">
          <h2><Ic d={RC.userSearch} size={21} /> Tìm kiếm thành viên</h2>
          <form className="rc-search" onSubmit={search}>
            <label className="rc-field">
              <Ic d={RC.search} size={18} />
              <input className="rc-input" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Nhập tên, SĐT, mã thành viên..." aria-label="Tìm thành viên" />
            </label>
            <button type="submit" className="rc-primary">Tìm kiếm</button>
          </form>
        </section>

        <section className="rc-card">
          <div className="rc-card-head">
            <h2>Yêu cầu tư vấn đang chờ <span className="rc-count">{requestsOk ? pending.length : "—"}</span></h2>
            <button type="button" className="rc-link" onClick={() => onNavigate("consultations")}>Xem tất cả <Ic d={RC.arrow} size={14} /></button>
          </div>
          {message && <p className="rc-alert" role="alert">{message}</p>}
          {!requestsOk ? <p className="rc-empty">Không tải được danh sách yêu cầu tư vấn.</p>
            : pending.length === 0 ? <p className="rc-empty">Không có yêu cầu nào đang chờ</p>
              : (
                <ul className="rc-requests">
                  {pending.slice(0, 5).map((r, i) => (
                    <li key={r.id}>
                      <span className={"rc-avatar" + (i % 2 ? " alt" : "")}>{initialsOf(r.name)}</span>
                      <div className="rc-req-main">
                        <div><b>{r.name || "Khách hàng"}</b>{r.sportName && <em>{r.sportName}</em>}</div>
                        <p>
                          <span><Ic d={RC.phone} size={14} />{r.phone || "—"}</span>
                          <i aria-hidden="true" />
                          <span><Ic d={RC.clock} size={14} />{whenLabel(r.createdAt)}</span>
                        </p>
                      </div>
                      <button type="button" className="rc-primary small" disabled={busyId !== null} onClick={() => take(r.id)}>
                        {busyId === r.id ? "Đang tiếp nhận…" : "Tiếp nhận"}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
        </section>
      </div>

      <div className="rc-col">
        <section className="rc-card">
          <h2>Thao tác nhanh</h2>
          <div className="rc-actions">
            {actions.map((a) => (
              <button key={a.page} type="button" className="rc-action" onClick={() => onNavigate(a.page)}>
                <i><Ic d={a.icon} size={22} /></i>
                <span><b>{a.title}</b><small>{a.sub}</small></span>
                <Ic d={RC.chev} size={20} />
              </button>
            ))}
          </div>
        </section>

        <section className="rc-card">
          <div className="rc-card-head">
            <h2>Gói hội viên đang áp dụng</h2>
            <button type="button" className="rc-link" onClick={() => onNavigate("membership-plans")}>Xem chi tiết <Ic d={RC.arrow} size={14} /></button>
          </div>
          {d.plans == null ? <p className="rc-empty">Không tải được danh sách gói hội viên.</p>
            : plans.length === 0 ? <p className="rc-empty">Chưa có gói hội viên nào.</p>
              : (
                <ul className="rc-plans">
                  {plans.slice(0, 3).map((p) => (
                    <li key={p.id}>
                      <div><b>{p.name}</b><p>{p.description || "Chưa có mô tả."}</p></div>
                      <span>{vnd(p.price)} <small>{planPeriod(p.durationMonths)}</small></span>
                    </li>
                  ))}
                </ul>
              )}
        </section>
      </div>
    </div>
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

const CI = {
  users: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75",
  board: "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2 M9 2h6v4H9z M9 14l2 2 4-4",
  award: "M12 15a6 6 0 1 0 0-12 6 6 0 0 0 0 12 M8.2 13.9 7 22l5-3 5 3-1.2-8.1",
  chev: "M9 18l6-6-6-6",
  arrow: "M5 12h14 M12 5l7 7-7 7",
  calx: "M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M10 14l4 4 M14 14l-4 4",
  check: "M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4 12 14.01l-3-3",
  cal: "M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
};
const PT_STATUS = { CONFIRMED: ["Đã xác nhận", "ok"], PENDING_PAYMENT: ["Chờ thanh toán", "warn"], COMPLETED: ["Hoàn thành", "ok"] };
const CLASS_STATUS = { SCHEDULED: ["Đã lên lịch", "ok"], COMPLETED: ["Đã hoàn thành", "ok"], CANCELLED: ["Đã hủy", "bad"] };

function CStat({ icon, tone, label, value, sub, pct }) {
  return (
    <article className={"md-stat " + tone}>
      <header><span>{label}</span><i><Ic d={icon} /></i></header>
      <div className="cd-stat-row"><strong>{value}</strong><small>{sub}</small></div>
      <div className="cd-line"><i style={{ width: pct + "%" }} /></div>
    </article>
  );
}

function Coach({ d, onNavigate }) {
  const now = new Date();
  const sameDay = (v) => v && new Date(v).toDateString() === now.toDateString();
  const byStart = (a, b) => new Date(a.start) - new Date(b.start);
  const sportName = new Map(list(d.sports).map((x) => [Number(x.id), x.name]));
  const mySports = list(d.coachSports).map((x) => x.sportName).filter(Boolean);

  const classes = list(d.classSessions);
  const todayClass = classes.filter((x) => sameDay(x.startTime) && x.sessionStatus !== "CANCELLED");
  const ptAll = list(d.bookings);
  const ptToday = ptAll.filter((b) => sameDay(b.startTime) && !/CANCEL|EXPIRED/i.test(b.status || ""));
  const ptUp = upcoming(d.bookings).filter((b) => b.status !== "EXPIRED");
  const pending = list(d.assessments).filter((a) => a.status === "PENDING");
  const done = list(d.assessments).filter((a) => a.status === "COMPLETED");
  const toMark = todayClass.filter((x) => x.sessionStatus === "SCHEDULED" && new Date(x.startTime) <= now);
  const unpaid = ptAll.filter((b) => b.status === "PENDING_PAYMENT");

  const schedule = [
    ...todayClass.map((x) => ({ id: "c" + x.classSessionId, start: x.startTime, end: x.endTime, kind: "class", title: x.courseClassName || x.courseName || "Lớp học", sub: [x.courseName, x.sportName].filter(Boolean).join(" · "), st: CLASS_STATUS[x.sessionStatus] || [x.sessionStatus, "warn"], canMark: x.sessionStatus === "SCHEDULED" && new Date(x.startTime) <= now })),
    ...ptToday.map((b) => ({ id: "p" + b.id, start: b.startTime, end: b.endTime, kind: "pt", title: "PT 1-1 · " + (b.sportName || ""), sub: b.memberName || "", st: PT_STATUS[b.status] || [b.status, "warn"] })),
  ].sort(byStart);
  const nextClass = [...todayClass].sort((a, b) => new Date(a.startTime) - new Date(b.startTime)).find((x) => new Date(x.startTime) > now);

  const tasks = [
    { icon: CI.check, title: "Điểm danh buổi học đang diễn ra", ok: toMark.length === 0, text: toMark.length ? toMark.length + " buổi đã bắt đầu, chờ bạn điểm danh" : "Không có buổi nào cần điểm danh", go: "coach-attendance" },
    { icon: CI.board, title: "Hoàn tất đánh giá kỹ năng", ok: pending.length === 0, text: pending.length ? pending.length + " học viên đang chờ đánh giá" : "Bạn đã xử lý hết các đánh giá", go: "coach-assessments" },
    { icon: CI.users, title: "Theo dõi thanh toán buổi PT", ok: unpaid.length === 0, text: unpaid.length ? unpaid.length + " buổi chờ học viên thanh toán" : "Tất cả buổi PT đã được thanh toán", go: "coach-pt-sessions" },
  ];
  const doneCount = tasks.filter((t) => t.ok).length;

  return (<>
    <section className="cd-welcome">
      <div>
        <h1>Xin chào, Coach {d.me?.name || ""} 👋 {mySports.length > 0 && <span className="md-badge">{mySports.join(" · ")}</span>}</h1>
        <p>Chúc bạn một ngày tràn đầy năng lượng! Dưới đây là tổng quan công việc của bạn hôm nay.</p>
      </div>
      <div className="cd-actions">
        <span className="cd-date"><Ic d={CI.cal} size={16} /> Hôm nay: {now.toLocaleDateString("vi-VN")}</span>
        <button type="button" className="cd-ghost" onClick={() => onNavigate("coach-attendance")}><Ic d={CI.check} size={16} /> Điểm danh</button>
        <button type="button" className="md-cta" onClick={() => onNavigate("coach-pt-sessions")}><Ic d={CI.users} size={16} /> Buổi PT cá nhân</button>
      </div>
    </section>

    <div className="md-stats">
      <CStat icon={CI.users} tone="blue" label="Buổi lớp hôm nay" value={todayClass.length}
        sub={nextClass ? "Kế tiếp: " + hm(nextClass.startTime) : "Chưa có lịch phân công"}
        pct={todayClass.length ? (todayClass.filter((x) => x.sessionStatus === "COMPLETED").length / todayClass.length) * 100 : 0} />
      <CStat icon={ICON.pt} tone="green" label="Buổi PT sắp tới" value={ptUp.length}
        sub={ptUp[0] ? "Kế tiếp: " + hm(ptUp[0].startTime) : "Chưa có học viên đặt lịch"} pct={ptUp.length ? 100 : 0} />
      <CStat icon={CI.board} tone={pending.length ? "orange" : "green"} label="Đánh giá cần làm" value={pending.length}
        sub={pending.length ? "Cần đánh giá sớm" : "Tuyệt vời! Không tồn đọng"} pct={pending.length ? 100 : 100} />
      <CStat icon={CI.award} tone="purple" label="Đã hoàn tất đánh giá" value={done.length}
        sub={done.length ? "Tổng số đã xử lý" : "Bắt đầu sau buổi học đầu"}
        pct={done.length + pending.length ? (done.length / (done.length + pending.length)) * 100 : 0} />
    </div>

    <div className="md-grid">
      <section className="md-card">
        <div className="md-card-head"><h2>Lịch dạy hôm nay <span className="cd-count">{schedule.length} ca</span></h2>
          <button type="button" className="md-link" onClick={() => onNavigate("coach-schedule")}>Xem lịch cả tuần →</button></div>
        {schedule.length === 0 ? (
          <div className="cd-empty"><i><Ic d={CI.calx} size={34} /></i><h3>Hôm nay bạn không có buổi dạy nào!</h3>
            <p>Hãy tận dụng thời gian này để nghỉ ngơi hoặc chuẩn bị giáo án cho các buổi sắp tới. Lịch sẽ tự cập nhật khi trung tâm phân ca.</p>
            <button type="button" className="md-cta" onClick={() => onNavigate("coach-schedule")}><Ic d={CI.cal} size={16} /> Xem lịch cả tuần</button></div>
        ) : (
          <ul className="md-timeline">
            {schedule.map((x) => (
              <li key={x.id}>
                <div className="cd-time"><b>{hm(x.start)}</b><span>{x.end ? hm(x.end) : ""}</span></div>
                <div className="md-info">
                  <h3>{x.title} <span className={"md-tag " + (x.kind === "pt" ? "pt" : "cls")}>{x.kind === "pt" ? "PT 1-1" : "Lớp học"}</span></h3>
                  {x.sub && <p>{x.sub}</p>}
                </div>
                <div className="cd-row-end">
                  <span className={"md-chip " + x.st[1]}>{x.st[0]}</span>
                  {x.kind === "class" && <button type="button" className="cd-mini" disabled={!x.canMark} onClick={() => onNavigate("coach-attendance")}>{x.canMark ? "Điểm danh" : "Chưa đến giờ"}</button>}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="md-card">
        <div className="md-card-head"><h2>Đánh giá kỹ năng đang chờ <span className={"cd-count " + (pending.length ? "warn" : "ok")}>{pending.length} chờ đánh giá</span></h2></div>
        {pending.length === 0 ? (
          <div className="cd-empty ok"><i><Ic d={CI.check} size={34} /></i><h3>Tuyệt vời!</h3>
            <p>Bạn đã hoàn thành tất cả đánh giá kỹ năng. Không có hồ sơ nào đang chờ xử lý.</p></div>
        ) : (
          <ul className="cd-list">
            {pending.slice(0, 5).map((a) => (
              <li key={a.id}>
                <span className="cd-avatar">{String(a.memberId).slice(-2)}</span>
                <div><b>Học viên #{a.memberId}</b><small>{sportName.get(Number(a.sportId)) || "Bộ môn #" + a.sportId} · giao ngày {a.createdAt ? new Date(a.createdAt).toLocaleDateString("vi-VN") : "—"}</small></div>
                <button type="button" className="cd-mini" onClick={() => onNavigate("coach-assessments")}>Mở đánh giá</button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>

    <div className="md-grid cd-grid2">
      <section className="md-card">
        <div className="md-card-head"><h2>Buổi PT sắp tới <span className="cd-count">Sắp tới</span></h2>
          <button type="button" className="md-link" onClick={() => onNavigate("coach-pt-sessions")}>Xem tất cả →</button></div>
        {ptUp.length === 0 ? (
          <div className="cd-empty small"><i><Ic d={CI.calx} size={28} /></i><h3>Chưa có buổi PT nào sắp tới</h3>
            <p>Các buổi tập mới sẽ xuất hiện tại đây khi học viên đặt lịch với bạn.</p></div>
        ) : (
          <ul className="md-timeline">
            {ptUp.slice(0, 4).map((b) => {
              const st = PT_STATUS[b.status] || [b.status, "warn"];
              return (
                <li key={b.id}>
                  <div className="md-date"><small>{WD[new Date(b.startTime).getDay()]}</small><b>{new Date(b.startTime).getDate()}</b><span>{hm(b.startTime)}</span></div>
                  <div className="md-info"><h3>{b.memberName || "Học viên"}</h3><p>{b.sportName}{b.durationMinutes ? " · " + b.durationMinutes + " phút" : ""}</p></div>
                  <span className={"md-chip " + st[1]}>{st[0]}</span>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section className="md-card">
        <div className="md-card-head"><h2>Việc cần làm <span className="cd-count">Hôm nay</span></h2><span className="cd-muted">{doneCount}/{tasks.length} hoàn tất</span></div>
        <div className="cd-tasks">
          {tasks.map((t) => (
            <button type="button" key={t.title} className={"cd-task" + (t.ok ? " ok" : "")} onClick={() => onNavigate(t.go)}>
              <i><Ic d={t.ok ? CI.check : t.icon} size={20} /></i>
              <span><b>{t.title}</b><small>{t.text}</small></span>
              <Ic d={CI.chev} size={18} />
            </button>
          ))}
        </div>
      </section>
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
                <span className="md-chip ok">{SESSION_STATUS[x.status] || x.status || "Đã lên lịch"}</span>
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
  const { data, loading, reload } = useLoad(token, role);
  const [title, sub] = TITLES[role] || TITLES.MEMBER;
  const View = { ADMIN: Admin, RECEPTIONIST: Receptionist, COACH: Coach, MEMBER: Member }[role] || Member;

  if (role === "RECEPTIONIST") {
    const pendingCount = data.requests == null ? 0 : list(data.requests).filter((r) => r.status === "PENDING").length;
    return (
      <div className="layout">
        <Sidebar role={role} activePage="dashboard" onLogout={onLogout} onNavigate={onNavigate} badges={{ consultations: pendingCount }} />
        <main className="main rc-main">
          <header className="rc-topbar">
            <nav className="rc-crumb" aria-label="Breadcrumb">
              <span style={{ cursor: "pointer" }} onClick={() => onNavigate("dashboard")}>SPORTS CENTER</span><i>›</i><span>LỄ TÂN</span><i>›</i><b>TỔNG QUAN</b>
            </nav>
            <div className="rc-user">
              <span className="rc-avatar">{initialsOf(data.me?.name)}</span>
              <div><b>{data.me?.name || "Lê Minh Trang"}</b><small>Quầy lễ tân 01</small></div>
            </div>
          </header>
          <div className="rc-content">
            {loading
              ? <div className="queue-state" role="status">Đang tải dữ liệu…</div>
              : <View d={data} onNavigate={onNavigate} token={token} reload={reload} />}
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="dashboard" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main dash-main">
        {role !== "MEMBER" && role !== "COACH" && <><h1>{title}</h1>
        <p className="subtitle">{sub || "Hôm nay: " + today}</p></>}
        {loading ? <div className="queue-state">Đang tải dữ liệu…</div> : <View d={data} onNavigate={onNavigate} />}
      </main>
    </div>
  );
}