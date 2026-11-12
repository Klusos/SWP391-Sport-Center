import { useCallback, useEffect, useRef, useState } from "react";
import { confirmRefundApi, getRefundRequiredPaymentsApi } from "../api";
import { coursePaymentMoney } from "../coursePaymentQueue";
import { loadAllRefundQueuePages, refundPurposeLabel, refundQueuePage, refundQueueRowsForPurposes, refundReferenceLabel } from "../refundQueue.js";

const API_PAGE_SIZE = 100;
const DISPLAY_PAGE_SIZE = 10;

export default function ReceptionistRefundQueue({ token, purposes, groups }) {
  const [queue, setQueue] = useState({ loading: true, payments: null, error: "", notice: "" });
  const [pages, setPages] = useState({});
  const [busyId, setBusyId] = useState(null);
  const busy = useRef(false);
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const currentRequest = ++requestId.current;
    setQueue((current) => ({ ...current, loading: true, error: "" }));
    try {
      const payments = await loadAllRefundQueuePages((page, size) => getRefundRequiredPaymentsApi(token, page, size), API_PAGE_SIZE);
      if (currentRequest === requestId.current) setQueue((current) => ({ ...current, loading: false, payments, error: "" }));
    } catch (error) {
      if (currentRequest === requestId.current) setQueue((current) => ({ ...current, loading: false, error: error.message || "Không tải được hàng đợi hoàn tiền." }));
    }
  }, [token]);

  useEffect(() => {
    void load();
    return () => { requestId.current += 1; };
  }, [load]);

  async function confirmRefund(payment) {
    if (busy.current || payment?.status !== "REFUND_REQUIRED") return;
    const reference = refundReferenceLabel(payment);
    if (!window.confirm(`Xác nhận đã hoàn ${coursePaymentMoney(payment.amount)} cho ${payment.memberName || `thành viên #${payment.memberId}`}?\nThanh toán #${payment.id} · ${refundPurposeLabel(payment)} · ${reference}\n\nChỉ xác nhận sau khi đã hoàn tiền thực tế.`)) return;
    const note = window.prompt("Ghi chú kế toán (không bắt buộc):", "");
    if (note === null) return;
    busy.current = true;
    setBusyId(payment.id);
    setQueue((current) => ({ ...current, error: "", notice: "" }));
    try {
      await confirmRefundApi(token, payment.id, note.trim() || null);
      setQueue((current) => ({ ...current, notice: `Đã ghi nhận hoàn tiền thanh toán #${payment.id}.` }));
    } catch (error) {
      setQueue((current) => ({ ...current, error: error.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : /Only a REFUND_REQUIRED/.test(error.message || "")
          ? "Khoản này đã được xử lý. Hãy làm mới danh sách."
          : error.message || "Không thể xác nhận hoàn tiền." }));
    } finally {
      await load();
      busy.current = false;
      setBusyId(null);
    }
  }

  return <>
    {queue.notice && <p className="cp-refund-notice" role="status">{queue.notice}</p>}
    {queue.error && <p className="cp-refund-error" role="alert">{queue.error}</p>}
    {groups.map((group) => {
      const rows = refundQueueRowsForPurposes(queue.payments, group.purposes ?? purposes);
      const pageNumber = Math.min(pages[group.id] || 0, Math.max(0, Math.ceil(rows.length / DISPLAY_PAGE_SIZE) - 1));
      const page = refundQueuePage(rows, pageNumber, DISPLAY_PAGE_SIZE);
      return <section className="cp-refund-queue" aria-labelledby={`cp-refund-title-${group.id}`} key={group.id}>
        <div className="cp-refund-heading">
          <div><h2 id={`cp-refund-title-${group.id}`}>{group.title}</h2><p>{group.description} · Chỉ xác nhận sau khi đã hoàn tiền thực tế.</p></div>
          <button type="button" disabled={queue.loading || busyId !== null} onClick={() => void load()}>Làm mới</button>
        </div>
        {queue.loading ? <p role="status">Đang tải hàng đợi hoàn tiền…</p>
          : queue.payments == null ? null
            : rows.length === 0 ? <p>Không có khoản nào cần hoàn tiền trong danh mục này.</p>
              : <>
                <div className="cp-refund-list">{page.content.map((payment) => <article className="cp-refund-item" key={payment.id}>
                  <div className="cp-refund-item-main">
                    <strong>Thanh toán #{payment.id} · {payment.memberName || `Thành viên #${payment.memberId}`}</strong>
                    <span>{refundPurposeLabel(payment)} · {refundReferenceLabel(payment)} · {coursePaymentMoney(payment.amount)} · {payment.status}</span>
                    {payment.purpose === "COURSE_REGISTRATION" && <span>{[payment.courseName, payment.courseOfferingName, payment.className].filter(Boolean).join(" · ")}</span>}
                    {payment.note && <small>{payment.note}</small>}
                  </div>
                  <button type="button" disabled={busyId !== null || payment.status !== "REFUND_REQUIRED"} onClick={() => void confirmRefund(payment)}>
                    {busyId === payment.id ? "Đang xử lý…" : "Xác nhận đã hoàn tiền"}
                  </button>
                </article>)}</div>
                <nav className="cp-refund-pagination" aria-label={`Trang ${group.title}`}>
                  <span>Hiển thị {page.content.length} / {rows.length} khoản</span>
                  <div><button type="button" disabled={queue.loading || busyId !== null || pageNumber === 0} onClick={() => setPages((current) => ({ ...current, [group.id]: pageNumber - 1 }))}>Trang trước</button>
                    <span>Trang {pageNumber + 1} / {page.totalPages}</span>
                    <button type="button" disabled={queue.loading || busyId !== null || page.last} onClick={() => setPages((current) => ({ ...current, [group.id]: pageNumber + 1 }))}>Trang sau</button></div>
                </nav>
              </>}
      </section>;
    })}
  </>;
}
