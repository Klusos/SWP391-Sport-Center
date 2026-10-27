import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
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
import { isMembershipEffective, todayInVietnam } from "../membershipDates";
import { activeMembershipChain, cancellableMembershipId, isAutoRenewalPayment, RENEWAL_NOTICE_DAYS } from "../membershipRenewal";
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
    if (busyPlanId !== null || pendingMembershipPayments.length > 0) return;
    const confirmation = method === "ONLINE"
      ? `Tạo yêu cầu VNPay Sandbox cho gói “${plan.name}” với giá ${money(plan.price)}? Gói chỉ được kích hoạt sau khi máy chủ xác nhận thanh toán thành công.`
      : `Tạo yêu cầu thanh toán tại quầy cho gói “${plan.name}” với giá ${money(plan.price)}? Gói chỉ được kích hoạt sau khi nhân viên xác nhận đã nhận tiền.`;
    if (!window.confirm(confirmation)) return;
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
      setPayments((current) => [payment, ...(current || [])]);
      if (method === "ONLINE") {
        if (!validVnpayUrl(payment.paymentUrl)) {
          await load();
          setError(`Đã tạo thanh toán #${payment.id}, nhưng máy chủ không trả về liên kết VNPay Sandbox hợp lệ. Thanh toán vẫn đang chờ; không tạo lại hoặc tự hủy. Hãy làm mới trạng thái và liên hệ lễ tân nếu cần hỗ trợ.`);
          return;
        }
        window.location.assign(payment.paymentUrl);
      } else {
        setNotice(`Đã tạo yêu cầu thanh toán #${payment.id}. Vui lòng thanh toán tại quầy; gói chưa được kích hoạt.`);
      }
    } catch (requestError) {
      const message = membershipPurchaseErrorMessage(requestError);
      await load();
      setError(message);
    } finally {
      setBusyPlanId(null);
    }
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
    if (!enable && !window.confirm(`Tắt tự gia hạn cho gói “${membership.planName}”? Yêu cầu gia hạn tự động chưa thanh toán (nếu có) sẽ bị hủy.`)) return;
    setTogglingId(membership.id);
    setError("");
    setNotice("");
    try {
      await setAutoRenewApi(token, membership.id, enable);
      await load();
      setNotice(enable
        ? `Đã bật tự gia hạn cho gói “${membership.planName}”. ${RENEWAL_NOTICE_DAYS} ngày trước khi hết hạn, hệ thống sẽ tạo yêu cầu gia hạn và gửi thông báo cho bạn.`
        : `Đã tắt tự gia hạn cho gói “${membership.planName}”.`);
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
      const online = await createPaymentApi(token, {
        method: "ONLINE",
        purpose: "MEMBERSHIP",
        referenceId: Number(payment.referenceId),
        autoRenew: isAutoRenewalPayment(payment),
      });
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
  const currentMemberships = (memberships || []).filter(isMembershipEffective);
  const membershipChain = activeMembershipChain(memberships, today);
  const cancellableId = cancellableMembershipId(membershipChain, today);
  const pendingRequestFor = (membershipId) => cancellationRequests.find(
    (request) => request.membershipId === membershipId && request.status === "PENDING");

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
        <h2>Gói của tôi</h2>
        {loading ? <p role="status">Đang tải thông tin…</p> : membershipChain.length === 0
          ? <p className="dash-empty">Bạn chưa có gói đang hoạt động.</p>
          : <ul className="dash-rows">{membershipChain.map((membership) => <li key={membership.id}>
            <span><b>{membership.planName || "Gói thành viên"}</b><br /><small>{membership.startDate || "—"} → {membership.endDate || "—"}</small></span>
            <em>{isMembershipEffective(membership) ? statusLabel[membership.status] || membership.status : "Sắp bắt đầu"}</em>
            <button type="button" className={membership.autoRenew ? "" : "secondary"} disabled={togglingId !== null}
              aria-pressed={Boolean(membership.autoRenew)} onClick={() => void toggleAutoRenew(membership)}>
              {togglingId === membership.id ? "Đang lưu…" : membership.autoRenew ? "Tự gia hạn: Bật" : "Tự gia hạn: Tắt"}
            </button>
            {membership.id === cancellableId && (pendingRequestFor(membership.id)
              ? <button type="button" className="secondary" disabled={togglingId !== null}
                onClick={() => void withdrawRequest(pendingRequestFor(membership.id))}>Rút yêu cầu hủy</button>
              : canRequestCancellation(membership, today)
                ? <button type="button" className="secondary" disabled={togglingId !== null}
                  onClick={() => void requestCancellation(membership)}>Yêu cầu hủy gói</button>
                : <small>Đã quá hạn yêu cầu hủy</small>)}
            {membership.id === cancellableId && pendingRequestFor(membership.id) && <small>Đang chờ nhân viên duyệt hủy</small>}
            {membership.id === cancellableId && !pendingRequestFor(membership.id) && canRequestCancellation(membership, today)
              && <small>Hạn gửi yêu cầu: hết ngày {formatDate(lastRequestDay(membership.startDate))}</small>}
          </li>)}</ul>}
        {membershipChain.length > 0 && <p className="subtitle">Gói đang chạy không hủy và không hoàn tiền; nếu không muốn tiếp tục, hãy tắt tự gia hạn. Gói đã mua trước nhưng chưa bắt đầu có thể gửi yêu cầu hủy (hủy từ gói sau cùng), chậm nhất {CANCELLATION_NOTICE_HOURS} giờ trước ngày bắt đầu; khi nhân viên duyệt, khoản đã thanh toán được hoàn 100% tại quầy.</p>}
        {membershipChain.length > 0 && <p className="subtitle">Khi thanh toán gia hạn thành công, thời hạn mới sẽ nối tiếp ngày kết thúc hiện tại. Bật tự gia hạn: {RENEWAL_NOTICE_DAYS} ngày trước khi gói cuối cùng hết hạn, hệ thống tạo yêu cầu gia hạn cùng gói và gửi thông báo; bạn thanh toán tại quầy hoặc VNPay trước ngày hết hạn. Chưa thanh toán thì yêu cầu tự hủy và gói hết hạn bình thường.</p>}
      </section>

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
            <small>Thời hạn: {plan.durationMonths} tháng · Giảm khóa học {plan.courseDiscountPercent ?? 0}%</small>
            {plan.description && <p>{plan.description}</p>}
            <div className="member-membership-payment-actions">
              <button type="button" disabled={busyPlanId !== null || pendingMembershipPayments.length > 0}
                onClick={() => void startPurchase(plan, "COUNTER")}>
                {busyPlanId === plan.id ? "Đang tạo yêu cầu…" : currentMemberships.length ? "Mua / gia hạn · tại quầy" : "Mua gói · tại quầy"}
              </button>
              <button type="button" disabled={busyPlanId !== null || pendingMembershipPayments.length > 0}
                onClick={() => void startPurchase(plan, "ONLINE")}>
                {busyPlanId === plan.id ? "Đang tạo yêu cầu…" : currentMemberships.length ? "Mua / gia hạn · VNPay Sandbox" : "Mua gói · VNPay Sandbox"}
              </button>
            </div>
          </article>)}</div>}
      <p className="banner info" role="note">VNPay Sandbox là môi trường thử nghiệm. Trạng thái Membership chỉ thay đổi theo xác nhận thanh toán từ máy chủ.</p>
    </main>
  </div>;
}
