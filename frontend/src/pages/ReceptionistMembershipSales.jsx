import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  createPaymentApi,
  getMembershipsByMemberApi,
  getPublicMembershipPlansApi,
  getUsersApi,
  processCounterPaymentApi,
} from "../api";
import "./ReceptionistMembershipSales.css";

const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];
const money = (value) => value == null ? "Chưa có giá" : Number(value).toLocaleString("vi-VN") + " ₫";

export default function ReceptionistMembershipSales({ token, role, onLogout, onNavigate }) {
  const [members, setMembers] = useState([]);
  const [plans, setPlans] = useState([]);
  const [memberId, setMemberId] = useState("");
  const [planId, setPlanId] = useState("");
  const [query, setQuery] = useState("");
  const [membershipData, setMembershipData] = useState(null);
  const [pendingPayment, setPendingPayment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [usersResult, plansResult] = await Promise.all([getUsersApi(token), getPublicMembershipPlansApi()]);
      setMembers(rows(usersResult).filter((user) => user?.role === "MEMBER" && user?.status === "ACTIVE"));
      const activePlans = rows(plansResult).filter((plan) => plan?.status === "ACTIVE");
      setPlans(activePlans);
      setPlanId((current) => activePlans.some((plan) => String(plan.id) === current) ? current : "");
    } catch (requestError) {
      setError(requestError.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403
          ? "Tài khoản không có quyền hỗ trợ mua gói hội viên."
          : requestError.message || "Không tải được danh sách thành viên hoặc gói hội viên.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    let current = true;
    Promise.resolve().then(() => current && load());
    return () => { current = false; };
  }, [load]);
  useEffect(() => {
    if (!memberId) return undefined;
    let current = true;
    getMembershipsByMemberApi(token, memberId)
      .then((result) => { if (current) setMembershipData({ memberId, rows: rows(result) }); })
      .catch((requestError) => { if (current) setError(requestError.message || "Không tải được thông tin gói hiện tại."); });
    return () => { current = false; };
  }, [memberId, token]);

  const visibleMembers = useMemo(() => {
    const term = query.trim().toLocaleLowerCase("vi");
    return members.filter((member) => !term || [member.id, member.name].some((value) => String(value ?? "").toLocaleLowerCase("vi").includes(term)));
  }, [members, query]);
  const selectedMember = members.find((member) => String(member.id) === memberId) || null;
  const selectedPlan = plans.find((plan) => String(plan.id) === planId) || null;
  const memberMemberships = membershipData?.memberId === memberId ? membershipData.rows : null;
  const activeMemberships = (memberMemberships || []).filter((membership) => membership.status === "ACTIVE");

  async function createPendingPayment(event) {
    event.preventDefault();
    if (saving || !selectedMember || !selectedPlan || pendingPayment) return;
    setSaving(true); setError(""); setNotice("");
    try {
      const payment = await createPaymentApi(token, {
        memberId: Number(selectedMember.id),
        method: "COUNTER",
        purpose: "MEMBERSHIP",
        referenceId: Number(selectedPlan.id),
        autoRenew: false,
      });
      setPendingPayment(payment);
      setNotice(`Đã tạo thanh toán tại quầy #${payment.id} ở trạng thái ${payment.status}. Chưa kích hoạt gói.`);
    } catch (requestError) {
      setError(requestError.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403
          ? "Tài khoản không được phép tạo thanh toán cho thành viên này."
          : requestError.message || "Không thể tạo thanh toán membership.");
    } finally { setSaving(false); }
  }

  async function processPayment(success) {
    if (!pendingPayment || saving) return;
    const action = success ? "xác nhận đã nhận đủ khoản thanh toán" : "ghi nhận thanh toán thất bại";
    if (!window.confirm(`Bạn có chắc muốn ${action} cho thanh toán #${pendingPayment.id}?`)) return;
    setSaving(true); setError(""); setNotice("");
    try {
      const result = await processCounterPaymentApi(token, pendingPayment.id, success,
        success ? "Front desk payment confirmed" : "Front desk payment not received");
      setPendingPayment(result);
      if (result.status === "SUCCESS") {
        setNotice(`Thanh toán #${result.id} thành công. Gói hội viên đã được kích hoạt hoặc gia hạn.`);
        const updated = await getMembershipsByMemberApi(token, selectedMember.id);
        setMembershipData({ memberId: String(selectedMember.id), rows: rows(updated) });
      } else if (result.status === "CANCELLED") {
        setNotice(`Thanh toán #${result.id} đã được ghi nhận thất bại. Gói hội viên chưa được kích hoạt.`);
      } else {
        setError(`Thanh toán kết thúc với trạng thái ${result.status || "không xác định"}. Vui lòng kiểm tra lịch sử thanh toán.`);
      }
    } catch (requestError) {
      setError(requestError.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403
          ? "Chỉ nhân viên được phép xử lý thanh toán tại quầy."
          : requestError.message || "Không thể xử lý thanh toán.");
    } finally { setSaving(false); }
  }

  function startAnother() { setPendingPayment(null); setNotice(""); setError(""); setPlanId(""); }

  return <div className="layout">
    <Sidebar role={role} activePage="membership-sales" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main membership-sales-page">
      <header className="membership-sales-header">
        <div><p className="pt-eyebrow">SPORTCENTER / RECEPTIONIST</p><h1>Hỗ trợ mua gói hội viên</h1>
          <p>Tạo thanh toán tại quầy cho thành viên. Gói chỉ được kích hoạt hoặc gia hạn sau khi xác nhận thanh toán thành công.</p></div>
        <button type="button" onClick={() => void load()} disabled={loading}>Làm mới</button>
      </header>
      {error && <p className="membership-sales-alert error" role="alert">{error}</p>}
      {notice && <p className="membership-sales-alert success" role="status">{notice}</p>}
      {loading ? <p role="status">Đang tải thành viên và gói hội viên…</p> : <>
        <section className="membership-sales-card">
          <h2>1. Chọn thành viên</h2>
          <label>Tìm theo tên hoặc mã thành viên<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nhập tên hoặc mã…" /></label>
          <label>Thành viên<select value={memberId} disabled={Boolean(pendingPayment)} onChange={(event) => { setMemberId(event.target.value); setNotice(""); setError(""); }}>
            <option value="">Chọn thành viên ACTIVE</option>{visibleMembers.map((member) => <option key={member.id} value={member.id}>#{member.id} · {member.name || "Thành viên"}</option>)}
          </select></label>
          {selectedMember && memberMemberships === null && <p role="status">Đang tải gói hiện tại…</p>}
          {selectedMember && memberMemberships && <p className="membership-sales-current">{activeMemberships.length
            ? `Thành viên đang có ${activeMemberships.length} gói ACTIVE. Gói mua mới sẽ được gia hạn sau ngày kết thúc hiện tại theo quy tắc backend.`
            : "Thành viên chưa có gói ACTIVE; thanh toán thành công sẽ kích hoạt gói mới."}</p>}
        </section>
        <section className="membership-sales-card">
          <h2>2. Chọn gói</h2>
          {plans.length === 0 ? <p>Hiện không có gói ACTIVE để bán.</p> : <label>Gói hội viên<select value={planId} disabled={Boolean(pendingPayment)} onChange={(event) => setPlanId(event.target.value)}>
            <option value="">Chọn gói ACTIVE</option>{plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name} · {money(plan.price)} · {plan.durationMonths} tháng</option>)}
          </select></label>}
          {selectedPlan && <div className="membership-sales-plan"><strong>{selectedPlan.name}</strong><span>{money(selectedPlan.price)} · {selectedPlan.durationMonths} tháng</span><small>Giá được backend xác định từ MembershipPlan; form không gửi số tiền.</small></div>}
        </section>
        {!pendingPayment && <form onSubmit={createPendingPayment} className="membership-sales-actions">
          <button type="submit" disabled={saving || !selectedMember || !selectedPlan || memberMemberships === null}>{saving ? "Đang tạo…" : "Tạo thanh toán tại quầy"}</button>
        </form>}
        {pendingPayment && <section className="membership-sales-card membership-sales-payment" aria-live="polite">
          <h2>Thanh toán #{pendingPayment.id}</h2><dl><div><dt>Thành viên</dt><dd>{selectedMember?.name || `#${pendingPayment.memberId || memberId}`}</dd></div>
            <div><dt>Gói</dt><dd>{selectedPlan?.name || pendingPayment.purpose}</dd></div><div><dt>Số tiền</dt><dd>{money(pendingPayment.amount)}</dd></div>
            <div><dt>Trạng thái</dt><dd>{pendingPayment.status}</dd></div></dl>
          {pendingPayment.status === "PENDING" ? <div className="membership-sales-actions">
            <button type="button" disabled={saving} onClick={() => void processPayment(true)}>{saving ? "Đang xử lý…" : "Xác nhận đã nhận thanh toán"}</button>
            <button type="button" className="secondary" disabled={saving} onClick={() => void processPayment(false)}>Ghi nhận chưa thanh toán</button>
          </div> : <button type="button" onClick={startAnother}>Tạo giao dịch khác</button>}
          <p className="membership-sales-note">Chỉ xác nhận thành công sau khi đã nhận tiền tại quầy. Mọi thao tác ghi nhận qua API thanh toán hiện có.</p>
        </section>}
      </>}
    </main>
  </div>;
}
