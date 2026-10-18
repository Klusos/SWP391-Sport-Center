import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import { getCurrentUserApi, getPublicMembershipPlansApi, getMembershipsByMemberApi } from "../api";
const list = (d) => Array.isArray(d) ? d : d?.content ?? [];
const money = (v) => v == null ? "—" : Number(v).toLocaleString("vi-VN")+"đ";
export default function MemberMembership({ token, role, onLogout, onNavigate }) {
  const [plans, setPlans] = useState(null), [mine, setMine] = useState(null), [error, setError] = useState("");
  useEffect(() => { let active = true; Promise.all([getCurrentUserApi(token), getPublicMembershipPlansApi()]).then(async ([user, available]) => {
    const memberships = await getMembershipsByMemberApi(token, user.id); if (active) { setMine(list(memberships)); setPlans(list(available)); }
  }).catch((e) => { if (active) { setError(e.message || "Không tải được thông tin hội viên."); setMine([]); setPlans([]); } }); return () => { active = false; }; }, [token]);
  return <div className="layout"><Sidebar role={role} activePage="member-membership" onLogout={onLogout} onNavigate={onNavigate}/><main className="main">
    <h1>Gói thành viên</h1><p className="subtitle">Theo dõi thông tin gói thành viên và xem các gói đang được cung cấp.</p>
    {error && <div className="banner error" role="alert">{error}</div>}
    <section className="dash-card"><h2>Gói của tôi</h2>{mine === null ? <div className="queue-state">Đang tải…</div> : mine.length === 0 ? <p className="dash-empty">Bạn chưa có thông tin gói thành viên.</p> :
      <ul className="dash-rows">{mine.map((m) => <li key={m.id}><span><b>{m.planName || "Gói thành viên"}</b><br/><small>{m.startDate || "—"} → {m.endDate || "—"}</small></span><em>{m.status || "—"}</em></li>)}</ul>}</section>
    <p className="banner info" role="note">Đăng ký, thanh toán và gia hạn gói chưa được thực hiện tại trang này.</p>
    <h2 style={{margin:"26px 0 14px",fontSize:18}}>Các gói đang mở</h2>
    {plans === null ? <div className="queue-state">Đang tải…</div> : plans.length === 0 ? <p className="dash-empty">Hiện chưa có gói nào.</p> :
      <div className="pf-plans">{plans.map((p) => <article key={p.id} className="pf-plan"><h2>{p.name}</h2><strong>{money(p.price)}</strong><small>Thời hạn: {p.durationMonths} tháng · Giảm khóa học {p.courseDiscountPercent ?? 0}%</small>{p.description && <p>{p.description}</p>}</article>)}</div>}
  </main></div>;
}