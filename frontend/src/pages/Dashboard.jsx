import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import RevenueBars from "../components/RevenueBars";
import { defaultRevenueRange, formatVnd } from "../revenueReport";
import { upcomingPtBookings } from "../ptBooking";
import { isMembershipEffective, todayInVietnam } from "../membershipDates";
import "./Dashboard.css";
import "./Membertheme.css";
import {
  getUsersApi, getSportsApi, getCoachesApi, getConsultationRequestsApi,
  getPublicMembershipPlansApi, getAssignedPtBookingsApi, getMySkillAssessmentAssignmentsApi,
    getMyPtBookingsApi, getMyMemberSportLevelsApi,
    getCurrentUserApi, getMembershipsByMemberApi, getMyClassSessionsApi,
  getMyAttendanceApi, getMyActionablePendingPaymentSummaryApi, getAdminRevenueReportApi,
  getTrainingClassesApi, getCoachAttendanceSessionsApi,
} from "../api";
import { coachAssessmentSummary, confirmedEnrollmentCount, pendingCoachAssessments, upcomingTeachingSessions } from "../coachDashboard.js";
import { activeMembershipPlanRows, pendingConsultationRows, receptionistDashboardMetrics, searchReceptionistMembers } from "../receptionistDashboard.js";
import { visibleAdminDashboardSports } from "../adminDashboard.js";
import "./ReceptionistDashboard.css";

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
    me: getCurrentUserApi, members: memberUsers, sports: getSportsApi, plans: getPublicMembershipPlansApi,
    pendingRequests: (t) => getConsultationRequestsApi(t, { status: "PENDING", page: 0, size: 5 }),
  },
  COACH: {
    bookings: getAssignedPtBookingsApi,
    assessments: getMySkillAssessmentAssignmentsApi,
    sports: getSportsApi,
    classes: getTrainingClassesApi,
    teachingSessions: getCoachAttendanceSessionsApi,
  },
  MEMBER: {
    bookings: getMyPtBookingsApi, sessions: getMyClassSessionsApi,
    levels: getMyMemberSportLevelsApi, attendance: getMyAttendanceApi,
    actionablePayments: getMyActionablePendingPaymentSummaryApi,
    profile: async (t) => {
      const user = await getCurrentUserApi(t);
      const memberships = list(await getMembershipsByMemberApi(t, user.id));
      return { user, memberships };
    },
  },
};

function useLoad(token, role) {
  const [state, setState] = useState({ data: {}, errors: {}, loading: true });
  useEffect(() => {
    let off = false;
    const loaders = LOADERS[role] || {};
    const keys = Object.keys(loaders);
    Promise.allSettled(keys.map((k) => loaders[k](token))).then((rs) => {
      if (off) return;
      const data = {};
      const errors = {};
      keys.forEach((k, i) => {
        if (rs[i].status === "fulfilled") data[k] = rs[i].value;
        else {
          data[k] = null;
          errors[k] = rs[i].reason?.message || "Không thể tải dữ liệu.";
        }
      });
      setState({ data, errors, loading: false });
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

function Admin({ d, token, errors = {} }) {
  const [showAllSports, setShowAllSports] = useState(false);
  const sports = list(d.sports);
  const visibleSports = visibleAdminDashboardSports(sports, showAllSports);
  const reqs = list(d.requests);
  const by = (s) => reqs.filter((r) => r.status === s).length;
  const rows = [["Chờ xử lý", by("PENDING"), "orange"], ["Đang xử lý", by("IN_PROGRESS"), "blue"], ["Hoàn thành", by("COMPLETED"), "green"]];
  const sum = rows.reduce((a, r) => a + r[1], 0) || 1;
  return <div className="admin-dashboard">
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
      {errors.sports ? <p className="dash-error" role="alert">Không tải được danh sách bộ môn: {errors.sports}</p> : sports.length === 0 ? <Empty>Chưa có bộ môn nào.</Empty> : (
        <ul className="dash-rows">
          {visibleSports.map((s) => <li key={s.id}><span>{s.name}</span><em>{s.status}</em></li>)}
        </ul>
      )}
      {!errors.sports && sports.length > 6 && <button
        className="admin-sports-toggle"
        type="button"
        aria-expanded={showAllSports}
        onClick={() => setShowAllSports((expanded) => !expanded)}
      >{showAllSports ? "Thu gọn" : `Xem tất cả (${sports.length})`}</button>}
    </Card>
  </div>;
}

const RECEPTIONIST_ICON = {
  members: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75",
  plans: "M3 7h18 M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M7 12h4 M7 16h7",
  sports: "M12 2 4 6v6c0 5 3.4 8.5 8 10 4.6-1.5 8-5 8-10V6l-8-4z M8 12l2.5 2.5L16 9",
  pending: "M12 8v4l3 2 M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20",
  search: "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16 M21 21l-4.35-4.35",
  consult: "M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z",
  sales: "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1z M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8 M12 17.5v-11",
  arrow: "M5 12h14 M13 6l6 6-6 6",
};

function ReceptionistIcon({ name, size = 18 }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {RECEPTIONIST_ICON[name].split(" M").map((path, index) => <path key={index} d={(index ? "M" : "") + path} />)}
  </svg>;
}

function Receptionist({ d, errors = {}, onNavigate, loading }) {
  const [query, setQuery] = useState("");
  const [searchSubmitted, setSearchSubmitted] = useState(false);
  const memberResults = searchReceptionistMembers(d.members, query);
  const metrics = receptionistDashboardMetrics(d);
  const requests = pendingConsultationRows(d.pendingRequests);
  const plans = activeMembershipPlanRows(d.plans);
  const dateLabel = new Date().toLocaleDateString("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric", timeZone: "Asia/Ho_Chi_Minh" });
  const kpis = [
    ["Tổng thành viên", metrics.members, errors.members ? "Không tải được dữ liệu" : "Tài khoản hội viên", "members", "blue"],
    ["Gói hội viên", metrics.activePlans, errors.plans ? "Không tải được dữ liệu" : "Đang áp dụng", "plans", "green"],
    ["Bộ môn", metrics.sports, errors.sports ? "Không tải được dữ liệu" : "Danh mục hiện có", "sports", "blue"],
    ["Yêu cầu chờ xử lý", metrics.pendingConsultations, errors.pendingRequests ? "Không tải được dữ liệu" : "Đang chờ tiếp nhận", "pending", metrics.pendingConsultations > 0 ? "amber" : "green"],
  ];
  const actions = [
    ["Yêu cầu tư vấn", "Xem và tiếp nhận yêu cầu", "consultations", "consult"],
    ["Bán gói hội viên", "Tạo thanh toán tại quầy", "membership-sales", "sales"],
    ["Danh sách thành viên", "Tra cứu hồ sơ hội viên", "members", "members"],
  ];

  return <div className="rc-content">
    <header className="rc-head">
      <div><p className="rc-eyebrow">SPORTCENTER / TIẾP NHẬN</p><h1>Tổng quan lễ tân</h1><p>Quản lý thành viên, tư vấn và gói hội viên.</p></div>
      <span className="rc-date"><ReceptionistIcon name="pending" /><b>{dateLabel}</b><i aria-hidden="true" /><small>Hôm nay</small></span>
    </header>

    <section className="rc-kpis" aria-label="Chỉ số nhanh" aria-busy={loading}>
      {kpis.map(([label, value, note, icon, tone]) => <article key={label} className="rc-kpi">
        <header><span>{label}</span><i className={`rc-tile ${tone}`}><ReceptionistIcon name={icon} size={21} /></i></header>
        <strong>{loading ? "—" : value ?? "—"}</strong><p className={errors[icon === "pending" ? "pendingRequests" : icon] ? "error" : tone}><span aria-hidden="true" />{note}</p>
      </article>)}
    </section>

    <section className="rc-actions" aria-label="Thao tác nhanh">
      {actions.map(([title, note, page, icon]) => <button key={page} type="button" className="rc-action" onClick={() => onNavigate(page)}>
        <i className="rc-tile blue"><ReceptionistIcon name={icon} /></i><div><b>{title}</b><small>{note}</small></div><ReceptionistIcon name="arrow" />
      </button>)}
    </section>

    <div className="rc-grid">
      <section className="rc-card">
        <div className="rc-card-head"><div><h2>Tìm kiếm thành viên</h2><p>Tìm trong danh sách thành viên hiện có.</p></div>
          <button type="button" className="rc-link" onClick={() => onNavigate("members")}>Mở danh sách <ReceptionistIcon name="arrow" size={14} /></button></div>
        <form className="rc-search" onSubmit={(event) => { event.preventDefault(); setSearchSubmitted(true); }}>
          <label className="rc-input-wrap"><ReceptionistIcon name="search" /><input value={query} onChange={(event) => { setQuery(event.target.value); setSearchSubmitted(false); }} placeholder="Tên, số điện thoại, email hoặc mã hội viên" aria-label="Tìm thành viên" /></label>
          <button type="submit" className="rc-btn-primary">Tìm kiếm</button>
        </form>
        {searchSubmitted && <div className="rc-search-results" aria-live="polite">
          {errors.members ? <p className="rc-empty">Không thể tìm kiếm khi danh sách thành viên chưa tải được.</p>
            : memberResults.length === 0 ? <p className="rc-empty">Không tìm thấy thành viên phù hợp.</p>
              : <><p className="rc-search-count">Tìm thấy {memberResults.length} thành viên</p><ul className="rc-quick-list">
                {memberResults.slice(0, 5).map((member) => <li key={member.id}><b>{member.name || `Thành viên #${member.id}`}</b><span>#{member.id}{member.phone ? ` · ${member.phone}` : ""}</span></li>)}
              </ul></>}
        </div>}
      </section>

      <section className="rc-card">
        <div className="rc-card-head"><h2>Yêu cầu tư vấn đang chờ <span className="rc-count amber">{loading ? "—" : metrics.pendingConsultations ?? "—"}</span></h2>
          <button type="button" className="rc-link" onClick={() => onNavigate("consultations")}>Xem tất cả <ReceptionistIcon name="arrow" size={14} /></button></div>
        {errors.pendingRequests ? <p className="rc-empty" role="alert">Không tải được danh sách yêu cầu tư vấn.</p>
          : loading ? <p className="rc-empty" role="status">Đang tải yêu cầu tư vấn…</p>
            : requests.length === 0 ? <p className="rc-empty">Không có yêu cầu nào đang chờ.</p>
              : <ul className="rc-requests">{requests.map((request, index) => <li key={request.id}>
                <span className={`rc-avatar${index % 2 ? " alt" : ""}`}>{String(request.name || "?").trim().split(/\s+/).slice(-2).map((word) => word[0]).join("").toUpperCase() || "?"}</span>
                <div className="rc-req-main"><div><b>{request.name || `Yêu cầu #${request.id}`}</b>{request.sportName && <em>{request.sportName}</em>}</div>
                  <p>{request.phone && <span><ReceptionistIcon name="members" size={14} />{request.phone}</span>}<i aria-hidden="true" /><span>{request.createdAt ? fmt(request.createdAt) : "Chưa có ngày gửi"}</span></p></div>
              </li>)}</ul>}
      </section>
    </div>

    <section className="rc-card">
      <div className="rc-card-head"><div><h2>Gói hội viên đang áp dụng</h2><p>Giá và quyền lợi lấy từ danh mục hiện tại.</p></div>
        <button type="button" className="rc-link" onClick={() => onNavigate("membership-sales")}>Mở bán gói <ReceptionistIcon name="arrow" size={14} /></button></div>
      {errors.plans ? <p className="rc-empty" role="alert">Không tải được danh sách gói hội viên.</p>
        : loading ? <p className="rc-empty" role="status">Đang tải gói hội viên…</p>
          : plans.length === 0 ? <p className="rc-empty">Chưa có gói hội viên đang áp dụng.</p>
            : <ul className="rc-plans">{plans.slice(0, 3).map((plan) => <li key={plan.id}>
              <div><b>{plan.name}</b><p>{plan.description || "Chưa có mô tả."}</p></div>
              <span>{formatVnd(plan.price)} <small>{Number(plan.durationMonths) === 12 ? "/ năm" : `/ ${plan.durationMonths} tháng`}</small></span>
            </li>)}</ul>}
    </section>
  </div>;
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

function Coach({ d, errors = {} }) {
  const up = upcoming(d.bookings);
  const pending = pendingCoachAssessments(d.assessments);
  const classes = list(d.classes);
  const sports = list(d.sports);
  const teachingSessions = upcomingTeachingSessions(d.teachingSessions);
  const upcomingPt = up;
  return (<>
    <Stats items={[
      { label: "Lớp được phân công", value: total(d.classes), color: "blue" },
      { label: "Lượt đăng ký lớp đã xác nhận", value: d.classes == null ? "—" : confirmedEnrollmentCount(classes), color: "green" },
      { label: "Buổi dạy sắp tới", value: d.teachingSessions == null ? "—" : teachingSessions.length, color: "orange" },
      { label: "Buổi PT sắp tới", value: d.bookings == null ? "—" : upcomingPt.length, color: "blue" },
      { label: "Đánh giá cần làm", value: d.assessments == null ? "—" : pending.length, color: "orange" },
    ]} />
    <div className="dash-grid wide">
      <Card title="Lớp được phân công" note={errors.classes ? "" : `${classes.length} lớp`}>
        {errors.classes ? <p className="dash-error" role="alert">Không tải được danh sách lớp: {errors.classes}</p>
          : classes.length === 0 ? <Empty>Chưa có lớp nào được phân công.</Empty>
            : <ul className="dash-rows">{classes.slice(0, 6).map((item) => <li key={item.classId}>
              <span><b>{item.className || `Lớp #${item.classId}`}</b><br /><small>{[item.courseName, item.sportName].filter(Boolean).join(" · ")}</small></span>
              <em>{Number(item.confirmedMembers || 0)} học viên</em>
            </li>)}</ul>}
        <Link className="coach-dashboard-link" to="/coach/training">Mở quản lý giáo án</Link>
      </Card>
      <Card title="Lịch dạy sắp tới" note="Lớp học">
        {errors.teachingSessions ? <p className="dash-error" role="alert">Không tải được lịch dạy: {errors.teachingSessions}</p>
          : <TeachingSessionList items={teachingSessions} />}
        <Link className="coach-dashboard-link" to="/coach/schedule">Mở lịch trình</Link>
      </Card>
      <Card title="Buổi PT sắp tới">
        {errors.bookings ? <p className="dash-error" role="alert">Không tải được lịch PT: {errors.bookings}</p>
          : <BookingList items={upcomingPt} who={(b) => b.memberName || b.memberFullName || b.member?.fullName || ""} />}
        <Link className="coach-dashboard-link" to="/coach/pt-sessions">Mở các buổi PT</Link>
      </Card>
      <Card title="Đánh giá kỹ năng cần làm">
        {errors.assessments ? <p className="dash-error" role="alert">Không tải được đánh giá: {errors.assessments}</p>
          : pending.length === 0 ? <Empty>Không có đánh giá nào đang chờ.</Empty> : (
          <ul className="dash-rows">
            {pending.slice(0, 5).map((a) => {
              const summary = coachAssessmentSummary(a, sports);
              return <li className="dash-assessment-row" key={a.id}>
                <span className="dash-assessment-info">
                  <b>{summary.member}</b>
                  <small>{summary.sport} · Assessment #{a.id}</small>
                  <small>{summary.assignment}</small>
                </span>
                <em className="dash-assessment-status">{summary.status}</em>
              </li>;
            })}
          </ul>
        )}
        {errors.sports && pending.length > 0 && <p className="dash-assessment-note">Không tải được tên bộ môn; đang hiển thị mã bộ môn.</p>}
        <Link className="coach-dashboard-link" to="/coach/skill-assessments">Mở trang đánh giá kỹ năng</Link>
      </Card>
    </div>
  </>);
}

function TeachingSessionList({ items }) {
  if (items.length === 0) return <Empty>Không có buổi dạy sắp tới.</Empty>;
  return <ul className="dash-sessions">{items.slice(0, 5).map((session) => <li key={session.classSessionId}>
    <div><small>{fmt(session.startTime)}</small><b>{session.courseClassName || "Lớp học"}</b></div>
    <span>{[session.courseName, session.sportName].filter(Boolean).join(" · ")}</span>
    <em>{session.sessionStatus || "SCHEDULED"}</em>
  </li>)}</ul>;
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

function Member({ d, errors = {}, onNavigate }) {
  const user = d.profile?.user;
  const memberships = d.profile?.memberships || [];
  const active = memberships.filter((membership) => isMembershipEffective(membership, todayInVietnam()))
    .sort((a, b) => String(b.endDate).localeCompare(String(a.endDate)))[0];
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

  const scheduledClassIds = new Set(list(d.sessions).map((session) => session.classId).filter((id) => id != null));
  const att = list(d.attendance).map((a) => ({ ...a, when: pick(a, "startTime", "sessionStartTime", "markedAt", "date", "createdAt"), name: pick(a, "className", "sessionName", "courseName") }))
    .sort((a, b) => new Date(b.when) - new Date(a.when));
  const month = att.filter((a) => a.when && new Date(a.when).getMonth() === now.getMonth() && new Date(a.when).getFullYear() === now.getFullYear());
  const attended = month.filter((a) => /PRESENT|ON_TIME|LATE/i.test(a.status || "")).length;
  const actionablePayments = d.actionablePayments;
  const pendingBillCount = actionablePayments?.count;
  const pendingBillTotal = actionablePayments?.totalAmount;
  const levels = list(d.levels);

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
        <h2>{errors.profile ? "Không tải được trạng thái gói hội viên" : "Bạn chưa có gói thành viên"}</h2>
        <p>{errors.profile ? errors.profile : "Chọn một gói để mở khóa ưu đãi khóa học và huấn luyện viên cá nhân."}</p>
        <div className="md-hero-bottom"><span /><button type="button" className="md-ghost" onClick={() => onNavigate("member-membership")}>Xem các gói</button></div>
      </>)}
    </section>

    <div className="md-stats">
      <MStat icon={ICON.pt} tone="blue" label="Buổi PT sắp tới" value={ptUp.length}
        sub={ptUp[0] ? "Buổi tiếp theo: " + hm(ptUp[0].startTime) + " " + WD[new Date(ptUp[0].startTime).getDay()] : "Chưa có buổi nào"} />
      <MStat icon={ICON.cls} tone="green" label="Lớp có buổi học" value={scheduledClassIds.size}
        sub={scheduledClassIds.size ? "Từ các buổi học đã đăng ký" : "Chưa có buổi học đăng ký"} />
      <MStat icon={ICON.ok} tone="purple" label="Điểm danh tháng này" value={<>{attended}<em> / {month.length}</em></>}
        sub={month.length ? "Đạt " + Math.round((attended / month.length) * 100) + "% số buổi" : "Chưa có buổi nào trong tháng"} />
      <MStat icon={ICON.bill} tone={pendingBillCount > 0 ? "orange" : "gray"} label="Khoản có thể thanh toán"
        value={actionablePayments == null ? "—" : <>{pendingBillCount}{pendingBillCount > 0 && <em> ({vnd(pendingBillTotal)})</em>}</>}
        sub={errors.actionablePayments ? "Không tải được trạng thái thanh toán: " + errors.actionablePayments
          : pendingBillCount > 0 ? "Gồm các khoản còn hạn của gói, khóa học hoặc PT" : "Không có khoản nào còn đủ điều kiện thanh toán"} />
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
  const { data, errors, loading } = useLoad(token, role);
  const [title, sub] = TITLES[role] || TITLES.MEMBER;
  const View = { ADMIN: Admin, RECEPTIONIST: Receptionist, COACH: Coach, MEMBER: Member }[role] || Member;

  if (role === "RECEPTIONIST") {
    return <div className="layout">
      <Sidebar role={role} activePage="dashboard" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main rc-main">
        <header className="rc-topbar">
          <nav className="rc-crumb" aria-label="Điều hướng"><span>SPORTCENTER</span><i>›</i><span>LỄ TÂN</span><i>›</i><b>TỔNG QUAN</b></nav>
          <div className="rc-user"><span className="rc-avatar">{String(data.me?.name || "").trim().split(/\s+/).slice(-2).map((word) => word[0]).join("").toUpperCase() || "—"}</span>
            <div><b>{data.me?.name || (errors.me ? "Không tải được tài khoản" : "Quầy tiếp nhận")}</b><small>Nhân viên lễ tân</small></div></div>
        </header>
        <Receptionist d={data} errors={errors} onNavigate={onNavigate} loading={loading} />
      </main>
    </div>;
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="dashboard" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main dash-main">
        {role !== "MEMBER" && <><h1>{title}</h1>
        <p className="subtitle">{sub || "Hôm nay: " + today}</p></>}
        {loading ? <div className="queue-state">Đang tải dữ liệu…</div> : <View d={data} errors={errors} onNavigate={onNavigate} token={token} />}
      </main>
    </div>
  );
}
