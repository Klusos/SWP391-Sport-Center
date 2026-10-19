import { useEffect, useState } from "react";
import "./Checkout.css";
import { API_BASE_URL, createPaymentApi, cancelPaymentApi, processCounterPaymentApi, getMyPaymentsApi, getPaymentsByMemberApi } from "../api";

const isLocalGateway = (url) => { try { return new URL(url).origin === new URL(API_BASE_URL).origin; } catch { return false; } };
const money = (v) => Number(v ?? 0).toLocaleString("vi-VN") + "đ";
export const STATUS_LABEL = { PENDING: "Chờ thanh toán", SUCCESS: "Đã thanh toán", CANCELLED: "Đã hủy", REFUND_REQUIRED: "Cần hoàn tiền" };
export const statusClass = (s) => "status-pill status-" + ({ SUCCESS: "completed", PENDING: "pending", REFUND_REQUIRED: "in_progress" }[s] || "cancelled");

/* Hóa đơn + chọn phương thức + theo dõi trạng thái thanh toán.
   purpose: MEMBERSHIP | COURSE_REGISTRATION | PT_BOOKING; referenceId: planId | registrationId | bookingId.
   lines: [{label, amount, neg?}]; staff + memberId: lễ tân tạo hóa đơn thay cho thành viên. */
export default function Checkout({ token, title, purpose, referenceId, memberId, staff, lines, total, allowAutoRenew, onClose, onDone }) {
  const [method, setMethod] = useState(staff ? "COUNTER" : "ONLINE");
  const [autoRenew, setAutoRenew] = useState(false);
  const [payment, setPayment] = useState(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const pending = payment?.status === "PENDING";

  useEffect(() => {
    if (!pending) return;
    const t = setInterval(async () => {
      try {
        const d = await (staff ? getPaymentsByMemberApi(token, payment.memberId) : getMyPaymentsApi(token));
        const p = (Array.isArray(d) ? d : d?.content ?? []).find((x) => x.id === payment.id);
        if (p) setPayment((prev) => ({ ...prev, ...p, paymentUrl: prev.paymentUrl }));
      } catch { /* thử lại ở lần sau */ }
    }, 4000);
    return () => clearInterval(t);
  }, [pending, payment?.id]);

  async function run(fn) {
    setBusy(true); setErr("");
    try { return await fn(); } catch (e) { setErr(e.status === 503 ? "Cổng thanh toán online hiện chưa được bật. Vui lòng chọn thanh toán tại quầy." : e.message); } finally { setBusy(false); }
  }

  const create = () => run(async () => {
    const body = { method, purpose, referenceId };
    if (memberId) body.memberId = memberId;
    if (purpose === "MEMBERSHIP") body.autoRenew = autoRenew;
    const p = await createPaymentApi(token, body);
    setPayment(p);
    if (method === "ONLINE" && p.paymentUrl && !isLocalGateway(p.paymentUrl)) window.open(p.paymentUrl, "_blank", "noopener");
  });
  const counter = (success) => run(async () => setPayment(await processCounterPaymentApi(token, payment.id, { success, note: success ? "Đã thu tiền tại quầy" : "Không thu được tiền" })));
  const simulate = () => run(async () => {
    const r = await fetch(payment.paymentUrl);
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(d.message || "Không hoàn tất được thanh toán");
    setPayment((prev) => ({ ...prev, ...d, paymentUrl: prev.paymentUrl }));
  });
  const cancel = () => run(async () => setPayment(await cancelPaymentApi(token, payment.id)));
  const close = () => { onDone?.(); onClose(); };

  const st = payment?.status;
  return (
    <div className="ck-overlay" role="dialog" aria-modal="true" aria-labelledby="ck-title">
      <div className="ck-box">
        <h2 id="ck-title">{payment ? "Hóa đơn " + payment.transactionCode : "Hóa đơn thanh toán"}</h2>
        <p className="ck-item">{title}</p>
        <dl className="ck-lines">
          {lines.map((l) => <div key={l.label}><dt>{l.label}</dt><dd className={l.neg ? "neg" : ""}>{l.neg ? "−" : ""}{money(l.amount)}</dd></div>)}
          <div className="ck-total"><dt>Thành tiền</dt><dd>{money(payment?.amount ?? total)}</dd></div>
        </dl>

        {!payment && (<>
          <div className="ck-methods" role="radiogroup" aria-label="Phương thức thanh toán">
            {!staff && (
              <label className={"ck-method" + (method === "ONLINE" ? " on" : "")}>
                <input type="radio" name="ckm" checked={method === "ONLINE"} onChange={() => setMethod("ONLINE")} />
                <span><b>Thanh toán online</b><small>Chuyển sang cổng thanh toán</small></span>
              </label>
            )}
            <label className={"ck-method" + (method === "COUNTER" ? " on" : "")}>
              <input type="radio" name="ckm" checked={method === "COUNTER"} onChange={() => setMethod("COUNTER")} />
              <span><b>Thanh toán tại quầy</b><small>Trả cho lễ tân bằng mã hóa đơn</small></span>
            </label>
          </div>
          {allowAutoRenew && (
            <label className="ck-check"><input type="checkbox" checked={autoRenew} onChange={(e) => setAutoRenew(e.target.checked)} /> Tự động gia hạn khi hết hạn</label>
          )}
        </>)}

        {payment && (<>
          <p><span className={statusClass(st)}>{STATUS_LABEL[st] || st}</span></p>
          {pending && payment.method === "ONLINE" && (
            isLocalGateway(payment.paymentUrl)
              ? <p className="ck-note">Cổng thanh toán giả lập (chỉ dùng để demo). Bấm “Thanh toán” để mô phỏng giao dịch thành công.</p>
              : <p className="ck-note">Hoàn tất thanh toán ở cổng online. Trang sẽ tự cập nhật khi giao dịch xong.{" "}
                  {payment.paymentUrl && <a href={payment.paymentUrl} target="_blank" rel="noopener noreferrer">Mở cổng thanh toán</a>}</p>
          )}
          {pending && payment.method === "COUNTER" && (
            <p className="ck-note">{staff ? "Thu tiền của khách rồi bấm “Đã thu tiền”." : <>Hãy đưa mã <span className="ck-code">{payment.transactionCode}</span> cho lễ tân để thanh toán.</>}</p>
          )}
          {st === "SUCCESS" && <p className="ck-note">Thanh toán thành công. Cảm ơn bạn!</p>}
          {st === "CANCELLED" && <p className="ck-note">Giao dịch đã bị hủy. {payment.note || ""}</p>}
          {st === "REFUND_REQUIRED" && <p className="ck-note">Thanh toán đã nhận nhưng không áp dụng được ({payment.note}). Vui lòng liên hệ lễ tân để được hoàn tiền.</p>}
        </>)}

        {err && <div className="banner error" role="alert">{err}</div>}
        <div className="ck-actions">
          {!payment && <button type="button" onClick={close}>Đóng</button>}
          {!payment && <button type="button" className="ck-primary" disabled={busy} onClick={create}>{busy ? "Đang tạo…" : "Xác nhận thanh toán"}</button>}
          {pending && payment.method === "ONLINE" && isLocalGateway(payment.paymentUrl) && <button type="button" className="ck-primary" disabled={busy} onClick={simulate}>Thanh toán</button>}
          {pending && staff && payment.method === "COUNTER" && <button type="button" className="ck-primary" disabled={busy} onClick={() => counter(true)}>Đã thu tiền</button>}
          {pending && staff && payment.method === "COUNTER" && <button type="button" disabled={busy} onClick={() => counter(false)}>Không thu được</button>}
          {pending && <button type="button" disabled={busy} onClick={cancel}>Hủy hóa đơn</button>}
          {payment && !pending && <button type="button" className="ck-primary" onClick={close}>Xong</button>}
          {pending && <button type="button" onClick={close}>Đóng</button>}
        </div>
      </div>
    </div>
  );
}