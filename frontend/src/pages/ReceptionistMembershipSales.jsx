import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  cancelMembershipApi,
  createPaymentApi,
  getMembershipsByMemberApi,
  getMemberPaymentsApi,
  getPendingMembershipPaymentsApi,
  getPublicMembershipPlansApi,
  getUsersApi,
  processCounterPaymentApi,
} from "../api";
import { todayInVietnam } from "../membershipDates";
import { activeMembershipChain, cancellableMembershipId, cancelMembershipErrorMessage, isAutoRenewalPayment } from "../membershipRenewal";
import { isMembershipPayment, upgradeErrorMessage } from "../membershipUpgrade";
import "./ReceptionistMembershipSales.css";

const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];
const money = (value) => value == null ? "Chưa có giá" : Number(value).toLocaleString("vi-VN") + " ₫";
const purposeLabel = (payment) => payment?.purpose === "UPGRADE" ? "Nâng cấp lên "
  : isAutoRenewalPayment(payment) ? "Gia hạn tự động · " : "";

export default function ReceptionistMembershipSales({ token, role, onLogout, onNavigate }) {
  const [members, setMembers] = useState([]);
  const [plans, setPlans] = useState([]);
  const [memberId, setMemberId] = useState("");
  const [planId, setPlanId] = useState("");
  const [query, setQuery] = useState("");
  const [membershipData, setMembershipData] = useState(null);
  const [paymentData, setPaymentData] = useState(null);
  const [pendingPayment, setPendingPayment] = useState(null);
  const [queue, setQueue] = useState(null);
  const [queuePage, setQueuePage] = useState(0);
  const [queueLoading, setQueueLoading] = useState(false);
  const queueRequest = useRef(0);
  const [historyVersion, setHistoryVersion] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const loadQueue = useCallback(async () => {
    const requestId = ++queueRequest.current;
    setQueueLoading(true);
    try {
      const result = await getPendingMembershipPaymentsApi(token, queuePage);
      if (requestId !== queueRequest.current) return;
      if (queuePage > 0 && result.content.length === 0) {
        setQueuePage(queuePage - 1);
      } else setQueue(result);
    } catch (requestError) {
      if (requestId !== queueRequest.current) return;
      setQueue(null);
      setError(requestError.status === 401 ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403 ? "Bạn không có quyền xem hàng chờ thanh toán." : requestError.message);
    } finally { if (requestId === queueRequest.current) setQueueLoading(false); }
  }, [token, queuePage]);
  useEffect(() => {
    let current = true;
    Promise.resolve().then(() => current && loadQueue());
    return () => { current = false; queueRequest.current += 1; };
  }, [loadQueue]);

  function selectPayment(payment) {
    setQuery("");
    setMemberId(String(payment.memberId));
    setPlanId(String(payment.referenceId));
    setPendingPayment(payment);
    setNotice(""); setError("");
  }

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
    Promise.all([getMembershipsByMemberApi(token, memberId), getMemberPaymentsApi(token, memberId)])
      .then(([membershipResult, paymentResult]) => {
        if (!current) return;
        const memberPayments = rows(paymentResult).filter((payment) => isMembershipPayment(payment.purpose));
        setMembershipData({ memberId, rows: rows(membershipResult) });
        setPaymentData({ memberId, rows: memberPayments });
      })
      .catch((requestError) => { if (current) setError(requestError.message || "Không tải được thông tin gói hiện tại."); });
    return () => { current = false; };
  }, [memberId, token, historyVersion]);

  const visibleMembers = useMemo(() => {
    const term = query.trim().toLocaleLowerCase("vi");
    return members.filter((member) => !term || [member.id, member.name].some((value) => String(value ?? "").toLocaleLowerCase("vi").includes(term)));
  }, [members, query]);
  const selectedMember = members.find((member) => String(member.id) === memberId) || null;
  const selectedPlan = plans.find((plan) => String(plan.id) === (pendingPayment ? String(pendingPayment.referenceId) : planId)) || null;
  const memberMemberships = membershipData?.memberId === memberId ? membershipData.rows : null;
  const activeMemberships = (memberMemberships || []).filter((membership) => membership.status === "ACTIVE");
  const today = todayInVietnam();
  const memberChain = activeMembershipChain(memberMemberships, today);
  const staffCancellableId = cancellableMembershipId(memberChain, today, { staffCanCancelRunning: role === "ADMIN" });

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
      setPaymentData((current) => ({
        memberId: String(selectedMember.id),
        rows: [payment, ...(current?.memberId === String(selectedMember.id) ? current.rows : [])],
      }));
      await loadQueue();
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
    if (!pendingPayment || pendingPayment.method !== "COUNTER" || pendingPayment.status !== "PENDING" || saving) return;
    const action = success ? "xác nhận đã nhận đủ khoản thanh toán" : "HỦY thanh toán vì chưa nhận tiền (không thể xác nhận lại)";
    if (!window.confirm(`Bạn có chắc muốn ${action} cho thanh toán #${pendingPayment.id}?`)) return;
    setSaving(true); setError(""); setNotice("");
    try {
      const result = await processCounterPaymentApi(token, pendingPayment.id, success,
        success ? "Front desk payment confirmed" : "Front desk payment not received");
      setPendingPayment(result);
      setPaymentData((current) => current?.memberId === String(pendingPayment.memberId)
        ? { ...current, rows: current.rows.map((payment) => payment.id === result.id ? result : payment) }
        : current);
      await loadQueue();
      if (result.status === "SUCCESS") {
        setNotice(result.purpose === "UPGRADE"
          ? `Thanh toán #${result.id} thành công. Gói hội viên đã được nâng cấp, ngày hết hạn giữ nguyên.`
          : `Thanh toán #${result.id} thành công. Gói hội viên đã được kích hoạt hoặc gia hạn.`);
      } else if (result.status === "CANCELLED") {
        setNotice(`Thanh toán #${result.id} đã bị hủy. Gói hội viên chưa được kích hoạt.`);
      } else {
        setError(`Thanh toán kết thúc với trạng thái ${result.status || "không xác định"}. Vui lòng kiểm tra lịch sử thanh toán.`);
      }
      setHistoryVersion((value) => value + 1);
    } catch (requestError) {
      setNotice("");
      setError(requestError.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403
          ? "Chỉ nhân viên được phép xử lý thanh toán tại quầy."
          : pendingPayment.purpose === "UPGRADE"
            ? upgradeErrorMessage(requestError)
            : requestError.message || "Không thể xử lý thanh toán.");
    } finally { setSaving(false); }
  }

  async function cancelForMember(membership) {
    if (saving) return;
    const upcoming = String(membership.startDate) > today;
    let reason = null;
    if (upcoming) {
      if (!window.confirm(`Hủy gói #${membership.id} (${membership.planName}) chưa bắt đầu? Khoản đã mua gói sẽ chuyển sang "Cần hoàn tiền" để hoàn 100%.`)) return;
    } else {
      reason = window.prompt(`Hủy đặc biệt gói đang chạy #${membership.id} (${membership.planName}). Quyền lợi dừng ngay. Nhập lý do (bắt buộc):`);
      if (reason === null) return;
      if (!reason.trim()) { setError("Cần nhập lý do khi hủy gói đang chạy."); return; }
    }
    setSaving(true); setError(""); setNotice("");
    try {
      await cancelMembershipApi(token, membership.id, reason);
      setNotice(upcoming
        ? `Đã hủy gói #${membership.id}. Hãy hoàn tiền khoản đã thanh toán cho gói này (trạng thái "Cần hoàn tiền").`
        : `Đã hủy gói #${membership.id}. Hoàn tiền (nếu có) xử lý trực tiếp theo lý do đã ghi.`);
      setHistoryVersion((value) => value + 1);
    } catch (requestError) {
      setError(cancelMembershipErrorMessage(requestError));
    } finally { setSaving(false); }
  }

  function startAnother() { setPendingPayment(null); setNotice(""); setError(""); setPlanId(""); }

  return <div className="layout">
    <Sidebar role={role} activePage={role === "ADMIN" ? "admin-membership-sales" : "membership-sales"} onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main membership-sales-page">
      <header className="membership-sales-header">
        <div><p className="pt-eyebrow">SPORTCENTER / RECEPTIONIST</p><h1>Hỗ trợ mua gói hội viên</h1>
          <p>Tạo thanh toán tại quầy cho thành viên. Gói chỉ được kích hoạt hoặc gia hạn sau khi xác nhận thanh toán thành công.</p></div>
        <button type="button" onClick={() => { void load(); void loadQueue(); setHistoryVersion((value) => value + 1); }} disabled={loading || saving || queueLoading}>Làm mới</button>
      </header>
      {error && <p className="membership-sales-alert error" role="alert">{error}</p>}
      {notice && <p className="membership-sales-alert success" role="status">{notice}</p>}
      <section className="membership-sales-card">
        <h2>Thanh toán gói hội viên chờ tại quầy</h2>
        {queueLoading ? <p role="status">Đang tải hàng chờ…</p> : queue && <>
          {queue.content.length === 0 ? <p>Không có thanh toán chờ tại quầy.</p> : queue.content.map((payment) => <div key={payment.id} className="membership-sales-history-row">
            <span>#{payment.id} · {payment.memberName} (#{payment.memberId}) · {purposeLabel(payment)}{plans.find((plan) => plan.id === payment.referenceId)?.name || `Gói #${payment.referenceId}`} · {money(payment.amount)} · {payment.status}</span>
            <button type="button" disabled={saving} onClick={() => selectPayment(payment)}>Chọn thanh toán</button>
          </div>)}
          <div className="membership-sales-actions">
            <button type="button" disabled={saving || queuePage === 0} onClick={() => setQueuePage(queuePage - 1)}>Trang trước</button>
            <span>Trang {queuePage + 1} / {Math.max(1, queue.totalPages)}</span>
            <button type="button" disabled={saving || queue.last} onClick={() => setQueuePage(queuePage + 1)}>Trang sau</button>
          </div>
        </>}
      </section>
      {loading ? <p role="status">Đang tải thành viên và gói hội viên…</p> : <>
        <section className="membership-sales-card">
          <h2>1. Chọn thành viên</h2>
          <label>Tìm theo tên hoặc mã thành viên<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nhập tên hoặc mã…" /></label>
          <label>Thành viên<select value={memberId} disabled={saving} onChange={(event) => { setMemberId(event.target.value); setMembershipData(null); setPaymentData(null); setPendingPayment(null); setNotice(""); setError(""); }}>
            {pendingPayment && !selectedMember && <option value={memberId}>#{memberId} · {pendingPayment.memberName}</option>}
            <option value="">Chọn thành viên ACTIVE</option>{visibleMembers.map((member) => <option key={member.id} value={member.id}>#{member.id} · {member.name || "Thành viên"}</option>)}
          </select></label>
          {selectedMember && memberMemberships === null && <p role="status">Đang tải gói hiện tại…</p>}
          {selectedMember && memberMemberships && <p className="membership-sales-current">{activeMemberships.length
            ? `Thành viên đang có ${activeMemberships.length} gói ACTIVE. Gói mua mới sẽ được gia hạn sau ngày kết thúc hiện tại theo quy tắc backend.`
            : "Thành viên chưa có gói ACTIVE; thanh toán thành công sẽ kích hoạt gói mới."}</p>}
          {selectedMember && memberChain.length > 0 && <div className="membership-sales-history">
            <h3>Gói hiện có</h3>
            {memberChain.map((membership) => <div key={membership.id} className="membership-sales-history-row">
              <span>#{membership.id} · {membership.planName} · {membership.startDate} → {membership.endDate} · {String(membership.startDate) > today ? "Sắp bắt đầu" : "Đang chạy"}</span>
              {membership.id === staffCancellableId && <button type="button" disabled={saving} onClick={() => void cancelForMember(membership)}>
                {String(membership.startDate) > today ? "Hủy gói (hoàn 100%)" : "Hủy đặc biệt"}
              </button>}
            </div>)}
            <p className="membership-sales-note">Gói chưa bắt đầu: hủy và đánh dấu hoàn 100% khoản đã mua. Gói đang chạy: chỉ quản trị viên hủy trong trường hợp đặc biệt, phải ghi lý do; hoàn tiền (nếu có) xử lý trực tiếp.</p>
          </div>}
          {selectedMember && paymentData?.memberId === memberId && <div className="membership-sales-history">
            <h3>Lịch sử thanh toán gói</h3>
            {paymentData.rows.length === 0 ? <p>Chưa có giao dịch membership.</p> : paymentData.rows.map((payment) => <div key={payment.id} className="membership-sales-history-row">
              <span>#{payment.id} · {payment.purpose === "UPGRADE" ? "Nâng cấp · " : ""}{money(payment.amount)} · {payment.status}</span>
              {payment.status === "PENDING" && payment.method === "COUNTER" && <button type="button" disabled={saving} onClick={() => selectPayment(payment)}>Tiếp tục xử lý</button>}
            </div>)}
          </div>}
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
          <h2>Thanh toán #{pendingPayment.id}</h2><dl><div><dt>Thành viên</dt><dd>{pendingPayment.memberName || `#${pendingPayment.memberId}`}</dd></div>
            <div><dt>Gói</dt><dd>{purposeLabel(pendingPayment)}{selectedPlan?.name || `Gói #${pendingPayment.referenceId}`}</dd></div><div><dt>Số tiền</dt><dd>{money(pendingPayment.amount)}</dd></div>
            <div><dt>Trạng thái</dt><dd>{pendingPayment.status}</dd></div></dl>
          {pendingPayment.status === "PENDING" && pendingPayment.method === "COUNTER" ? <div className="membership-sales-actions">
            <button type="button" disabled={saving} onClick={() => void processPayment(true)}>{saving ? "Đang xử lý…" : "Xác nhận đã nhận thanh toán"}</button>
            <button type="button" className="secondary" disabled={saving} onClick={() => void processPayment(false)}>Hủy thanh toán (chưa nhận tiền)</button>
          </div> : <button type="button" onClick={startAnother}>Tạo giao dịch khác</button>}
          <p className="membership-sales-note">Chỉ xác nhận thành công sau khi đã nhận tiền tại quầy. Mọi thao tác ghi nhận qua API thanh toán hiện có.</p>
        </section>}
      </>}
    </main>
  </div>;
}
