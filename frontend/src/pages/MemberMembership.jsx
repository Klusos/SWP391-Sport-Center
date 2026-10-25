import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  cancelPaymentApi,
  createPaymentApi,
  getCurrentUserApi,
  getMembershipsByMemberApi,
  getMyPaymentsApi,
  getPublicMembershipPlansApi,
} from "../api";
import { validVnpayUrl } from "../courseCheckout";
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
      setPlans(rows(availablePlans).filter((plan) => String(plan?.status).toUpperCase() === "ACTIVE"));
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
      setError(requestError.status === 409
        ? "Đã có yêu cầu thanh toán đang chờ cho gói này. Hãy tiếp tục thanh toán hoặc hủy yêu cầu hiện tại."
        : requestError.status === 401
          ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
          : requestError.status === 403
            ? "Bạn chỉ có thể tạo thanh toán cho tài khoản của mình."
            : requestError.message || "Không thể tạo yêu cầu thanh toán.");
      await load();
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

  const currentMemberships = (memberships || []).filter((membership) => membership?.status === "ACTIVE");

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
              <span><b>{plan?.name || `Gói #${payment.referenceId}`}</b><br /><small>Thanh toán #{payment.id} · {money(payment.amount)} · {payment.method === "ONLINE" ? "VNPay Sandbox · trạng thái do máy chủ xác nhận" : "Thanh toán tại quầy · chờ nhân viên xác nhận"}</small></span>
              <button type="button" disabled={cancelingId !== null} onClick={() => void cancelPendingPayment(payment)}>
                {cancelingId === payment.id ? "Đang hủy…" : "Hủy yêu cầu"}
              </button>
            </li>;
          })}</ul>}
        {pendingMembershipPayments.length > 0 && <p className="banner info" role="note">Không thể tạo thêm thanh toán Membership khi còn yêu cầu đang chờ. Với VNPay, hãy kiểm tra trạng thái trước khi thử lại; việc quay về từ cổng thanh toán không tự xác nhận thành công.</p>}
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
