import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import { statusLabel as presentStatusLabel } from "../uiPresentation.js";
import PaymentMethodModal from "../components/PaymentMethodModal";
import {
  cancelPaymentApi,
  createPaymentApi,
  getCurrentUserApi,
  getMembershipUpgradeOptionsApi,
  getMembershipsByMemberApi,
  getMyCancellationRequestsApi,
  getMyPaymentsApi,
  getMyRenewalIntentsApi,
  getPublicMembershipPlansApi,
  requestMembershipCancellationApi,
  setAutoRenewApi,
  startRenewalPaymentAttemptApi,
  withdrawCancellationRequestApi,
} from "../api";
import { validOnlinePaymentUrl } from "../courseCheckout";
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
} from "../membershipRenewal";
import { membershipPurchaseErrorMessage } from "../membershipPurchase";
import { membershipPlanBenefits } from "../membershipPlanPresentation.js";
import { buildPendingMembershipPresentation } from "../memberMembershipPresentation.js";
import { isMembershipPayment, upgradeBlockedMessage, upgradeErrorMessage } from "../membershipUpgrade";
import { filterPaymentHistory, paginateItems, sortMembershipHistory } from "../membershipHistoryModel";
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
  const [plans, setPlans] = useState(null);
  const [memberships, setMemberships] = useState(null);
  const [payments, setPayments] = useState(null);
  const [renewalIntents, setRenewalIntents] = useState([]);
  const [renewalError, setRenewalError] = useState("");
  const [upgrade, setUpgrade] = useState(null);
  const [cancellationRequests, setCancellationRequests] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [busyPlanId, setBusyPlanId] = useState(null);
  const [startingRenewalId, setStartingRenewalId] = useState(null);
  const [cancelingId, setCancelingId] = useState(null);
  const [paymentModalPlan, setPaymentModalPlan] = useState(null);
  const [paymentReconciliationRequired, setPaymentReconciliationRequired] =
    useState(false);
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
      const [ownMemberships, ownPayments, upgradeOptions, ownRequests, ownRenewals] =
        await Promise.all([
          getMembershipsByMemberApi(token, user.id),
          getMyPaymentsApi(token),
          getMembershipUpgradeOptionsApi(token, user.id),
          getMyCancellationRequestsApi(token),
          getMyRenewalIntentsApi(token).catch((renewalRequestError) => {
            setRenewalError(renewalRequestError.message || "Không thể tải trạng thái gia hạn.");
            return null;
          }),
        ]);
      setMemberships(rows(ownMemberships));
      setPayments(rows(ownPayments));
      setUpgrade(upgradeOptions);
      setCancellationRequests(rows(ownRequests));
      setRenewalIntents(rows(ownRenewals));
      if (ownRenewals !== null) setRenewalError("");
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
      setRenewalIntents([]);
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
    const params = new URLSearchParams(window.location.search);
    const returnedPaymentId = params.get("paymentId");
    Promise.resolve().then(async () => {
      if (!active) return;
      await load();
      if (active && returnedPaymentId) {
        setNotice("Đã quay lại từ cổng thanh toán. Trạng thái bên dưới được tải từ máy chủ; chuyển hướng trình duyệt không tự xác nhận thanh toán.");
        params.delete("paymentId");
        params.delete("paymentStatus");
        params.delete("reason");
        const query = params.toString();
        window.history.replaceState({}, document.title, window.location.pathname + (query ? `?${query}` : ""));
      }
    });
    return () => {
      active = false;
    };
  }, [load]);

  async function startRenewalPayment(intent) {
    if (startingRenewalId !== null) return;
    setStartingRenewalId(intent.intentId);
    setError("");
    setNotice("");
    try {
      const action = await startRenewalPaymentAttemptApi(token, intent.intentId);
      setRenewalIntents((current) => (current || []).map((item) =>
        item.intentId === intent.intentId ? action.intent : item));
      if (!validOnlinePaymentUrl(action.checkoutUrl)) {
        await load();
        setError("Yêu cầu gia hạn đã được ghi nhận nhưng liên kết thanh toán không hợp lệ. Hãy làm mới trạng thái; yêu cầu chưa được xác nhận là đã thanh toán.");
        return;
      }
      window.location.assign(action.checkoutUrl);
    } catch (requestError) {
      await load();
      setError(requestError.status === 409
        ? "Yêu cầu gia hạn hiện không thể thanh toán hoặc cần đối soát. Trạng thái mới nhất đã được tải lại."
        : requestError.message || "Không thể bắt đầu thanh toán gia hạn.");
    } finally {
      setStartingRenewalId(null);
    }
  }

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
  const pendingPresentation = useMemo(
    () => buildPendingMembershipPresentation(renewalIntents, pendingMembershipPayments),
    [renewalIntents, pendingMembershipPayments]
  );
  const unresolvedRenewalIntents = pendingPresentation.unresolvedIntents;
  const renewalRequestIntents = pendingPresentation.requestIntents;

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
        if (!validOnlinePaymentUrl(payment.paymentUrl)) {
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
    if (busyPlanId !== null || purchaseBlockedByMembership || pendingMembershipPayments.length > 0 || paymentReconciliationRequired) return false;
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
        if (!validOnlinePaymentUrl(payment.paymentUrl)) {
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
      const reconciliationUnknown = requestError.status !== 409;
      setPaymentReconciliationRequired(reconciliationUnknown);
      setError(reconciliationUnknown ? `${message} Làm mới lịch sử thanh toán trước khi thử lại.` : message);
      return false;
    } finally {
      setBusyPlanId(null);
    }
  }

  function openPurchaseModal(plan) {
    if (
      loading ||
      busyPlanId !== null ||
      purchaseBlockedByMembership ||
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
      // Refresh the linked Renewal Intent as well as Payment status so a
      // cancelled attempt is no longer presented as pending.
      await load();
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
        "Tắt gia hạn? Yêu cầu gia hạn chưa thanh toán (nếu có) sẽ bị hủy."
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
          ? `Đã bật gia hạn. Trong 3 ngày cuối trước khi gói “${membership.planName}” hết hạn, hệ thống có thể tạo yêu cầu gia hạn nếu đủ điều kiện. Bạn cần thanh toán thành công; hệ thống không tự động thu tiền.`
          : "Đã tắt gia hạn."
      );
    } catch (requestError) {
      setError(
        requestError.status === 401
          ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
          : requestError.message || "Không thể thay đổi cài đặt gia hạn."
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
      if (!validOnlinePaymentUrl(online.paymentUrl)) {
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
  const membershipHistory = sortMembershipHistory(sections.history);
  const pagedMembershipHistory = paginateItems(membershipHistory, membershipHistoryPage, 5);
  const filteredPaymentHistory = filterPaymentHistory(payments, { status: paymentStatusFilter, purpose: paymentPurposeFilter });
  const pagedPaymentHistory = paginateItems(filteredPaymentHistory, paymentHistoryPage, 10);
  const membershipChain = activeMembershipChain(memberships, today);
  const purchaseBlockedByMembership = currentMemberships.length > 0 || sections.upcoming.length > 0
    || renewalIntents.some((intent) => intent.status === "OPEN");
  const cancellableId = cancellableMembershipId(membershipChain, today);
  const pendingRequestFor = (membershipId) => cancellationRequests.find(
    (request) => request.membershipId === membershipId && request.status === "PENDING");
  const renderMembershipBenefits = (membership) => {
    if (!membership || (membership.courseDiscountPercent == null && membership.allowOneOnOneCoaching == null && membership.personalTrainingDiscountPercent == null && membership.allowPersonalTrainingPlan == null)) {
      return <p className="membership-neutral-note">Chưa có dữ liệu quyền lợi cho gói hội viên này. Trạng thái mở bán của gói không quyết định hiệu lực gói đã đăng ký.</p>;
    }
    return <ul className="membership-benefit-list">
      {membership.courseDiscountPercent != null && <li><i className="bi bi-check-circle-fill" aria-hidden="true" /><span>Giảm {membership.courseDiscountPercent}% khi đăng ký khóa học</span></li>}
      {membership.allowOneOnOneCoaching === true && membership.personalTrainingDiscountPercent != null && <li><i className="bi bi-check-circle-fill" aria-hidden="true" /><span>Giảm {membership.personalTrainingDiscountPercent}% khi đặt lịch PT 1-1</span></li>}
      {membership.allowOneOnOneCoaching === true && membership.personalTrainingDiscountPercent == null && <li><i className="bi bi-info-circle-fill" aria-hidden="true" /><span>Chưa có dữ liệu mức giảm PT 1-1.</span></li>}
      {membership.allowOneOnOneCoaching === false && <li><i className="bi bi-info-circle-fill" aria-hidden="true" /><span>Gói này không bao gồm quyền lợi PT 1-1</span></li>}
      {membership.allowPersonalTrainingPlan === true && <li><i className="bi bi-check-circle-fill" aria-hidden="true" /><span>Được phép tạo giáo án cá nhân</span></li>}
      {membership.allowPersonalTrainingPlan === false && <li><i className="bi bi-info-circle-fill" aria-hidden="true" /><span>Gói này không bao gồm quyền tạo giáo án cá nhân</span></li>}
    </ul>;
  };
  const renderMembershipRows = (items) => items.map((membership) => {
    const last = membership.id === membershipChain[membershipChain.length - 1]?.id;
    const pendingRequest = membership.id === cancellableId ? pendingRequestFor(membership.id) : null;
    const requestable = membership.id === cancellableId && !pendingRequest && canRequestCancellation(membership, today);
    return <div className="mm-chain-row" key={membership.id}>
      <div className="mm-chain-plan"><b>{membership.planName || "Gói hội viên"}</b>
        <small>{formatDate(membership.startDate)} → {formatDate(membership.endDate)} (ngày kết thúc không bao gồm)</small>
        <small>{money(membership.planPrice)} · {membership.durationMonths ?? "—"} tháng · {membership.benefitSource === "LEGACY_PLAN_FALLBACK" ? "quyền lợi của gói cũ chưa được xác minh" : "quyền lợi theo gói tại thời điểm mua"}</small>
      </div>
      <span className="mm-chain-status upcoming">Sắp có hiệu lực — chưa có hiệu lực hôm nay</span>
      <div className="mm-chain-renew">
        <small>Gia hạn: {membership.autoRenew ? "Bật" : "Tắt"}</small>
        {last ? <button type="button" className={membership.autoRenew ? "" : "secondary"} disabled={togglingId !== null}
          aria-pressed={Boolean(membership.autoRenew)} onClick={() => void toggleAutoRenew(membership)}>
          {togglingId === membership.id ? "Đang lưu…" : membership.autoRenew ? "Tắt gia hạn" : "Bật gia hạn"}
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
  const renderRenewalIntent = ({ intent, attempt }, canStart) => {
    const reconciliation = ["REFUND_REQUIRED", "REFUNDED", "CONFLICT", "COLLECTION_CONFLICT", "RECONCILIATION_REQUIRED"].includes(String(intent.latestResult || "").toUpperCase())
      || ["CONFLICT", "COLLECTION_CONFLICT", "RECONCILIATION_REQUIRED"].includes(String(intent.status || "").toUpperCase())
      || intent.attempts?.some((item) => item.manualReconciliationRequired || (item.collectionRecorded && !item.settlementAccepted));
    const status = intent.latestResult || intent.status;
    const statusText = reconciliation
      ? "Khoản thu cần được nhân viên đối soát. Trạng thái này chưa xác nhận tiền đã được hoàn."
      : attempt?.status === "EXPIRED" ? "Lần thanh toán trước đã hết hạn; bạn có thể thử lại nếu yêu cầu còn hiệu lực."
        : "Yêu cầu đang mở; cần thanh toán thành công để hoàn tất gia hạn.";
    return <article className="member-renewal-card" key={`renewal-${intent.intentId}`}>
      <div className="member-renewal-heading"><div><h3>{intent.planName || "Gói hội viên"}</h3><small>Yêu cầu gia hạn #{intent.intentId}</small></div>
        <span className={`member-payment-status ${String(status).toLowerCase()}`}>{presentStatusLabel(status, "renewalIntent")}</span></div>
      <dl className="membership-payment-facts">
        <div><dt>Báo giá đã lưu</dt><dd>{money(intent.quotedAmount)}</dd></div>
        <div><dt>Thời hạn gói</dt><dd>{intent.durationMonths} tháng</dd></div>
        <div><dt>Hạn thanh toán</dt><dd>{formatDateTime(attempt?.deadline || intent.validUntil)}</dd></div>
        <div><dt>Kỳ gia hạn dự kiến</dt><dd>{formatDate(intent.successorStartDate)} – {formatDate(intent.successorEndDate)}</dd></div>
      </dl>
      <p className="member-renewal-result" role="status">{statusText}</p>
      {intent.status === "OPEN" && <p className="membership-policy-note renewal-cancellation-warning" role="note">
        Nếu thanh toán gia hạn thành công, gói mới bắt đầu trong 3 ngày cuối trước ngày hiệu lực. Khi đó, bạn không đủ điều kiện gửi yêu cầu hủy thông thường theo thời hạn báo trước 72 giờ.
      </p>}
      {attempt && <div className="member-renewal-attempt"><span>Lần thanh toán #{attempt.paymentId}: {paymentStatusLabel[attempt.status] || attempt.status}</span>{attempt.manualReconciliationRequired && <strong>Đang cần đối soát thủ công</strong>}</div>}
      {canStart && intent.status === "OPEN" && !reconciliation && <button type="button" disabled={startingRenewalId !== null} onClick={() => void startRenewalPayment(intent)}>{startingRenewalId === intent.intentId ? "Đang tạo liên kết…" : attempt ? "Thử thanh toán lại" : "Thanh toán gia hạn"}</button>}
    </article>;
  };

  return (
    <div className="layout">
      <Sidebar role={role} activePage="member-membership" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main member-membership-page">
        <header className="member-membership-header">
          <div>
            <p className="membership-eyebrow">HỘI VIÊN</p>
            <h1>Gói hội viên</h1>
            <p className="subtitle">Quản lý quyền lợi, yêu cầu thanh toán và lịch sử gói hội viên của bạn.</p>
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
            {loading ? <p role="status">Đang tải thông tin gói hội viên…</p> : currentMemberships.length === 0
              ? <div className="membership-empty-current"><p>Bạn chưa có gói hội viên đang hiệu lực.</p><span>Gói sắp có hiệu lực và lịch sử được hiển thị bên dưới.</span></div>
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
                    <div className="membership-current-meta">{remainingDays !== null && <span>Còn {remainingDays} ngày hiệu lực</span>}</div>
                    <div className="membership-current-actions">
                      <div className="membership-auto-renew-control"><span>Gia hạn: <strong>{membership.autoRenew ? "Bật" : "Tắt"}</strong></span>
                        {last && <button type="button" className={membership.autoRenew ? "" : "secondary"} disabled={togglingId !== null} aria-pressed={Boolean(membership.autoRenew)} onClick={() => void toggleAutoRenew(membership)}>{togglingId === membership.id ? "Đang lưu…" : membership.autoRenew ? "Tắt gia hạn" : "Bật gia hạn"}</button>}
                        {!last && <small>Đã có gói hội viên tiếp nối</small>}
                      </div>
                      <div className="mm-chain-cancel">{pendingRequest ? <><button type="button" className="secondary" disabled={togglingId !== null} onClick={() => void withdrawRequest(pendingRequest)}>Rút yêu cầu hủy</button><small>Đang chờ nhân viên duyệt</small></> : requestable ? <><button type="button" className="secondary" disabled={togglingId !== null} onClick={() => void requestCancellation(membership)}>Yêu cầu hủy gói</button><small>Hạn gửi: hết ngày {formatDate(lastRequestDay(membership.startDate))}</small></> : membership.id === cancellableId ? <small>Đã quá hạn yêu cầu hủy</small> : null}</div>
                    </div>
                  </article>;
                })}</div>}
            {hasOverlappingEffectiveMemberships(sections) && <p className="banner info" role="status">Có nhiều gói hội viên cùng hiệu lực hôm nay. Hệ thống không tự thay đổi dữ liệu; vui lòng liên hệ lễ tân nếu cần kiểm tra.</p>}
            {membershipChain.length > 0 && <p className="membership-policy-note">Khi bật gia hạn, hệ thống sẽ tạo yêu cầu trong 3 ngày cuối trước khi gói hết hạn nếu đủ điều kiện. Bạn có thể tắt gia hạn trước khi thanh toán. Hệ thống không tự động thu tiền.</p>}
            {sections.upcoming.length > 0 && <p className="membership-policy-note">Với gói hội viên sắp có hiệu lực, bạn có thể gửi yêu cầu hủy trước ít nhất {CANCELLATION_NOTICE_HOURS} giờ so với ngày bắt đầu. Nhân viên cần duyệt; gói chỉ bị hủy sau khi yêu cầu được chấp thuận.</p>}
          </section>

          <section className="dash-card membership-benefits-card" aria-labelledby="membership-benefits-title">
            <div className="membership-section-heading"><div><p className="membership-eyebrow">QUYỀN LỢI</p><h2 id="membership-benefits-title">Quyền lợi gói hội viên</h2></div><span className="membership-icon" aria-hidden="true"><i className="bi bi-award-fill" /></span></div>
            {loading ? <p role="status">Đang tải quyền lợi…</p> : currentMemberships.length === 0
              ? <p className="membership-neutral-note">Quyền lợi theo gói sẽ hiển thị khi bạn có gói hội viên đang hiệu lực.</p>
              : <div className="membership-benefits-groups">{currentMemberships.map((membership) => {
                  return <div className="membership-benefit-group" key={membership.id}>
                    {currentMemberships.length > 1 && <h3>{membership.planName || "Gói hội viên"}</h3>}
                    <p className="membership-neutral-note">{membership.benefitSource === "LEGACY_PLAN_FALLBACK"
                      ? "Hồ sơ gói cũ chưa có thông tin quyền lợi đã xác minh; nội dung đang hiển thị theo cấu hình hiện tại."
                      : "Quyền lợi áp dụng theo gói tại thời điểm mua."}</p>
                    {renderMembershipBenefits(membership)}
                  </div>;
                })}</div>}
          </section>
        </div>

        <section className="dash-card membership-queue-section" aria-labelledby="membership-queue-title">
          <div className="membership-section-heading">
            <div><p className="membership-eyebrow">ĐÃ ĐƯỢC GHI NHẬN</p><h2 id="membership-queue-title">Gói hội viên sắp có hiệu lực</h2></div>
            {!loading && <span className="membership-count-badge">{sections.upcoming.length}</span>}
          </div>
          {loading ? <p role="status">Đang tải gói hội viên…</p> : sections.upcoming.length === 0
            ? <p className="dash-empty">Không có gói hội viên nào đang chờ đến ngày bắt đầu.</p>
            : <div className="mm-chain">{renderMembershipRows(sections.upcoming)}</div>}
        </section>

        {!loading && upgrade?.currentMembershipId && <section className="dash-card" aria-labelledby="membership-upgrade-title">
          <div className="membership-section-heading"><div><p className="membership-eyebrow">LỰA CHỌN TỪ HỆ THỐNG</p><h2 id="membership-upgrade-title">Nâng cấp gói hội viên</h2></div></div>
          {upgrade.blockedReason
            ? <p className="dash-empty">{upgradeBlockedMessage(upgrade.blockedReason)}</p>
            : !upgrade.options?.length
              ? <p className="dash-empty">Hiện không có lựa chọn nâng cấp nào được hệ thống cung cấp.</p>
              : <div className="membership-card-grid">{upgrade.options.map((option) => <article className="membership-plan-card membership-upgrade-card" key={option.planId}>
                  <div><span className="membership-card-kicker">NÂNG CẤP ĐƯỢC PHÉP</span><h3>{upgrade.currentPlanName} → {option.planName}</h3></div>
                  <strong className="membership-price">{money(option.amount)}</strong>
                  <p>Giá gói {money(option.planPrice)} · {option.durationMonths} tháng · Giảm khóa học {option.courseDiscountPercent}% · Giảm PT {option.personalTrainingDiscountPercent}%</p>
                  <dl className="membership-upgrade-quote-details">
                    <div><dt>Giá gói đích</dt><dd>{money(option.planPrice)}</dd></div>
                    <div><dt>Credit đã xác minh</dt><dd>−{money(option.appliedCredit)}</dd></div>
                    <div><dt>Số tiền phải thanh toán</dt><dd>{money(option.amount)}</dd></div>
                    <div><dt>Thời hạn đầy đủ</dt><dd>{option.durationMonths} tháng</dd></div>
                  </dl>
                  <p className="membership-upgrade-effective-note">Gói mới bắt đầu theo ngày Việt Nam khi thanh toán được xác nhận thành công; thời hạn được tính đầy đủ từ ngày đó.</p>
                  <button type="button" disabled={busyPlanId !== null || pendingMembershipPayments.length > 0} onClick={() => openUpgradeModal(option)}>
                    {busyPlanId === option.planId ? "Đang tạo yêu cầu…" : "Nâng cấp"}
                  </button>
                </article>)}</div>}
        </section>}

        <section className="dash-card membership-pending-section" aria-labelledby="membership-pending-title">
          <div className="membership-section-heading"><div><h2 id="membership-pending-title">Yêu cầu và thanh toán đang chờ</h2></div>
            {!loading && <span className={pendingPresentation.paymentItems.length + renewalRequestIntents.length + unresolvedRenewalIntents.length ? "membership-count-badge pending" : "membership-count-badge"}>{pendingPresentation.paymentItems.length + renewalRequestIntents.length + unresolvedRenewalIntents.length}</span>}
          </div>
          {renewalError && <p className="banner info" role="status">Không tải được trạng thái gia hạn: {renewalError}</p>}
          {loading ? <p role="status">Đang tải yêu cầu và thanh toán…</p> : pendingPresentation.paymentItems.length + renewalRequestIntents.length + unresolvedRenewalIntents.length === 0
            ? <p className="dash-empty">Không có yêu cầu hoặc thanh toán nào đang chờ.</p>
            : <div className="membership-pending-list">
              {renewalRequestIntents.map((entry) => renderRenewalIntent(entry, true))}
              {unresolvedRenewalIntents.map((entry) => renderRenewalIntent(entry, false))}
              {pendingPresentation.paymentItems.map(({ payment, renewalIntent, renewalAttempt, renewalRetry }) => {
                const plan = (plans || []).find((item) => Number(item.id) === Number(payment.referenceId));
                const quote = payment.upgradeQuote;
                const targetName = quote?.targetPlanName || plan?.name || "gói #" + payment.referenceId;
                const title = renewalIntent ? `Gia hạn ${renewalIntent.planName || targetName}` : payment.purpose === "UPGRADE"
                  ? "Nâng cấp lên " + targetName
                  : isAutoRenewalPayment(payment)
                    ? "Gia hạn " + targetName
                    : targetName;                return <article className="membership-pending-card" key={payment.id}>
                  <div className="membership-pending-main"><div><span className="membership-field-label">{title}</span><strong>Thanh toán #{payment.id}</strong></div>
                    <span className={"member-payment-status " + String(payment.status).toLowerCase()}>{paymentStatusLabel[payment.status] || payment.status}</span></div>
                  {payment.purpose === "UPGRADE" && quote ? <>
                    <dl className="membership-payment-facts membership-upgrade-payment-quote">
                      <div><dt>Giá gói đích</dt><dd>{money(quote.targetPrice)}</dd></div>
                      <div><dt>Giá trị khấu trừ</dt><dd>−{money(quote.appliedCredit)}</dd></div>
                      <div className="membership-upgrade-payable"><dt>Cần thanh toán</dt><dd>{money(quote.payable)}</dd></div>
                    </dl>
                    <dl className="membership-payment-facts membership-upgrade-payment-meta">
                      <div><dt>Thời hạn mới</dt><dd>{quote.targetDurationMonths} tháng</dd></div>
                      <div><dt>Phương thức</dt><dd>{paymentMethodLabel[payment.method] || payment.method}</dd></div>
                    </dl>
                  </> : <dl className="membership-payment-facts">
                    <div><dt>Số tiền</dt><dd>{money(payment.amount)}</dd></div>
                    <div><dt>Phương thức</dt><dd>{paymentMethodLabel[payment.method] || payment.method}</dd></div>
                    {renewalIntent ? <><div><dt>Báo giá gia hạn đã lưu</dt><dd>{money(renewalIntent.quotedAmount)}</dd></div><div><dt>Thời hạn gói</dt><dd>{renewalIntent.durationMonths} tháng</dd></div><div><dt>Hạn thanh toán</dt><dd>{formatDateTime(renewalAttempt?.deadline || renewalIntent.validUntil)}</dd></div><div><dt>Kỳ gia hạn dự kiến</dt><dd>{formatDate(renewalIntent.successorStartDate)} – {formatDate(renewalIntent.successorEndDate)}</dd></div></> : <div><dt>Tạo lúc</dt><dd>{formatDateTime(payment.createdAt)}</dd></div>}
                  </dl>}
                  {renewalIntent && <p className="membership-policy-note renewal-cancellation-warning" role="note">
                    Sau khi thanh toán gia hạn thành công, bạn không thể gửi yêu cầu hủy thông thường vì gói mới đã ở trong thời hạn báo trước 72 giờ.
                  </p>}
                  {payment.purpose === "UPGRADE" && !quote && <p className="membership-upgrade-quote-warning" role="note">Thông tin giá của yêu cầu cũ chưa đầy đủ; vui lòng liên hệ nhân viên để được kiểm tra trước khi thanh toán.</p>}                  <div className="membership-payment-actions">
                    {payment.purpose === "MEMBERSHIP" && payment.method === "COUNTER" && <button type="button" className="button-secondary" disabled={cancelingId !== null || busyPlanId !== null} onClick={() => void switchToVnpay(payment)}>Chuyển sang VNPay</button>}
                    {renewalIntent && renewalRetry && <button type="button" disabled={startingRenewalId !== null} onClick={() => void startRenewalPayment(renewalIntent)}>{startingRenewalId === renewalIntent.intentId ? "Đang tạo liên kết…" : "Thử thanh toán lại"}</button>}
                    {payment.method === "ONLINE" && !renewalRetry && <button type="button" disabled={cancelingId !== null} onClick={() => { if (validOnlinePaymentUrl(payment.paymentUrl)) window.location.assign(payment.paymentUrl); else setError("Liên kết thanh toán không hợp lệ. Vui lòng làm mới trạng thái."); }}>Tiếp tục thanh toán</button>}
                    <button type="button" className="button-secondary" disabled={cancelingId !== null || busyPlanId !== null} onClick={() => void cancelPendingPayment(payment)}>{cancelingId === payment.id ? "Đang hủy…" : "Hủy yêu cầu"}</button>
                  </div>
                </article>;
              })}</div>}
          {pendingPresentation.paymentItems.length > 0 && <p className="membership-policy-note">Hãy kiểm tra trạng thái thanh toán trước khi tạo yêu cầu khác.</p>}
        </section>

        <section className="dash-card" aria-labelledby="membership-plans-title">
          <div className="membership-section-heading"><div><p className="membership-eyebrow">GÓI ĐANG MỞ BÁN</p><h2 id="membership-plans-title">Các gói thành viên</h2></div></div>
          {loading ? <p role="status">Đang tải danh sách gói…</p> : !plans?.length
            ? <p className="dash-empty">Hiện chưa có gói nào được mở bán.</p>
            : <div className="membership-card-grid">{plans.map((plan) => <article className="membership-plan-card" key={plan.id}>
                <div><span className="membership-card-kicker">{plan.durationMonths} tháng</span><h3>{plan.name}</h3></div>
                <strong className="membership-price">{money(plan.price)}</strong>
                {plan.description && <p>{plan.description}</p>}
                <ul className="membership-plan-benefits">
                  {plan.courseDiscountPercent != null && <li>Giảm khóa học {plan.courseDiscountPercent}%</li>}
                  {plan.allowOneOnOneCoaching === true && plan.personalTrainingDiscountPercent != null && <li>Giảm PT 1-1 {plan.personalTrainingDiscountPercent}%</li>}
                  {plan.allowOneOnOneCoaching === false && <li>Không bao gồm PT 1-1</li>}
                  {plan.allowPersonalTrainingPlan === true && <li>Được phép tạo giáo án cá nhân</li>}
                  {plan.allowPersonalTrainingPlan === false && <li>Không bao gồm giáo án cá nhân</li>}
                </ul>
                <button type="button" disabled={busyPlanId !== null || purchaseBlockedByMembership || pendingMembershipPayments.length > 0 || paymentReconciliationRequired} onClick={() => openPurchaseModal(plan)}>
                  {busyPlanId === plan.id ? "Đang tạo yêu cầu…" : purchaseBlockedByMembership ? "Đã có gói hội viên" : "Mua gói"}
                </button>
              </article>)}</div>}
          {purchaseBlockedByMembership && <p className="membership-policy-note" role="status">Bạn đã có gói hội viên đang có hiệu lực hoặc sắp có hiệu lực. Có thể đăng ký gói mới sau khi gói hiện tại hết hạn.</p>}
          <p className="membership-policy-note">Gói hội viên chỉ được kích hoạt hoặc gia hạn sau khi thanh toán được xác nhận thành công.</p>
        </section>

        <section className="dash-card" aria-labelledby="membership-history-title">
          <div className="membership-section-heading"><div><p className="membership-eyebrow">LỊCH SỬ</p><h2 id="membership-history-title">Lịch sử gói hội viên</h2></div></div>
          {loading ? <p role="status">Đang tải lịch sử…</p> : sections.history.length === 0
            ? <p className="dash-empty">Chưa có gói hội viên trong lịch sử.</p>
            : <><div className="mm-chain">{pagedMembershipHistory.items.map((membership) => <div className="mm-chain-row history-row" key={membership.id}>
                <div className="mm-chain-plan"><b>{membership.planName || "Gói hội viên"}</b><small>{formatDate(membership.startDate)} → {formatDate(membership.endDate)} · {money(membership.planPrice)} · {membership.durationMonths ?? "—"} tháng</small>
                  <small>{membership.benefitSource === "LEGACY_PLAN_FALLBACK" ? "Quyền lợi của gói cũ chưa được xác minh." : "Quyền lợi theo gói tại thời điểm mua."}</small>
                  <small>{membershipPlanBenefits(membership).map(({ label, value }) => `${label}: ${value}`).join(" · ")}</small></div>
                <span className="mm-chain-status">{String(membership.status).toUpperCase() === "ACTIVE" && membership.endDate && String(membership.endDate) <= today ? "Hết hiệu lực (trạng thái " + membership.status + ")" : statusLabel[String(membership.status).toUpperCase()] || membership.status || "Không rõ trạng thái"}</span>
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
                  <tbody>{pagedPaymentHistory.items.map((payment) => <tr key={payment.id}><td>#{payment.id}</td><td>{formatDateTime(payment.createdAt)}</td><td>{paymentPurposeLabel[payment.purpose] || payment.purpose}{isAutoRenewalPayment(payment) ? " (Renewal)" : ""}</td><td>{paymentMethodLabel[payment.method] || payment.method}</td><td>{money(payment.amount)}</td>
                    <td><span className={"member-payment-status " + String(payment.status).toLowerCase()}>{paymentStatusLabel[payment.status] || payment.status}</span>{payment.status === "REFUNDED" && payment.refundedAt && <small>{formatDateTime(payment.refundedAt)}</small>}{payment.status === "REFUND_REQUIRED" && <small>Liên hệ lễ tân để nhận tiền</small>}</td></tr>)}</tbody>
                </table></div><HistoryPagination label="thanh toán" page={pagedPaymentHistory.page} pageCount={pagedPaymentHistory.pageCount} onPageChange={setPaymentHistoryPage} />
              </>}
            </>}
        </section>

        {!loading && cancellationRequests.length > 0 && <section className="dash-card" aria-labelledby="membership-cancellation-title">
          <div className="membership-section-heading"><div><p className="membership-eyebrow">YÊU CẦU</p><h2 id="membership-cancellation-title">Yêu cầu hủy gói hội viên</h2></div></div>
          <ul className="dash-rows">{cancellationRequests.map((request) => <li key={request.id}><span><b>{request.planName}</b> · {formatDate(request.startDate)} → {formatDate(request.endDate)}<br /><small>Gửi lúc {formatDateTime(request.requestedAt)}{request.reason ? " · Lý do: " + request.reason : ""}{request.decidedAt && request.status !== "WITHDRAWN" ? " · Xử lý lúc " + formatDateTime(request.decidedAt) : ""}{request.decisionNote ? " · Ghi chú: " + request.decisionNote : ""}</small></span><em>{requestStatusLabel[request.status] || request.status}</em></li>)}</ul>
        </section>}
      </main>
      {upgradeModal && <div className="mm-upgrade-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && busyPlanId === null) closeUpgradeModal(); }}>
        <section ref={upgradeDialogRef} className="mm-upgrade-dialog" role="dialog" aria-modal="true" aria-labelledby="mm-upgrade-title" aria-describedby="mm-upgrade-description" aria-busy={busyPlanId !== null} tabIndex={-1}>
          <header className="mm-upgrade-dialog-header"><div><p className="mm-upgrade-eyebrow">SPORTCENTER · MEMBERSHIP</p><h2 id="mm-upgrade-title">Xác nhận nâng cấp</h2></div><button type="button" className="mm-upgrade-close" aria-label="Đóng hộp thoại" onClick={closeUpgradeModal} disabled={busyPlanId !== null}>×</button></header>
          <div className="mm-upgrade-summary" id="mm-upgrade-description"><p><span>Gói hiện tại</span><strong>{upgradeModal.currentPlanName}</strong></p><p><span>Gói nâng cấp</span><strong>{upgradeModal.option.planName}</strong></p><p><span>Số ngày còn lại</span><strong>{upgradeModal.remainingDays}</strong></p><p><span>Ngày hết hạn</span><strong>{formatDate(upgradeModal.currentEndDate)}</strong></p><div className="mm-upgrade-amount"><span>Số tiền nâng cấp</span><strong>{money(upgradeModal.option.amount)}</strong></div></div>
          <p className="mm-upgrade-explanation">Giá và điều kiện nâng cấp được lấy từ lựa chọn do hệ thống cung cấp.</p>
          {error && <p className="mm-upgrade-error" role="alert">{error}</p>}
          <dl className="mm-upgrade-quote-details">
            <div><dt>Giá đầy đủ của gói đích</dt><dd>{money(upgradeModal.option.planPrice)}</dd></div>
            <div><dt>Giá trị được khấu trừ từ gói đã thanh toán</dt><dd>−{money(upgradeModal.option.appliedCredit)}</dd></div>
            <div><dt>Thời hạn gói hội viên mới</dt><dd>{upgradeModal.option.durationMonths} tháng</dd></div>
            <div><dt>Ưu đãi khóa học / PT</dt><dd>{upgradeModal.option.courseDiscountPercent}% / {upgradeModal.option.personalTrainingDiscountPercent}%</dd></div>
            <div><dt>Giáo án PT / huấn luyện 1:1</dt><dd>{upgradeModal.option.allowPersonalTrainingPlan ? "Có" : "Không"} / {upgradeModal.option.allowOneOnOneCoaching ? "Có" : "Không"}</dd></div>
            {upgradeModal.option.planDescription && <div><dt>Mô tả gói đích</dt><dd>{upgradeModal.option.planDescription}</dd></div>}
          </dl>          <form onSubmit={(event) => { event.preventDefault(); if (upgradeSubmitLock.current || busyPlanId !== null || pendingMembershipPayments.length > 0 || !upgradeModal.method) return; upgradeSubmitLock.current = true; upgradeBusyRef.current = true; void startUpgrade(upgradeModal.option, upgradeModal.method).then((submitted) => { if (!submitted) { upgradeSubmitLock.current = false; upgradeBusyRef.current = false; } }); }}>
            <fieldset className="mm-upgrade-methods" disabled={busyPlanId !== null}><legend>Chọn phương thức thanh toán</legend>{[{ value: "COUNTER", label: "Thanh toán tại quầy (COUNTER)" }, { value: "ONLINE", label: "VNPay Sandbox (ONLINE)" }].map((method) => <label className={"mm-upgrade-method" + (upgradeModal.method === method.value ? " selected" : "")} key={method.value}><input type="radio" name="upgrade-payment-method" value={method.value} checked={upgradeModal.method === method.value} onChange={() => setUpgradeModal((current) => ({ ...current, method: method.value }))} /><span>{method.label}</span></label>)}</fieldset>
            <footer className="mm-upgrade-actions"><button type="button" className="mm-upgrade-cancel" onClick={closeUpgradeModal} disabled={busyPlanId !== null}>Đóng</button><button type="submit" className="mm-upgrade-confirm" disabled={busyPlanId !== null || pendingMembershipPayments.length > 0}>{busyPlanId === upgradeModal.option.planId ? "Đang tạo yêu cầu…" : "Xác nhận nâng cấp"}</button></footer>
          </form>
        </section>
      </div>}
      {paymentModalPlan && <PaymentMethodModal amount={money(paymentModalPlan.price)} context={"Gói " + paymentModalPlan.name + " · " + paymentModalPlan.durationMonths + " tháng · mua mới"} busy={busyPlanId === paymentModalPlan.id} error={error} canContinue={!purchaseBlockedByMembership && !paymentReconciliationRequired && pendingMembershipPayments.length === 0} onCancel={closePaymentModal} onSuccess={() => setPaymentModalPlan(null)} onContinue={(method) => startPurchase(paymentModalPlan, method)} />}
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
