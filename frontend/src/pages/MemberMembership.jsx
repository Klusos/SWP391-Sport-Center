import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import { validVnpayUrl, vietnamDate } from "../courseCheckout";
import { membershipPaymentMessage, membershipPaymentPayload } from "../membershipCheckout";
import {
  cancelPaymentApi,
  cancelMembershipAutoRenewApi,
  createRenewalCheckoutApi,
  createPaymentApi,
  enableMembershipAutoRenewApi,
  getCurrentUserApi,
  getMembershipsByMemberApi,
  getMyPaymentsApi,
  getPublicMembershipPlansApi,
  selectRenewalPaymentMethodApi,
} from "../api";
import { canRequestRenewalCheckout, renewalAttemptState, renewalOptionsPayload, renewalPayments, safeRenewalCheckoutUrl } from "../renewalCheckout";
import { autoRenewEnableReason, canCancelMembershipAutoRenew, canEnableMembershipAutoRenew, membershipSections } from "../membershipAutoRenew";
import "./Portal.css";
import "./MemberMembership.css";

const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];
const money = (value) => value == null ? "—" : Number(value).toLocaleString("vi-VN") + " ₫";
const statusLabel = { ACTIVE: "Đang hoạt động", EXPIRED: "Đã hết hạn", PENDING: "Chờ thanh toán", SUCCESS: "Đã thanh toán", CANCELLED: "Đã hủy", REFUND_REQUIRED: "Cần xử lý hoàn tiền" };

export default function MemberMembership({ token, role, onLogout, onNavigate }) {
  const [plans, setPlans] = useState(null);
  const [memberships, setMemberships] = useState(null);
  const [payments, setPayments] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [busyPlanId, setBusyPlanId] = useState(null);
  const [cancelingId, setCancelingId] = useState(null);
  const [memberId, setMemberId] = useState(null);
  const [uncertainAttempt, setUncertainAttempt] = useState(null);
  const [retryAccepted, setRetryAccepted] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState(null);
  const [renewalOptions, setRenewalOptions] = useState({});
  const [renewalBusyId, setRenewalBusyId] = useState(null);
  const [autoRenewBusyId, setAutoRenewBusyId] = useState(null);
  const actionLock = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [user, availablePlans] = await Promise.all([
        getCurrentUserApi(token),
        getPublicMembershipPlansApi(),
      ]);
      const [ownMemberships, ownPayments] = await Promise.all([
        getMembershipsByMemberApi(token, user.id),
        getMyPaymentsApi(token),
      ]);
      setMemberships(rows(ownMemberships));
      setPayments(rows(ownPayments));
      setRenewalOptions((current) => {
        const next = { ...current };
        renewalPayments(rows(ownPayments)).forEach((payment) => {
          if (!Object.hasOwn(next, payment.id)) next[payment.id] = {
            method: payment.method || "",
            autoRenew: payment.renewalAutoRenewChoice ?? "",
          };
        });
        return next;
      });
      setMemberId(user.id);
      setPlans(rows(availablePlans).filter((plan) => String(plan?.status).toUpperCase() === "ACTIVE"));
      try {
        const key = `sportcenter.membership-checkout.${user.id}`;
        const saved = JSON.parse(sessionStorage.getItem(key) || "null");
        const history = rows(ownPayments).filter((payment) => payment.purpose === "MEMBERSHIP" && payment?.isRenewal !== true);
        const attemptedAt = Date.parse(saved?.attemptedAt || "");
        const matching = (saved?.paymentId && history.find((payment) => payment.id === saved.paymentId))
          || (Number.isFinite(attemptedAt) && history.find((payment) => Number(payment.referenceId) === Number(saved.planId)
            && vietnamDate(payment.createdAt)?.getTime() >= attemptedAt - 5000));
        if (matching) {
          sessionStorage.removeItem(key);
          setUncertainAttempt(null);
        } else if (saved?.uncertain) {
          setUncertainAttempt(saved);
        }
      } catch {
        // History remains usable when browser storage is unavailable.
      }
    } catch (requestError) {
      setMemberships([]);
      setPayments([]);
      setPlans([]);
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
    (payment) => payment?.purpose === "MEMBERSHIP" && payment?.status === "PENDING",
  ), [payments]);
  const pendingOrdinaryMembershipPayments = useMemo(() => pendingMembershipPayments.filter(
    (payment) => payment?.isRenewal !== true,
  ), [pendingMembershipPayments]);
  const renewalRows = useMemo(() => renewalPayments(payments), [payments]);

  async function changeMembershipAutoRenew(membership, enabled) {
    if (autoRenewBusyId !== null) return;
    if (!enabled && !window.confirm("Tắt Auto Renew cho gói này? Nếu đã có yêu cầu gia hạn đang chờ, hệ thống sẽ hủy yêu cầu đó. Khoản đã được xác minh nhưng chưa đối soát có thể cần nhân viên xem xét.")) return;
    setAutoRenewBusyId(membership.id); setError(""); setNotice("");
    try {
      if (enabled) await enableMembershipAutoRenewApi(token, membership.id);
      else await cancelMembershipAutoRenewApi(token, membership.id);
      await load();
      setNotice(enabled
        ? "Đã bật Auto Renew cho Membership. Khi đủ điều kiện, hệ thống tạo yêu cầu thanh toán; không tự động thu tiền."
        : "Đã tắt Auto Renew. Lịch sử thanh toán đã được tải lại để phản ánh trạng thái yêu cầu gia hạn.");
    } catch (requestError) {
      const prefix = ({ 400: "Membership chưa đủ điều kiện thay đổi Auto Renew.", 401: "Phiên đăng nhập đã hết hạn.", 403: "Bạn không có quyền thay đổi Membership này.", 404: "Không tìm thấy Membership.", 409: "Đã có yêu cầu gia hạn cuối kỳ hoặc giao dịch cần đối soát; Auto Renew chưa được thay đổi." })[requestError.status];
      setError(prefix || requestError.message || "Không thể cập nhật Auto Renew.");
      if ([401, 403, 404, 409].includes(requestError.status)) await load();
    } finally { setAutoRenewBusyId(null); }
  }

  function replacePayment(updated) {
    setPayments((current) => (current || []).map((payment) => payment.id === updated.id ? updated : payment));
  }

  async function saveRenewalOptions(payment) {
    const choice = renewalOptions[payment.id] || {};
    let payload;
    try { payload = renewalOptionsPayload(choice.method, choice.autoRenew); }
    catch (validationError) { setError(validationError.message); return; }
    setRenewalBusyId(payment.id); setError(""); setNotice("");
    try {
      const updated = await selectRenewalPaymentMethodApi(token, payment.id, payload);
      replacePayment(updated);
      setNotice(`Đã lưu lựa chọn cho yêu cầu gia hạn #${payment.id}.`);
    } catch (requestError) {
      const prefix = ({ 400: "Lựa chọn không hợp lệ.", 401: "Phiên đăng nhập đã hết hạn.", 403: "Bạn không có quyền cập nhật yêu cầu này.", 404: "Không tìm thấy yêu cầu gia hạn.", 409: "Yêu cầu không còn đủ điều kiện hoặc đã có phiên thanh toán." })[requestError.status];
      setError(prefix || requestError.message || "Không thể lưu lựa chọn gia hạn.");
      if ([401, 403, 404, 409].includes(requestError.status)) await load();
    } finally { setRenewalBusyId(null); }
  }

  async function startRenewalCheckout(payment) {
    if (renewalBusyId !== null) return;
    setRenewalBusyId(payment.id); setError(""); setNotice("");
    try {
      const response = await createRenewalCheckoutApi(token, payment.id);
      if (response.paymentId !== payment.id || !safeRenewalCheckoutUrl(response.checkoutUrl)) {
        throw new Error("Máy chủ không trả về URL VNPay hợp lệ. Trạng thái chưa được xem là đã thanh toán; hãy tải lại lịch sử.");
      }
      window.location.assign(response.checkoutUrl);
    } catch (requestError) {
      const prefix = ({ 400: "Thông tin yêu cầu không hợp lệ.", 401: "Phiên đăng nhập đã hết hạn.", 403: "Bạn không có quyền thanh toán yêu cầu này.", 404: "Không tìm thấy yêu cầu gia hạn.", 409: "Yêu cầu chưa đủ điều kiện hoặc phiên checkout trước vẫn còn hiệu lực." })[requestError.status];
      setError(prefix || requestError.message || "Không thể tạo phiên checkout.");
      await load();
    } finally { setRenewalBusyId(null); }
  }

  async function startPurchase(plan, method) {
    if (actionLock.current || busyPlanId !== null || pendingMembershipPayments.length > 0) return;
    if (uncertainAttempt && !retryAccepted) return;
    actionLock.current = true;
    setBusyPlanId(plan.id);
    setError("");
    setNotice("");
    let requestStarted = false;
    try {
      const key = `sportcenter.membership-checkout.${memberId}`;
      const marker = { planId: Number(plan.id), attemptedAt: new Date().toISOString(), uncertain: true };
      try { sessionStorage.setItem(key, JSON.stringify(marker)); }
      catch { throw new Error("Không thể lưu trạng thái khôi phục. Hãy bật session storage trước khi bắt đầu thanh toán."); }
      requestStarted = true;
      const payment = await createPaymentApi(token, membershipPaymentPayload(plan.id, method));
      sessionStorage.setItem(key, JSON.stringify({ ...marker, paymentId: payment.id, uncertain: false }));
      setPayments((current) => [payment, ...(current || [])]);
      setSelectedPlan(null);
      sessionStorage.removeItem(key);
      setUncertainAttempt(null);
      setRetryAccepted(false);
      if (method === "ONLINE") {
        if (!validVnpayUrl(payment.paymentUrl)) {
          throw new Error("Máy chủ không trả về URL VNPay Sandbox hợp lệ. Giao dịch có thể vẫn đang chờ; hãy làm mới lịch sử trước khi thử lại.");
        }
        window.location.assign(payment.paymentUrl);
      } else {
        setNotice(`Đã tạo yêu cầu thanh toán #${payment.id}. Vui lòng thanh toán tại quầy; gói chưa được kích hoạt.`);
      }
    } catch (requestError) {
      setError(requestError.status === 409
        ? "Đã có yêu cầu thanh toán đang chờ cho gói này. Hãy tiếp tục thanh toán hoặc hủy yêu cầu hiện tại."
        : requestError.status === 401
          ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
          : requestError.status === 403
            ? "Bạn chỉ có thể tạo thanh toán cho tài khoản của mình."
            : `${requestError.message || "Không thể tạo yêu cầu thanh toán."}${requestStarted ? " Nếu yêu cầu bị gián đoạn, hãy làm mới lịch sử và kiểm tra trạng thái trước khi thử lại." : ""}`);
      if (requestStarted) {
        const marker = { planId: Number(plan.id), attemptedAt: new Date().toISOString(), uncertain: true };
        try { sessionStorage.setItem(`sportcenter.membership-checkout.${memberId}`, JSON.stringify(marker)); } catch { /* Keep visible failure state. */ }
        setUncertainAttempt(marker);
        setRetryAccepted(false);
      }
      await load();
    } finally {
      actionLock.current = false;
      setBusyPlanId(null);
    }
  }

  async function cancelPendingPayment(payment) {
    if (cancelingId !== null || payment.status !== "PENDING" || actionLock.current) return;
    const warning = payment.method === "ONLINE"
      ? `Hủy yêu cầu ONLINE #${payment.id}? Đóng trang VNPay không tự hủy giao dịch. Nếu VNPay đã nhận thanh toán trước khi yêu cầu hủy được xử lý, giao dịch có thể cần nhân viên đối soát/hoàn tiền.`
      : `Hủy yêu cầu COUNTER #${payment.id}? Chỉ hủy khi bạn chưa thanh toán tại quầy.`;
    if (!window.confirm(warning)) return;
    actionLock.current = true;
    setCancelingId(payment.id);
    setError("");
    setNotice("");
    try {
      const updated = await cancelPaymentApi(token, payment.id);
      setPayments((current) => (current || []).map((item) => item.id === updated.id ? updated : item));
      setNotice(updated.status === "CANCELLED"
        ? `Yêu cầu thanh toán #${updated.id} đã được hủy. Bạn có thể tạo yêu cầu mới.`
        : `Máy chủ trả về trạng thái ${updated.status} cho thanh toán #${updated.id}. Đã cập nhật lịch sử.`);
    } catch (requestError) {
      setError(requestError.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403
          ? "Bạn chỉ có thể hủy thanh toán của chính mình."
          : requestError.message || "Không thể hủy yêu cầu thanh toán.");
      await load();
    } finally {
      actionLock.current = false;
      setCancelingId(null);
    }
  }

  const membershipGroups = membershipSections(memberships);
  const renderMembershipRows = (items) => <ul className="dash-rows">{items.map((membership) => {
    const enableReason = autoRenewEnableReason(membership, memberships, payments);
    const canEnable = canEnableMembershipAutoRenew(membership, memberships, payments);
    return <li key={membership.id}>
      <span><b>{membership.planName || "Gói thành viên"}</b><br /><small>{membership.startDate || "—"} → {membership.endDate || "—"}</small><br />
        <small>Auto Renew: {membership.autoRenew ? "Bật" : "Tắt"}. Việc bật chỉ cho phép tạo yêu cầu thanh toán; không tự động thu tiền.</small>
        {!membership.autoRenew && <small className="membership-auto-renew-reason">{enableReason}</small>}</span>
      <span className="membership-auto-renew-actions">
        {canCancelMembershipAutoRenew(membership) && <button type="button" disabled={autoRenewBusyId !== null} onClick={() => void changeMembershipAutoRenew(membership, false)}>{autoRenewBusyId === membership.id ? "Đang cập nhật…" : "Tắt Auto Renew"}</button>}
        {!membership.autoRenew && <button type="button" disabled={autoRenewBusyId !== null || !canEnable} title={enableReason} onClick={() => void changeMembershipAutoRenew(membership, true)}>{autoRenewBusyId === membership.id ? "Đang cập nhật…" : "Bật Auto Renew"}</button>}
      </span>
      <em>{statusLabel[membership.status] || membership.status || "—"}</em>
    </li>;
  })}</ul>;

  return <div className="layout">
    <Sidebar role={role} activePage="member-membership" onLogout={onLogout} onNavigate={onNavigate} />
    <main className="main">
      <header className="member-membership-header">
        <div><h1>Gói thành viên</h1><p className="subtitle">Mua mới hoặc gia hạn bằng VNPay Sandbox hoặc thanh toán tại quầy. Gói chỉ được kích hoạt sau khi máy chủ xác minh hoặc nhân viên xác nhận đã nhận tiền.</p></div>
        <button type="button" onClick={() => void load()} disabled={loading}>{loading ? "Đang tải…" : "Làm mới"}</button>
      </header>
      {error && <div className="banner error" role="alert">{error}</div>}
      {notice && <div className="banner success" role="status">{notice}</div>}

      <section className="dash-card">
        <h2>Membership hiện tại</h2>
        {loading ? <p role="status">Đang tải thông tin…</p> : membershipGroups.current.length === 0
          ? <p className="dash-empty">Bạn chưa có Membership đang có hiệu lực hôm nay.</p>
          : <>{membershipGroups.hasOverlap && <p className="banner error" role="alert">Phát hiện {membershipGroups.current.length} Membership cùng có hiệu lực hôm nay. Dữ liệu có khoảng thời gian chồng lấn; vui lòng liên hệ nhân viên để kiểm tra.</p>}{renderMembershipRows(membershipGroups.current)}</>}
        {!loading && <>
          <h3>Membership đã lên lịch</h3>
          {membershipGroups.future.length ? renderMembershipRows(membershipGroups.future) : <p className="dash-empty">Không có Membership bắt đầu trong tương lai.</p>}
          <h3>Lịch sử Membership</h3>
          {membershipGroups.history.length ? renderMembershipRows(membershipGroups.history) : <p className="dash-empty">Chưa có Membership trong lịch sử.</p>}
        </>}
        {membershipGroups.current.length > 0 && <p className="subtitle">Khi thanh toán gia hạn thành công, thời hạn mới sẽ nối tiếp ngày kết thúc hiện tại theo quy tắc của hệ thống.</p>}
      </section>

      <section className="dash-card renewal-list" aria-labelledby="renewal-heading">
        <h2 id="renewal-heading">Yêu cầu gia hạn</h2>
        {loading ? <p role="status">Đang tải yêu cầu gia hạn…</p> : renewalRows.length === 0
          ? <p className="dash-empty">Hiện không có yêu cầu gia hạn.</p>
          : <div className="renewal-cards">{renewalRows.map((payment) => {
            const options = renewalOptions[payment.id] || { method: payment.method || "", autoRenew: payment.renewalAutoRenewChoice ?? "" };
            const attemptState = renewalAttemptState(payment);
            const attempt = payment.renewalCheckoutAttempt;
            const source = (memberships || []).find((item) => Number(item.id) === Number(payment.renewalSourceMembershipId));
            const pending = payment.status === "PENDING";
            const locked = Boolean(attempt);
            const saved = options.method === payment.method && options.autoRenew === (payment.renewalAutoRenewChoice ?? "");
            const expiry = attempt?.expiresAt ? vietnamDate(attempt.expiresAt) : null;
            const expiryText = expiry ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh" }).format(expiry) : "không rõ";
            return <article className="renewal-card" key={payment.id}>
              <header><div><h3>Gia hạn từ gói #{payment.renewalSourceMembershipId}</h3><p>{source ? `${source.planName || "Gói thành viên"} · ${source.startDate || "—"} → ${source.endDate || "—"}` : "Thông tin gói nguồn được giới hạn theo dữ liệu hiện có."}</p></div>
                <strong className={`renewal-status renewal-status-${String(payment.status).toLowerCase()}`}>{statusLabel[payment.status] || payment.status}</strong></header>
              <p>Thanh toán #{payment.id} · {money(payment.amount)} · {payment.method || "Chưa chọn phương thức"}</p>
              {payment.status === "SUCCESS" && <p role="status">Thanh toán gia hạn đã được xác nhận. Tải lại thông tin gói để xem thời hạn mới.</p>}
              {payment.status === "REFUND_REQUIRED" && <p className="banner error" role="alert">Khoản thanh toán cần nhân viên đối soát. Không tạo thêm thanh toán cho yêu cầu này.</p>}
              {payment.status === "CANCELLED" && <p>Yêu cầu gia hạn đã hủy.</p>}
              {pending && <>
                <fieldset className="renewal-options" disabled={renewalBusyId === payment.id || locked}>
                  <legend>Lựa chọn cho kỳ gia hạn này</legend>
                  <label htmlFor={`renewal-method-${payment.id}`}>Phương thức thanh toán</label>
                  <select id={`renewal-method-${payment.id}`} value={options.method}
                    onChange={(event) => setRenewalOptions((current) => ({ ...current, [payment.id]: { ...options, method: event.target.value } }))}>
                    <option value="">Chọn phương thức</option><option value="ONLINE">ONLINE · VNPay</option><option value="COUNTER">COUNTER · Tại quầy</option>
                  </select>
                  <span className="renewal-choice-label">Tự động gia hạn kỳ tiếp theo</span>
                  <label><input type="radio" name={`renewal-auto-${payment.id}`} checked={options.autoRenew === true} onChange={() => setRenewalOptions((current) => ({ ...current, [payment.id]: { ...options, autoRenew: true } }))} /> Bật</label>
                  <label><input type="radio" name={`renewal-auto-${payment.id}`} checked={options.autoRenew === false} onChange={() => setRenewalOptions((current) => ({ ...current, [payment.id]: { ...options, autoRenew: false } }))} /> Tắt</label>
                  <small>Lựa chọn này chỉ áp dụng cho kỳ gia hạn mới; không cho phép hệ thống tự thu tiền.</small>
                </fieldset>
                {!locked && <button type="button" disabled={renewalBusyId !== null || !options.method || typeof options.autoRenew !== "boolean" || saved}
                  onClick={() => void saveRenewalOptions(payment)}>{renewalBusyId === payment.id ? "Đang lưu…" : "Lưu lựa chọn"}</button>}
                {options.method === "COUNTER" && <p role="note">Thanh toán tại quầy cần nhân viên xác nhận sau khi nhận tiền. Không cần checkout trực tuyến.</p>}
                {attempt && <p role="status">Phiên checkout: {attempt.status}. {attemptState === "ISSUED" ? `Có phiên còn hiệu lực đến ${expiryText}; URL cũ không được lưu trong lịch sử và không thể mở lại tại đây.` : attemptState === "EXPIRED" ? `Phiên đã hết hạn lúc ${expiryText}. Có thể yêu cầu phiên mới nếu máy chủ vẫn cho phép.` : attemptState === "FAILED" ? "Lần thử trước thất bại; khoản gia hạn vẫn đang chờ." : `Trạng thái phiên: ${attempt.status}.`}</p>}
                {options.method === "ONLINE" && payment.renewalAutoRenewChoice !== null && payment.renewalAutoRenewChoice !== undefined && <button type="button" disabled={renewalBusyId !== null || !saved || !canRequestRenewalCheckout(payment)}
                  onClick={() => void startRenewalCheckout(payment)}>{renewalBusyId === payment.id ? "Đang tạo checkout…" : attemptState === "EXPIRED" || attemptState === "FAILED" ? "Thử checkout lại" : "Thanh toán trực tuyến"}</button>}
              </>}
            </article>;
          })}</div>}
      </section>

      <section className="dash-card">
        <h2>Thanh toán đang chờ</h2>
        {loading ? <p role="status">Đang tải lịch sử thanh toán…</p> : pendingOrdinaryMembershipPayments.length === 0
          ? <p className="dash-empty">Không có yêu cầu thanh toán gói nào đang chờ.</p>
          : <ul className="dash-rows">{pendingOrdinaryMembershipPayments.map((payment) => {
            const plan = (plans || []).find((item) => Number(item.id) === Number(payment.referenceId));
            return <li key={payment.id}>
              <span><b>{plan?.name || `Gói #${payment.referenceId}`}</b><br /><small>Thanh toán #{payment.id} · {money(payment.amount)} · {payment.method} · {statusLabel[payment.status]}</small><br /><small>{membershipPaymentMessage(payment)}</small></span>
              <button type="button" disabled={cancelingId !== null} onClick={() => void cancelPendingPayment(payment)}>
                {cancelingId === payment.id ? "Đang hủy…" : "Hủy yêu cầu"}
              </button>
            </li>;
          })}</ul>}
        {pendingMembershipPayments.length > 0 && <p className="banner info" role="note">Không tạo yêu cầu khác khi đang có giao dịch chờ. Thanh toán chỉ kích hoạt sau khi máy chủ xác minh thành công hoặc nhân viên xác nhận đã nhận tiền tại quầy.</p>}
      </section>

      <section className="dash-card">
        <h2>Lịch sử thanh toán gói hội viên</h2>
        {loading ? <p role="status">Đang tải lịch sử thanh toán…</p> : (payments || []).filter((payment) => payment?.isRenewal !== true && payment?.purpose === "MEMBERSHIP").length === 0
          ? <p className="dash-empty">Chưa có thanh toán gói hội viên.</p>
          : <div className="membership-history-scroll" role="region" aria-label="Lịch sử thanh toán gói hội viên" tabIndex="0"><ul className="dash-rows membership-history-rows">{(payments || []).filter((payment) => payment?.isRenewal !== true && payment?.purpose === "MEMBERSHIP").map((payment) => {
            const plan = (plans || []).find((item) => Number(item.id) === Number(payment.referenceId));
            return <li key={payment.id}>
              <div className="membership-history-main"><b>{plan?.name || `Gói #${payment.referenceId}`}</b><span>Thanh toán #{payment.id}</span><span>{payment.method}</span><strong>{money(payment.amount)}</strong><em>{statusLabel[payment.status] || payment.status}</em></div>
              <p>{membershipPaymentMessage(payment)}</p>{payment.note && <p className="membership-payment-note">{payment.note}</p>}
            </li>;
          })}</ul></div>}
      </section>

      {uncertainAttempt && pendingMembershipPayments.length === 0 && <section className="dash-card membership-retry-notice" aria-labelledby="membership-retry-title">
        <h2 id="membership-retry-title">Cần kiểm tra lần thanh toán trước</h2>
        <p>Không tìm thấy kết quả chắc chắn cho yêu cầu gần đây. Làm mới lịch sử và đối chiếu giao dịch trước khi thử lại; liên kết VNPay cũ không thể khôi phục.</p>
        <label><input type="checkbox" checked={retryAccepted} disabled={busyPlanId !== null} onChange={(event) => setRetryAccepted(event.target.checked)} /> Tôi đã kiểm tra lịch sử và xác nhận chưa có thanh toán thành công hoặc đang chờ.</label>
      </section>}

      <h2 style={{ margin: "26px 0 14px", fontSize: 18 }}>Các gói đang mở</h2>
      {loading ? <div className="queue-state" role="status">Đang tải gói…</div>
        : !plans?.length ? <p className="dash-empty">Hiện chưa có gói ACTIVE nào.</p>
          : <div className="pf-plans">{plans.map((plan) => <article key={plan.id} className="pf-plan">
            <h2>{plan.name}</h2><strong>{plan.monthlyPrice == null ? "Chưa cấu hình giá" : `${money(plan.monthlyPrice)} / tháng`}</strong>
            <small>Thời hạn mua cố định: 1 tháng · Giảm khóa học {plan.courseDiscountPercent ?? 0}%</small>
            {plan.description && <p>{plan.description}</p>}
            <div className="membership-payment-actions">
              <button type="button" disabled={busyPlanId !== null || !plan.monthlyPrice || pendingMembershipPayments.length > 0 || (uncertainAttempt && !retryAccepted)} onClick={() => { setSelectedPlan(plan); }}>
                Mua gói
              </button>
            </div>
          </article>)}</div>}
      <p className="banner info" role="note">Thanh toán trực tuyến sử dụng VNPay Sandbox. Gói chỉ được kích hoạt sau khi máy chủ xác minh thanh toán thành công; yêu cầu COUNTER cần nhân viên xác nhận đã nhận tiền.</p>
      {selectedPlan && <div className="membership-modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && busyPlanId === null) setSelectedPlan(null); }}>
        <section className="membership-payment-dialog" role="dialog" aria-modal="true" aria-labelledby="membership-dialog-title" aria-describedby="membership-dialog-description">
          <h2 id="membership-dialog-title">Mua gói một tháng</h2>
          <p id="membership-dialog-description"><strong>{selectedPlan.name}</strong><br />Thời hạn: 1 tháng<br />Giá: {money(selectedPlan.monthlyPrice)}</p>
          <p>Chọn phương thức thanh toán. Chưa có giao dịch nào được tạo.</p>
          <div className="membership-dialog-actions">
            <button type="button" disabled={busyPlanId !== null} onClick={() => void startPurchase(selectedPlan, "ONLINE")}>
              {busyPlanId === selectedPlan.id ? "Đang xử lý…" : "ONLINE — VNPay Sandbox"}
            </button>
            <button type="button" disabled={busyPlanId !== null} onClick={() => void startPurchase(selectedPlan, "COUNTER")}>
              {busyPlanId === selectedPlan.id ? "Đang xử lý…" : "COUNTER — Thanh toán tại quầy"}
            </button>
            <button type="button" className="membership-dialog-cancel" disabled={busyPlanId !== null} onClick={() => setSelectedPlan(null)}>Đóng</button>
          </div>
        </section>
      </div>}
    </main>
  </div>;
}
