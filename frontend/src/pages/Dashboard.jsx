import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import RevenueBars from "../components/RevenueBars";
import { defaultRevenueRange, formatVnd } from "../revenueReport";
import "./Dashboard.css";
import {
  getUsersApi, getSportsApi, getCoachesApi, getConsultationRequestsApi,
  getPublicMembershipPlansApi, getAssignedPtBookingsApi, getMySkillAssessmentAssignmentsApi,
  getMyPtBookingsApi, getMyMemberSportLevelsApi, getMySkillAssessmentsApi,
  getMyConsultationRequestsApi,
  getAdminRevenueReportApi,
  getTrainingClassesApi, getCoachAttendanceSessionsApi,
} from "../api";
import { confirmedEnrollmentCount, upcomingTeachingSessions } from "../coachDashboard.js";

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
    members: memberUsers, sports: getSportsApi, plans: getPublicMembershipPlansApi,
    requests: (t) => getConsultationRequestsApi(t, { size: 100 }),
  },
  COACH: {
    bookings: getAssignedPtBookingsApi,
    assessments: getMySkillAssessmentAssignmentsApi,
    classes: getTrainingClassesApi,
    teachingSessions: getCoachAttendanceSessionsApi,
  },
  MEMBER: {
    bookings: getMyPtBookingsApi, levels: getMyMemberSportLevelsApi,
    assessments: getMySkillAssessmentsApi, requests: getMyConsultationRequestsApi,
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

function Coach({ d, errors = {} }) {
  const up = upcoming(d.bookings);
  const pending = list(d.assessments).filter((a) => !/COMPLETED|CANCEL/i.test(a.status || ""));
  const classes = list(d.classes);
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
            {pending.slice(0, 5).map((a) => (
              <li key={a.id}><span>{a.memberName || a.memberFullName || "Học viên"}</span><em>{a.sportName || a.sport?.name || ""}</em></li>
            ))}
          </ul>
        )}
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

function Member({ d, onNavigate }) {
  const up = upcoming(d.bookings);
  const levels = list(d.levels);
  return (<>
    <Stats items={[
      { label: "Buổi PT đã đặt", value: total(d.bookings), color: "blue" },
      { label: "Sắp diễn ra", value: d.bookings == null ? "—" : up.length, color: "green" },
      { label: "Bộ môn có trình độ", value: total(d.levels), color: "orange" },
      { label: "Lần đánh giá kỹ năng", value: total(d.assessments), color: "blue" },
    ]} />
    <div className="dash-grid wide">
      <Card title="Lịch tập cá nhân sắp tới">
        <BookingList items={up} who={(b) => b.coachName || b.coachFullName || b.coach?.fullName || ""} />
      </Card>
      <Card title="Trình độ của tôi">
        {levels.length === 0 ? <Empty>Bạn chưa được đánh giá trình độ.</Empty> : (
          <ul className="dash-rows">
            {levels.slice(0, 5).map((l, i) => (
              <li key={l.id ?? i}><span>{l.sportName || l.sport?.name || "Bộ môn"}</span><b>{l.level || l.skillLevel || "—"}</b></li>
            ))}
          </ul>
        )}
      </Card>
    </div>
    <Card title="Truy cập nhanh" className="narrow">
      <div className="dash-actions">
        <button type="button" onClick={() => onNavigate("member-pt-booking")}><b>Đặt buổi huấn luyện</b><span>Chọn HLV và giờ tập</span></button>
        <button type="button" onClick={() => onNavigate("member-levels")}><b>Trình độ &amp; đánh giá</b><span>Nhận xét từ HLV</span></button>
        <Link to="/member/membership"><b>Gói thành viên</b><span>Xem và đăng ký gói tập</span></Link>
        <Link to="/bo-mon"><b>Lớp học</b><span>Bộ môn và khóa học</span></Link>
      </div>
    </Card>
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

  return (
    <div className="layout">
      <Sidebar role={role} activePage="dashboard" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main dash-main">
        <h1>{title}</h1>
        <p className="subtitle">{sub || "Hôm nay: " + today}</p>
        {loading ? <div className="queue-state">Đang tải dữ liệu…</div> : <View d={data} errors={errors} onNavigate={onNavigate} token={token} />}
      </main>
    </div>
  );
}
