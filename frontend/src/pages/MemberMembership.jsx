import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import PaymentMethodModal from "../components/PaymentMethodModal";
import {
  cancelPaymentApi,
  createPaymentApi,
  getCurrentUserApi,
  getMembershipUpgradeOptionsApi,
  getMembershipsByMemberApi,
  getMyCancellationRequestsApi,
  getMyPaymentsApi,
  getPublicMembershipPlansApi,
  requestMembershipCancellationApi,
  setAutoRenewApi,
  withdrawCancellationRequestApi,
} from "../api";
import { validVnpayUrl } from "../courseCheckout";
import {
  canRequestCancellation, CANCELLATION_NOTICE_HOURS, cancellationRequestErrorMessage, formatDate, formatDateTime, lastRequestDay,
  paymentMethodLabel, paymentPurposeLabel, paymentStatusLabel, requestStatusLabel,
} from "../membershipCancellation";
import { hasOverlappingEffectiveMemberships, membershipSections, todayInVietnam } from "../membershipDates";
import { activeMembershipChain, autoRenewalOnlineReplacementPayload, cancellableMembershipId, isAutoRenewalPayment, RENEWAL_NOTICE_DAYS } from "../membershipRenewal";
import { membershipPurchaseErrorMessage } from "../membershipPurchase";
import { isMembershipPayment, upgradeBlockedMessage, upgradeErrorMessage } from "../membershipUpgrade";
import "./Portal.css";
import "./MemberMembership.css";

const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];
const money = (value) => value == null ? "—" : Number(value).toLocaleString("vi-VN") + " ₫";
const statusLabel = { ACTIVE: "Đang hoạt động", EXPIRED: "Đã hết hạn", UPGRADED: "Đã nâng cấp", CANCELLED: "Đã hủy" };

export default function MemberMembership({ token, role, onLogout, onNavigate }) {
  const [plans, setPlans] = useState(null);
  const [memberships, setMemberships] = useState(null);
  const [payments, setPayments] = useState(null);
  const [upgrade, setUpgrade] = useState(null);
  const [cancellationRequests, setCancellationRequests] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [busyPlanId, setBusyPlanId] = useState(null);
  const [cancelingId, setCancelingId] = useState(null);
  const [paymentModalPlan, setPaymentModalPlan] = useState(null);
  const [paymentReconciliationRequired, setPaymentReconciliationRequired] = useState(false);
  const [togglingId, setTogglingId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [user, availablePlans] = await Promise.all([
        getCurrentUserApi(token),
        getPublicMembershipPlansApi(),
      ]);
      const [ownMemberships, ownPayments, upgradeOptions, ownRequests] = await Promise.all([
        getMembershipsByMemberApi(token, user.id),
        getMyPaymentsApi(token),
        getMembershipUpgradeOptionsApi(token, user.id),
        getMyCancellationRequestsApi(token),
      ]);
      setMemberships(rows(ownMemberships));
      setPayments(rows(ownPayments));
      setUpgrade(upgradeOptions);
      setCancellationRequests(rows(ownRequests));
      setPlans(rows(availablePlans).filter((plan) => String(plan?.status).toUpperCase() === "ACTIVE"));
      setPaymentReconciliationRequired(false);
      return rows(ownPayments);
    } catch (requestError) {
      setMemberships([]);
      setPayments([]);
      setPlans([]);
      setUpgrade(null);
      setCancellationRequests([]);
      setError(requestError.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403
          ? "Tài khoản không có quyền xem thông tin gói hội viên."
          : requestError.message || "Không tải được thông tin hội viên và thanh toán.");
      return false;
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => active && load());
    return () => { active = false; };
  }, [load]);

  const pendingMembershipPayments = useMemo(() => (payments || []).filter(
    (payment) => isMembershipPayment(payment?.purpose) && payment?.status === "PENDING",
  ), [payments]);

  async function startUpgrade(option, method) {
    if (busyPlanId !== null || pendingMembershipPayments.length > 0) return;
    const where = method === "ONLINE" ? "qua VNPay Sandbox" : "tại quầy";
    if (!window.confirm(`Nâng cấp lên “${option.planName}” ${where} với ${money(option.amount)} cho ${upgrade.remainingDays} ngày còn lại? `
      + `Gói mới có hiệu lực ngay sau khi thanh toán thành công và vẫn hết hạn ngày ${upgrade.currentEndDate}. `
      + (method === "ONLINE" ? "Báo giá có hiệu lực 15 phút." : "Báo giá có hiệu lực đến hết hôm nay."))) return;
    setBusyPlanId(option.planId);
    setError("");
    setNotice("");
    try {
      const payment = await createPaymentApi(token, { method, purpose: "UPGRADE", referenceId: Number(option.planId) });
      setPayments((current) => [payment, ...(current || [])]);
      if (method === "ONLINE") {
        if (!validVnpayUrl(payment.paymentUrl)) {
          await load();
          setError(`Đã tạo thanh toán nâng cấp #${payment.id}, nhưng máy chủ không trả về liên kết VNPay Sandbox hợp lệ. Hãy làm mới trạng thái hoặc hủy yêu cầu rồi thử lại.`);
          return;
        }
        window.location.assign(payment.paymentUrl);
      } else {
        setNotice(`Đã tạo yêu cầu nâng cấp #${payment.id}. Vui lòng thanh toán tại quầy trong hôm nay; gói hiện tại chưa thay đổi.`);
      }
    } catch (requestError) {
      const message = upgradeErrorMessage(requestError);
      await load();
      setError(message);
    } finally {
      setBusyPlanId(null);
    }
  }

  async function startPurchase(plan, method) {
    if (busyPlanId !== null || pendingMembershipPayments.length > 0 || paymentReconciliationRequired) return false;
    setBusyPlanId(plan.id);
    setError("");
    setNotice("");
    try {
      const payment = await createPaymentApi(token, {
        method,
        purpose: "MEMBERSHIP",
        referenceId: Number(plan.id),
        autoRenew: false,
      });
      setPayments((current) => [payment, ...(current || []).filter((item) => item.id !== payment.id)]);
      const refreshedPayments = await load();
      const paymentVisibleInHistory = Array.isArray(refreshedPayments)
        && refreshedPayments.some((item) => Number(item.id) === Number(payment.id));
      if (!paymentVisibleInHistory) {
        setPaymentReconciliationRequired(true);
        setPayments((current) => [payment, ...(current || []).filter((item) => item.id !== payment.id)]);
      }
      if (method === "ONLINE") {
        if (!validVnpayUrl(payment.paymentUrl)) {
          setError(`Đã tạo thanh toán #${payment.id}, nhưng máy chủ không trả về liên kết VNPay Sandbox hợp lệ. Thanh toán vẫn đang chờ; không tạo lại hoặc tự hủy. Hãy làm mới trạng thái và liên hệ lễ tân nếu cần hỗ trợ.`);
          return true;
        }
        window.location.assign(payment.paymentUrl);
        return true;
      } else {
        setNotice(`Đã tạo yêu cầu thanh toán #${payment.id}. Vui lòng thanh toán tại quầy; gói chưa được kích hoạt.`);
        return true;
      }
    } catch (requestError) {
      const message = membershipPurchaseErrorMessage(requestError);
      await load();
      setPaymentReconciliationRequired(true);
      setError(`${message} Làm mới lịch sử thanh toán trước khi thử lại.`);
      return false;
    } finally {
      setBusyPlanId(null);
    }
  }

  function openPurchaseModal(plan) {
    if (loading || busyPlanId !== null || pendingMembershipPayments.length > 0 || paymentReconciliationRequired) return;
    setError("");
    setPaymentModalPlan(plan);
  }

  function closePaymentModal() {
    if (busyPlanId !== null) return;
    setPaymentModalPlan(null);
  }

  async function cancelPendingPayment(payment) {
    if (cancelingId !== null) return;
    if (!window.confirm(`Hủy yêu cầu thanh toán #${payment.id}? Thao tác này không hủy một gói đã được thanh toán.`)) return;
    setCancelingId(payment.id);
    setError("");
    setNotice("");
    try {
      const updated = await cancelPaymentApi(token, payment.id);
      setPayments((current) => (current || []).map((item) => item.id === updated.id ? updated : item));
      setNotice(`Yêu cầu thanh toán #${updated.id} đã được hủy. Bạn có thể tạo yêu cầu mới.`);
    } catch (requestError) {
      setError(requestError.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403
          ? "Bạn chỉ có thể hủy thanh toán của chính mình."
          : requestError.message || "Không thể hủy yêu cầu thanh toán.");
      await load();
    } finally {
      setCancelingId(null);
    }
  }

  async function toggleAutoRenew(membership) {
    if (togglingId !== null) return;
    const enable = !membership.autoRenew;
    if (!enable && !window.confirm("Tắt tự gia hạn? Yêu cầu gia hạn tự động chưa thanh toán (nếu có) sẽ bị hủy.")) return;
    setTogglingId(membership.id);
    setError("");
    setNotice("");
    try {
      await setAutoRenewApi(token, membership.id, enable);
      await load();
      setNotice(enable
        ? `Đã bật tự gia hạn. ${RENEWAL_NOTICE_DAYS} ngày trước khi gói “${membership.planName}” hết hạn, hệ thống sẽ tạo yêu cầu gia hạn và gửi thông báo cho bạn.`
        : "Đã tắt tự gia hạn.");
    } catch (requestError) {
      setError(requestError.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.message || "Không thể thay đổi tự gia hạn.");
    } finally {
      setTogglingId(null);
    }
  }

  // A pending counter request (e.g. an auto-renewal) can be paid online instead: cancel it, then create the ONLINE one.
  async function switchToVnpay(payment) {
    if (cancelingId !== null || busyPlanId !== null) return;
    if (!window.confirm(`Chuyển yêu cầu #${payment.id} sang thanh toán VNPay Sandbox? Yêu cầu tại quầy sẽ được hủy và thay bằng yêu cầu online cùng số tiền.`)) return;
    setCancelingId(payment.id);
    setError("");
    setNotice("");
    try {
      await cancelPaymentApi(token, payment.id);
      const online = await createPaymentApi(token, autoRenewalOnlineReplacementPayload(payment));
      if (!validVnpayUrl(online.paymentUrl)) {
        await load();
        setError(`Đã tạo thanh toán #${online.id}, nhưng máy chủ không trả về liên kết VNPay Sandbox hợp lệ. Hãy làm mới trạng thái hoặc liên hệ lễ tân.`);
        return;
      }
      window.location.assign(online.paymentUrl);
    } catch (requestError) {
      const message = membershipPurchaseErrorMessage(requestError);
      await load();
      setError(message);
    } finally {
      setCancelingId(null);
    }
  }

  async function requestCancellation(membership) {
    if (togglingId !== null) return;
    const reason = window.prompt(`Gửi yêu cầu hủy gói “${membership.planName}” (${formatDate(membership.startDate)} → ${formatDate(membership.endDate)})? `
      + "Nếu được duyệt, khoản đã thanh toán cho gói này được hoàn 100% tại quầy. Lý do (không bắt buộc):", "");
    if (reason === null) return;
    setTogglingId(membership.id);
    setError("");
    setNotice("");
    try {
      await requestMembershipCancellationApi(token, membership.id, reason.trim() || null);
      await load();
      setNotice(`Đã gửi yêu cầu hủy gói “${membership.planName}”. Gói vẫn giữ nguyên cho đến khi nhân viên duyệt; bạn sẽ nhận thông báo kết quả.`);
    } catch (requestError) {
      setError(cancellationRequestErrorMessage(requestError));
      await load();
    } finally {
      setTogglingId(null);
    }
  }

  async function withdrawRequest(request) {
    if (togglingId !== null) return;
    if (!window.confirm(`Rút yêu cầu hủy gói “${request.planName}”? Gói sẽ tiếp tục như bình thường.`)) return;
    setTogglingId(request.membershipId);
    setError("");
    setNotice("");
    try {
      await withdrawCancellationRequestApi(token, request.id);
      await load();
      setNotice(`Đã rút yêu cầu hủy gói “${request.planName}”.`);
    } catch (requestError) {
      setError(cancellationRequestErrorMessage(requestError));
      await load();
    } finally {
      setTogglingId(null);
    }
  }

  const today = todayInVietnam();
  const sections = membershipSections(memberships, today);
  const currentMemberships = sections.current;
  const membershipChain = activeMembershipChain(memberships, today);
  const cancellableId = cancellableMembershipId(membershipChain, today);
  const pendingRequestFor = (membershipId) => cancellationRequests.find(
    (request) => request.membershipId === membershipId && request.status === "PENDING");
  const renderMembershipRows = (items) => items.map((membership) => {
    const last = membership.id === membershipChain[membershipChain.length - 1]?.id;
    const running = currentMemberships.some((item) => item.id === membership.id);
    const pendingRequest = membership.id === cancellableId ? pendingRequestFor(membership.id) : null;
    const requestable = membership.id === cancellableId && !pendingRequest && canRequestCancellation(membership, today);
    return <div className="mm-chain-row" key={membership.id}>
      <div className="mm-chain-plan"><b>{membership.planName || "Membership"}</b>
        <small>{formatDate(membership.startDate)} → {formatDate(membership.endDate)}</small></div>
      <span className={"mm-chain-status " + (running ? "running" : "upcoming")}>{running ? statusLabel[membership.status] || membership.status : "Sắp có hiệu lực — chưa có hiệu lực hôm nay"}</span>
      <div className="mm-chain-renew">
        {last ? <button type="button" className={membership.autoRenew ? "" : "secondary"} disabled={togglingId !== null}
          aria-pressed={Boolean(membership.autoRenew)} onClick={() => void toggleAutoRenew(membership)}>
          {togglingId === membership.id ? "Đang lưu…" : membership.autoRenew ? "Tự gia hạn: Bật" : "Tự gia hạn: Tắt"}
        </button> : <small>Đã có gói nối tiếp</small>}
      </div>
      <div className="mm-chain-cancel">
        {pendingRequest ? <>
          <button type="button" className="secondary" disabled={togglingId !== null} onClick={() => void withdrawRequest(pendingRequest)}>Rút yêu cầu hủy</button>
          <small>Đang chờ nhân viên duyệt</small>
        </> : requestable ? <>
          <button type="button" className="secondary" disabled={togglingId !== null} onClick={() => void requestCancellation(membership)}>Yêu cầu hủy gói</button>
          <small>Hạn gửi: hết ngày {formatDate(lastRequestDay(membership.startDate))}</small>
        </> : membership.id === cancellableId ? <small>Đã quá hạn yêu cầu hủy</small> : null}
      </div>
    </div>;
  });

  return <div className="layout">
    <Sidebar role={role} activePage="member-membership" onLogout={onLogout} onNavigate={onNavigate} />
    <main className="main">
      <header className="member-membership-header">
        <div><h1>Gói thành viên</h1><p className="subtitle">Mua mới hoặc gia hạn tại quầy hay qua VNPay Sandbox. Gói chỉ được kích hoạt sau khi máy chủ xác nhận thanh toán thành công.</p></div>
        <button type="button" onClick={() => void load()} disabled={loading}>{loading ? "Đang tải…" : "Làm mới"}</button>
      </header>
      {error && <div className="banner error" role="alert">{error}</div>}
      {notice && <div className="banner success" role="status">{notice}</div>}

      <section className="dash-card">
        <h2>Gói đang có hiệu lực</h2>
        {loading ? <p role="status">Đang tải thông tin…</p> : sections.current.length === 0
          ? <p className="dash-empty">Hôm nay bạn không có gói thành viên nào đang có hiệu lực.</p>
          : <div className="mm-chain">{renderMembershipRows(sections.current)}</div>}
        {hasOverlappingEffectiveMemberships(sections) && <p className="banner info" role="status">
          Có nhiều gói thành viên cùng có hiệu lực hôm nay. Dữ liệu có thể bị chồng lấn; hệ thống không tự thay đổi gói nào.
        </p>}
      </section>

      <section className="dash-card">
        <h2>Gói sắp có hiệu lực</h2>
        {loading ? <p role="status">Đang tải thông tin…</p> : sections.upcoming.length === 0
          ? <p className="dash-empty">Không có gói nào đang chờ đến ngày bắt đầu.</p>
          : <><p className="subtitle">Các gói này được xếp lịch cho ngày trong tương lai và chưa có hiệu lực hôm nay.</p>
            <div className="mm-chain">{renderMembershipRows(sections.upcoming)}</div></>}
      </section>

      <section className="dash-card">
        <h2>Lịch sử gói thành viên</h2>
        {loading ? <p role="status">Đang tải thông tin…</p> : sections.history.length === 0
          ? <p className="dash-empty">Chưa có gói thành viên trong lịch sử.</p>
          : <div className="mm-chain">{sections.history.map((membership) => <div className="mm-chain-row" key={membership.id}>
            <div className="mm-chain-plan"><b>{membership.planName || "Membership"}</b>
              <small>{formatDate(membership.startDate)} → {formatDate(membership.endDate)}</small></div>
            <span className="mm-chain-status">{String(membership.status).toUpperCase() === "ACTIVE" && membership.endDate && String(membership.endDate) <= today
              ? `Hết hiệu lực (trạng thái ${membership.status})`
              : statusLabel[String(membership.status).toUpperCase()] || membership.status || "Không rõ trạng thái"}</span>
            <small>Gói trong lịch sử</small>
          </div>)}</div>}
      </section>

      {membershipChain.length > 0 && <section className="dash-card">
        <h2>Tự gia hạn và yêu cầu hủy</h2>
        <ul className="mm-chain-notes">
          <li>Tự gia hạn áp dụng cho gói cuối: {RENEWAL_NOTICE_DAYS} ngày trước khi hết hạn, hệ thống tạo yêu cầu gia hạn và báo cho bạn. Bạn cần thanh toán tại quầy hoặc qua VNPay; chưa thanh toán thì yêu cầu tự hủy.</li>
          <li>Gói đang có hiệu lực không thể hủy và không hoàn tiền. Có thể yêu cầu hủy gói chưa bắt đầu (gói cuối) chậm nhất {CANCELLATION_NOTICE_HOURS} giờ trước ngày bắt đầu; nếu được duyệt sẽ hoàn 100% tại quầy.</li>
        </ul>
      </section>}
      {!loading && upgrade?.currentMembershipId && <section className="dash-card">
        <h2>Nâng cấp gói</h2>
        <p className="subtitle">Gói mới có hiệu lực ngay và giữ nguyên ngày hết hạn {upgrade.currentEndDate}. Bạn chỉ trả phần chênh lệch cho {upgrade.remainingDays} ngày còn lại (tính tháng 30 ngày, làm tròn lên 1.000 ₫). Không hạ cấp giữa kỳ và không hoàn tiền phần chênh lệch.</p>
        {upgrade.blockedReason ? <p className="dash-empty">{upgradeBlockedMessage(upgrade.blockedReason)}</p>
          : upgrade.options.length === 0 ? <p className="dash-empty">Bạn đang dùng gói cao nhất có thể nâng cấp.</p>
            : <div className="pf-plans">{upgrade.options.map((option) => <article key={option.planId} className="pf-plan">
              <h2>{upgrade.currentPlanName} → {option.planName}</h2><strong>{money(option.amount)}</strong>
              <small>Giá gói {money(option.planPrice)} / {option.durationMonths} tháng · Giảm khóa học {option.courseDiscountPercent}% · Giảm PT {option.personalTrainingDiscountPercent}%</small>
              <div className="member-membership-payment-actions">
                <button type="button" disabled={busyPlanId !== null || pendingMembershipPayments.length > 0}
                  onClick={() => void startUpgrade(option, "COUNTER")}>
                  {busyPlanId === option.planId ? "Đang tạo yêu cầu…" : "Nâng cấp · tại quầy"}
                </button>
                <button type="button" disabled={busyPlanId !== null || pendingMembershipPayments.length > 0}
                  onClick={() => void startUpgrade(option, "ONLINE")}>
                  {busyPlanId === option.planId ? "Đang tạo yêu cầu…" : "Nâng cấp · VNPay Sandbox"}
                </button>
              </div>
            </article>)}</div>}
      </section>}

      <section className="dash-card">
        <h2>Thanh toán đang chờ</h2>
        {loading ? <p role="status">Đang tải lịch sử thanh toán…</p> : pendingMembershipPayments.length === 0
          ? <p className="dash-empty">Không có yêu cầu thanh toán gói nào đang chờ.</p>
          : <ul className="dash-rows">{pendingMembershipPayments.map((payment) => {
            const plan = (plans || []).find((item) => Number(item.id) === Number(payment.referenceId));
            return <li key={payment.id}>
              <span><b>{payment.purpose === "UPGRADE" ? "Nâng cấp lên " : isAutoRenewalPayment(payment) ? "Gia hạn tự động · " : ""}{plan?.name || `Gói #${payment.referenceId}`}</b><br /><small>Thanh toán #{payment.id} · {money(payment.amount)} · {payment.method === "ONLINE" ? "VNPay Sandbox · trạng thái do máy chủ xác nhận" : "Thanh toán tại quầy · chờ nhân viên xác nhận"}</small></span>
              {payment.purpose === "MEMBERSHIP" && payment.method === "COUNTER" && <button type="button" disabled={cancelingId !== null || busyPlanId !== null} onClick={() => void switchToVnpay(payment)}>
                Chuyển sang VNPay
              </button>}
              <button type="button" disabled={cancelingId !== null} onClick={() => void cancelPendingPayment(payment)}>
                {cancelingId === payment.id ? "Đang hủy…" : "Hủy yêu cầu"}
              </button>
            </li>;
          })}</ul>}
        {pendingMembershipPayments.length > 0 && <p className="banner info" role="note">Không thể tạo thêm thanh toán Membership khi còn yêu cầu đang chờ. Với VNPay, hãy kiểm tra trạng thái trước khi thử lại; việc quay về từ cổng thanh toán không tự xác nhận thành công.</p>}
      </section>

      {!loading && cancellationRequests.length > 0 && <section className="dash-card">
        <h2>Yêu cầu hủy gói</h2>
        <ul className="dash-rows">{cancellationRequests.map((request) => <li key={request.id}>
          <span><b>{request.planName}</b> · {formatDate(request.startDate)} → {formatDate(request.endDate)}<br />
            <small>Gửi lúc {formatDateTime(request.requestedAt)}{request.reason ? ` · Lý do: ${request.reason}` : ""}
              {request.decidedAt && request.status !== "WITHDRAWN" ? ` · Xử lý lúc ${formatDateTime(request.decidedAt)}` : ""}
              {request.decisionNote ? ` · Ghi chú: ${request.decisionNote}` : ""}</small></span>
          <em>{requestStatusLabel[request.status] || request.status}</em>
        </li>)}</ul>
      </section>}

      <section className="dash-card">
        <h2>Lịch sử thanh toán</h2>
        {loading ? <p role="status">Đang tải lịch sử thanh toán…</p> : !payments?.length
          ? <p className="dash-empty">Bạn chưa có giao dịch nào.</p>
          : <div className="member-payment-history"><table>
            <thead><tr><th>Mã</th><th>Thời gian</th><th>Nội dung</th><th>Hình thức</th><th>Số tiền</th><th>Trạng thái</th></tr></thead>
            <tbody>{payments.map((payment) => <tr key={payment.id}>
              <td>#{payment.id}</td>
              <td>{formatDateTime(payment.createdAt)}</td>
              <td>{paymentPurposeLabel[payment.purpose] || payment.purpose}{isAutoRenewalPayment(payment) ? " (tự gia hạn)" : ""}</td>
              <td>{paymentMethodLabel[payment.method] || payment.method}</td>
              <td>{money(payment.amount)}</td>
              <td><span className={`member-payment-status ${String(payment.status).toLowerCase()}`}>{paymentStatusLabel[payment.status] || payment.status}</span>
                {payment.status === "REFUNDED" && payment.refundedAt && <small>{formatDateTime(payment.refundedAt)}</small>}
                {payment.status === "REFUND_REQUIRED" && <small>Liên hệ lễ tân để nhận tiền</small>}</td>
            </tr>)}</tbody>
          </table></div>}
      </section>

      <h2 style={{ margin: "26px 0 14px", fontSize: 18 }}>Các gói đang mở</h2>
      {loading ? <div className="queue-state" role="status">Đang tải gói…</div>
        : !plans?.length ? <p className="dash-empty">Hiện chưa có gói ACTIVE nào.</p>
          : <div className="pf-plans">{plans.map((plan) => <article key={plan.id} className="pf-plan">
            <h2>{plan.name}</h2><strong>{money(plan.price)}</strong>
            <small>Thời hạn: {plan.durationMonths} tháng · Giảm khóa học {plan.courseDiscountPercent ?? 0}% · {plan.allowOneOnOneCoaching
              ? `Đặt PT 1-1, giảm ${plan.personalTrainingDiscountPercent ?? 0}%` : "Không gồm PT 1-1"}</small>
            {plan.description && <p>{plan.description}</p>}
            <div className="member-membership-payment-actions">
              <button type="button" disabled={busyPlanId !== null || pendingMembershipPayments.length > 0 || paymentReconciliationRequired}
                onClick={() => openPurchaseModal(plan)}>
                {busyPlanId === plan.id ? "Đang tạo yêu cầu…" : currentMemberships.length ? "Mua / gia hạn" : "Mua gói"}
              </button>
            </div>
          </article>)}</div>}
      <p className="banner info" role="note">VNPay Sandbox là môi trường thử nghiệm. Trạng thái Membership chỉ thay đổi theo xác nhận thanh toán từ máy chủ.</p>
    </main>
    {paymentModalPlan && <PaymentMethodModal
      amount={money(paymentModalPlan.price)}
      context={`Gói ${paymentModalPlan.name} · ${paymentModalPlan.durationMonths} tháng · ${currentMemberships.length ? "mua hoặc gia hạn" : "mua mới"}`}
      busy={busyPlanId === paymentModalPlan.id}
      error={error}
      canContinue={!paymentReconciliationRequired && pendingMembershipPayments.length === 0}
      onCancel={closePaymentModal}
      onSuccess={() => setPaymentModalPlan(null)}
      onContinue={(method) => startPurchase(paymentModalPlan, method)}
    />}
  </div>;
}
