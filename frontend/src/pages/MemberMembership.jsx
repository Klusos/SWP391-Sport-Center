import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
import { filterPaymentHistory, paginateItems, sortMembershipHistory } from "../membershipHistoryModel";
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
  const [membershipHistoryPage, setMembershipHistoryPage] = useState(0);
  const [paymentHistoryPage, setPaymentHistoryPage] = useState(0);
  const [paymentStatusFilter, setPaymentStatusFilter] = useState("");
  const [paymentPurposeFilter, setPaymentPurposeFilter] = useState("");
  const [upgradeModal, setUpgradeModal] = useState(null);
  const upgradeDialogRef = useRef(null);
  const upgradeSubmitLock = useRef(false);
  const upgradeBusyRef = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    setMembershipHistoryPage(0);
    setPaymentHistoryPage(0);
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

  useEffect(() => {
    if (!upgradeModal) return undefined;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    (upgradeDialogRef.current?.querySelector('input[name="upgrade-payment-method"]:checked')
      || upgradeDialogRef.current?.querySelector('input[name="upgrade-payment-method"]'))?.focus();

    function handleKeyDown(event) {
      if (event.key === "Escape" && !upgradeBusyRef.current) {
        event.preventDefault();
        setUpgradeModal(null);
        return;
      }
      if (event.key !== "Tab") return;
      const focusable = [...(upgradeDialogRef.current?.querySelectorAll("button:not(:disabled), input:not(:disabled)") || [])];
      if (!focusable.length) {
        event.preventDefault();
        upgradeDialogRef.current?.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [upgradeModal]);
  const pendingMembershipPayments = useMemo(() => (payments || []).filter(
    (payment) => isMembershipPayment(payment?.purpose) && payment?.status === "PENDING",
  ), [payments]);

  async function startUpgrade(option, method) {
    if (busyPlanId !== null || pendingMembershipPayments.length > 0) return false;
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
          return false;
        }
        setUpgradeModal(null);
        window.location.assign(payment.paymentUrl);
        return true;
      }
      setNotice(`Đã tạo yêu cầu nâng cấp #${payment.id}. Vui lòng thanh toán tại quầy trong hôm nay; gói hiện tại chưa thay đổi.`);
      setUpgradeModal(null);
      return true;
    } catch (requestError) {
      const message = upgradeErrorMessage(requestError);
      await load();
      setError(message);
      return false;
    } finally {
      setBusyPlanId(null);
      upgradeSubmitLock.current = false;
      upgradeBusyRef.current = false;
    }
  }

  function openUpgradeModal(option) {
    if (busyPlanId !== null || pendingMembershipPayments.length > 0) return;
    setError("");
    setNotice("");
    setUpgradeModal({
      option,
      method: "",
      currentPlanName: upgrade.currentPlanName,
      remainingDays: upgrade.remainingDays,
      currentEndDate: upgrade.currentEndDate,
    });
  }

  function closeUpgradeModal() {
    if (busyPlanId !== null || upgradeBusyRef.current) return;
    setUpgradeModal(null);
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
  const membershipHistory = sortMembershipHistory(sections.history);
  const pagedMembershipHistory = paginateItems(membershipHistory, membershipHistoryPage, 5);
  const filteredPaymentHistory = filterPaymentHistory(payments, { status: paymentStatusFilter, purpose: paymentPurposeFilter });
  const pagedPaymentHistory = paginateItems(filteredPaymentHistory, paymentHistoryPage, 10);
  const membershipChain = activeMembershipChain(memberships, today);
  const cancellableId = cancellableMembershipId(membershipChain, today);
  const pendingRequestFor = (membershipId) => cancellationRequests.find(
    (request) => request.membershipId === membershipId && request.status === "PENDING");
  const renderMembershipBenefits = (plan) => {
    if (!plan || (plan.courseDiscountPercent == null && plan.allowOneOnOneCoaching == null && plan.personalTrainingDiscountPercent == null && plan.allowPersonalTrainingPlan == null)) {
      return <p className="membership-neutral-note">Chưa có dữ liệu quyền lợi cho gói Membership này. Trạng thái bán của gói không quyết định hiệu lực Membership hiện có.</p>;
    }
    return <ul className="membership-benefit-list">
      {plan.courseDiscountPercent != null && <li><i className="bi bi-check-circle-fill" aria-hidden="true" /><span>Giảm {plan.courseDiscountPercent}% khi đăng ký khóa học</span></li>}
      {plan.allowOneOnOneCoaching === true && plan.personalTrainingDiscountPercent != null && <li><i className="bi bi-check-circle-fill" aria-hidden="true" /><span>Giảm {plan.personalTrainingDiscountPercent}% khi đặt lịch PT 1-1</span></li>}
      {plan.allowOneOnOneCoaching === true && plan.personalTrainingDiscountPercent == null && <li><i className="bi bi-info-circle-fill" aria-hidden="true" /><span>Chưa có dữ liệu mức giảm PT 1-1.</span></li>}
      {plan.allowOneOnOneCoaching === false && <li><i className="bi bi-info-circle-fill" aria-hidden="true" /><span>Gói này không bao gồm quyền lợi PT 1-1</span></li>}
      {plan.allowPersonalTrainingPlan === true && <li><i className="bi bi-check-circle-fill" aria-hidden="true" /><span>Được phép tạo Individual Training Plan</span></li>}
      {plan.allowPersonalTrainingPlan === false && <li><i className="bi bi-info-circle-fill" aria-hidden="true" /><span>Gói này không bao gồm quyền tạo Individual Training Plan</span></li>}
    </ul>;
  };
  const renderMembershipRows = (items) => items.map((membership) => {
    const last = membership.id === membershipChain[membershipChain.length - 1]?.id;
    const pendingRequest = membership.id === cancellableId ? pendingRequestFor(membership.id) : null;
    const requestable = membership.id === cancellableId && !pendingRequest && canRequestCancellation(membership, today);
    return <div className="mm-chain-row" key={membership.id}>
      <div className="mm-chain-plan"><b>{membership.planName || "Membership"}</b>
        <small>Mã Membership #{membership.id}</small>
        <small>{formatDate(membership.startDate)} → {formatDate(membership.endDate)} (ngày kết thúc không bao gồm)</small>
      </div>
      <span className="mm-chain-status upcoming">Sắp có hiệu lực — chưa có hiệu lực hôm nay</span>
      <div className="mm-chain-renew">
        <small>Tự gia hạn: {membership.autoRenew ? "Bật" : "Tắt"}</small>
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

  return (
    <div className="layout">
      <Sidebar role={role} activePage="member-membership" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main member-membership-page">
        <header className="member-membership-header">
          <div>
            <p className="membership-eyebrow">HỘI VIÊN</p>
            <h1>Gói thành viên</h1>
            <p className="subtitle">Quản lý quyền lợi, yêu cầu thanh toán và lịch sử Membership của bạn.</p>
          </div>
          <button type="button" className="membership-refresh" onClick={() => void load()} disabled={loading}>
            {loading ? "Đang tải…" : "Làm mới"}
          </button>
        </header>
        {error && <div className="banner error" role="alert">{error}</div>}
        {notice && <div className="banner success" role="status">{notice}</div>}

        <div className="membership-overview-grid">
          <section className="dash-card membership-current-card" aria-labelledby="membership-current-title">
            <div className="membership-section-heading"><div><p className="membership-eyebrow">TÌNH TRẠNG HỘI VIÊN</p><h2 id="membership-current-title">Gói đang có hiệu lực</h2></div></div>
            {loading ? <p role="status">Đang tải thông tin Membership…</p> : currentMemberships.length === 0
              ? <div className="membership-empty-current"><p>Bạn chưa có Membership đang hiệu lực.</p><span>Membership sắp có hiệu lực và lịch sử được hiển thị bên dưới.</span></div>
              : <div className="membership-current-details">{currentMemberships.map((membership) => {
                  const last = membership.id === membershipChain[membershipChain.length - 1]?.id;
                  const pendingRequest = membership.id === cancellableId ? pendingRequestFor(membership.id) : null;
                  const requestable = membership.id === cancellableId && !pendingRequest && canRequestCancellation(membership, today);
                  const remainingDays = membership.endDate
                    ? Math.max(0, Math.round((Date.parse(`${membership.endDate}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / 86400000))
                    : null;
                  return <article className="membership-current-entry" key={membership.id}>
                    <div className="membership-current-entry-heading"><strong className="membership-current-name">{membership.planName || "—"}</strong><span className="membership-status-badge active">{statusLabel[membership.status] || membership.status}</span></div>
                    <div className="membership-date-pair">
                      <div><span className="membership-field-label">Ngày bắt đầu</span><strong>{formatDate(membership.startDate)}</strong></div>
                      <div><span className="membership-field-label">Ngày hết hạn (không bao gồm)</span><strong>{formatDate(membership.endDate)}</strong></div>
                    </div>
                    <div className="membership-current-meta"><span>Mã Membership #{membership.id}</span>{remainingDays !== null && <span>Còn {remainingDays} ngày hiệu lực</span>}</div>
                    <div className="membership-current-actions">
                      <div className="membership-auto-renew-control"><span>Tự gia hạn: <strong>{membership.autoRenew ? "Bật" : "Tắt"}</strong></span>
                        {last && <button type="button" className={membership.autoRenew ? "" : "secondary"} disabled={togglingId !== null} aria-pressed={Boolean(membership.autoRenew)} onClick={() => void toggleAutoRenew(membership)}>{togglingId === membership.id ? "Đang lưu…" : membership.autoRenew ? "Tắt tự gia hạn" : "Bật tự gia hạn"}</button>}
                        {!last && <small>Đã có Membership nối tiếp</small>}
                      </div>
                      <div className="mm-chain-cancel">{pendingRequest ? <><button type="button" className="secondary" disabled={togglingId !== null} onClick={() => void withdrawRequest(pendingRequest)}>Rút yêu cầu hủy</button><small>Đang chờ nhân viên duyệt</small></> : requestable ? <><button type="button" className="secondary" disabled={togglingId !== null} onClick={() => void requestCancellation(membership)}>Yêu cầu hủy gói</button><small>Hạn gửi: hết ngày {formatDate(lastRequestDay(membership.startDate))}</small></> : membership.id === cancellableId ? <small>Đã quá hạn yêu cầu hủy</small> : null}</div>
                    </div>
                  </article>;
                })}</div>}
            {hasOverlappingEffectiveMemberships(sections) && <p className="banner info" role="status">Có nhiều Membership cùng hiệu lực hôm nay. Hệ thống không tự thay đổi dữ liệu; vui lòng liên hệ lễ tân nếu cần kiểm tra.</p>}
            {membershipChain.length > 0 && <p className="membership-policy-note">Bật tự gia hạn có thể tạo yêu cầu thanh toán đang chờ gần ngày hết hạn. Hệ thống không tự động trừ tiền hoặc kích hoạt gói gia hạn nếu chưa có thanh toán thành công. Yêu cầu hủy Membership sắp có hiệu lực cần gửi trước ít nhất {CANCELLATION_NOTICE_HOURS} giờ.</p>}
          </section>

          <section className="dash-card membership-benefits-card" aria-labelledby="membership-benefits-title">
            <div className="membership-section-heading"><div><p className="membership-eyebrow">QUYỀN LỢI</p><h2 id="membership-benefits-title">Quyền lợi Membership</h2></div><span className="membership-icon" aria-hidden="true"><i className="bi bi-award-fill" /></span></div>
            {loading ? <p role="status">Đang tải quyền lợi…</p> : currentMemberships.length === 0
              ? <p className="membership-neutral-note">Quyền lợi theo gói sẽ hiển thị khi bạn có Membership đang hiệu lực.</p>
              : <div className="membership-benefits-groups">{currentMemberships.map((membership) => {
                  const plan = (plans || []).find((item) => Number(item.id) === Number(membership.planId)) || null;
                  return <div className="membership-benefit-group" key={membership.id}>{currentMemberships.length > 1 && <h3>{membership.planName || "Membership"} · #{membership.id}</h3>}{renderMembershipBenefits(plan)}</div>;
                })}</div>}
          </section>
        </div>

        <section className="dash-card membership-queue-section" aria-labelledby="membership-queue-title">
          <div className="membership-section-heading">
            <div><p className="membership-eyebrow">ĐÃ ĐƯỢC GHI NHẬN</p><h2 id="membership-queue-title">Membership sắp có hiệu lực</h2></div>
            {!loading && <span className="membership-count-badge">{sections.upcoming.length}</span>}
          </div>
          {loading ? <p role="status">Đang tải Membership…</p> : sections.upcoming.length === 0
            ? <p className="dash-empty">Không có Membership nào đang chờ đến ngày bắt đầu.</p>
            : <div className="mm-chain">{renderMembershipRows(sections.upcoming)}</div>}
        </section>

        {!loading && upgrade?.currentMembershipId && <section className="dash-card" aria-labelledby="membership-upgrade-title">
          <div className="membership-section-heading"><div><p className="membership-eyebrow">LỰA CHỌN TỪ HỆ THỐNG</p><h2 id="membership-upgrade-title">Nâng cấp Membership</h2></div></div>
          {upgrade.blockedReason
            ? <p className="dash-empty">{upgradeBlockedMessage(upgrade.blockedReason)}</p>
            : !upgrade.options?.length
              ? <p className="dash-empty">Hiện không có lựa chọn nâng cấp nào được hệ thống cung cấp.</p>
              : <div className="membership-card-grid">{upgrade.options.map((option) => <article className="membership-plan-card membership-upgrade-card" key={option.planId}>
                  <div><span className="membership-card-kicker">NÂNG CẤP ĐƯỢC PHÉP</span><h3>{upgrade.currentPlanName} → {option.planName}</h3></div>
                  <strong className="membership-price">{money(option.amount)}</strong>
                  <p>Giá gói {money(option.planPrice)} · {option.durationMonths} tháng · Giảm khóa học {option.courseDiscountPercent}% · Giảm PT {option.personalTrainingDiscountPercent}%</p>
                  <button type="button" disabled={busyPlanId !== null || pendingMembershipPayments.length > 0} onClick={() => openUpgradeModal(option)}>
                    {busyPlanId === option.planId ? "Đang tạo yêu cầu…" : "Nâng cấp"}
                  </button>
                </article>)}</div>}
        </section>}

        <section className="dash-card membership-pending-section" aria-labelledby="membership-pending-title">
          <div className="membership-section-heading"><div><p className="membership-eyebrow">TRẠNG THÁI TỪ MÁY CHỦ</p><h2 id="membership-pending-title">Thanh toán Membership đang chờ</h2></div>
            {!loading && <span className={pendingMembershipPayments.length ? "membership-count-badge pending" : "membership-count-badge"}>{pendingMembershipPayments.length}</span>}
          </div>
          {loading ? <p role="status">Đang tải thanh toán…</p> : pendingMembershipPayments.length === 0
            ? <p className="dash-empty">Không có yêu cầu thanh toán Membership đang chờ.</p>
            : <div className="membership-pending-list">{pendingMembershipPayments.map((payment) => {
                const plan = (plans || []).find((item) => Number(item.id) === Number(payment.referenceId));
                const title = payment.purpose === "UPGRADE"
                  ? "Nâng cấp lên " + (plan?.name || "gói #" + payment.referenceId)
                  : isAutoRenewalPayment(payment)
                    ? "Gia hạn " + (plan?.name || "gói #" + payment.referenceId)
                    : plan?.name || "Gói #" + payment.referenceId;
                return <article className="membership-pending-card" key={payment.id}>
                  <div className="membership-pending-main"><div><span className="membership-field-label">{title}</span><strong>Thanh toán #{payment.id}</strong></div>
                    <span className={"member-payment-status " + String(payment.status).toLowerCase()}>{paymentStatusLabel[payment.status] || payment.status}</span></div>
                  <dl className="membership-payment-facts"><div><dt>Số tiền</dt><dd>{money(payment.amount)}</dd></div><div><dt>Phương thức</dt><dd>{paymentMethodLabel[payment.method] || payment.method}</dd></div><div><dt>Tạo lúc</dt><dd>{formatDateTime(payment.createdAt)}</dd></div></dl>
                  <div className="membership-payment-actions">
                    {payment.purpose === "MEMBERSHIP" && payment.method === "COUNTER" && <button type="button" className="button-secondary" disabled={cancelingId !== null || busyPlanId !== null} onClick={() => void switchToVnpay(payment)}>Chuyển sang VNPay</button>}
                    {payment.method === "ONLINE" && <button type="button" disabled={cancelingId !== null} onClick={() => { if (validVnpayUrl(payment.paymentUrl)) window.location.assign(payment.paymentUrl); else setError("Liên kết VNPay không hợp lệ. Vui lòng làm mới trạng thái thanh toán."); }}>Tiếp tục thanh toán</button>}
                    <button type="button" className="button-secondary" disabled={cancelingId !== null || busyPlanId !== null} onClick={() => void cancelPendingPayment(payment)}>{cancelingId === payment.id ? "Đang hủy…" : "Hủy yêu cầu"}</button>
                  </div>
                </article>;
              })}</div>}
          {pendingMembershipPayments.length > 0 && <p className="membership-policy-note">Không thể tạo thêm yêu cầu Membership khi vẫn còn yêu cầu đang chờ. Hãy kiểm tra trạng thái thanh toán trước khi thử lại.</p>}
        </section>

        <section className="dash-card" aria-labelledby="membership-plans-title">
          <div className="membership-section-heading"><div><p className="membership-eyebrow">GÓI ĐANG MỞ BÁN</p><h2 id="membership-plans-title">Các gói thành viên</h2></div></div>
          {loading ? <p role="status">Đang tải danh sách gói…</p> : !plans?.length
            ? <p className="dash-empty">Hiện chưa có gói ACTIVE nào được mở bán.</p>
            : <div className="membership-card-grid">{plans.map((plan) => <article className="membership-plan-card" key={plan.id}>
                <div><span className="membership-card-kicker">{plan.durationMonths} tháng</span><h3>{plan.name}</h3></div>
                <strong className="membership-price">{money(plan.price)}</strong>
                {plan.description && <p>{plan.description}</p>}
                <ul className="membership-plan-benefits">
                  {plan.courseDiscountPercent != null && <li>Giảm khóa học {plan.courseDiscountPercent}%</li>}
                  {plan.allowOneOnOneCoaching === true && plan.personalTrainingDiscountPercent != null && <li>Giảm PT 1-1 {plan.personalTrainingDiscountPercent}%</li>}
                  {plan.allowOneOnOneCoaching === false && <li>Không bao gồm PT 1-1</li>}
                  {plan.allowPersonalTrainingPlan === true && <li>Được phép tạo Individual Training Plan</li>}
                  {plan.allowPersonalTrainingPlan === false && <li>Không bao gồm Individual Training Plan</li>}
                </ul>
                <button type="button" disabled={busyPlanId !== null || pendingMembershipPayments.length > 0 || paymentReconciliationRequired} onClick={() => openPurchaseModal(plan)}>
                  {busyPlanId === plan.id ? "Đang tạo yêu cầu…" : currentMemberships.length ? "Mua / gia hạn" : "Mua gói"}
                </button>
              </article>)}</div>}
          <p className="membership-policy-note">Membership chỉ được kích hoạt hoặc gia hạn sau khi máy chủ xác nhận thanh toán thành công.</p>
        </section>

        <section className="dash-card" aria-labelledby="membership-history-title">
          <div className="membership-section-heading"><div><p className="membership-eyebrow">LỊCH SỬ</p><h2 id="membership-history-title">Lịch sử Membership</h2></div></div>
          {loading ? <p role="status">Đang tải lịch sử…</p> : sections.history.length === 0
            ? <p className="dash-empty">Chưa có Membership trong lịch sử.</p>
            : <><div className="mm-chain">{pagedMembershipHistory.items.map((membership) => <div className="mm-chain-row history-row" key={membership.id}>
                <div className="mm-chain-plan"><b>{membership.planName || "Membership"}</b><small>{formatDate(membership.startDate)} → {formatDate(membership.endDate)}</small></div>
                <span className="mm-chain-status">{String(membership.status).toUpperCase() === "ACTIVE" && membership.endDate && String(membership.endDate) <= today ? "Hết hiệu lực (trạng thái " + membership.status + ")" : statusLabel[String(membership.status).toUpperCase()] || membership.status || "Không rõ trạng thái"}</span>
                <span>Mã Membership #{membership.id}</span>
              </div>)}</div><HistoryPagination label="Membership" page={pagedMembershipHistory.page} pageCount={pagedMembershipHistory.pageCount} onPageChange={setMembershipHistoryPage} /> </>}
        </section>

        <section className="dash-card" aria-labelledby="membership-payment-history-title">
          <div className="membership-section-heading"><div><p className="membership-eyebrow">ĐỐI SOÁT GIAO DỊCH</p><h2 id="membership-payment-history-title">Lịch sử thanh toán</h2></div></div>
          {loading ? <p role="status">Đang tải lịch sử thanh toán…</p> : !payments?.length
            ? <p className="dash-empty">Bạn chưa có giao dịch thanh toán nào.</p>
            : <>
              <div className="membership-history-filters">
                <label>Trạng thái thanh toán<select value={paymentStatusFilter} onChange={(event) => { setPaymentStatusFilter(event.target.value); setPaymentHistoryPage(0); }}><option value="">Tất cả</option>{[...new Set(payments.map((payment) => payment.status).filter(Boolean))].sort().map((status) => <option key={status} value={status}>{paymentStatusLabel[status] || status}</option>)}</select></label>
                <label>Mục đích<select value={paymentPurposeFilter} onChange={(event) => { setPaymentPurposeFilter(event.target.value); setPaymentHistoryPage(0); }}><option value="">Tất cả</option>{[...new Set(payments.map((payment) => payment.purpose).filter(Boolean))].sort().map((purpose) => <option key={purpose} value={purpose}>{paymentPurposeLabel[purpose] || purpose}</option>)}</select></label>
              </div>
              {filteredPaymentHistory.length === 0 ? <p className="dash-empty">Không có giao dịch khớp bộ lọc.</p> : <>
                <div className="member-payment-history"><table><thead><tr><th>Mã</th><th>Thời gian</th><th>Nội dung</th><th>Hình thức</th><th>Số tiền</th><th>Trạng thái</th></tr></thead>
                  <tbody>{pagedPaymentHistory.items.map((payment) => <tr key={payment.id}><td>#{payment.id}</td><td>{formatDateTime(payment.createdAt)}</td><td>{paymentPurposeLabel[payment.purpose] || payment.purpose}{isAutoRenewalPayment(payment) ? " (tự gia hạn)" : ""}</td><td>{paymentMethodLabel[payment.method] || payment.method}</td><td>{money(payment.amount)}</td>
                    <td><span className={"member-payment-status " + String(payment.status).toLowerCase()}>{paymentStatusLabel[payment.status] || payment.status}</span>{payment.status === "REFUNDED" && payment.refundedAt && <small>{formatDateTime(payment.refundedAt)}</small>}{payment.status === "REFUND_REQUIRED" && <small>Liên hệ lễ tân để nhận tiền</small>}</td></tr>)}</tbody>
                </table></div><HistoryPagination label="thanh toán" page={pagedPaymentHistory.page} pageCount={pagedPaymentHistory.pageCount} onPageChange={setPaymentHistoryPage} />
              </>}
            </>}
        </section>

        {!loading && cancellationRequests.length > 0 && <section className="dash-card" aria-labelledby="membership-cancellation-title">
          <div className="membership-section-heading"><div><p className="membership-eyebrow">YÊU CẦU</p><h2 id="membership-cancellation-title">Yêu cầu hủy Membership</h2></div></div>
          <ul className="dash-rows">{cancellationRequests.map((request) => <li key={request.id}><span><b>{request.planName}</b> · {formatDate(request.startDate)} → {formatDate(request.endDate)}<br /><small>Gửi lúc {formatDateTime(request.requestedAt)}{request.reason ? " · Lý do: " + request.reason : ""}{request.decidedAt && request.status !== "WITHDRAWN" ? " · Xử lý lúc " + formatDateTime(request.decidedAt) : ""}{request.decisionNote ? " · Ghi chú: " + request.decisionNote : ""}</small></span><em>{requestStatusLabel[request.status] || request.status}</em></li>)}</ul>
        </section>}
      </main>
      {upgradeModal && <div className="mm-upgrade-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && busyPlanId === null) closeUpgradeModal(); }}>
        <section ref={upgradeDialogRef} className="mm-upgrade-dialog" role="dialog" aria-modal="true" aria-labelledby="mm-upgrade-title" aria-describedby="mm-upgrade-description" aria-busy={busyPlanId !== null} tabIndex={-1}>
          <header className="mm-upgrade-dialog-header"><div><p className="mm-upgrade-eyebrow">SPORTCENTER · MEMBERSHIP</p><h2 id="mm-upgrade-title">Xác nhận nâng cấp</h2></div><button type="button" className="mm-upgrade-close" aria-label="Đóng hộp thoại" onClick={closeUpgradeModal} disabled={busyPlanId !== null}>×</button></header>
          <div className="mm-upgrade-summary" id="mm-upgrade-description"><p><span>Gói hiện tại</span><strong>{upgradeModal.currentPlanName}</strong></p><p><span>Gói nâng cấp</span><strong>{upgradeModal.option.planName}</strong></p><p><span>Số ngày còn lại</span><strong>{upgradeModal.remainingDays}</strong></p><p><span>Ngày hết hạn</span><strong>{formatDate(upgradeModal.currentEndDate)}</strong></p><div className="mm-upgrade-amount"><span>Số tiền nâng cấp</span><strong>{money(upgradeModal.option.amount)}</strong></div></div>
          <p className="mm-upgrade-explanation">Giá và điều kiện nâng cấp được lấy từ lựa chọn do hệ thống cung cấp.</p>
          {error && <p className="mm-upgrade-error" role="alert">{error}</p>}
          <form onSubmit={(event) => { event.preventDefault(); if (upgradeSubmitLock.current || busyPlanId !== null || pendingMembershipPayments.length > 0 || !upgradeModal.method) return; upgradeSubmitLock.current = true; upgradeBusyRef.current = true; void startUpgrade(upgradeModal.option, upgradeModal.method).then((submitted) => { if (!submitted) { upgradeSubmitLock.current = false; upgradeBusyRef.current = false; } }); }}>
            <fieldset className="mm-upgrade-methods" disabled={busyPlanId !== null}><legend>Chọn phương thức thanh toán</legend>{[{ value: "COUNTER", label: "Thanh toán tại quầy (COUNTER)" }, { value: "ONLINE", label: "VNPay Sandbox (ONLINE)" }].map((method) => <label className={"mm-upgrade-method" + (upgradeModal.method === method.value ? " selected" : "")} key={method.value}><input type="radio" name="upgrade-payment-method" value={method.value} checked={upgradeModal.method === method.value} onChange={() => setUpgradeModal((current) => ({ ...current, method: method.value }))} /><span>{method.label}</span></label>)}</fieldset>
            <footer className="mm-upgrade-actions"><button type="button" className="mm-upgrade-cancel" onClick={closeUpgradeModal} disabled={busyPlanId !== null}>Đóng</button><button type="submit" className="mm-upgrade-confirm" disabled={busyPlanId !== null || pendingMembershipPayments.length > 0}>{busyPlanId === upgradeModal.option.planId ? "Đang tạo yêu cầu…" : "Xác nhận nâng cấp"}</button></footer>
          </form>
        </section>
      </div>}
      {paymentModalPlan && <PaymentMethodModal amount={money(paymentModalPlan.price)} context={"Gói " + paymentModalPlan.name + " · " + paymentModalPlan.durationMonths + " tháng · " + (currentMemberships.length ? "mua hoặc gia hạn" : "mua mới")} busy={busyPlanId === paymentModalPlan.id} error={error} canContinue={!paymentReconciliationRequired && pendingMembershipPayments.length === 0} onCancel={closePaymentModal} onSuccess={() => setPaymentModalPlan(null)} onContinue={(method) => startPurchase(paymentModalPlan, method)} />}
    </div>
  );
}

function HistoryPagination({ label, page, pageCount, onPageChange }) {
  return <nav className="membership-history-pagination" aria-label={`Phân trang lịch sử ${label}`}>
    <button type="button" className="button-secondary" aria-label={`Trang trước trong lịch sử ${label}`} disabled={page <= 0} onClick={() => onPageChange(page - 1)}>Trước</button>
    <span aria-live="polite">Trang {page + 1} / {pageCount}</span>
    <button type="button" className="button-secondary" aria-label={`Trang tiếp theo trong lịch sử ${label}`} disabled={page + 1 >= pageCount} onClick={() => onPageChange(page + 1)}>Tiếp</button>
  </nav>;
}
