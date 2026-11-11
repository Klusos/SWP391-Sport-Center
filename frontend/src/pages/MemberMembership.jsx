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
  canRequestCancellation,
  CANCELLATION_NOTICE_HOURS,
  cancellationRequestErrorMessage,
  formatDate,
  formatDateTime,
  lastRequestDay,
  paymentMethodLabel,
  paymentPurposeLabel,
  paymentStatusLabel,
  requestStatusLabel,
} from "../membershipCancellation";
import {
  hasOverlappingEffectiveMemberships,
  membershipSections,
  todayInVietnam,
} from "../membershipDates";
import {
  activeMembershipChain,
  autoRenewalOnlineReplacementPayload,
  cancellableMembershipId,
  isAutoRenewalPayment,
  RENEWAL_NOTICE_DAYS,
} from "../membershipRenewal";
import { membershipPurchaseErrorMessage } from "../membershipPurchase";
import {
  isMembershipPayment,
  upgradeBlockedMessage,
  upgradeErrorMessage,
} from "../membershipUpgrade";
import "./Portal.css";
import "./MemberMembership.css";

const rows = (value) => (Array.isArray(value) ? value : value?.content ?? []);
const money = (value) =>
  value == null ? "—" : Number(value).toLocaleString("vi-VN") + " ₫";
const statusLabel = {
  ACTIVE: "Đang hoạt động",
  EXPIRED: "Đã hết hạn",
  UPGRADED: "Đã nâng cấp",
  CANCELLED: "Đã hủy",
};

export default function MemberMembership({
  token,
  role,
  onLogout,
  onNavigate,
}) {
  const [currentUser, setCurrentUser] = useState(null);
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
  const [paymentReconciliationRequired, setPaymentReconciliationRequired] =
    useState(false);
  const [togglingId, setTogglingId] = useState(null);
  const [upgradeModal, setUpgradeModal] = useState(null);
  const upgradeDialogRef = useRef(null);
  const upgradeSubmitLock = useRef(false);
  const upgradeBusyRef = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [user, availablePlans] = await Promise.all([
        getCurrentUserApi(token),
        getPublicMembershipPlansApi(),
      ]);
      setCurrentUser(user);
      const [ownMemberships, ownPayments, upgradeOptions, ownRequests] =
        await Promise.all([
          getMembershipsByMemberApi(token, user.id),
          getMyPaymentsApi(token),
          getMembershipUpgradeOptionsApi(token, user.id),
          getMyCancellationRequestsApi(token),
        ]);
      setMemberships(rows(ownMemberships));
      setPayments(rows(ownPayments));
      setUpgrade(upgradeOptions);
      setCancellationRequests(rows(ownRequests));
      setPlans(
        rows(availablePlans).filter(
          (plan) => String(plan?.status).toUpperCase() === "ACTIVE"
        )
      );
      setPaymentReconciliationRequired(false);
      return rows(ownPayments);
    } catch (requestError) {
      setMemberships([]);
      setPayments([]);
      setPlans([]);
      setUpgrade(null);
      setCancellationRequests([]);
      setError(
        requestError.status === 401
          ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
          : requestError.status === 403
          ? "Tài khoản không có quyền xem thông tin gói hội viên."
          : requestError.message ||
            "Không tải được thông tin hội viên và thanh toán."
      );
      return false;
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => active && load());
    return () => {
      active = false;
    };
  }, [load]);

  useEffect(() => {
    if (!upgradeModal) return undefined;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    (
      upgradeDialogRef.current?.querySelector(
        'input[name="upgrade-payment-method"]:checked'
      ) ||
      upgradeDialogRef.current?.querySelector(
        'input[name="upgrade-payment-method"]'
      )
    )?.focus();

    function handleKeyDown(event) {
      if (event.key === "Escape" && !upgradeBusyRef.current) {
        event.preventDefault();
        setUpgradeModal(null);
        return;
      }
      if (event.key !== "Tab") return;
      const focusable = [
        ...(upgradeDialogRef.current?.querySelectorAll(
          "button:not(:disabled), input:not(:disabled)"
        ) || []),
      ];
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

  const pendingMembershipPayments = useMemo(
    () =>
      (payments || []).filter(
        (payment) =>
          isMembershipPayment(payment?.purpose) && payment?.status === "PENDING"
      ),
    [payments]
  );

  async function startUpgrade(option, method) {
    if (busyPlanId !== null || pendingMembershipPayments.length > 0)
      return false;
    setBusyPlanId(option.planId);
    setError("");
    setNotice("");
    try {
      const payment = await createPaymentApi(token, {
        method,
        purpose: "UPGRADE", referenceId: Number(option.planId),
      });
      setPayments((current) => [payment, ...(current || [])]);
      if (method === "ONLINE") {
        if (!validVnpayUrl(payment.paymentUrl)) {
          await load();
          setError(
            `Đã tạo thanh toán nâng cấp #${payment.id}, nhưng máy chủ không trả về liên kết VNPay Sandbox hợp lệ. Hãy làm mới trạng thái hoặc hủy yêu cầu rồi thử lại.`
          );
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
      setPayments((current) => [
        payment,
        ...(current || []).filter((item) => item.id !== payment.id),
      ]);
      const refreshedPayments = await load();
      const paymentVisibleInHistory =
        Array.isArray(refreshedPayments) &&
        refreshedPayments.some((item) => Number(item.id) === Number(payment.id));
      if (!paymentVisibleInHistory) {
        setPaymentReconciliationRequired(true);
        setPayments((current) => [
          payment,
          ...(current || []).filter((item) => item.id !== payment.id),
        ]);
      }
      if (method === "ONLINE") {
        if (!validVnpayUrl(payment.paymentUrl)) {
          setError(
            `Đã tạo thanh toán #${payment.id}, nhưng máy chủ không trả về liên kết VNPay Sandbox hợp lệ. Thanh toán vẫn đang chờ; không tạo lại hoặc tự hủy. Hãy làm mới trạng thái và liên hệ lễ tân nếu cần hỗ trợ.`
          );
          return true;
        }
        window.location.assign(payment.paymentUrl);
        return true;
      } else {
        setNotice(
          `Đã tạo yêu cầu thanh toán #${payment.id}. Vui lòng thanh toán tại quầy; gói chưa được kích hoạt.`
        );
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
    if (
      loading ||
      busyPlanId !== null ||
      pendingMembershipPayments.length > 0 ||
      paymentReconciliationRequired
    )
      return;
    setError("");
    setPaymentModalPlan(plan);
  }

  function closePaymentModal() {
    if (busyPlanId !== null) return;
    setPaymentModalPlan(null);
  }

  async function cancelPendingPayment(payment) {
    if (cancelingId !== null) return;
    if (
      !window.confirm(
        `Hủy yêu cầu thanh toán #${payment.id}? Thao tác này không hủy một gói đã được thanh toán.`
      )
    )
      return;
    setCancelingId(payment.id);
    setError("");
    setNotice("");
    try {
      const updated = await cancelPaymentApi(token, payment.id);
      setPayments((current) =>
        (current || []).map((item) =>
          item.id === updated.id ? updated : item
        )
      );
      setNotice(
        `Yêu cầu thanh toán #${updated.id} đã được hủy. Bạn có thể tạo yêu cầu mới.`
      );
    } catch (requestError) {
      setError(
        requestError.status === 401
          ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
          : requestError.status === 403
          ? "Bạn chỉ có thể hủy thanh toán của chính mình."
          : requestError.message || "Không thể hủy yêu cầu thanh toán."
      );
      await load();
    } finally {
      setCancelingId(null);
    }
  }

  async function toggleAutoRenew(membership) {
    if (togglingId !== null) return;
    const enable = !membership.autoRenew;
    if (
      !enable &&
      !window.confirm(
        "Tắt tự gia hạn? Yêu cầu gia hạn tự động chưa thanh toán (nếu có) sẽ bị hủy."
      )
    )
      return;
    setTogglingId(membership.id);
    setError("");
    setNotice("");
    try {
      await setAutoRenewApi(token, membership.id, enable);
      await load();
      setNotice(
        enable
          ? `Đã bật tự gia hạn. ${RENEWAL_NOTICE_DAYS} ngày trước khi gói “${membership.planName}” hết hạn, hệ thống sẽ tạo yêu cầu gia hạn và gửi thông báo cho bạn.`
          : "Đã tắt tự gia hạn."
      );
    } catch (requestError) {
      setError(
        requestError.status === 401
          ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
          : requestError.message || "Không thể thay đổi tự gia hạn."
      );
    } finally {
      setTogglingId(null);
    }
  }

  async function switchToVnpay(payment) {
    if (cancelingId !== null || busyPlanId !== null) return;
    if (
      !window.confirm(
        `Chuyển yêu cầu #${payment.id} sang thanh toán VNPay Sandbox? Yêu cầu tại quầy sẽ được hủy và thay bằng yêu cầu online cùng số tiền.`
      )
    )
      return;
    setCancelingId(payment.id);
    setError("");
    setNotice("");
    try {
      await cancelPaymentApi(token, payment.id);
      const online = await createPaymentApi(
        token,
        autoRenewalOnlineReplacementPayload(payment)
      );
      if (!validVnpayUrl(online.paymentUrl)) {
        await load();
        setError(
          `Đã tạo thanh toán #${online.id}, nhưng máy chủ không trả về liên kết VNPay Sandbox hợp lệ. Hãy làm mới trạng thái hoặc liên hệ lễ tân.`
        );
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
    const reason = window.prompt(
      `Gửi yêu cầu hủy gói “${membership.planName}” (${formatDate(
        membership.startDate
      )} → ${formatDate(membership.endDate)})? ` +
        "Nếu được duyệt, khoản đã thanh toán cho gói này được hoàn 100% tại quầy. Lý do (không bắt buộc):",
      ""
    );
    if (reason === null) return;
    setTogglingId(membership.id);
    setError("");
    setNotice("");
    try {
      await requestMembershipCancellationApi(
        token,
        membership.id,
        reason.trim() || null
      );
      await load();
      setNotice(
        `Đã gửi yêu cầu hủy gói “${membership.planName}”. Gói vẫn giữ nguyên cho đến khi nhân viên duyệt; bạn sẽ nhận thông báo kết quả.`
      );
    } catch (requestError) {
      setError(cancellationRequestErrorMessage(requestError));
      await load();
    } finally {
      setTogglingId(null);
    }
  }

  async function withdrawRequest(request) {
    if (togglingId !== null) return;
    if (
      !window.confirm(
        `Rút yêu cầu hủy gói “${request.planName}”? Gói sẽ tiếp tục như bình thường.`
      )
    )
      return;
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
  const currentMembership = currentMemberships[0];
  const membershipChain = activeMembershipChain(memberships, today);
  const cancellableId = cancellableMembershipId(membershipChain, today);
  const pendingRequestFor = (membershipId) =>
    cancellationRequests.find(
      (request) =>
        request.membershipId === membershipId && request.status === "PENDING"
    );

  // Calculate days remaining on active plan
  const daysRemaining = useMemo(() => {
    if (!currentMembership?.endDate) return 0;
    const end = new Date(currentMembership.endDate);
    const now = new Date(today);
    const diff = Math.ceil((end - now) / (1000 * 60 * 60 * 24));
    return Math.max(0, diff);
  }, [currentMembership, today]);

  // Current plan details for benefit display
  const currentPlan = useMemo(() => {
    if (!currentMembership || !plans) return null;
    return plans.find((p) => p.name === currentMembership.planName) || null;
  }, [currentMembership, plans]);

  // Preferred upgrade option
  const primaryUpgradeOption = upgrade?.options?.[0] || null;

  const renderMembershipRows = (items) =>
    items.map((membership) => {
      const last =
        membership.id === membershipChain[membershipChain.length - 1]?.id;
      const running = currentMemberships.some((item) => item.id === membership.id);
      const pendingRequest =
        membership.id === cancellableId ? pendingRequestFor(membership.id) : null;
      const requestable =
        membership.id === cancellableId &&
        !pendingRequest &&
        canRequestCancellation(membership, today);
      return (
        <div className="mm-chain-row" key={membership.id}>
          <div className="mm-chain-plan">
            <b>{membership.planName || "Membership"}</b>
            <small>
              {formatDate(membership.startDate)} → {formatDate(membership.endDate)}
            </small>
          </div>
          <span
            className={
              "mm-chain-status " + (running ? "running" : "upcoming")
            }
          >
            {running
              ? statusLabel[membership.status] || membership.status
              : "Sắp có hiệu lực — chưa có hiệu lực hôm nay"}
          </span>
          <div className="mm-chain-renew">
            {last ? (
              <button
                type="button"
                className={membership.autoRenew ? "" : "secondary"}
                disabled={togglingId !== null}
                aria-pressed={Boolean(membership.autoRenew)}
                onClick={() => void toggleAutoRenew(membership)}
              >
                {togglingId === membership.id
                  ? "Đang lưu…"
                  : membership.autoRenew
                  ? "Tự gia hạn: Bật"
                  : "Tự gia hạn: Tắt"}
              </button>
            ) : (
              <small>Đã có gói nối tiếp</small>
            )}
          </div>
          <div className="mm-chain-cancel">
            {pendingRequest ? (
              <>
                <button
                  type="button"
                  className="secondary"
                  disabled={togglingId !== null}
                  onClick={() => void withdrawRequest(pendingRequest)}
                >
                  Rút yêu cầu hủy
                </button>
                <small>Đang chờ nhân viên duyệt</small>
              </>
            ) : requestable ? (
              <>
                <button
                  type="button"
                  className="secondary"
                  disabled={togglingId !== null}
                  onClick={() => void requestCancellation(membership)}
                >
                  Yêu cầu hủy gói
                </button>
                <small>
                  Hạn gửi: hết ngày {formatDate(lastRequestDay(membership.startDate))}
                </small>
              </>
            ) : membership.id === cancellableId ? (
              <small>Đã quá hạn yêu cầu hủy</small>
            ) : null}
          </div>
        </div>
      );
    });

  return (
    <div className="layout">
      <Sidebar
        role={role}
        activePage="member-membership"
        onLogout={onLogout}
        onNavigate={onNavigate}
      />
      <main className="main member-membership-page member-vip-membership-container">
        {/* TOP APP BAR HEADER */}
        <header className="member-membership-header vip-membership-header">
          <div>
            <div className="vip-header-breadcrumb">
              <span>SPORTCENTER</span>
              <span className="sep">›</span>
              <span>HỘI VIÊN</span>
              <span className="sep">›</span>
              <span className="active-path">QUẢN LÝ GÓI</span>
            </div>
            <h1 className="vip-page-title">Gói thành viên &amp; Quyền lợi</h1>
            <p className="vip-page-subtitle">
              Theo dõi thời hạn thẻ hội viên, quyền lợi gói, gia hạn tự động và nâng cấp hạng thẻ.
            </p>
          </div>

          <div className="vip-header-actions">
            <div className="vip-status-indicator">
              <span className="status-pulse-dot"></span>
              <span>
                {currentMemberships.length > 0
                  ? "Gói đang kích hoạt"
                  : "Chưa kích hoạt gói"}
              </span>
            </div>

            <button
              type="button"
              className="vip-btn-refresh"
              onClick={() => void load()}
              disabled={loading}
              title="Cập nhật thông tin mới nhất"
            >
              <svg
                className={`refresh-icon-svg ${loading ? "spinning" : ""}`}
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.2"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                />
              </svg>
              <span>{loading ? "Đang tải…" : "Làm mới thông tin"}</span>
            </button>
          </div>
        </header>

        {error && <div className="banner error" role="alert">{error}</div>}
        {notice && <div className="banner success" role="status">{notice}</div>}

        {/* SECTION 1: HERO - DIGITAL VIP PASS & PRIVILEGES (2-Column Grid 7:5) */}
        <section className="vip-hero-section">
          {/* Left: Digital VIP Virtual Card */}
          <div className="vip-card-digital">
            <div className="vip-card-overlay-ambient"></div>
            
            {/* Top Bar of Card */}
            <div className="vip-card-topbar">
              <div className="vip-card-badge-group">
                <span className="gold-shimmer-badge">
                  HỘI VIÊN VIP {currentMembership?.planName || "PLATINUM"}
                </span>
                <span className="vip-luxury-label">SPORTS CENTER LUXURY</span>
              </div>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="nfc-icon">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 2a10 10 0 0110 10 10 10 0 01-10 10M12 6a6 6 0 016 6 6 6 0 01-6 6M12 10a2 2 0 012 2 2 2 0 01-2 2" />
              </svg>
            </div>

            {/* Middle: Golden Chip, Member Name, ID & QR */}
            <div className="vip-card-middle">
              <div className="vip-card-info-col">
                <div className="emv-chip-gold">
                  <span className="chip-line"></span>
                </div>
                <span className="card-sub-label">Họ &amp; Tên Hội viên</span>
                <h2 className="card-member-name">
                  {currentUser?.fullName || "RILEY CHEN"}
                </h2>
                <p className="card-member-id">
                  #MEM-2026-{String(currentUser?.id || "005").padStart(3, "0")}
                </p>
              </div>

              {/* Check-in QR Badge */}
              <div className="vip-qr-badge">
                <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M3 3h6v6H3V3zm12 0h6v6h-6V3zm0 12h6v6h-6v-6zm-12 0h6v6H3v-6zm3-3v3m9 0v3m0-6h3m-3 3h3M9 6H6v3h3V6zm12 0h-3v3h3V6zM9 18H6v3h3v-3z" />
                </svg>
                <span className="qr-caption">CHECK-IN NHANH</span>
              </div>
            </div>

            {/* Bottom Meta Information */}
            <div className="vip-card-bottom">
              <div className="dates-range-group">
                <div>
                  <span className="date-label">Ngày bắt đầu</span>
                  <strong className="date-val">
                    {formatDate(currentMembership?.startDate) || "01/10/2026"}
                  </strong>
                </div>
                <span className="date-sep">───</span>
                <div>
                  <span className="date-label">Ngày hết hạn</span>
                  <strong className="date-val">
                    {formatDate(currentMembership?.endDate) || "31/12/2026"}
                  </strong>
                </div>
              </div>

              <div className="pills-status-group">
                <span className="pill-active-days">
                  Còn {daysRemaining > 0 ? daysRemaining : 50} ngày hiệu lực
                </span>

                {currentMembership ? (
                  <button
                    type="button"
                    className={`pill-autorenew-btn ${currentMembership.autoRenew ? "active" : ""}`}
                    disabled={togglingId !== null}
                    onClick={() => void toggleAutoRenew(currentMembership)}
                    title="Nhấp để thay đổi trạng thái tự gia hạn"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                    <span>
                      {togglingId === currentMembership.id
                        ? "Đang lưu…"
                        : currentMembership.autoRenew
                        ? "Tự gia hạn: BẬT"
                        : "Tự gia hạn: TẮT"}
                    </span>
                  </button>
                ) : (
                  <span className="pill-autorenew-btn">
                    Tự gia hạn: BẬT
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* Right: Current Privileges Card */}
          <div className="vip-privileges-card">
            <div>
              <div className="privileges-card-titlebar">
                <div className="privilege-icon-crown">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 2l3 7h6l-5 4 2 7-6-4-6 4 2-7-5-4h6z" />
                  </svg>
                </div>
                <h3 className="privilege-title">Quyền lợi thành viên hiện tại</h3>
              </div>

              <ul className="privilege-checklist">
                <li className="privilege-item">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#10b981" strokeWidth="2.5" className="privilege-check-icon">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                  <span>Miễn phí 100% ra vào tất cả cụm sân tập &amp; phòng Gym</span>
                </li>
                <li className="privilege-item">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#10b981" strokeWidth="2.5" className="privilege-check-icon">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                  <span>
                    Giảm {currentPlan?.courseDiscountPercent ?? 20}% khi đăng ký các khóa học đào tạo
                  </span>
                </li>
                <li className="privilege-item">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#10b981" strokeWidth="2.5" className="privilege-check-icon">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                  <span>
                    Giảm {currentPlan?.personalTrainingDiscountPercent ?? 15}% khi đặt lịch Huấn luyện viên cá nhân (PT 1-1)
                  </span>
                </li>
                <li className="privilege-item">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#10b981" strokeWidth="2.5" className="privilege-check-icon">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                  </svg>
                  <span>Miễn phí tủ đồ thông minh Smart Locker cá nhân</span>
                </li>
              </ul>
            </div>

            <div className="privilege-actions-footer">
              {primaryUpgradeOption ? (
                <button
                  type="button"
                  className="btn-upgrade-gold"
                  disabled={busyPlanId !== null || pendingMembershipPayments.length > 0}
                  onClick={() => openUpgradeModal(primaryUpgradeOption)}
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 2l3 7h6l-5 4 2 7-6-4-6 4 2-7-5-4h6z" />
                  </svg>
                  <span>Nâng cấp lên gói {primaryUpgradeOption.planName || "Kim Cương (Diamond)"} →</span>
                </button>
              ) : (
                <button
                  type="button"
                  className="btn-upgrade-gold"
                  onClick={() => {
                    const upgradeSection = document.getElementById("availablePlansSection");
                    upgradeSection?.scrollIntoView({ behavior: "smooth" });
                  }}
                >
                  <span>Xem các gói thành viên đang mở đăng ký →</span>
                </button>
              )}

              {cancellableId && currentMembership ? (
                <button
                  type="button"
                  className="btn-link-cancel"
                  onClick={() => requestCancellation(currentMembership)}
                >
                  Yêu cầu hủy gói / Hoàn tiền tại quầy
                </button>
              ) : (
                <p className="text-secondary-sub">Chính sách bảo lưu &amp; hỗ trợ tại quầy lễ tân</p>
              )}
            </div>
          </div>
        </section>

        {/* SECTION 2: TWO-PANEL INTERMEDIATE (Upcoming Package & Pending Payments) */}
        <section className="vip-two-panels-grid">
          {/* Panel 1: Upcoming Queued Membership */}
          <div className="vip-panel-card">
            <div>
              <div className="panel-card-header">
                <div className="panel-title-with-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                  </svg>
                  <h3>Chuỗi gói thành viên tương lai</h3>
                </div>
                <span className="badge-pill-queued">
                  {sections.upcoming.length} gói chờ
                </span>
              </div>

              {sections.upcoming.length === 0 ? (
                <div className="panel-empty-callout">
                  <p>Không có gói nào đang chờ đến ngày bắt đầu.</p>
                  <small>Gói tự gia hạn hoặc đăng ký trước sẽ tự động xuất hiện tại đây và kích hoạt liền mạch.</small>
                </div>
              ) : (
                <div className="queued-package-box">
                  <div className="queued-top-row">
                    <div>
                      <h4>Gói {sections.upcoming[0].planName}</h4>
                      <p className="queued-dates">
                        Bắt đầu {formatDate(sections.upcoming[0].startDate)} →{" "}
                        {formatDate(sections.upcoming[0].endDate)}
                      </p>
                    </div>
                    <span className="badge-ready-seamless">
                      Đã chuẩn bị liền mạch
                    </span>
                  </div>
                  <div className="queued-notice-row">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#059669" strokeWidth="2">
                      <circle cx="12" cy="12" r="10" />
                      <path d="M12 16v-4m0-4h.01" />
                    </svg>
                    <span>
                      Hệ thống tự động kích hoạt vào 00:00 ngày{" "}
                      {formatDate(sections.upcoming[0].startDate)}, không gián đoạn dịch vụ.
                    </span>
                  </div>
                </div>
              )}
            </div>

            <div className="panel-footer-row">
              <span className="policy-note">Chính sách duy trì: Ưu đãi giữ nguyên mức giá VIP cũ</span>
              <a href="#availablePlansSection" className="link-action-details">Chi tiết gia hạn</a>
            </div>
          </div>

          {/* Panel 2: Pending Payments Request */}
          <div className="vip-panel-card">
            <div>
              <div className="panel-card-header">
                <div className="panel-title-with-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#d97706" strokeWidth="2">
                    <circle cx="12" cy="12" r="10" />
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6l4 2" />
                  </svg>
                  <h3>Yêu cầu thanh toán đang chờ</h3>
                </div>
                {pendingMembershipPayments.length > 0 ? (
                  <span className="badge-pill-amber">
                    {pendingMembershipPayments[0].method === "ONLINE"
                      ? "Chờ xác nhận VNPay"
                      : "Chờ tại quầy"}
                  </span>
                ) : (
                  <span className="badge-pill-green">Đã hoàn tất</span>
                )}
              </div>

              {pendingMembershipPayments.length === 0 ? (
                <div className="panel-empty-callout">
                  <p>Không có yêu cầu thanh toán gói nào đang chờ.</p>
                  <small>Mọi giao dịch mua mới và gia hạn của bạn đã được đối soát thành công.</small>
                </div>
              ) : (
                <div className="pending-payment-box">
                  <div className="pending-top-row">
                    <div>
                      <span className="invoice-code">
                        Mã hóa đơn: #{pendingMembershipPayments[0].id}
                      </span>
                      <h4 className="pending-plan-name">
                        {pendingMembershipPayments[0].purpose === "UPGRADE"
                          ? "Nâng cấp lên "
                          : isAutoRenewalPayment(pendingMembershipPayments[0])
                          ? "Gia hạn tự động "
                          : "Gia hạn "}
                        {(plans || []).find(
                          (item) =>
                            Number(item.id) ===
                            Number(pendingMembershipPayments[0].referenceId)
                        )?.name || `Gói #${pendingMembershipPayments[0].referenceId}`}
                      </h4>
                      <p className="pending-amount-display">
                        {money(pendingMembershipPayments[0].amount)}
                      </p>
                    </div>
                    <div className="pending-deadline-box">
                      <span className="deadline-label">Hết hạn thanh toán:</span>
                      <span className="deadline-val">23:59 hôm nay</span>
                    </div>
                  </div>

                  <div className="pending-actions-row">
                    {pendingMembershipPayments[0].method === "ONLINE" ? (
                      <button
                        type="button"
                        className="btn-pay-vnpay"
                        onClick={() => {
                          if (validVnpayUrl(pendingMembershipPayments[0].paymentUrl)) {
                            window.location.assign(pendingMembershipPayments[0].paymentUrl);
                          } else {
                            setError("Liên kết VNPay Sandbox không hợp lệ. Vui lòng làm mới trang.");
                          }
                        }}
                      >
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <rect x="2" y="5" width="20" height="14" rx="2" />
                          <line x1="2" y1="10" x2="22" y2="10" />
                        </svg>
                        <span>Thanh toán ngay qua VNPay Sandbox</span>
                      </button>
                    ) : (
                      <button
                        type="button"
                        className="btn-pay-vnpay"
                        disabled={cancelingId !== null || busyPlanId !== null}
                        onClick={() => void switchToVnpay(pendingMembershipPayments[0])}
                      >
                        <span>Chuyển sang VNPay</span>
                      </button>
                    )}

                    <button
                      type="button"
                      className="btn-cancel-pending"
                      disabled={cancelingId !== null}
                      onClick={() => void cancelPendingPayment(pendingMembershipPayments[0])}
                    >
                      {cancelingId === pendingMembershipPayments[0].id
                        ? "Đang hủy…"
                        : "Hủy yêu cầu"}
                    </button>
                  </div>
                </div>
              )}
            </div>

            <p className="panel-footer-note">
              Lưu ý: Môi trường thử nghiệm VNPay Sandbox sẽ cập nhật kết quả tự động về cổng hội viên.
            </p>
          </div>
        </section>

        {/* SECTION 3: EXPLORE OTHER MEMBERSHIP PLANS (Catalog Grid) */}
        <section className="vip-catalog-section" id="availablePlansSection">
          <div className="catalog-header-title">
            <h2>Các gói hội viên đang mở đăng ký</h2>
            <p>Lựa chọn gói tập phù hợp với nhu cầu và mục tiêu thể thao của bạn.</p>
          </div>

          {loading ? (
            <div className="queue-state" role="status">Đang tải gói…</div>
          ) : !plans?.length ? (
            <p className="dash-empty">Hiện chưa có gói ACTIVE nào.</p>
          ) : (
            <div className="pf-plans vip-plans-grid">
              {plans.map((plan, index) => {
                const isCurrent = currentMemberships.some((m) => m.planName === plan.name);
                const isHighlight = isCurrent || index === 1;
                return (
                  <article
                    key={plan.id}
                    className={`pf-plan vip-plan-card ${isHighlight ? "plan-highlight" : ""}`}
                  >
                    {isCurrent ? (
                      <div className="badge-plan-top-ribbon">
                        ĐANG SỬ DỤNG · PHỔ BIẾN NHẤT
                      </div>
                    ) : index === 2 ? (
                      <div className="badge-plan-top-ribbon gold">
                        ĐẶC QUYỀN TỐI THƯỢNG
                      </div>
                    ) : null}

                    <div>
                      <div className="plan-title-row">
                        <h3>{plan.name}</h3>
                        <p className="plan-duration">Thời hạn: {plan.durationMonths} Tháng</p>
                      </div>

                      <div className="plan-price-block">
                        <span className="price-big">{money(plan.price)}</span>
                        <span className="price-period">/ {plan.durationMonths} tháng</span>
                      </div>

                      <ul className="plan-features-list">
                        <li>
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2.5">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                          </svg>
                          <span>Full access toàn bộ sân tập &amp; phòng Gym</span>
                        </li>
                        <li>
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2.5">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                          </svg>
                          <span>Giảm {plan.courseDiscountPercent ?? 0}% khi đăng ký khóa học</span>
                        </li>
                        <li>
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2.5">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                          </svg>
                          <span>
                            {plan.allowOneOnOneCoaching
                              ? `Giảm ${plan.personalTrainingDiscountPercent ?? 0}% khi thuê Huấn luyện viên PT 1-1`
                              : "Không gồm PT 1-1"}
                          </span>
                        </li>
                        <li>
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#2563eb" strokeWidth="2.5">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                          </svg>
                          <span>Tủ đồ thông minh Smart Locker tiện ích</span>
                        </li>
                      </ul>
                    </div>

                    <div className="member-membership-payment-actions">
                      <button
                        type="button"
                        className={`btn-purchase-plan ${isHighlight ? "btn-highlight" : ""}`}
                        disabled={
                          busyPlanId !== null ||
                          pendingMembershipPayments.length > 0 ||
                          paymentReconciliationRequired
                        }
                        onClick={() => openPurchaseModal(plan)}
                      >
                        {busyPlanId === plan.id
                          ? "Đang tạo yêu cầu…"
                          : currentMemberships.length
                          ? "Mua / gia hạn"
                          : "Mua gói"}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>

        {/* SECTION 4: UPGRADE OPTIONS DETAILED (If member has alternative upgrade options) */}
        {!loading && upgrade?.currentMembershipId && (
          <section className="dash-card vip-upgrade-options-section">
            <h2>Nâng cấp gói trực tiếp</h2>
            <p className="subtitle">
              Gói mới có hiệu lực ngay và giữ nguyên ngày hết hạn {upgrade.currentEndDate}. Bạn chỉ trả phần chênh lệch cho {upgrade.remainingDays} ngày còn lại (tính tháng 30 ngày, làm tròn lên 1.000 ₫). Không hạ cấp giữa kỳ và không hoàn tiền phần chênh lệch.
            </p>
            {upgrade.blockedReason ? (
              <p className="dash-empty">{upgradeBlockedMessage(upgrade.blockedReason)}</p>
            ) : upgrade.options.length === 0 ? (
              <p className="dash-empty">Bạn đang dùng gói cao nhất có thể nâng cấp.</p>
            ) : (
              <div className="pf-plans">
                {upgrade.options.map((option) => (
                  <article key={option.planId} className="pf-plan">
                    <h2>
                      {upgrade.currentPlanName} → {option.planName}
                    </h2>
                    <strong>{money(option.amount)}</strong>
                    <small>
                      Giá gói {money(option.planPrice)} / {option.durationMonths} tháng · Giảm khóa học {option.courseDiscountPercent}% · Giảm PT {option.personalTrainingDiscountPercent}%
                    </small>
                    <div className="member-membership-payment-actions">
                      <button
                        type="button"
                        disabled={
                          busyPlanId !== null || pendingMembershipPayments.length > 0
                        }
                        onClick={() => openUpgradeModal(option)}
                      >
                        {busyPlanId === option.planId ? "Đang tạo yêu cầu…" : "Nâng cấp"}
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        )}

        {/* SECTION 5: MEMBERSHIP CHAIN & HISTORY (Collapsible/Full details) */}
        <section className="dash-card">
          <h2>Lịch sử các gói thành viên</h2>
          {loading ? (
            <p role="status">Đang tải thông tin…</p>
          ) : sections.history.length === 0 ? (
            <p className="dash-empty">Chưa có gói thành viên trong lịch sử.</p>
          ) : (
            <div className="mm-chain">
              {sections.history.map((membership) => (
                <div className="mm-chain-row" key={membership.id}>
                  <div className="mm-chain-plan">
                    <b>{membership.planName || "Membership"}</b>
                    <small>
                      {formatDate(membership.startDate)} → {formatDate(membership.endDate)}
                    </small>
                  </div>
                  <span className="mm-chain-status">
                    {String(membership.status).toUpperCase() === "ACTIVE" &&
                    membership.endDate &&
                    String(membership.endDate) <= today
                      ? `Hết hiệu lực (trạng thái ${membership.status})`
                      : statusLabel[String(membership.status).toUpperCase()] ||
                        membership.status ||
                        "Không rõ trạng thái"}
                  </span>
                  <small>Gói trong lịch sử</small>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* SECTION 6: PAYMENT TRANSACTIONS AUDIT TABLE */}
        <section className="dash-card vip-history-card">
          <h2>Lịch sử giao dịch thanh toán</h2>
          {loading ? (
            <p role="status">Đang tải lịch sử thanh toán…</p>
          ) : !payments?.length ? (
            <p className="dash-empty">Bạn chưa có giao dịch nào.</p>
          ) : (
            <div className="member-payment-history">
              <table>
                <thead>
                  <tr>
                    <th>Mã</th>
                    <th>Thời gian</th>
                    <th>Nội dung</th>
                    <th>Hình thức</th>
                    <th>Số tiền</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {payments.map((payment) => (
                    <tr key={payment.id}>
                      <td>#{payment.id}</td>
                      <td>{formatDateTime(payment.createdAt)}</td>
                      <td>
                        {paymentPurposeLabel[payment.purpose] || payment.purpose}
                        {isAutoRenewalPayment(payment) ? " (tự gia hạn)" : ""}
                      </td>
                      <td>{paymentMethodLabel[payment.method] || payment.method}</td>
                      <td>{money(payment.amount)}</td>
                      <td>
                        <span
                          className={`member-payment-status ${String(
                            payment.status
                          ).toLowerCase()}`}
                        >
                          {paymentStatusLabel[payment.status] || payment.status}
                        </span>
                        {payment.status === "REFUNDED" && payment.refundedAt && (
                          <small>{formatDateTime(payment.refundedAt)}</small>
                        )}
                        {payment.status === "REFUND_REQUIRED" && (
                          <small>Liên hệ lễ tân để nhận tiền</small>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>

      {/* MODAL: UPGRADE CONFIRMATION DIALOG */}
      {upgradeModal && (
        <div
          className="mm-upgrade-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget && busyPlanId === null)
              closeUpgradeModal();
          }}
        >
          <section
            ref={upgradeDialogRef}
            className="mm-upgrade-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="mm-upgrade-title"
            aria-describedby="mm-upgrade-description"
            aria-busy={busyPlanId !== null}
            tabIndex={-1}
          >
            <header className="mm-upgrade-dialog-header">
              <div>
                <p className="mm-upgrade-eyebrow">SPORTCENTER / MEMBERSHIP</p>
                <h2 id="mm-upgrade-title">Xác nhận nâng cấp</h2>
              </div>
              <button
                type="button"
                className="mm-upgrade-close"
                aria-label="Đóng hộp thoại"
                onClick={closeUpgradeModal}
                disabled={busyPlanId !== null}
              >
                ×
              </button>
            </header>
            <div className="mm-upgrade-summary" id="mm-upgrade-description">
              <p>
                <span>Gói hiện tại</span>
                <strong>{upgradeModal.currentPlanName}</strong>
              </p>
              <p>
                <span>Gói nâng cấp</span>
                <strong>{upgradeModal.option.planName}</strong>
              </p>
              <p>
                <span>Số ngày còn lại</span>
                <strong>{upgradeModal.remainingDays}</strong>
              </p>
              <p>
                <span>Ngày hết hạn hiện tại</span>
                <strong>{formatDate(upgradeModal.currentEndDate)}</strong>
              </p>
              <div className="mm-upgrade-amount">
                <span>Số tiền nâng cấp</span>
                <strong>{money(upgradeModal.option.amount)}</strong>
              </div>
            </div>
            <p className="mm-upgrade-explanation">
              Gói mới có hiệu lực ngay sau khi thanh toán thành công và giữ nguyên ngày hết hạn Membership hiện tại. Báo giá và số tiền được lấy từ hệ thống.
            </p>
            {error && (
              <p className="mm-upgrade-error" role="alert">
                {error}
              </p>
            )}
            <form
              onSubmit={(event) => {
                event.preventDefault();
                if (upgradeSubmitLock.current || busyPlanId !== null || pendingMembershipPayments.length > 0 || !upgradeModal.method) return;
                upgradeSubmitLock.current = true;
                upgradeBusyRef.current = true;
                void startUpgrade(upgradeModal.option, upgradeModal.method).then(
                  (submitted) => {
                    if (!submitted) {
                      upgradeSubmitLock.current = false;
                      upgradeBusyRef.current = false;
                    }
                  }
                );
              }}
            >
              <fieldset
                className="mm-upgrade-methods"
                disabled={busyPlanId !== null}
              >
                <legend>Chọn phương thức thanh toán</legend>
                {[
                  { value: "COUNTER", label: "Thanh toán tại quầy (COUNTER)" },
                  { value: "ONLINE", label: "VNPay Sandbox (ONLINE)" },
                ].map((method) => (
                  <label
                    className={
                      "mm-upgrade-method" +
                      (upgradeModal.method === method.value ? " selected" : "")
                    }
                    key={method.value}
                  >
                    <input
                      type="radio"
                      name="upgrade-payment-method"
                      value={method.value}
                      checked={upgradeModal.method === method.value}
                      onChange={() =>
                        setUpgradeModal((current) => ({
                          ...current,
                          method: method.value,
                        }))
                      }
                    />
                    <span>{method.label}</span>
                  </label>
                ))}
              </fieldset>
              <footer className="mm-upgrade-actions">
                <button
                  type="button"
                  className="mm-upgrade-cancel"
                  onClick={closeUpgradeModal}
                  disabled={busyPlanId !== null}
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  className="mm-upgrade-confirm"
                  disabled={
                    busyPlanId !== null || pendingMembershipPayments.length > 0
                  }
                >
                  {busyPlanId === upgradeModal.option.planId
                    ? "Đang tạo yêu cầu…"
                    : "Xác nhận nâng cấp"}
                </button>
              </footer>
            </form>
          </section>
        </div>
      )}

      {/* MODAL: PAYMENT METHOD MODAL FOR PURCHASING PLANS */}
      {paymentModalPlan && (
        <PaymentMethodModal
          amount={money(paymentModalPlan.price)}
          context={`Gói ${paymentModalPlan.name} · ${
            paymentModalPlan.durationMonths
          } tháng · ${
            currentMemberships.length ? "mua hoặc gia hạn" : "mua mới"
          }`}
          busy={busyPlanId === paymentModalPlan.id}
          error={error}
          canContinue={
            !paymentReconciliationRequired &&
            pendingMembershipPayments.length === 0
          }
          onCancel={closePaymentModal}
          onSuccess={() => setPaymentModalPlan(null)}
          onContinue={(method) => startPurchase(paymentModalPlan, method)}
        />
      )}
    </div>
  );
}
