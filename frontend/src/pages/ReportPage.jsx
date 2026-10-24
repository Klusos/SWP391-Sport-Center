import { useEffect, useState } from "react";
import { getAdminRevenueReportApi } from "../api";
import RevenueBars from "../components/RevenueBars";
import { defaultRevenueRange, formatVnd, REVENUE_PURPOSES } from "../revenueReport";
import "./ReportPage.css";

const purposeTotals = (report) => report?.revenueByPurpose || {};

export default function ReportPage({ token }) {
  const [range, setRange] = useState(() => defaultRevenueRange());
  const [groupBy, setGroupBy] = useState("MONTH");
  const [applied, setApplied] = useState(() => ({ ...defaultRevenueRange(), groupBy: "MONTH" }));
  const [result, setResult] = useState(null);
  const [retry, setRetry] = useState(0);
  const [filterError, setFilterError] = useState("");
  const queryKey = `${applied.from}|${applied.to}|${applied.groupBy}`;
  const currentResult = result?.key === queryKey && result?.retry === retry ? result : null;
  const report = currentResult?.data;
  const totals = purposeTotals(report);
  const visibleError = token
    ? currentResult?.error || ""
    : "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại bằng tài khoản ADMIN.";
  const visibleLoading = Boolean(token) && !currentResult;

  useEffect(() => {
    if (!token) return undefined;
    const controller = new AbortController();
    getAdminRevenueReportApi(token, applied, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setResult({ key: queryKey, retry, data, error: "" });
      })
      .catch((requestError) => {
        if (!controller.signal.aborted) {
          const message = requestError.status === 403
            ? "Bạn không có quyền xem báo cáo doanh thu."
            : requestError.message || "Không thể tải báo cáo doanh thu.";
          setResult({ key: queryKey, retry, data: null, error: message });
        }
      });
    return () => controller.abort();
  }, [token, applied, queryKey, retry]);

  function applyFilters(event) {
    event.preventDefault();
    if (!range.from || !range.to) {
      setFilterError("Vui lòng chọn ngày bắt đầu và ngày kết thúc.");
      return;
    }
    if (range.to < range.from) {
      setFilterError("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.");
      return;
    }
    setFilterError("");
    setApplied({ ...range, groupBy });
  }

  return (
    <div className="revenue-report-page">
      <div className="page-intro">
        <div>
          <h1>Báo cáo doanh thu</h1>
          <p>Doanh thu được tính từ giao dịch SUCCESS theo thời điểm xác nhận thanh toán.</p>
        </div>
        <button className="btn-light" type="button" onClick={() => window.print()}>In báo cáo</button>
      </div>

      <form className="panel revenue-filters" onSubmit={applyFilters}>
        <label> Từ ngày
          <input type="date" value={range.from} onChange={(event) => setRange((current) => ({ ...current, from: event.target.value }))} required />
        </label>
        <label> Đến ngày
          <input type="date" value={range.to} onChange={(event) => setRange((current) => ({ ...current, to: event.target.value }))} required />
        </label>
        <label> Nhóm theo
          <select value={groupBy} onChange={(event) => setGroupBy(event.target.value)}>
            <option value="DAY">Ngày</option>
            <option value="MONTH">Tháng</option>
            <option value="YEAR">Năm</option>
          </select>
        </label>
        <button className="btn-primary" type="submit" disabled={visibleLoading}>Cập nhật</button>
        {filterError && <p className="revenue-error" role="alert">{filterError}</p>}
      </form>

      {visibleLoading ? <div className="panel revenue-state" role="status">Đang tải báo cáo doanh thu…</div> : null}
      {!visibleLoading && visibleError ? (
        <div className="panel revenue-state revenue-error" role="alert">
          <p>{visibleError}</p>
          <button className="btn-light" type="button" onClick={() => setRetry((current) => current + 1)}>Thử lại</button>
        </div>
      ) : null}
      {!visibleLoading && !visibleError && report ? (
        <>
          <section className="revenue-summary-grid" aria-label="Tóm tắt doanh thu">
            <article className="panel revenue-total-card">
              <span>Tổng doanh thu</span>
              <strong>{formatVnd(report.totalRevenue)}</strong>
              <small>{report.from} — {report.to}</small>
            </article>
            {REVENUE_PURPOSES.map(([purpose, label]) => (
              <article className="panel revenue-purpose-card" key={purpose}>
                <span>{label}</span>
                <strong>{formatVnd(totals[purpose])}</strong>
              </article>
            ))}
          </section>

          <section className="panel revenue-chart-panel">
            <div className="panel-head">
              <div>
                <h2>Doanh thu theo thời gian</h2>
                <p>Đơn vị: VND · Múi giờ: Asia/Ho_Chi_Minh · Chỉ tính trạng thái SUCCESS</p>
              </div>
              <span className="status info">Theo {applied.groupBy === "DAY" ? "ngày" : applied.groupBy === "YEAR" ? "năm" : "tháng"}</span>
            </div>
            {report.periods?.length ? (
              <RevenueBars periods={report.periods} groupBy={applied.groupBy} />
            ) : (
              <div className="revenue-state">Không có kỳ doanh thu trong khoảng đã chọn.</div>
            )}
            {Number(report.totalRevenue || 0) === 0 && (
              <p className="revenue-empty-note">Không có giao dịch SUCCESS trong khoảng ngày đã chọn.</p>
            )}
          </section>
          <p className="revenue-footnote">PENDING, CANCELLED và REFUND_REQUIRED không được tính là doanh thu. REFUND_REQUIRED cần được xử lý riêng; báo cáo này không khẳng định khoản tiền đã được hoàn.</p>
        </>
      ) : null}
    </div>
  );
}
