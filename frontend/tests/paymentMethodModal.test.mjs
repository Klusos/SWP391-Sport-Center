import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const source = await readFile(new URL("../src/components/PaymentMethodModal.jsx", import.meta.url), "utf8");
const css = await readFile(new URL("../src/components/PaymentMethodModal.css", import.meta.url), "utf8");

test("shared payment modal exposes an accessible dialog and both payment methods", () => {
  assert.match(source, /role="dialog"/);
  assert.match(source, /aria-modal="true"/);
  assert.match(source, /aria-labelledby=\{titleId\}/);
  assert.match(source, /aria-describedby=\{descriptionId\}/);
  assert.match(source, /Chọn phương thức thanh toán/);
  assert.match(source, /value: "ONLINE", label: "ONLINE — VNPay"/);
  assert.match(source, /value: "COUNTER", label: "COUNTER — Thanh toán tại quầy"/);
  assert.match(source, /onClick=\{\(\) => !isBusy && cancelRef\.current\?\.\(\)\}/);
  assert.match(source, /type="submit"/);
  assert.match(source, /disabled=\{!method \|\| isBusy \|\| !canContinue\}/);
});

test("modal supports Escape, focus trapping/restoration, backdrop close, and busy lock", () => {
  assert.match(source, /event\.key === "Escape" && !busyRef\.current/);
  assert.match(source, /event\.shiftKey && document\.activeElement === first/);
  assert.match(source, /!event\.shiftKey && document\.activeElement === last/);
  assert.match(source, /previousFocus\?\.isConnected\) previousFocus\.focus\(\)/);
  assert.match(source, /event\.target === event\.currentTarget && !isBusy/);
  assert.match(source, /if \(!method \|\| isBusy \|\| submitLock\.current\) return/);
  assert.match(source, /setSubmitting\(true\)/);
  assert.match(source, /submitLock\.current/);
  assert.match(source, /role="alert"/);
  assert.match(source, /role="status"/);
});

test("opening and changing methods cannot create payments; only explicit Continue calls the supplied handler", () => {
  assert.doesNotMatch(source, /createPaymentApi|fetch\(/);
  assert.match(source, /onChange=\{\(\) => setMethod\(item\.value\)\}/);
  assert.match(source, /const closeAfterContinue = await onContinue\(method\)/);
  assert.match(source, /if \(closeAfterContinue === true\) onSuccess\?\.\(\)/);
  assert.doesNotMatch(source, /closeAfterContinue[^\n]*cancelRef/);
  assert.match(css, /\.payment-method-backdrop/);
  assert.match(css, /\.payment-method-option\.selected\{border-color/);
  assert.match(css, /min-height:76px/);
  assert.match(css, /:focus-visible/);
});
