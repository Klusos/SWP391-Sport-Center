import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import Checkout from "../components/Checkout";
import "./Portal.css";
import { getCurrentUserApi, getPublicMembershipPlansApi, getMembershipsByMemberApi, setAutoRenewApi } from "../api";

const list = (d) => (Array.isArray(d) ? d : d?.content ?? []);
const money = (v) => (v == null ? "—" : Number(v).toLocaleString("vi-VN") + "đ");

export default function MemberMembership({ token, role, onLogout, onNavigate }) {
  const [me, setMe] = useState(null);
  const [plans, setPlans] = useState(null);
  const [mine, setMine] = useState([]);
  const [msg, setMsg] = useState(null);
  const [checkout, setCheckout] = useState(null);
  const fail = (e) => setMsg({ type: "error", text: e.message });

  async function load() {
    try {
      const user = me || await getCurrentUserApi(token);
      setMe(user);
      setMine(list(await getMembershipsByMemberApi(token, user.id).catch(() => [])));
      if (!plans) setPlans(list(await getPublicMembershipPlansApi()));
    } catch (e) { fail(e); setPlans((p) => p || []); }
  }
  useEffect(() => { load(); }, [token]);

  async function toggle(m) {
    setMsg(null);
    try { await setAutoRenewApi(token, m.id, !m.autoRenew); await load(); } catch (e) { fail(e); }
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="member-membership" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main">
        <h1>Gói thành viên</h1>
        <p className="subtitle">Xem gói đang dùng và mua gói mới. Gói có hiệu lực sau khi thanh toán thành công.</p>
        {msg && <div className={"banner " + msg.type} role="status">{msg.text}</div>}

        <section className="dash-card">
          <h2>Gói của tôi</h2>
          {mine.length === 0 ? <p className="dash-empty">Bạn chưa có gói thành viên nào.</p> : (
            <ul className="dash-rows">
              {mine.map((m) => (
                <li key={m.id}>
                  <span><b>{m.planName}</b><br /><small>{m.startDate} → {m.endDate}</small></span>
                  <span><em>{m.status}</em>{" "}
                    {m.status === "ACTIVE" && <button type="button" onClick={() => toggle(m)}>{m.autoRenew ? "Tắt tự gia hạn" : "Bật tự gia hạn"}</button>}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <h2 style={{ margin: "26px 0 14px", fontSize: 18 }}>Các gói đang mở</h2>
        {plans === null ? <div className="queue-state">Đang tải…</div>
          : plans.length === 0 ? <p className="dash-empty">Hiện chưa có gói nào.</p> : (
            <div className="pf-plans">
              {plans.map((p) => (
                <article key={p.id} className="pf-plan">
                  <h2>{p.name}</h2>
                  <strong>{money(p.price)}</strong>
                  <small>Thời hạn: {p.durationMonths} tháng · Giảm khóa học {p.courseDiscountPercent ?? 0}%</small>
                  <p>{p.description}</p>
                  <button type="button" className="pf-primary" onClick={() => setCheckout(p)}>Mua gói</button>
                </article>
              ))}
            </div>
          )}

        {checkout && (
          <Checkout token={token} title={"Gói thành viên: " + checkout.name} purpose="MEMBERSHIP" referenceId={checkout.id}
            lines={[{ label: checkout.name + " (" + checkout.durationMonths + " tháng)", amount: checkout.price }]} total={checkout.price}
            allowAutoRenew onClose={() => setCheckout(null)} onDone={load} />
        )}
      </main>
    </div>
  );
}