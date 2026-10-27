import { useCallback, useEffect, useRef, useState } from "react";
import { Link, Navigate, useLocation, useParams } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import PaymentResultCard from "../components/PaymentResultCard";
import { createPaymentApi, getCurrentUserApi, getMembershipsByMemberApi, getMyPaymentsApi, getMyPtBookingsApi, getMyRegistrationsApi, cancelPaymentApi } from "../api";
import { acquireAction, canCheckout, checkoutAccess, paymentMessage, pendingCounter, positiveId, prepareCheckout, registrationMessage, registrationPayments, shouldPoll, startStatusPolling, validVnpayUrl, vietnamDate } from "../courseCheckout";
import { findOwnedPayment, findOwnedPtBooking } from "../paymentReturn";
import { isMembershipPayment } from "../membershipUpgrade";
import "./CourseCheckout.css";

const money = (value) => Number(value).toLocaleString("vi-VN", { style: "currency", currency: "VND" });
const deadline = (value) => vietnamDate(value) ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh" }).format(vietnamDate(value)) : "Không có hạn hợp lệ";
const array = (value) => Array.isArray(value) ? value : value?.content ?? [];

export default function CourseCheckout({ auth, onLogout, onNavigate }) {
  const location = useLocation();
  const { registrationId } = useParams();
  const params = new URLSearchParams(location.search);
  const result = !registrationId;
  const paymentId = positiveId(params.get("paymentId"));
  const key = `sportcenter.course-checkout.${auth?.userId}`;
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [statusError, setStatusError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [retryAccepted, setRetryAccepted] = useState(false);
  const [pollEnded, setPollEnded] = useState(false);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const action = useRef(false);
  const alive = useRef(false);
  const token = auth?.accessToken;

  function context() {
    try { return JSON.parse(sessionStorage.getItem(key) || "null"); } catch { return null; }
  }
  function remember(value) {
    // IDs and an uncertain-attempt flag only. Never store a gateway URL or credentials.
    try { sessionStorage.setItem(key, JSON.stringify(value)); }
    catch { throw new Error("Không thể lưu trạng thái khôi phục trong phiên trình duyệt. Vui lòng cho phép lưu trữ phiên trước khi tạo thanh toán."); }
  }

  const read = useCallback(async () => {
    if (result) {
      if (!paymentId) return { payments: array(await getMyPaymentsApi(token)), registrations: [], memberships: [], ptBookings: [] };
      const payments = array(await getMyPaymentsApi(token));
      const returnedPayment = findOwnedPayment(payments, paymentId);
      if (isMembershipPayment(returnedPayment?.purpose)) {
        const user = await getCurrentUserApi(token);
        const memberships = array(await getMembershipsByMemberApi(token, user.id));
        return { payments, registrations: [], memberships, ptBookings: [] };
      }
      if (returnedPayment?.purpose === "PT_BOOKING") {
        const ptBookings = array(await getMyPtBookingsApi(token));
        return { payments, registrations: [], memberships: [], ptBookings };
      }
      if (returnedPayment?.purpose === "COURSE_REGISTRATION") {
        const registrations = array(await getMyRegistrationsApi(token));
        return { payments, registrations, memberships: [], ptBookings: [] };
      }
      // Unknown and absent purposes need no course-registration read to render a safe fallback.
      const registrations = [];
      return { payments, registrations, memberships: [], ptBookings: [] };
    }
    const [payments, registrations] = await Promise.all([getMyPaymentsApi(token), getMyRegistrationsApi(token)]);
    return { payments: array(payments), registrations: array(registrations), memberships: [], ptBookings: [] };
  }, [token, result, paymentId]);

  useEffect(() => {
    const resume = () => { if (!action.current) setRefreshVersion((value) => value + 1); };
    window.addEventListener("pageshow", resume);
    window.addEventListener("focus", resume);
    return () => { window.removeEventListener("pageshow", resume); window.removeEventListener("focus", resume); };
  }, []);

  useEffect(() => {
    alive.current = true;
    const stop = token && auth.role === "MEMBER" ? startStatusPolling(read,
      (next, ended) => { setData(next); setStatusError(""); setPollEnded(ended); },
      (requestError) => { setStatusError(requestError.message); setPollEnded(true); },
      (next) => {
        const payment = next.payments.find((item) => item.id === paymentId);
        const registration = next.registrations.find((item) => item.id === (positiveId(registrationId) || (payment?.purpose === "COURSE_REGISTRATION" ? payment.referenceId : null)));
        return shouldPoll(registration, registrationPayments(next.payments, registration?.id))
          || (result && paymentId && !payment)
          || (result && isMembershipPayment(payment?.purpose) && payment.status === "PENDING")
          || (result && payment?.purpose === "PT_BOOKING" && payment.status === "PENDING");
      }) : () => {};
    return () => { alive.current = false; stop(); };
  }, [read, token, auth?.role, paymentId, registrationId, result, refreshVersion]);

  if (checkoutAccess(auth) === "LOGIN") return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (checkoutAccess(auth) !== "MEMBER") return <Navigate to="/" replace />;

  const targetPayment = data?.payments.find((payment) => payment.id === paymentId);
  const targetPtBooking = findOwnedPtBooking(data?.ptBookings, targetPayment);
  // A return query must resolve through authenticated ownership; never fall back to a guessed registration.
  const id = result ? (targetPayment?.purpose === "COURSE_REGISTRATION" ? targetPayment.referenceId : null) : positiveId(registrationId);
  const registration = data?.registrations.find((item) => item.id === id);
  const payments = registrationPayments(data?.payments || [], id);
  const counter = pendingCounter(payments);
  const uncertain = payments.some((payment) => payment.method === "ONLINE" && payment.status === "PENDING") || (context()?.registrationId === id && context()?.uncertain);
  const blocked = payments.some((payment) => ["SUCCESS", "REFUND_REQUIRED"].includes(payment.status));

  async function refresh() {
    if (!acquireAction(action)) return;
    setBusy(true); setError(""); setStatusError("");
    try { const next = await read(); if (alive.current) setData(next); }
    catch (requestError) { if (alive.current) setError(requestError.message); }
    finally { action.current = false; if (alive.current) setBusy(false); }
  }

  async function pay(method) {
    if (!acquireAction(action)) return;
    setBusy(true); setError(""); setNotice("");
    let started = false;
    try {
      const next = await read();
      if (!alive.current) return;
      setData(next);
      const prepared = prepareCheckout(next, id, method, retryAccepted, context()?.registrationId === id && context()?.uncertain);
      if (prepared.counter) { setNotice(`Đã có thanh toán tại quầy #${prepared.counter.id}. Vui lòng hoàn tất tại lễ tân; không tạo thêm giao dịch.`); return; }
      // Persist before POST so an interrupted request cannot silently become a second attempt.
      remember({ registrationId: id, uncertain: true });
      started = true;
      const payment = await createPaymentApi(token, prepared.payload);
      if (!alive.current) return;
      setData((previous) => ({ ...previous, payments: [payment, ...previous.payments.filter((item) => item.id !== payment.id)] }));
      remember({ registrationId: id, paymentId: payment.id, uncertain: false });
      setRetryAccepted(false);
      setRefreshVersion((value) => value + 1);
      if (method === "ONLINE") {
        if (!validVnpayUrl(payment.paymentUrl)) throw new Error("Không mở được URL VNPay Sandbox hợp lệ. Giao dịch có thể vẫn đang chờ; kiểm tra lịch sử trước khi thử lại.");
        window.location.assign(payment.paymentUrl);
      } else setNotice(`Đã tạo thanh toán tại quầy #${payment.id}. Vui lòng đến lễ tân trước hạn đăng ký. Chỗ học chỉ được xác nhận sau khi nhân viên ghi nhận đã nhận tiền.`);
    } catch (requestError) {
      if (alive.current) setError(`${requestError.message}${started ? " Nếu yêu cầu bị gián đoạn, hãy làm mới trạng thái trước khi thử lại; không thể kết luận tiền đã được nhận." : ""}`);
    } finally { action.current = false; if (alive.current) setBusy(false); }
  }

  async function cancelCounter(payment) {
    if (action.current || !window.confirm(`Hủy thanh toán tại quầy #${payment.id}? Thao tác này không hủy đăng ký giữ chỗ.`)) return;
    action.current = true; setBusy(true); setError("");
    try {
      await cancelPaymentApi(token, payment.id);
      const next = await read();
      if (alive.current) { setData(next); setNotice("Đã hủy thanh toán tại quầy. Đăng ký giữ chỗ có trạng thái riêng."); }
    } catch (requestError) { if (alive.current) setError(requestError.message); }
    finally { action.current = false; if (alive.current) setBusy(false); }
  }

  if (result) {
    return <main className="payment-result-page">
        <PaymentResultCard
          payment={targetPayment}
          ptBooking={targetPtBooking}
          memberships={data?.memberships || []}
          registration={registration}
          loading={!data && !error && !statusError}
          error={error}
          statusError={statusError}
          pollEnded={pollEnded}
          onRefresh={() => void refresh()}
          busy={busy}
        />
      </main>;
  }

  return <div className="layout"><Sidebar role="MEMBER" activePage="member-courses" onLogout={onLogout} onNavigate={onNavigate} />
    <main className="main course-checkout"><h1>Thanh toán đăng ký lớp</h1>
      <p>Kiểm tra trạng thái thanh toán và đăng ký của bạn.</p>
      <Link to="/member/courses">Quay lại khóa học và lịch sử đăng ký</Link>
      {(error || statusError) && <p className="banner error" role="alert">{error || statusError}</p>}
      {notice && <p role="status">{notice}</p>}
      {!data ? <p role="status">{error || statusError ? "Chưa tải được trạng thái xác thực." : "Đang tải trạng thái…"}</p> : <>
        {!result && !registration && <p role="alert">Không tìm thấy đăng ký trong tài khoản của bạn.</p>}
        {registration && <>
          <section><h2>Đăng ký #{registration.id}</h2><p>{registration.courseName} · {registration.courseOfferingName} · {registration.className}</p>
            <dl><dt>Thành tiền</dt><dd>{money(registration.finalPrice)}</dd><dt>Trạng thái đăng ký</dt><dd>{registration.status}</dd><dt>Hạn thanh toán (giờ Việt Nam)</dt><dd>{deadline(registration.paymentDeadline)}</dd></dl>
            <p role="status">{registrationMessage(registration)}</p>
            {registration.status === "CONFIRMED" && <Link to="/member/schedule">Xem lịch học</Link>}
          </section>
          <section><h2>Lịch sử thanh toán của đăng ký</h2>{payments.length === 0 ? <p>Chưa có giao dịch được máy chủ ghi nhận.</p> : payments.map((payment) => <article key={payment.id}>
            <h3>Thanh toán #{payment.id} · {payment.method}</h3><p>{money(payment.amount)} · {payment.status}</p><p role="status">{paymentMessage(payment)}</p>
            {payment.method === "COUNTER" && payment.status === "PENDING" && <><p>Đến lễ tân trước {deadline(registration.paymentDeadline)}. Hội viên không thể tự xác nhận đã nhận tiền.</p><button type="button" disabled={busy} onClick={() => void cancelCounter(payment)}>Hủy yêu cầu tại quầy</button></>}
            {payment.method === "ONLINE" && payment.status === "PENDING" && <p>Liên kết VNPay có hiệu lực khoảng 15 phút và không thể khôi phục từ lịch sử. Không thể xác định giao dịch đang xử lý hay liên kết đã hết hạn. Không trả tiền lần nữa nếu đã bị trừ tiền; hãy liên hệ lễ tân.</p>}
          </article>)}</section>
          {canCheckout(registration) && !blocked && <section><h2>Chọn cách thanh toán</h2>
            {uncertain && <label className="checkout-warning"><input type="checkbox" checked={retryAccepted} disabled={busy} onChange={(event) => setRetryAccepted(event.target.checked)} /> Tôi đã kiểm tra giao dịch trước, chưa thanh toán và hiểu lần thử mới có thể tạo giao dịch trùng. Nếu đã trả tiền hoặc chưa chắc chắn, tôi sẽ liên hệ lễ tân.</label>}
            {counter ? <p>Đã có yêu cầu tại quầy #{counter.id}. Hoàn tất tại lễ tân hoặc hủy yêu cầu này trước khi chọn cách khác.</p> : <div className="checkout-actions">
              <button type="button" disabled={busy || (uncertain && !retryAccepted)} onClick={() => void pay("ONLINE")}>Thanh toán VNPay Sandbox</button>
              <button type="button" disabled={busy || (uncertain && !retryAccepted)} onClick={() => void pay("COUNTER")}>Thanh toán tại lễ tân</button>
            </div>}
          </section>}
        </>}
      </>}
      <button type="button" disabled={busy} onClick={() => void refresh()}>{busy ? "Đang xử lý…" : "Làm mới trạng thái"}</button>
      {pollEnded && <p>Tự động kiểm tra đã dừng. Bạn có thể làm mới để xem trạng thái mới nhất.</p>}
    </main>
  </div>;
}
