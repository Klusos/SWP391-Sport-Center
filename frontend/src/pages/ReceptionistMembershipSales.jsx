import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import AdminTopBar from "../components/AdminTopBar";
import ReceptionistRefundQueue from "../components/ReceptionistRefundQueue.jsx";
import {
  cancelMembershipApi,
  createPaymentApi,
  decideCancellationRequestApi,
  getCancellationRequestsApi,
  getMembershipsByMemberApi,
  getMemberPaymentsApi,
  getMemberRenewalIntentsApi,
  getPendingMembershipPaymentsApi,
  getPublicMembershipPlansApi,
  getUsersApi,
  processCounterPaymentApi,
  startReceptionistRenewalCounterAttemptApi,
} from "../api";
import { cancellationRequestErrorMessage, formatDate, formatDateTime, paymentStatusLabel } from "../membershipCancellation";
import { todayInVietnam } from "../membershipDates";
import { activeMembershipChain, cancellableMembershipId, cancelMembershipErrorMessage, isAutoRenewalPayment } from "../membershipRenewal";
import { isMembershipPayment, upgradeErrorMessage } from "../membershipUpgrade";
import { membershipPlanBenefits } from "../membershipPlanPresentation.js";
import { membershipPurchaseErrorMessage } from "../membershipPurchase.js";
import { MEMBERSHIP_REFUND_PURPOSES } from "../refundQueue.js";
import "./ReceptionistMembershipSales.css";

const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];
const money = (value) => value == null ? "Chưa có giá" : Number(value).toLocaleString("vi-VN") + " ₫";
const vietnamNowLocal = () => {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map(({ type, value: part }) => [type, part]));
  return `${value.year}-${value.month}-${value.day}T${value.hour}:${value.minute}:${value.second}`;
};
const deadlinePassed = (value) => !value || String(value).slice(0, 19) <= vietnamNowLocal();
const purposeLabel = (payment) => payment?.purpose === "UPGRADE" ? "Nâng cấp lên "
  : isAutoRenewalPayment(payment) ? "Gia hạn tự động · " : "";

const paymentTargetName = (payment, plans) => payment?.upgradeQuote?.targetPlanName
  || plans.find((plan) => String(plan.id) === String(payment?.referenceId))?.name
  || `Gói #${payment?.referenceId}`;
export default function ReceptionistMembershipSales({ token, role, currentUserName, onLogout, onNavigate }) {
  const isAdmin = role === "ADMIN";
  const [members, setMembers] = useState([]);
  const [plans, setPlans] = useState([]);
  const [memberId, setMemberId] = useState("");
  const [planId, setPlanId] = useState("");
  const [query, setQuery] = useState("");
  const [membershipData, setMembershipData] = useState(null);
  const [paymentData, setPaymentData] = useState(null);
  const [renewalData, setRenewalData] = useState(null);
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
  const [cancelRequests, setCancelRequests] = useState(null);

  // Membership cancellation requests remain managed on the membership page.
  const loadReviewQueues = useCallback(async () => {
    try {
      const requests = await getCancellationRequestsApi(token);
      setCancelRequests(rows(requests));
    } catch (requestError) {
      setError(requestError.status === 401 ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.message || "Không tải được yêu cầu hủy gói.");
    }
  }, [token]);
  useEffect(() => {
    let current = true;
    Promise.resolve().then(() => current && loadReviewQueues());
    return () => { current = false; };
  }, [loadReviewQueues]);

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
    Promise.all([getMembershipsByMemberApi(token, memberId), getMemberPaymentsApi(token, memberId),
      getMemberRenewalIntentsApi(token, memberId)])
      .then(([membershipResult, paymentResult, renewalResult]) => {
        if (!current) return;
        const memberPayments = rows(paymentResult).filter((payment) => isMembershipPayment(payment.purpose));
        setMembershipData({ memberId, rows: rows(membershipResult) });
        setPaymentData({ memberId, rows: memberPayments });
        setRenewalData({ memberId, rows: rows(renewalResult) });
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
  const selectedPlanName = pendingPayment?.upgradeQuote?.targetPlanName || selectedPlan?.name || (pendingPayment ? "Plan #" + pendingPayment.referenceId : null);
  const selectedPlanDuration = pendingPayment?.upgradeQuote?.targetDurationMonths ?? selectedPlan?.durationMonths;
  const memberMemberships = membershipData?.memberId === memberId ? membershipData.rows : null;
  const today = todayInVietnam();
  const memberChain = activeMembershipChain(memberMemberships, today);
  const purchaseBlockedByMembership = memberChain.length > 0;
  // Direct cancellation is ADMIN-only; receptionists only review member requests.
  const staffCancellableId = role === "ADMIN" ? cancellableMembershipId(memberChain, today, { staffCanCancelRunning: true }) : null;

  async function createPendingPayment(event) {
    event.preventDefault();
    if (saving || !selectedMember || !selectedPlan || pendingPayment || purchaseBlockedByMembership) return;
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
      setError(requestError.status === 409
        ? membershipPurchaseErrorMessage(requestError)
        : requestError.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403
          ? "Tài khoản không được phép tạo thanh toán cho thành viên này."
          : requestError.message || "Không thể tạo thanh toán membership.");
    } finally { setSaving(false); }
  }

  async function refreshRenewals(targetMemberId = memberId) {
    const renewalResult = await getMemberRenewalIntentsApi(token, targetMemberId);
    setRenewalData({ memberId: String(targetMemberId), rows: rows(renewalResult) });
  }

  async function createRenewalCounterAttempt(intent) {
    if (saving || !selectedMember) return;
    setSaving(true); setError(""); setNotice("");
    try {
      const action = await startReceptionistRenewalCounterAttemptApi(token, selectedMember.id, intent.intentId);
      await refreshRenewals(selectedMember.id);
      setNotice(`Đã tạo giao dịch Renewal COUNTER #${action.attempt.paymentId} cho ${selectedMember.name}. Chỉ xác nhận sau khi đã nhận đủ tiền.`);
    } catch (requestError) {
      setError(requestError.status === 409
        ? "Renewal chưa thể tạo thêm lượt thanh toán. Kiểm tra lượt đang chờ, hạn Intent hoặc trạng thái đối soát."
        : requestError.status === 403
          ? "Tài khoản không có quyền xử lý Renewal tại quầy."
          : requestError.message || "Không thể tạo giao dịch Renewal COUNTER.");
    } finally { setSaving(false); }
  }

  async function confirmRenewalCounter(intent, attempt) {
    if (saving || attempt.status !== "PENDING") return;
    if (!window.confirm(`Chỉ xác nhận khi quầy đã nhận đủ ${money(attempt.amount)} cho Renewal #${intent.intentId}.`)) return;
    setSaving(true); setError(""); setNotice("");
    try {
      const result = await processCounterPaymentApi(token, attempt.paymentId, true,
        "Renewal COUNTER collection confirmed by authenticated staff");
      await refreshRenewals(selectedMember.id);
      setHistoryVersion((value) => value + 1);
      if (result.status === "SUCCESS") {
        setNotice(`Renewal #${intent.intentId} đã được ghi nhận thanh toán. Membership kế tiếp bắt đầu ${intent.successorStartDate}.`);
      } else if (result.status === "REFUND_REQUIRED") {
        setError(`Renewal #${intent.intentId} cần đối soát thủ công. Trạng thái này không có nghĩa tiền đã được hoàn.`);
      } else {
        setError(`Không hoàn tất Renewal #${intent.intentId}; trạng thái thanh toán: ${result.status || "không xác định"}.`);
      }
    } catch (requestError) {
      setError(requestError.status === 403
        ? "Chỉ ADMIN hoặc RECEPTIONIST được xác nhận thanh toán tại quầy."
        : requestError.message || "Không thể xác nhận Renewal COUNTER.");
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

  async function decideRequest(request, approve) {
    if (saving) return;
    let note = null;
    if (approve) {
      if (!window.confirm(`Duyệt hủy gói #${request.membershipId} (${request.planName}) của ${request.memberName}? `
        + `Gói bị hủy và ${request.purchasePaymentId ? `thanh toán #${request.purchasePaymentId} (${money(request.purchaseAmount)})` : "khoản đã mua (nếu có)"} chuyển sang "Chờ hoàn tiền".`)) return;
    } else {
      note = window.prompt(`Từ chối yêu cầu hủy gói #${request.membershipId} của ${request.memberName}. Nhập lý do gửi cho hội viên (bắt buộc):`);
      if (note === null) return;
      if (!note.trim()) { setError("Cần nhập lý do khi từ chối yêu cầu hủy gói."); return; }
    }
    setSaving(true); setError(""); setNotice("");
    try {
      await decideCancellationRequestApi(token, request.id, approve, note?.trim() || null);
      setNotice(approve
        ? `Đã duyệt hủy gói #${request.membershipId}. Khoản cần hoàn tiền xuất hiện trong mục "Cần hoàn tiền".`
        : `Đã từ chối yêu cầu hủy gói #${request.membershipId}; hội viên đã được thông báo.`);
      setHistoryVersion((value) => value + 1);
    } catch (requestError) {
      setError(cancellationRequestErrorMessage(requestError));
    } finally {
      await loadReviewQueues();
      setSaving(false);
    }
  }

  function startAnother() { setPendingPayment(null); setNotice(""); setError(""); setPlanId(""); }

  return <div className="layout">
    <Sidebar role={role} activePage={role === "ADMIN" ? "admin-membership-sales" : "membership-sales"} onNavigate={onNavigate} onLogout={onLogout} />
    <main className={`main membership-sales-page${isAdmin ? " membership-sales-page--admin" : ""}`}>
      {isAdmin && <AdminTopBar pageLabel="QUẢN LÝ GÓI THÀNH VIÊN" displayName={currentUserName} avatarName={currentUserName} />}
      <div className={isAdmin ? "membership-sales-content" : "membership-sales-receptionist-content"}>
      <header className="membership-sales-header">
        <div><h1>Hỗ trợ mua gói hội viên</h1>
          <p>Tạo thanh toán tại quầy cho thành viên. Gói chỉ được kích hoạt hoặc gia hạn sau khi xác nhận thanh toán thành công.</p></div>
        <button type="button" onClick={() => { void load(); void loadQueue(); void loadReviewQueues(); setHistoryVersion((value) => value + 1); }} disabled={loading || saving || queueLoading}>Làm mới</button>
      </header>
      {error && <p className="membership-sales-alert error" role="alert">{error}</p>}
      {notice && <p className="membership-sales-alert success" role="status">{notice}</p>}
      <section className="membership-sales-card">
        <h2>Thanh toán gói hội viên chờ tại quầy</h2>
        {queueLoading ? <p role="status">Đang tải hàng chờ…</p> : queue && <>
          {queue.content.length === 0 ? <p>Không có thanh toán chờ tại quầy.</p> : queue.content.map((payment) => <div key={payment.id} className="membership-sales-history-row">
            <span>#{payment.id} · {payment.memberName} (#{payment.memberId}) · {purposeLabel(payment)}{paymentTargetName(payment, plans)} · {money(payment.amount)} · {paymentStatusLabel[payment.status] || payment.status}</span>
            <button type="button" disabled={saving} onClick={() => selectPayment(payment)}>Chọn thanh toán</button>
          </div>)}
          <div className="membership-sales-actions">
            <button type="button" disabled={saving || queuePage === 0} onClick={() => setQueuePage(queuePage - 1)}>Trang trước</button>
            <span>Trang {queuePage + 1} / {Math.max(1, queue.totalPages)}</span>
            <button type="button" disabled={saving || queue.last} onClick={() => setQueuePage(queuePage + 1)}>Trang sau</button>
          </div>
        </>}
      </section>
      <section className="membership-sales-card">
        <h2>Yêu cầu hủy gói chờ duyệt</h2>
        {cancelRequests === null ? <p role="status">Đang tải yêu cầu…</p> : cancelRequests.length === 0
          ? <p>Không có yêu cầu hủy gói nào đang chờ.</p>
          : cancelRequests.map((request) => <div key={request.id} className="membership-sales-history-row">
            <span>#{request.id} · {request.memberName} (#{request.memberId}) · {request.planName} {formatDate(request.startDate)} → {formatDate(request.endDate)}
              {" · "}{request.purchaseAmount != null ? `hoàn ${money(request.purchaseAmount)}` : "không có thanh toán gốc"}
              {" · "}gửi {formatDateTime(request.requestedAt)}{request.reason ? ` · Lý do: ${request.reason}` : ""}</span>
            <span className="membership-sales-actions">
              <button type="button" disabled={saving} onClick={() => void decideRequest(request, true)}>Duyệt hủy</button>
              <button type="button" className="secondary" disabled={saving} onClick={() => void decideRequest(request, false)}>Từ chối</button>
            </span>
          </div>)}
        <p className="membership-sales-note">Chỉ duyệt được khi gói chưa bắt đầu và là gói sau cùng. Yêu cầu chưa xử lý sẽ tự hết hiệu lực khi gói bắt đầu.</p>
      </section>

      <ReceptionistRefundQueue token={token} purposes={MEMBERSHIP_REFUND_PURPOSES} groups={[{
        id: "membership",
        purposes: MEMBERSHIP_REFUND_PURPOSES,
        title: "Ho\u00e0n ti\u1ec1n h\u1ed9i vi\u00ean",
        description: "Kho\u1ea3n h\u1ed9i vi\u00ean v\u00e0 n\u00e2ng c\u1ea5p g\u00f3i \u0111\u00e3 y\u00eau c\u1ea7u ho\u00e0n ti\u1ec1n",
      }]} />

      {loading ? <p role="status">Đang tải thành viên và gói hội viên…</p> : <div className="membership-sales-checkout-layout">
        <div className="membership-sales-steps">
        <section className="membership-sales-card">
          <h2>1. Chọn thành viên</h2>
          <label>Tìm theo tên hoặc mã thành viên<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nhập tên hoặc mã…" /></label>
          <label>Thành viên<select value={memberId} disabled={saving} onChange={(event) => { setMemberId(event.target.value); setMembershipData(null); setPaymentData(null); setPendingPayment(null); setNotice(""); setError(""); }}>
            {pendingPayment && !selectedMember && <option value={memberId}>#{memberId} · {pendingPayment.memberName}</option>}
            <option value="">Chọn thành viên ACTIVE</option>{visibleMembers.map((member) => <option key={member.id} value={member.id}>#{member.id} · {member.name || "Thành viên"}</option>)}
          </select></label>
          {selectedMember && <div className="membership-sales-selected-member" aria-label="Thành viên đang chọn">
            <span className="membership-sales-selected-check" aria-hidden="true">✓</span>
            <div><strong>{selectedMember.name || `Thành viên #${selectedMember.id}`}</strong><small>MEMBER #{selectedMember.id}</small>
              {(selectedMember.email || selectedMember.phone) && <small>{[selectedMember.email, selectedMember.phone].filter(Boolean).join(" · ")}</small>}</div>
            <span className="membership-sales-selected-tag">Đã chọn</span>
          </div>}
          {selectedMember && memberMemberships === null && <p role="status">Đang tải gói hiện tại…</p>}
          {selectedMember && memberMemberships && <p className="membership-sales-current">{purchaseBlockedByMembership
            ? "Thành viên đã có Membership đang hiệu lực hoặc sắp có hiệu lực; không thể tạo Purchase thông thường."
            : "Thành viên không có Membership hiệu lực; thanh toán thành công sẽ kích hoạt gói mới."}</p>}
          {selectedMember && memberChain.length > 0 && <div className="membership-sales-history">
            <h3>Gói hiện có</h3>
            {memberChain.map((membership) => <div key={membership.id} className="membership-sales-history-row">
              <span>#{membership.id} · {membership.planName} · {membership.startDate} → {membership.endDate} · {String(membership.startDate) > today ? "Sắp bắt đầu" : "Đang chạy"}</span>
              {membership.id === staffCancellableId && <button type="button" disabled={saving} onClick={() => void cancelForMember(membership)}>
                {String(membership.startDate) > today ? "Hủy gói (hoàn 100%)" : "Hủy đặc biệt"}
              </button>}
              <small>{money(membership.planPrice)} · {membership.durationMonths ?? "—"} tháng · {membership.benefitSource === "LEGACY_PLAN_FALLBACK" ? "Quyền lợi theo Plan hiện tại (legacy chưa xác minh)." : "Quyền lợi theo snapshot Membership đã mua."}</small>
              <small>{membershipPlanBenefits(membership).map(({ label, value }) => `${label}: ${value}`).join(" · ")}</small>
            </div>)}
            <p className="membership-sales-note">{role === "ADMIN"
              ? "Gói chưa bắt đầu: hủy và đánh dấu hoàn 100% khoản đã mua. Gói đang chạy: chỉ hủy trong trường hợp đặc biệt, phải ghi lý do; hoàn tiền (nếu có) xử lý trực tiếp."
              : "Lễ tân không hủy gói trực tiếp. Hội viên gửi yêu cầu hủy trên trang Gói thành viên; lễ tân duyệt hoặc từ chối ở mục \"Yêu cầu hủy gói chờ duyệt\". Trường hợp đặc biệt vui lòng chuyển cho quản trị viên."}</p>
          </div>}
          {selectedMember && paymentData?.memberId === memberId && <div className="membership-sales-history">
            <h3>Lịch sử thanh toán gói</h3>
            {paymentData.rows.length === 0 ? <p>Chưa có giao dịch membership.</p> : paymentData.rows.map((payment) => <div key={payment.id} className="membership-sales-history-row">
              <span>#{payment.id} · {payment.purpose === "UPGRADE" ? "Nâng cấp · " : ""}{money(payment.amount)} · {paymentStatusLabel[payment.status] || payment.status}</span>
              {payment.status === "PENDING" && payment.method === "COUNTER" && <button type="button" disabled={saving} onClick={() => selectPayment(payment)}>Tiếp tục xử lý</button>}
            </div>)}
          </div>}
        </section>
        {selectedMember && <section className="membership-sales-card" aria-label="Renewal của thành viên">
          <h2>Gia hạn Membership</h2>
          {renewalData?.memberId !== memberId ? <p role="status">Đang tải Renewal…</p>
            : renewalData.rows.length === 0 ? <p>Thành viên chưa có Renewal Intent do hệ thống tạo.</p>
              : renewalData.rows.map((intent) => {
                const needsReview = intent.latestResult === "REFUND_REQUIRED"
                  || intent.attempts?.some((attempt) => attempt.manualReconciliationRequired);
                const livePending = intent.attempts?.some((attempt) => attempt.status === "PENDING"
                  && !deadlinePassed(attempt.deadline));
                const open = intent.status === "OPEN" && !needsReview && !intent.successorMembershipId;
                return <div key={intent.intentId} className="membership-sales-history-row">
                  <strong>Intent #{intent.intentId} · {intent.planName}</strong>
                  <span>Báo giá đã chốt: {money(intent.quotedAmount)} · {intent.durationMonths} tháng</span>
                  <small>Snapshot quyền lợi: khóa học {intent.courseDiscountPercent}% · PT {intent.personalTrainingDiscountPercent}% · ITP {intent.allowPersonalTrainingPlan ? "có" : "không"} · 1:1 {intent.allowOneOnOneCoaching ? "có" : "không"}</small>
                  <small>Hạn Intent: {formatDateTime(intent.validUntil)} · Kỳ kế tiếp: {formatDate(intent.successorStartDate)} – {formatDate(intent.successorEndDate)} (ngày kết thúc độc quyền)</small>
                  <small>Trạng thái: {intent.status} · Kết quả: {intent.latestResult}</small>
                  {intent.attempts?.map((attempt) => <div key={attempt.paymentId}>
                    <small>Payment #{attempt.paymentId}: {attempt.method} · {attempt.status} · {money(attempt.amount)} · hạn {formatDateTime(attempt.deadline)}</small>
                    {attempt.manualReconciliationRequired && <p role="alert">Cần đối soát thủ công. REFUND_REQUIRED không có nghĩa là tiền đã được hoàn.</p>}
                    {attempt.status === "PENDING" && attempt.method === "ONLINE" && !deadlinePassed(attempt.deadline)
                      && <small>Đang chờ thanh toán ONLINE qua IPN đã xác thực.</small>}
                    {attempt.status === "PENDING" && attempt.method === "COUNTER" && !deadlinePassed(attempt.deadline)
                      && <button type="button" disabled={saving} onClick={() => void confirmRenewalCounter(intent, attempt)}>
                        Xác nhận đã nhận tiền tại quầy
                      </button>}
                  </div>)}
                  {intent.successorMembershipId && <small>Membership kế tiếp: #{intent.successorMembershipId}</small>}
                  {open && !livePending && <button type="button" disabled={saving}
                    onClick={() => void createRenewalCounterAttempt(intent)}>
                    Tạo lượt thanh toán Renewal tại quầy
                  </button>}
                  {open && livePending && <small>Đã có lượt thanh toán còn hạn; không tạo lượt thứ hai.</small>}
                  {needsReview && <p role="alert">Có khoản thu cần đối soát thủ công; không tạo lượt thanh toán mới.</p>}
                </div>;
              })}
        </section>}
        <section className="membership-sales-card">
          <h2>2. Chọn gói</h2>
          {plans.length === 0 ? <p>Hiện không có gói ACTIVE để bán.</p> : <fieldset className="membership-sales-plan-options" disabled={Boolean(pendingPayment)}>
            <legend className="visually-hidden">Chọn gói hội viên</legend>
            <div className="membership-sales-plan-grid">{plans.map((plan) => <label key={plan.id} className={`membership-sales-plan-choice${String(plan.id) === String(planId) ? " is-selected" : ""}`}>
              <input type="radio" name="membershipPlan" value={plan.id} checked={String(plan.id) === String(planId)} onChange={() => setPlanId(String(plan.id))} />
              <span className="membership-sales-plan-choice-body">
                <span className="membership-sales-plan-choice-heading"><strong>{plan.name}</strong><em>{plan.status === "ACTIVE" ? "Đang áp dụng" : plan.status || "Trạng thái chưa rõ"}</em></span>
                <span className="membership-sales-plan-price">{money(plan.price)} <small>· {plan.durationMonths ?? "—"} tháng</small></span>
                <span className="membership-sales-plan-description">{plan.description || "Chưa có mô tả."}</span>
                <span className="membership-sales-plan-benefits">{membershipPlanBenefits(plan).map((benefit) => <span key={benefit.label}><small>{benefit.label}</small><b>{benefit.value}</b></span>)}</span>
              </span>
            </label>)}</div>
          </fieldset>}
          {selectedPlan && <div className="membership-sales-plan"><strong>{selectedPlan.name}</strong><small>Giá được backend xác định từ MembershipPlan; biểu mẫu không gửi số tiền.</small></div>}
        </section>
        </div>
        <aside className="membership-sales-order-summary" aria-labelledby="membership-order-title">
          <p className="membership-sales-order-kicker">TÓM TẮT GIAO DỊCH</p>
          <h2 id="membership-order-title">Thông tin thanh toán</h2>
          <div className="membership-sales-order-row"><span>Thành viên</span><strong>{selectedMember?.name || (pendingPayment ? pendingPayment.memberName || `#${pendingPayment.memberId}` : "Chưa chọn")}</strong>
            {selectedMember && <small>#{selectedMember.id}</small>}</div>
          <div className="membership-sales-order-row"><span>Gói hội viên</span><strong>{selectedPlanName || "Chưa chọn"}</strong>
            {(selectedPlan || pendingPayment?.upgradeQuote) && <small>{selectedPlanDuration ?? "—"} tháng</small>}</div>
          <div className="membership-sales-order-amount"><span>{pendingPayment ? "Số tiền thanh toán" : "Giá niêm yết của gói"}</span>
            <strong>{pendingPayment ? money(pendingPayment.amount) : selectedPlan ? money(selectedPlan.price) : "Chưa xác định"}</strong></div>
          {!pendingPayment && <p className="membership-sales-order-note">Số tiền chính thức và trạng thái sẽ được xác nhận từ phản hồi tạo giao dịch.</p>}
          {!pendingPayment && <form onSubmit={createPendingPayment} className="membership-sales-actions">
            <button type="submit" disabled={saving || !selectedMember || !selectedPlan || memberMemberships === null || purchaseBlockedByMembership}>{saving ? "Đang tạo…" : "Tạo thanh toán tại quầy"}</button>
          </form>}
          {pendingPayment && <section className="membership-sales-payment" aria-live="polite">
          <h2>Thanh toán #{pendingPayment.id}</h2><dl><div><dt>Thành viên</dt><dd>{pendingPayment.memberName || `#${pendingPayment.memberId}`}</dd></div>
            <div><dt>Gói</dt><dd>{purposeLabel(pendingPayment)}{selectedPlanName || `Gói #${pendingPayment.referenceId}`}</dd></div><div><dt>Số tiền</dt><dd>{money(pendingPayment.amount)}</dd></div>
            <div><dt>Trạng thái</dt><dd>{paymentStatusLabel[pendingPayment.status] || pendingPayment.status}</dd></div></dl>
                    {pendingPayment.purpose === "UPGRADE" && pendingPayment.upgradeQuote && <dl className="membership-sales-upgrade-quote">
            <div><dt>Membership nguồn</dt><dd>#{pendingPayment.upgradeQuote.sourceMembershipId} · {pendingPayment.upgradeQuote.sourceStartDate}–{pendingPayment.upgradeQuote.sourceEndDate}</dd></div>
            <div><dt>Giá gói đích</dt><dd>{money(pendingPayment.upgradeQuote.targetPrice)}</dd></div>
            <div><dt>Credit</dt><dd>−{money(pendingPayment.upgradeQuote.appliedCredit)}</dd></div>
            <div><dt>Thời hạn</dt><dd>{pendingPayment.upgradeQuote.targetDurationMonths} tháng</dd></div>
            <div><dt>Ưu đãi khóa học / PT</dt><dd>{pendingPayment.upgradeQuote.courseDiscountPercent}% / {pendingPayment.upgradeQuote.personalTrainingDiscountPercent}%</dd></div>
          </dl>}
          {pendingPayment.purpose === "UPGRADE" && !pendingPayment.upgradeQuote && <p className="membership-sales-note" role="note">Báo giá Upgrade cũ chưa có snapshot đầy đủ; không xác nhận thanh toán theo giá Plan hiện tại.</p>}{pendingPayment.status === "PENDING" && pendingPayment.method === "COUNTER" ? <div className="membership-sales-actions">
            <button type="button" disabled={saving} onClick={() => void processPayment(true)}>{saving ? "Đang xử lý…" : "Xác nhận đã nhận thanh toán"}</button>
            <button type="button" className="secondary" disabled={saving} onClick={() => void processPayment(false)}>Hủy thanh toán (chưa nhận tiền)</button>
          </div> : <button type="button" onClick={startAnother}>Tạo giao dịch khác</button>}
          <p className="membership-sales-note">Chỉ xác nhận thành công sau khi đã nhận tiền tại quầy. Mọi thao tác ghi nhận qua API thanh toán hiện có.</p>
        </section>}
        </aside>
      </div>}
      </div>
    </main>
  </div>;
}
