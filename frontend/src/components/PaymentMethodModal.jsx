import { useEffect, useId, useRef, useState } from "react";
import "./PaymentMethodModal.css";

const methods = [
  { value: "ONLINE", label: "ONLINE — VNPay", detail: "Thanh toán trực tuyến" },
  { value: "COUNTER", label: "COUNTER — Thanh toán tại quầy", detail: "Thanh toán trực tiếp tại quầy lễ tân" },
];

export default function PaymentMethodModal({ amount, context, busy = false, error = "", canContinue = true, onCancel, onSuccess, onContinue }) {
  const titleId = useId();
  const descriptionId = useId();
  const firstMethodRef = useRef(null);
  const dialogRef = useRef(null);
  const submitLock = useRef(false);
  const cancelRef = useRef(onCancel);
  const busyRef = useRef(busy);
  const [method, setMethod] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [localError, setLocalError] = useState("");
  const isBusy = busy || submitting;

  useEffect(() => {
    cancelRef.current = onCancel;
    busyRef.current = isBusy;
  }, [onCancel, isBusy]);

  useEffect(() => {
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    firstMethodRef.current?.focus();

    function handleKeyDown(event) {
      if (event.key === "Escape" && !busyRef.current) {
        event.preventDefault();
        cancelRef.current?.();
        return;
      }
      if (event.key !== "Tab") return;

      const focusable = [...(dialogRef.current?.querySelectorAll("button:not(:disabled), input:not(:disabled)") || [])];
      if (!focusable.length) {
        event.preventDefault();
        dialogRef.current?.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);

  async function continueWithPaymentMethod(event) {
    event.preventDefault();
    if (!method || isBusy || submitLock.current) return;
    submitLock.current = true;
    busyRef.current = true;
    setSubmitting(true);
    setLocalError("");
    try {
      const closeAfterContinue = await onContinue(method);
      if (closeAfterContinue === true) onSuccess?.();
    } catch (requestError) {
      setLocalError(requestError?.message || "Không thể tiếp tục thanh toán. Vui lòng thử lại.");
    } finally {
      submitLock.current = false;
      busyRef.current = busy;
      setSubmitting(false);
    }
  }

  function closeFromBackdrop(event) {
    if (event.target === event.currentTarget && !isBusy) cancelRef.current?.();
  }

  return <div className="payment-method-backdrop" onMouseDown={closeFromBackdrop}>
    <section
      ref={dialogRef}
      className="payment-method-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={descriptionId}
      aria-busy={isBusy}
      tabIndex={-1}
    >
      <header className="payment-method-dialog-header">
        <div>
          <p className="payment-method-eyebrow">SPORTCENTER / THANH TOÁN</p>
          <h2 id={titleId}>Chọn phương thức thanh toán</h2>
        </div>
        <button type="button" className="payment-method-close" aria-label="Đóng hộp thoại" onClick={() => !isBusy && cancelRef.current?.()} disabled={isBusy}>×</button>
      </header>

      <div className="payment-method-summary" id={descriptionId}>
        {context && <p>{context}</p>}
        <strong>{amount}</strong>
      </div>

      <form onSubmit={continueWithPaymentMethod}>
        <fieldset className="payment-method-options" disabled={isBusy}>
          <legend>Phương thức</legend>
          {methods.map((item, index) => <label className={`payment-method-option${method === item.value ? " selected" : ""}`} key={item.value}>
            <input
              ref={index === 0 ? firstMethodRef : undefined}
              type="radio"
              name="payment-method"
              value={item.value}
              checked={method === item.value}
              onChange={() => setMethod(item.value)}
            />
            <span><b>{item.label}</b><small>{item.detail}</small></span>
          </label>)}
        </fieldset>

        {(error || localError) && <p className="payment-method-error" role="alert">{error || localError}</p>}
        {isBusy && <p className="payment-method-loading" role="status">Đang xử lý yêu cầu thanh toán…</p>}

        <footer className="payment-method-actions">
          <button type="button" className="payment-method-cancel" onClick={() => !isBusy && cancelRef.current?.()} disabled={isBusy}>Hủy</button>
          <button type="submit" className="payment-method-continue" disabled={!method || isBusy || !canContinue}>
            {isBusy ? "Đang xử lý…" : "Tiếp tục"}
          </button>
        </footer>
      </form>
    </section>
  </div>;
}
