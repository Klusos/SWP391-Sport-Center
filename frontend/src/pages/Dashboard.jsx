import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import "./Dashboard.css";
import {
  getUsersApi, getSportsApi, getCoachesApi, getConsultationRequestsApi,
  getPublicMembershipPlansApi, getAssignedPtBookingsApi, getMySkillAssessmentAssignmentsApi,
  getMyPtBookingsApi, getMyMemberSportLevelsApi, getMySkillAssessmentsApi,
  getMyConsultationRequestsApi,
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
    members: memberUsers, sports: getSportsApi, plans: getPublicMembershipPlansApi,
    requests: (t) => getConsultationRequestsApi(t, { size: 100 }),
  },
  COACH: { bookings: getAssignedPtBookingsApi, assessments: getMySkillAssessmentAssignmentsApi },
  MEMBER: {
    bookings: getMyPtBookingsApi, levels: getMyMemberSportLevelsApi,
    assessments: getMySkillAssessmentsApi, requests: getMyConsultationRequestsApi,
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

function Receptionist({ d, onNavigate }) {
  const actions = [
    ["Đăng ký thành viên", "Mới", "register"], ["Yêu cầu tư vấn", "Tiếp nhận", "consultations"],
    ["Danh sách thành viên", "Tra cứu", "members"], ["Gói hội viên", "Bảng giá", "membership-plans"],
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
        <Link to="/tuyen-sinh"><b>Gói thành viên</b><span>Xem và đăng ký gói tập</span></Link>
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
  const { data, loading } = useLoad(token, role);
  const [title, sub] = TITLES[role] || TITLES.MEMBER;
  const View = { ADMIN: Admin, RECEPTIONIST: Receptionist, COACH: Coach, MEMBER: Member }[role] || Member;

  return (
    <div className="layout">
      <Sidebar role={role} activePage="dashboard" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main dash-main">
        <h1>{title}</h1>
        <p className="subtitle">{sub || "Hôm nay: " + today}</p>
        {loading ? <div className="queue-state">Đang tải dữ liệu…</div> : <View d={data} onNavigate={onNavigate} />}
      </main>
    </div>
  );
}