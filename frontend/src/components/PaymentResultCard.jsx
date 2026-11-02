import { Link } from "react-router-dom";
import { paymentPurposePresentation, paymentServiceLabel, paymentStatusPresentation, serviceStatusLabel } from "../paymentResult";
import "./PaymentResultCard.css";

const money = (value) => Number.isFinite(Number(value))
  ? Number(value).toLocaleString("vi-VN", { style: "currency", currency: "VND" })
  : "Chưa có dữ liệu";

function formatVietnamDateTime(value) {
  if (!value) return "Chưa có dữ liệu";
  const timezoneLess = /^\d{4}-\d\d-\d\dT\d\d:\d\d(?::\d\d(?:\.\d+)?)?$/.test(value);
  const date = new Date(timezoneLess ? `${value}+07:00` : value);
  if (Number.isNaN(date.getTime())) return "Chưa có dữ liệu";
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh",
  }).format(date);
}

function Detail({ label, children }) {
  return <div className="payment-result-detail"><dt>{label}</dt><dd>{children}</dd></div>;
}

function ServiceDetails({ payment, ptBooking, membershipRecords, registration }) {
  switch (payment.purpose) {
    case "PT_BOOKING":
      return ptBooking ? <>
        <Detail label="Mã lịch tập">#{ptBooking.id}</Detail>
        <Detail label="Môn thể thao / Huấn luyện viên">{ptBooking.sportName || "Chưa có dữ liệu"} · {ptBooking.coachName || "Chưa có dữ liệu"}</Detail>
        <Detail label="Lịch hẹn">{formatVietnamDateTime(ptBooking.startTime)} – {formatVietnamDateTime(ptBooking.endTime)} (giờ Việt Nam)</Detail>
        <Detail label="Trạng thái buổi tập"><span className={`payment-result-resource-status status-${String(ptBooking.status || "unknown").toLowerCase()}`}>{serviceStatusLabel(ptBooking.status)}</span></Detail>
      </> : <Detail label="Trạng thái buổi tập"><span role="alert">Chưa tìm thấy buổi tập trong lịch sử của tài khoản. Trạng thái chưa được xác nhận.</span></Detail>;
    case "MEMBERSHIP":
      return <>
        <Detail label="Mã gói">#{payment.referenceId}</Detail>
        {membershipRecords.length ? membershipRecords.map((membership) => <div className="payment-result-membership" key={membership.id}>
          <Detail label="Mã kỳ hội viên">#{membership.id}</Detail>
          <Detail label="Gói hội viên">{membership.planName || `Gói #${membership.planId}`}</Detail>
          <Detail label="Trạng thái hội viên"><span className={`payment-result-resource-status status-${String(membership.status || "unknown").toLowerCase()}`}>{serviceStatusLabel(membership.status)}</span></Detail>
          <Detail label="Thời hạn">{membership.startDate || "—"} – {membership.endDate || "—"}</Detail>
        </div>) : <Detail label="Gói hội viên">{payment.status === "PENDING" ? "Gói sẽ được cập nhật sau khi máy chủ xác nhận thanh toán." : "Chưa tìm thấy bản ghi gói hội viên tương ứng trong dữ liệu tài khoản."}</Detail>}
      </>;
    case "UPGRADE":
      return <>
        <Detail label="Mã gói đích">#{payment.referenceId}</Detail>
        <Detail label="Trạng thái nâng cấp">{payment.status === "SUCCESS"
          ? "Thanh toán đã được máy chủ xác nhận. Hãy kiểm tra danh sách gói hội viên để xem trạng thái gói mới."
          : "Thông tin gói mới chỉ được cập nhật sau khi máy chủ xác nhận thanh toán."}</Detail>
      </>;
    case "COURSE_REGISTRATION":
      return registration ? <>
        <Detail label="Mã đăng ký">#{registration.id}</Detail>
        <Detail label="Khóa học">{registration.courseName || "Chưa có dữ liệu"}</Detail>
        <Detail label="Lớp học">{registration.className || registration.courseOfferingName || "Chưa có dữ liệu"}</Detail>
        <Detail label="Trạng thái đăng ký"><span className={`payment-result-resource-status status-${String(registration.status || "unknown").toLowerCase()}`}>{serviceStatusLabel(registration.status)}</span></Detail>
      </> : <Detail label="Trạng thái đăng ký"><span role="alert">Chưa tìm thấy đăng ký trong lịch sử của tài khoản.</span></Detail>;
    default:
      return <Detail label="Trạng thái dịch vụ">Không nhận diện được loại dịch vụ của giao dịch.</Detail>;
  }
}

export default function PaymentResultCard({
  payment,
  ptBooking = null,
  memberships = [],
  registration = null,
  loading = false,
  error = "",
  statusError = "",
  pollEnded = false,
  onRefresh,
  busy = false,
}) {
  const purpose = paymentPurposePresentation(payment);
  const status = paymentStatusPresentation(payment?.status);
  const matchingMemberships = payment?.purpose === "MEMBERSHIP"
    ? memberships.filter((item) => Number(item.planId) === Number(payment.referenceId))
    : [];
  const serviceStatus = payment?.purpose === "PT_BOOKING" ? ptBooking?.status
    : payment?.purpose === "MEMBERSHIP" ? matchingMemberships.some((item) => item.status === "ACTIVE") ? "ACTIVE" : matchingMemberships[0]?.status
      : payment?.purpose === "UPGRADE" ? null
      : payment?.purpose === "COURSE_REGISTRATION" ? registration?.status : null;
  const needsRefresh = !payment || payment?.status === "PENDING"
    || (payment?.status === "SUCCESS" && ["PENDING_PAYMENT"].includes(serviceStatus))
    || status.tone === "unknown"
    || Boolean(statusError || error || pollEnded);

  return <section className={`payment-result-card tone-${payment ? status.tone : "unknown"}`} aria-labelledby="payment-result-title" aria-live="polite">
    <div className={`payment-result-icon tone-${payment ? status.tone : "unknown"}`} aria-hidden="true">{payment ? status.icon : "?"}</div>
    <p className="payment-result-eyebrow">{purpose?.label || "Kết quả giao dịch"}</p>
    <h1 id="payment-result-title">{payment ? status.title : loading ? "Đang tải kết quả thanh toán" : error || statusError ? "Chưa tải được trạng thái" : "Chưa tìm thấy giao dịch"}</h1>
    <p className="payment-result-explanation">{payment ? status.explanation : loading ? "Đang đối chiếu giao dịch với lịch sử thanh toán của tài khoản…" : error || statusError ? "Không thể xác minh giao dịch lúc này. Hãy thử làm mới trạng thái." : "Mã trên đường dẫn chỉ dùng để tra cứu. Hãy đăng nhập đúng tài khoản và kiểm tra lại lịch sử thanh toán."}</p>

    {(error || statusError) && <p className="payment-result-error" role="alert">{error || statusError}</p>}
    {loading && !payment && <p role="status">Đang tải trạng thái giao dịch…</p>}

    {payment && <>
      <div className="payment-result-status-row">
        <span className="payment-result-status-label">Trạng thái thanh toán</span>
        <span className={`payment-result-badge tone-${status.tone}`}>{serviceStatusLabel(payment.status)}</span>
      </div>
      <dl className="payment-result-details">
        <Detail label="Mã thanh toán">#{payment.id}</Detail>
        <Detail label="Số tiền">{money(payment.amount)}</Detail>
        <Detail label="Phương thức">{payment.method === "ONLINE" ? "VNPay" : payment.method === "COUNTER" ? "Thanh toán tại quầy" : payment.method || "Chưa có dữ liệu"}</Detail>
      </dl>
      <section className="payment-result-service" aria-label={paymentServiceLabel(payment.purpose)}>
        <h2>{paymentServiceLabel(payment.purpose)}</h2>
        {payment.purpose === "PT_BOOKING" && !ptBooking && !loading
          ? <p role="alert">Chưa tải được thông tin buổi tập. Không thể kết luận buổi tập đã được xác nhận từ trạng thái thanh toán.</p>
          : <dl className="payment-result-details"><ServiceDetails payment={payment} ptBooking={ptBooking} membershipRecords={matchingMemberships} registration={registration} /></dl>}
      </section>
      {payment.status === "SUCCESS" && serviceStatus && serviceStatus !== "CONFIRMED" && serviceStatus !== "ACTIVE" && <p className="payment-result-caution" role="status">Thanh toán thành công không đồng nghĩa dịch vụ đã được xác nhận. Trạng thái dịch vụ hiện tại: {serviceStatusLabel(serviceStatus)}.</p>}
      {payment.status === "REFUND_REQUIRED" && <p className="payment-result-caution" role="alert">Vui lòng liên hệ lễ tân để được hỗ trợ đối soát hoặc hoàn tiền.</p>}
    </>}

    <nav className="payment-result-actions" aria-label="Điều hướng kết quả thanh toán">
      {purpose ? <Link className="payment-result-primary" to={purpose.path}>{purpose.action}</Link>
        : <Link className="payment-result-primary" to="/member/dashboard">Về trang tổng quan</Link>}
      {needsRefresh && <button type="button" disabled={busy || loading} onClick={onRefresh}>{busy ? "Đang làm mới…" : "Làm mới trạng thái"}</button>}
    </nav>
    {pollEnded && <p className="payment-result-footnote">Tự động kiểm tra đã dừng. Bạn có thể làm mới để xem trạng thái mới nhất.</p>}
  </section>;
}
