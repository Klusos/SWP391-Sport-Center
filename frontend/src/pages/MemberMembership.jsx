import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import { validVnpayUrl, vietnamDate } from "../courseCheckout";
import { membershipPaymentMessage, membershipPaymentPayload } from "../membershipCheckout";
import {
  cancelPaymentApi,
  createPaymentApi,
  getCurrentUserApi,
  getMembershipsByMemberApi,
  getMyPaymentsApi,
  getPublicMembershipPlansApi,
} from "../api";
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
  const [purchaseMonths, setPurchaseMonths] = useState(1);
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
      setMemberId(user.id);
      setPlans(rows(availablePlans).filter((plan) => String(plan?.status).toUpperCase() === "ACTIVE"));
      try {
        const key = `sportcenter.membership-checkout.${user.id}`;
        const saved = JSON.parse(sessionStorage.getItem(key) || "null");
        const history = rows(ownPayments).filter((payment) => payment.purpose === "MEMBERSHIP");
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
      const payment = await createPaymentApi(token, membershipPaymentPayload(plan.id, method, purchaseMonths));
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

  const currentMemberships = (memberships || []).filter((membership) => membership?.status === "ACTIVE");

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
        <h2>Gói của tôi</h2>
        {loading ? <p role="status">Đang tải thông tin…</p> : currentMemberships.length === 0
          ? <p className="dash-empty">Bạn chưa có gói đang hoạt động.</p>
          : <ul className="dash-rows">{currentMemberships.map((membership) => <li key={membership.id}>
            <span><b>{membership.planName || "Gói thành viên"}</b><br /><small>{membership.startDate || "—"} → {membership.endDate || "—"}</small></span>
            <em>{statusLabel[membership.status] || membership.status || "—"}</em>
          </li>)}</ul>}
        {currentMemberships.length > 0 && <p className="subtitle">Khi thanh toán gia hạn thành công, thời hạn mới sẽ nối tiếp ngày kết thúc hiện tại theo quy tắc của hệ thống.</p>}
      </section>

      <section className="dash-card">
        <h2>Thanh toán đang chờ</h2>
        {loading ? <p role="status">Đang tải lịch sử thanh toán…</p> : pendingMembershipPayments.length === 0
          ? <p className="dash-empty">Không có yêu cầu thanh toán gói nào đang chờ.</p>
          : <ul className="dash-rows">{pendingMembershipPayments.map((payment) => {
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
        {loading ? <p role="status">Đang tải lịch sử thanh toán…</p> : (payments || []).filter((payment) => payment?.purpose === "MEMBERSHIP").length === 0
          ? <p className="dash-empty">Chưa có thanh toán gói hội viên.</p>
          : <div className="membership-history-scroll" role="region" aria-label="Lịch sử thanh toán gói hội viên" tabIndex="0"><ul className="dash-rows membership-history-rows">{(payments || []).filter((payment) => payment?.purpose === "MEMBERSHIP").map((payment) => {
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
            <small>Thời hạn mua: 1, 3, 6 hoặc 12 tháng · Giảm khóa học {plan.courseDiscountPercent ?? 0}%</small>
            {plan.description && <p>{plan.description}</p>}
            <div className="membership-payment-actions">
              <button type="button" disabled={busyPlanId !== null || !plan.monthlyPrice || pendingMembershipPayments.length > 0 || (uncertainAttempt && !retryAccepted)} onClick={() => { setPurchaseMonths(1); setSelectedPlan(plan); }}>
                Mua gói / Gia hạn
              </button>
            </div>
          </article>)}</div>}
      <p className="banner info" role="note">Thanh toán trực tuyến sử dụng VNPay Sandbox. Gói chỉ được kích hoạt sau khi máy chủ xác minh thanh toán thành công; yêu cầu COUNTER cần nhân viên xác nhận đã nhận tiền.</p>
      {selectedPlan && <div className="membership-modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget && busyPlanId === null) setSelectedPlan(null); }}>
        <section className="membership-payment-dialog" role="dialog" aria-modal="true" aria-labelledby="membership-dialog-title" aria-describedby="membership-dialog-description">
          <h2 id="membership-dialog-title">Mua gói / Gia hạn</h2>
          <p id="membership-dialog-description"><strong>{selectedPlan.name}</strong><br />Giá hàng tháng: {money(selectedPlan.monthlyPrice)}</p>
          <label htmlFor="membership-purchase-months">Thời hạn mua</label>
          <select id="membership-purchase-months" value={purchaseMonths} disabled={busyPlanId !== null}
            onChange={(event) => setPurchaseMonths(Number(event.target.value))}>
            {[1, 3, 6, 12].map((months) => <option key={months} value={months}>{months} tháng</option>)}
          </select>
          <p>Tổng tiền: {money(Number(selectedPlan.monthlyPrice) * purchaseMonths)}</p>
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
