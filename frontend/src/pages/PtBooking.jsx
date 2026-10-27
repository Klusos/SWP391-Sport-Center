import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import {
  cancelPaymentApi,
  cancelPtBookingApi,
  createPaymentApi,
  createPtBookingApi,
  getMyPaymentsApi,
  getMyPtBookingsApi,
  getPtEligibleCoachesApi,
  getPublicSportsApi,
} from "../api";
import { validVnpayUrl } from "../courseCheckout";
import {
  canMemberCancel, formatPtTime, holdActive, minutesLeft, pendingPtPayment, PT_CANCEL_NOTICE_HOURS, PT_HOLD_MINUTES,
  ptErrorMessage, ptStatusClass, ptStatusLabel,
} from "../ptBooking";
import "./PtBooking.css";

const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];
const money = (value) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(value ?? 0);

function returnMessage(status, paymentId) {
  const label = paymentId ? `Thanh toán #${paymentId}` : "Thanh toán";
  if (status === "SUCCESS") return { ok: true, text: `${label} qua VNPay thành công. Buổi PT của bạn đã được xác nhận.` };
  if (status === "REFUND_REQUIRED") return { ok: false, text: `${label} được ghi nhận sau khi hết thời gian giữ chỗ; trung tâm sẽ hoàn tiền cho bạn.` };
  if (status === "PENDING") return { ok: true, text: `${label} đang chờ VNPay xác nhận. Làm mới trang sau ít phút.` };
  return { ok: false, text: `${label} chưa hoàn tất (${status}). Bạn có thể thử lại khi buổi còn trong thời gian giữ chỗ.` };
}

export default function PtBooking({ token, role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]), [sportId, setSportId] = useState("");
  const [startTime, setStartTime] = useState(""), [coaches, setCoaches] = useState([]);
  const [history, setHistory] = useState([]), [selectedCoach, setSelectedCoach] = useState("");
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false);
  const [error, setError] = useState(""), [notice, setNotice] = useState("");
  const [now, setNow] = useState(Date.now);
  const [searchParams] = useSearchParams();
  const action = useRef(false);
  const lifecycle = useRef(0);

  const loadHistory = useCallback(async () => {
    const [bookings, ownPayments] = await Promise.all([getMyPtBookingsApi(token), getMyPaymentsApi(token)]);
    setHistory(rows(bookings));
    setPayments(rows(ownPayments));
  }, [token]);

  useEffect(() => {
    const version = ++lifecycle.current;
    let active = true;
    Promise.resolve().then(async () => {
      if (!active) return;
      setLoading(true); setError("");
      try {
        const [items] = await Promise.all([getPublicSportsApi(), loadHistory()]);
        if (active && lifecycle.current === version) setSports(rows(items));
      } catch (requestError) { if (active) setError(ptErrorMessage(requestError, "Không tải được dữ liệu đặt PT.")); }
      finally { if (active) setLoading(false); }
    });
    const clock = window.setInterval(() => setNow(Date.now()), 15000);
    return () => { active = false; lifecycle.current += 1; window.clearInterval(clock); };
  }, [loadHistory]);

  function changeCriteria(setter, value) {
    setter(value); setCoaches([]); setSelectedCoach(""); setNotice("");
  }

  // Runs one user action at a time and reloads the history afterwards, even when the action fails.
  async function run(task, fallback) {
    if (action.current || loading) return;
    action.current = true; setBusy(true); setError(""); setNotice("");
    const version = lifecycle.current;
    try {
      await task(version);
    } catch (requestError) {
      if (lifecycle.current === version) setError(ptErrorMessage(requestError, fallback));
    } finally {
      try { if (lifecycle.current === version) await loadHistory(); } catch { /* keep the error shown above */ }
      action.current = false;
      if (lifecycle.current === version) setBusy(false);
    }
  }

  function findCoaches(event) {
    event.preventDefault();
    if (!sports.some((sport) => String(sport.id) === sportId) || !startTime) {
      setError("Vui lòng chọn môn và giờ bắt đầu."); return;
    }
    setCoaches([]); setSelectedCoach("");
    void run(async (version) => {
      const eligible = rows(await getPtEligibleCoachesApi(token, sportId, startTime));
      if (lifecycle.current !== version) return;
      setCoaches(eligible);
      if (!eligible.length) setNotice("Không có huấn luyện viên phù hợp nào rảnh vào giờ này.");
    }, "Không kiểm tra được lịch huấn luyện viên.");
  }

  function book(event) {
    event.preventDefault();
    const choice = coaches.find((coach) => String(coach.coachId) === selectedCoach);
    if (!choice) { setError("Vui lòng chọn một huấn luyện viên."); return; }
    void run(async (version) => {
      const result = await createPtBookingApi(token, { coachId: choice.coachId, sportId: Number(sportId), startTime });
      if (lifecycle.current !== version) return;
      setCoaches([]); setSelectedCoach("");
      setNotice(`Đã giữ chỗ buổi PT #${result.id} đến ${formatPtTime(result.paymentExpiresAt, false)}. `
        + `Vui lòng thanh toán trong ${PT_HOLD_MINUTES} phút (VNPay hoặc tại quầy) ở mục lịch sử bên dưới; quá hạn buổi sẽ tự hủy.`);
    }, "Đặt buổi PT thất bại.");
  }

  function payOnline(booking, replacing = null) {
    void run(async () => {
      if (replacing) await cancelPaymentApi(token, replacing.id);
      const payment = await createPaymentApi(token, { method: "ONLINE", purpose: "PT_BOOKING", referenceId: booking.id });
      if (!validVnpayUrl(payment.paymentUrl)) {
        throw new Error(`Đã tạo thanh toán #${payment.id} nhưng không nhận được liên kết VNPay hợp lệ. Hãy hủy yêu cầu đó rồi thử lại.`);
      }
      window.location.assign(payment.paymentUrl);
    }, "Không mở được thanh toán VNPay.");
  }

  function payAtCounter(booking) {
    if (!window.confirm(`Tạo yêu cầu thanh toán tại quầy ${money(booking.finalPrice)} cho buổi PT #${booking.id}? `
      + `Bạn cần đến quầy trả tiền trước ${formatPtTime(booking.paymentExpiresAt, false)}; quá hạn giữ chỗ buổi sẽ tự hủy.`)) return;
    void run(async () => {
      const payment = await createPaymentApi(token, { method: "COUNTER", purpose: "PT_BOOKING", referenceId: booking.id });
      setNotice(`Đã tạo yêu cầu thanh toán tại quầy #${payment.id}. Vui lòng đến quầy trước ${formatPtTime(booking.paymentExpiresAt, false)}.`);
    }, "Không tạo được yêu cầu thanh toán tại quầy.");
  }

  function cancelPayment(payment) {
    if (!window.confirm(`Hủy yêu cầu thanh toán #${payment.id}? Buổi PT vẫn được giữ chỗ đến hết thời hạn.`)) return;
    void run(async () => {
      await cancelPaymentApi(token, payment.id);
      setNotice(`Đã hủy yêu cầu thanh toán #${payment.id}. Bạn có thể chọn cách thanh toán khác.`);
    }, "Không hủy được yêu cầu thanh toán.");
  }

  function cancelBooking(booking) {
    const refund = booking.status === "CONFIRMED" ? " Khoản đã thanh toán sẽ được hoàn 100% tại quầy." : "";
    if (!window.confirm(`Hủy buổi PT ${booking.sportName} lúc ${formatPtTime(booking.startTime)} với ${booking.coachName}?${refund}`)) return;
    void run(async () => {
      await cancelPtBookingApi(token, booking.id);
      setNotice(`Đã hủy buổi PT #${booking.id}.${booking.status === "CONFIRMED" ? " Vui lòng liên hệ lễ tân để nhận lại tiền." : ""}`);
    }, "Không hủy được buổi PT.");
  }

  const returnedStatus = searchParams.get("paymentStatus");
  const returned = returnedStatus ? returnMessage(returnedStatus, searchParams.get("paymentId")) : null;

  return <div className="layout pt-booking-layout">
    <Sidebar role={role} activePage="member-pt-booking" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main pt-main">
      <div className="pt-page">
        <header className="pt-header">
          <div className="pt-header-content">
            <p className="pt-eyebrow">SPORTCENTER / HỘI VIÊN</p>
            <h1>Đặt buổi PT 1-1</h1>
            <p className="pt-description">Mỗi buổi 90 phút. Sau khi đặt, chỗ được giữ {PT_HOLD_MINUTES} phút để bạn thanh toán qua VNPay hoặc tại quầy.
              Buổi đã xác nhận có thể hủy trước giờ tập ít nhất {PT_CANCEL_NOTICE_HOURS} giờ và được hoàn 100%.</p>
            <Link className="pt-assessment-link" to="/member/levels-assessments">Trình độ &amp; lịch sử đánh giá</Link>
          </div>
        </header>

        {returned && <p className={"pt-alert " + (returned.ok ? "pt-success" : "pt-error")} role="status">{returned.text}</p>}
        {error && <p className="pt-alert pt-error" role="alert">{error}</p>}
        {notice && <p className="pt-alert pt-success" role="status">{notice}</p>}

        <section className="pt-card">
          <div className="pt-section-heading">
            <span className="pt-section-icon"><span aria-hidden="true">🏋️</span></span>
            <div><h2>Chọn môn và giờ bắt đầu</h2><p>Hệ thống chỉ hiện huấn luyện viên dạy môn này và còn trống trong 90 phút đó.</p></div>
          </div>

          {loading ? <div className="pt-loading" role="status"><span className="pt-spinner" /><span>Đang tải…</span></div>
            : <form className="pt-form" onSubmit={findCoaches}>
              <label className="pt-field"><span>Môn thể thao</span>
                <select required value={sportId} onChange={(e) => changeCriteria(setSportId, e.target.value)} disabled={busy}>
                  <option value="">Chọn môn</option>
                  {sports.map((sport) => <option key={sport.id} value={sport.id}>{sport.name}</option>)}
                </select>
              </label>
              <label className="pt-field"><span>Giờ bắt đầu</span>
                <input required type="datetime-local" value={startTime} onChange={(e) => changeCriteria(setStartTime, e.target.value)} disabled={busy} />
              </label>
              <button className="pt-primary-button pt-find-button" type="submit" disabled={busy || !sportId || !startTime}>
                {busy ? "Đang kiểm tra…" : "Tìm huấn luyện viên"}
              </button>
            </form>}

          {!loading && coaches.length > 0 && <form className="pt-coach-form" onSubmit={book}>
            <div className="pt-subsection-heading"><div><h3>Huấn luyện viên còn trống</h3><p>{coaches.length} huấn luyện viên</p></div></div>
            <div className="pt-coach-list">
              {coaches.map((coach) => <label className={"pt-coach" + (selectedCoach === String(coach.coachId) ? " selected" : "")} key={coach.coachId}>
                <input type="radio" name="coach" required value={coach.coachId} checked={selectedCoach === String(coach.coachId)}
                  onChange={(e) => setSelectedCoach(e.target.value)} disabled={busy} />
                <span className="pt-coach-info"><strong>{coach.coachName}</strong><small>{coach.sportName} · {coach.durationMinutes} phút</small></span>
                <span className="pt-price">
                  {Number(coach.discount) > 0 && <del>{money(coach.originalPrice)}</del>}
                  <strong>{money(coach.finalPrice)}</strong>
                  {Number(coach.discount) > 0 && <small>Ưu đãi hội viên −{money(coach.discount)}</small>}
                </span>
              </label>)}
            </div>
            <button className="pt-primary-button pt-book-button" type="submit" disabled={busy || !selectedCoach}>
              {busy ? "Đang xử lý…" : `Đặt buổi và giữ chỗ ${PT_HOLD_MINUTES} phút`}
            </button>
          </form>}
        </section>

        <section className="pt-card pt-history-card">
          <div className="pt-section-heading">
            <span className="pt-section-icon"><span aria-hidden="true">📅</span></span>
            <div><h2>Lịch sử đặt PT</h2><p>Thanh toán, theo dõi và hủy các buổi PT của bạn.</p></div>
          </div>

          {loading ? <div className="pt-loading" role="status">Đang tải lịch sử…</div>
            : history.length === 0 ? <div className="pt-empty"><span className="pt-empty-icon" aria-hidden="true">📋</span>
              <strong>Bạn chưa đặt buổi PT nào.</strong><p>Các buổi đã đặt sẽ hiện ở đây.</p></div>
              : <div className="pt-history">{history.map((booking) => {
                const pending = pendingPtPayment(payments, booking.id);
                const holding = holdActive(booking, now);
                return <article className="pt-history-row" key={booking.id}>
                  <div className="pt-history-main">
                    <strong>{booking.sportName} · {booking.coachName}</strong>
                    <span>{formatPtTime(booking.startTime)}</span>
                    <small>Kết thúc {formatPtTime(booking.endTime, false)} · Buổi #{booking.id}</small>
                    {holding && !pending && <small>Còn khoảng {minutesLeft(booking, now)} phút để thanh toán (hạn {formatPtTime(booking.paymentExpiresAt, false)}).</small>}
                    {holding && pending?.method === "COUNTER" && <small>Chờ thanh toán tại quầy (#{pending.id}) — đến quầy trước {formatPtTime(booking.paymentExpiresAt, false)}.</small>}
                    {holding && pending?.method === "ONLINE" && <small>Đang chờ VNPay xác nhận thanh toán #{pending.id}.</small>}
                  </div>
                  <div className="pt-history-meta">
                    <strong className="pt-history-price">{money(booking.finalPrice)}</strong>
                    <span className={"pt-status " + (ptStatusClass[booking.status] || "")}>{ptStatusLabel[booking.status] || booking.status}</span>
                    {holding && !pending && <>
                      <button type="button" className="pt-primary-button" disabled={busy} onClick={() => payOnline(booking)}>Thanh toán VNPay</button>
                      <button type="button" className="pt-secondary-button" disabled={busy} onClick={() => payAtCounter(booking)}>Thanh toán tại quầy</button>
                    </>}
                    {holding && pending?.method === "COUNTER" && <button type="button" className="pt-primary-button" disabled={busy}
                      onClick={() => payOnline(booking, pending)}>Đổi sang VNPay</button>}
                    {holding && pending && <button type="button" className="pt-secondary-button" disabled={busy}
                      onClick={() => cancelPayment(pending)}>Hủy yêu cầu thanh toán</button>}
                    {canMemberCancel(booking, now) && <button type="button" className="pt-secondary-button" disabled={busy}
                      onClick={() => cancelBooking(booking)}>Hủy buổi</button>}
                  </div>
                </article>;
              })}</div>}
        </section>
      </div>
    </main>
  </div>;
}
