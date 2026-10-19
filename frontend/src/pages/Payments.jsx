import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import Checkout, { STATUS_LABEL, statusClass } from "../components/Checkout";
import "./Portal.css";
import "./Dashboard.css";
import {
  getUsersApi, getPublicMembershipPlansApi, getMyPaymentsApi, getPaymentsByMemberApi,
  cancelPaymentApi, processCounterPaymentApi,
} from "../api";

const list = (d) => (Array.isArray(d) ? d : d?.content ?? []);
const money = (v) => Number(v ?? 0).toLocaleString("vi-VN") + "đ";
const when = (s) => (s ? new Date(s).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" }) : "—");
const PURPOSE = { MEMBERSHIP: "Gói thành viên", COURSE_REGISTRATION: "Đăng ký lớp", PT_BOOKING: "Buổi PT" };
const METHOD = { ONLINE: "Online", COUNTER: "Tại quầy" };

export default function Payments({ token, role, onLogout, onNavigate }) {
  const isStaff = role !== "MEMBER";
  const [members, setMembers] = useState([]);
  const [plans, setPlans] = useState([]);
  const [memberId, setMemberId] = useState("");
  const [planId, setPlanId] = useState("");
  const [rows, setRows] = useState(isStaff ? [] : null);
  const [msg, setMsg] = useState(null);
  const [busy, setBusy] = useState(false);
  const [sell, setSell] = useState(null);
  const fail = (e) => setMsg({ type: "error", text: e.message });

  const load = (id = memberId) => {
    setRows(null);
    return (isStaff ? getPaymentsByMemberApi(token, id) : getMyPaymentsApi(token))
      .then((d) => setRows(list(d))).catch((e) => { fail(e); setRows([]); });
  };

  useEffect(() => {
    if (!isStaff) { load(); return; }
    getUsersApi(token).then((d) => setMembers(list(d).filter((u) => u.role === "MEMBER"))).catch(fail);
    getPublicMembershipPlansApi().then((d) => setPlans(list(d))).catch(() => {});
  }, [token]);
  useEffect(() => { if (isStaff) { if (memberId) load(memberId); else setRows([]); } }, [memberId]);

  async function act(fn, ok) {
    setBusy(true); setMsg(null);
    try { await fn(); setMsg({ type: "success", text: ok }); await load(); } catch (e) { fail(e); } finally { setBusy(false); }
  }
  const counter = (p, success) => act(() => processCounterPaymentApi(token, p.id, { success, note: success ? "Đã thu tiền tại quầy" : "Không thu được tiền" }), success ? "Đã xác nhận thu tiền." : "Đã ghi nhận không thu được.");
  const cancel = (p) => window.confirm("Hủy hóa đơn " + p.transactionCode + "?") && act(() => cancelPaymentApi(token, p.id), "Đã hủy hóa đơn.");

  const paid = (rows || []).filter((p) => p.status === "SUCCESS").reduce((a, p) => a + Number(p.amount || 0), 0);
  const waiting = (rows || []).filter((p) => p.status === "PENDING").length;
  const plan = plans.find((p) => String(p.id) === planId);

  return (
    <div className="layout">
      <Sidebar role={role} activePage={isStaff ? "payments" : "member-payments"} onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main">
        <h1>Hóa đơn</h1>
        <p className="subtitle">{isStaff ? "Tra cứu hóa đơn, xác nhận thu tiền tại quầy và bán gói cho thành viên." : "Các hóa đơn bạn đã tạo khi mua gói, đăng ký lớp hoặc đặt buổi PT."}</p>
        {msg && <div className={"banner " + msg.type} role="status">{msg.text}</div>}

        {isStaff && (
          <div className="queue-toolbar">
            <div className="field" style={{ minWidth: 300, margin: 0 }}>
              <label htmlFor="payMember">Thành viên</label>
              <select id="payMember" value={memberId} onChange={(e) => setMemberId(e.target.value)}>
                <option value="">— Chọn thành viên —</option>
                {members.map((m) => <option key={m.id} value={m.id}>{m.name} ({m.email})</option>)}
              </select>
            </div>
            {memberId && (<>
              <div className="field" style={{ minWidth: 240, margin: 0 }}>
                <label htmlFor="payPlan">Bán gói</label>
                <select id="payPlan" value={planId} onChange={(e) => setPlanId(e.target.value)}>
                  <option value="">— Chọn gói —</option>
                  {plans.map((p) => <option key={p.id} value={p.id}>{p.name} – {money(p.price)}</option>)}
                </select>
              </div>
              <button type="button" className="queue-refresh" disabled={!plan} onClick={() => setSell(plan)}>Tạo hóa đơn</button>
            </>)}
          </div>
        )}

        {(!isStaff || memberId) && (<>
          <div className="dash-stats" style={{ gridTemplateColumns: "repeat(3, minmax(0, 1fr))" }}>
            <div className="dash-stat"><div><span>Đã thanh toán</span><strong>{money(paid)}</strong></div><i className="dash-dot green" /></div>
            <div className="dash-stat"><div><span>Hóa đơn chờ thanh toán</span><strong>{waiting}</strong></div><i className="dash-dot orange" /></div>
            <div className="dash-stat"><div><span>Tổng hóa đơn</span><strong>{rows ? rows.length : "—"}</strong></div><i className="dash-dot" /></div>
          </div>
          {rows === null ? <div className="queue-state">Đang tải…</div> : rows.length === 0 ? <div className="queue-state">Chưa có hóa đơn nào.</div> : (
            <div className="queue-table-wrap">
              <table className="queue-table">
                <thead><tr><th>Mã hóa đơn</th><th>Nội dung</th><th>Số tiền</th><th>Hình thức</th><th>Trạng thái</th><th>Tạo lúc</th><th>Thanh toán lúc</th><th /></tr></thead>
                <tbody>
                  {rows.map((p) => (
                    <tr key={p.id}>
                      <td>{p.transactionCode}</td><td>{PURPOSE[p.purpose] || p.purpose}</td>
                      <td>{money(p.amount)}</td><td>{METHOD[p.method] || p.method}</td>
                      <td><span className={statusClass(p.status)}>{STATUS_LABEL[p.status] || p.status}</span></td>
                      <td>{when(p.createdAt)}</td><td>{when(p.paidAt)}</td>
                      <td>{p.status === "PENDING" && (
                        <div className="adm-actions" style={{ display: "flex", gap: 6 }}>
                          {isStaff && p.method === "COUNTER" && <button type="button" disabled={busy} onClick={() => counter(p, true)}>Đã thu tiền</button>}
                          {isStaff && p.method === "COUNTER" && <button type="button" disabled={busy} onClick={() => counter(p, false)}>Không thu được</button>}
                          <button type="button" disabled={busy} onClick={() => cancel(p)}>Hủy</button>
                        </div>)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>)}

        {sell && (
          <Checkout token={token} staff memberId={Number(memberId)} title={"Bán gói cho thành viên"} purpose="MEMBERSHIP" referenceId={sell.id}
            lines={[{ label: sell.name + " (" + sell.durationMonths + " tháng)", amount: sell.price }]} total={sell.price}
            allowAutoRenew onClose={() => setSell(null)} onDone={() => load()} />
        )}
      </main>
    </div>
  );
}