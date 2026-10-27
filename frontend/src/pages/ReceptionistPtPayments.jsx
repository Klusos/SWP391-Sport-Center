import { useCallback, useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import { getPendingPtPaymentsApi, processCounterPaymentApi } from "../api";
import { formatPtTime, ptErrorMessage, vietnamInstant } from "../ptBooking";
import "./PtBooking.css";

const money = (value) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(value ?? 0);

export default function ReceptionistPtPayments({ token, role, onLogout, onNavigate }) {
  const [queue, setQueue] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [now, setNow] = useState(Date.now);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getPendingPtPaymentsApi(token, page);
      if (page > 0 && result.content.length === 0) setPage(page - 1);
      else setQueue(result);
    } catch (requestError) {
      setError(requestError.status === 403 ? "Bạn không có quyền xem hàng chờ thanh toán PT." : ptErrorMessage(requestError, "Không tải được hàng chờ."));
    } finally { setLoading(false); }
  }, [token, page]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => active && load());
    // Holds last 15 minutes, so the queue refreshes often and the countdown every few seconds.
    const refresh = window.setInterval(() => void load(), 20000);
    const clock = window.setInterval(() => setNow(Date.now()), 5000);
    return () => { active = false; window.clearInterval(refresh); window.clearInterval(clock); };
  }, [load]);

  async function settle(row, success) {
    if (busy) return;
    const question = success
      ? `Xác nhận đã nhận đủ ${money(row.amount)} từ ${row.memberName} cho buổi PT #${row.bookingId} (thanh toán #${row.paymentId})?`
      : `Hủy thanh toán #${row.paymentId} vì chưa nhận tiền? Buổi PT vẫn giữ chỗ đến hết hạn để hội viên chọn cách khác.`;
    if (!window.confirm(question)) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const result = await processCounterPaymentApi(token, row.paymentId, success,
        success ? "PT payment received at the counter" : "PT payment not received at the counter");
      if (result.status === "SUCCESS") setNotice(`Thanh toán #${row.paymentId} thành công. Buổi PT #${row.bookingId} đã được xác nhận.`);
      else if (result.status === "REFUND_REQUIRED") setError(`Thanh toán #${row.paymentId} được ghi nhận sau khi hết giữ chỗ nên buổi không được xác nhận. Khoản này nằm trong mục "Cần hoàn tiền".`);
      else setNotice(`Đã hủy thanh toán #${row.paymentId}.`);
    } catch (requestError) {
      setError(ptErrorMessage(requestError, "Không xử lý được thanh toán."));
    } finally {
      setBusy(false);
      await load();
    }
  }

  return <div className="layout">
    <Sidebar role={role} activePage="pt-payments" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main pt-main">
      <div className="pt-page">
        <header className="pt-header"><div className="pt-header-content"><p className="pt-eyebrow">SPORTCENTER / THU TIỀN TẠI QUẦY</p>
          <h1>Thanh toán buổi PT</h1>
          <p className="pt-description">Yêu cầu thanh toán tại quầy của các buổi PT còn trong thời gian giữ chỗ 15 phút. Chỉ xác nhận khi đã thực sự nhận đủ tiền; tự làm mới mỗi 20 giây.</p></div>
          <button type="button" className="pt-primary-button" disabled={loading || busy} onClick={() => void load()}>Làm mới</button>
        </header>
        {error && <p className="pt-alert pt-error" role="alert">{error}</p>}
        {notice && <p className="pt-alert pt-success" role="status">{notice}</p>}
        <section className="pt-card">
          {loading && !queue ? <p role="status">Đang tải hàng chờ…</p> : !queue || queue.content.length === 0
            ? <p className="pt-empty">Không có thanh toán PT nào đang chờ tại quầy.</p>
            : <div className="pt-table-wrap"><table className="pt-receptionist-table">
              <thead><tr><th>Hội viên</th><th>Buổi PT</th><th>Số tiền</th><th>Hạn giữ chỗ</th><th>Thao tác</th></tr></thead>
              <tbody>{queue.content.map((row) => {
                const deadline = vietnamInstant(row.paymentDeadline);
                const left = deadline ? Math.ceil((deadline.getTime() - now) / 60000) : 0;
                return <tr key={row.paymentId}>
                  <td>{row.memberName}<small>Hội viên #{row.memberId}</small></td>
                  <td>{row.sportName} · {row.coachName}<small>{formatPtTime(row.startTime)} – {formatPtTime(row.endTime, false)}</small>
                    <small>Buổi #{row.bookingId} · Thanh toán #{row.paymentId}</small></td>
                  <td>{money(row.amount)}</td>
                  <td>{formatPtTime(row.paymentDeadline, false)}<small>{left > 0 ? `Còn khoảng ${left} phút` : "Đã hết hạn; làm mới"}</small></td>
                  <td>
                    <button type="button" className="pt-primary-button" disabled={busy || left <= 0} onClick={() => void settle(row, true)}>Đã nhận tiền</button>{" "}
                    <button type="button" className="pt-secondary-button" disabled={busy} onClick={() => void settle(row, false)}>Hủy (chưa nhận tiền)</button>
                  </td>
                </tr>;
              })}</tbody>
            </table></div>}
          {queue && queue.totalPages > 1 && <p>
            <button type="button" className="pt-secondary-button" disabled={busy || page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
            {" "}Trang {page + 1} / {queue.totalPages}{" "}
            <button type="button" className="pt-secondary-button" disabled={busy || queue.last} onClick={() => setPage(page + 1)}>Trang sau</button>
          </p>}
        </section>
      </div>
    </main>
  </div>;
}
