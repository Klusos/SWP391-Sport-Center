import { useEffect, useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { getPendingCoursePaymentsApi, getMemberPaymentsApi, processCounterPaymentApi } from "../api";
import { canViewCoursePayments, coursePaymentExpired, coursePaymentMoney, coursePaymentTime, createCoursePaymentQueue } from "../coursePaymentQueue";
import { vietnamDate } from "../courseCheckout";
import "./ReceptionistCoursePayments.css";

function readUncertain(key) {
  try {
    const value = JSON.parse(sessionStorage.getItem(key) || "[]");
    return Array.isArray(value) ? value.filter((row) => [row.paymentId, row.memberId, row.registrationId].every((id) => Number.isSafeInteger(id) && id > 0)) : [];
  } catch { return []; }
}

export default function ReceptionistCoursePayments({ token, role, userId, onNavigate, onLogout }) {
  const [queue, setQueue] = useState({ loading: true, data: null, page: 0, error: "" });
  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState(null);
  const [uncertain, setUncertain] = useState([]);
  const [now, setNow] = useState(Date.now);
  const controller = useRef(null);
  const storageKey = `sportcenter.course-receipts.${userId}.${role}`;

  useEffect(() => {
    if (!token || !canViewCoursePayments(role)) return undefined;
    const initial = readUncertain(storageKey);
    const workflow = createCoursePaymentQueue({
      fetchQueue: (page, size) => getPendingCoursePaymentsApi(token, page, size),
      settle: (id, success, note) => processCounterPaymentApi(token, id, success, note),
      fetchHistory: (memberId) => getMemberPaymentsApi(token, memberId),
      confirm: (text) => window.confirm(text),
      onQueue: setQueue, onBusy: setBusy, onOutcome: setOutcome, onUncertain: setUncertain,
      initialUncertain: initial,
      saveUncertain: (rows) => sessionStorage.setItem(storageKey, JSON.stringify(rows)),
    });
    controller.current = workflow;
    let active = true;
    Promise.resolve().then(() => { if (active) { setUncertain(initial); void workflow.load(0); } });
    const refresh = () => { void workflow.refresh(); };
    const timer = window.setInterval(refresh, 30000);
    const clock = window.setInterval(() => setNow(Date.now()), 1000);
    window.addEventListener("focus", refresh);
    return () => {
      active = false; workflow.dispose(); controller.current = null;
      window.clearInterval(timer); window.clearInterval(clock); window.removeEventListener("focus", refresh);
    };
  }, [token, role, storageKey]);

  if (!token) return <Navigate to="/login" replace />;
  if (!canViewCoursePayments(role)) return <Navigate to="/" replace />;

  return <div className="layout">
    <Sidebar role={role} activePage="course-payments" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main course-payment-queue">
      <header><div><p>SPORTCENTER / THU TIỀN TẠI QUẦY</p><h1>Thanh toán khóa học</h1>
        <p>Chỉ hiển thị yêu cầu COUNTER còn hạn. Xác nhận chỉ khi đã thực sự nhận đủ tiền.</p></div>
        <button type="button" disabled={busy || queue.loading} onClick={() => void controller.current?.refresh()}>Làm mới</button>
      </header>
      <p>Tự động làm mới mỗi 30 giây. Hạn theo giờ Việt Nam; đồng hồ đếm ngược chỉ tham khảo. Máy chủ kiểm tra lại khi ghi nhận tiền.</p>
      {outcome && <p className={`receipt-outcome ${outcome.kind}`} role={outcome.kind === "exception" ? "alert" : "status"}>{outcome.text}</p>}
      {uncertain.length > 0 && <section className="receipt-review" aria-label="Yêu cầu cần đối soát"><h2>Yêu cầu chưa có kết quả chắc chắn</h2>
        <p>Không xác nhận lại các giao dịch này. Kiểm tra lịch sử hoặc liên hệ người phụ trách đối soát. Mất kết nối không có nghĩa giao dịch thất bại.</p>
        {uncertain.map((row) => <div key={row.paymentId}><span>Thanh toán #{row.paymentId} · Đăng ký #{row.registrationId} · Thành viên #{row.memberId}</span>
          <button type="button" disabled={busy} onClick={() => void controller.current?.check(row)}>Kiểm tra lịch sử #{row.paymentId}</button></div>)}
      </section>}
      {queue.loading ? <p role="status">Đang tải hàng chờ thanh toán…</p> : queue.error ? <div role="alert"><p>{queue.error}</p><button type="button" disabled={busy} onClick={() => void controller.current?.refresh()}>Thử tải lại</button></div>
        : queue.data && <>
          {queue.data.content.length === 0 ? <p role="status">Không có thanh toán khóa học tại quầy còn hiệu lực.</p> : <div className="course-payment-table"><table>
            <caption>Thanh toán khóa học chờ thu, cũ nhất trước</caption>
            <thead><tr><th>Thành viên</th><th>Khóa / lớp</th><th>Mã</th><th>Số tiền</th><th>Thời gian (Việt Nam)</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
            <tbody>{queue.data.content.map((row) => {
              const expired = coursePaymentExpired(row, now);
              const blocked = uncertain.some((item) => item.paymentId === row.paymentId);
              const remaining = Math.max(0, Math.ceil(((vietnamDate(row.paymentDeadline)?.getTime() || 0) - now) / 60000));
              return <tr key={row.paymentId}>
                <td>{row.memberName}<small>#{row.memberId}</small></td>
                <td>{row.courseName || `Khóa #${row.courseId}`}<small>{row.className || `Lớp #${row.classId}`}</small></td>
                <td>Thanh toán #{row.paymentId}<small>Đăng ký #{row.registrationId}</small></td>
                <td>{coursePaymentMoney(row.amount)}</td>
                <td>Tạo: {coursePaymentTime(row.paymentCreatedAt)}<small>Hạn: {coursePaymentTime(row.paymentDeadline)}</small>
                  <small>{expired ? "Hạn hiển thị đã qua; cần làm mới" : `Còn khoảng ${remaining} phút`}</small></td>
                <td>{row.paymentStatus}<small>Đăng ký: {row.registrationStatus}</small></td>
                <td><button type="button" disabled={busy || expired || blocked || row.paymentStatus !== "PENDING" || row.registrationStatus !== "PENDING_PAYMENT"}
                  onClick={() => void controller.current?.receive(row)}>Xác nhận đã nhận tiền #{row.paymentId}</button></td>
              </tr>;
            })}</tbody>
          </table></div>}
          <nav className="course-payment-pagination" aria-label="Trang hàng chờ khóa học">
            <button type="button" disabled={busy || queue.page === 0} onClick={() => void controller.current?.load(queue.page - 1)}>Trang trước</button>
            <span>Trang {queue.page + 1} / {Math.max(1, queue.data.totalPages)} · {queue.data.totalElements} giao dịch</span>
            <button type="button" disabled={busy || queue.data.last} onClick={() => void controller.current?.load(queue.page + 1)}>Trang sau</button>
          </nav>
        </>}
    </main>
  </div>;
}
