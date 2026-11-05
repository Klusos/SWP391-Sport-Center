import { useEffect, useMemo, useRef, useState } from "react";
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

function getInitials(name) {
  return String(name || "HV")
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((part) => part[0] || "")
    .join("")
    .toUpperCase() || "HV";
}

const AVATAR_TONES = ["tone-blue", "tone-orange", "tone-emerald", "tone-indigo"];

export default function ReceptionistCoursePayments({ token, role, userId, onNavigate, onLogout }) {
  const [queue, setQueue] = useState({ loading: true, data: null, page: 0, error: "" });
  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState(null);
  const [uncertain, setUncertain] = useState([]);
  const [now, setNow] = useState(Date.now);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedCourseFilter, setSelectedCourseFilter] = useState("ALL");
  const [urgentOnly, setUrgentOnly] = useState(false);
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

  const rows = queue.data?.content || [];

  const metrics = useMemo(() => {
    let totalAmount = 0;
    let urgentCount = 0;
    const courseCounts = new Map();

    rows.forEach((row) => {
      totalAmount += Number(row.amount || 0);
      const expired = coursePaymentExpired(row, now);
      const remaining = Math.max(0, Math.ceil(((vietnamDate(row.paymentDeadline)?.getTime() || 0) - now) / 60000));
      if (!expired && remaining <= 15) {
        urgentCount += 1;
      }
      const courseLabel = row.courseName || `Khóa #${row.courseId}`;
      courseCounts.set(courseLabel, (courseCounts.get(courseLabel) || 0) + 1);
    });

    return {
      totalCount: queue.data?.totalElements ?? rows.length,
      totalAmount,
      urgentCount,
      courseChips: Array.from(courseCounts.entries()).slice(0, 4),
    };
  }, [rows, queue.data?.totalElements, now]);

  const filteredRows = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return rows.filter((row) => {
      const courseLabel = row.courseName || `Khóa #${row.courseId}`;
      if (selectedCourseFilter !== "ALL" && courseLabel !== selectedCourseFilter) return false;
      const expired = coursePaymentExpired(row, now);
      const remaining = Math.max(0, Math.ceil(((vietnamDate(row.paymentDeadline)?.getTime() || 0) - now) / 60000));
      if (urgentOnly && (expired || remaining > 30)) return false;
      if (!q) return true;
      const haystack = `${row.memberName || ""} #${row.memberId} #${row.paymentId} #${row.registrationId} ${row.courseName || ""} ${row.className || ""}`.toLowerCase();
      return haystack.includes(q);
    });
  }, [rows, searchQuery, selectedCourseFilter, urgentOnly, now]);

  const liveClockLabel = useMemo(() => {
    return new Intl.DateTimeFormat("vi-VN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
      timeZone: "Asia/Ho_Chi_Minh",
    }).format(new Date(now)) + " (GMT+7)";
  }, [now]);

  if (!token) return <Navigate to="/login" replace />;
  if (!canViewCoursePayments(role)) return <Navigate to="/" replace />;

  return (
    <div className="layout">
      <Sidebar role={role} activePage="course-payments" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main course-payment-queue">
        {/* Top Utility Bar */}
        <div className="cp-topbar">
          <div className="cp-breadcrumb">
            <span>SPORTCENTER</span>
            <span className="cp-sep">/</span>
            <span>QUẦY LỄ TÂN</span>
            <span className="cp-sep">/</span>
            <span className="cp-active-crumb">THU TIỀN TẠI QUẦY</span>
          </div>
          <div className="cp-topbar-right">
            <div className="cp-clock-pill">
              <svg width="15" height="15" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
              </svg>
              <span>{liveClockLabel}</span>
            </div>
          </div>
        </div>

        <div className="cp-body">
          {/* Header Section */}
          <header className="cp-hero-header">
            <div className="cp-hero-titles">
              <div className="cp-title-row">
                <h1>Thu tiền mặt tại quầy</h1>
                <span className="cp-counter-badge">HÀNG CHỜ COUNTER</span>
              </div>
              <p className="cp-subtitle">
                Chỉ xác nhận khi đã thực sự nhận đủ tiền mặt hoặc quẹt thẻ POS từ hội viên.
              </p>
            </div>
            <div className="cp-header-actions">
              <button
                type="button"
                className="cp-btn-refresh"
                disabled={busy || queue.loading}
                onClick={() => void controller.current?.refresh()}
              >
                <svg width="15" height="15" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
                <span>Làm mới</span>
              </button>
            </div>
          </header>

          {/* Category Mode Toggle Tabs */}
          <section className="cp-mode-tabs" aria-label="Chuyển danh mục thu tiền">
            <button type="button" className="cp-mode-tab active" aria-current="page">
              <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
              </svg>
              <span>Khóa học &amp; Lớp thể thao</span>
              <span className="cp-tab-count">{metrics.totalCount}</span>
            </button>
            <button
              type="button"
              className="cp-mode-tab"
              onClick={() => onNavigate?.("pt-payments")}
            >
              <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
              </svg>
              <span>Huấn luyện viên cá nhân PT</span>
            </button>
          </section>

          {/* Metrics Row */}
          <section className="cp-metrics-grid" aria-label="Thống kê nhanh">
            <div className="cp-metric-card">
              <div className="cp-metric-info">
                <span className="cp-metric-label">CHỜ THU TIỀN MẶT</span>
                <div className="cp-metric-value">
                  {String(metrics.totalCount).padStart(2, "0")} <small>giao dịch</small>
                </div>
              </div>
              <div className="cp-metric-icon blue">
                <svg width="22" height="22" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
              </div>
            </div>

            <div className="cp-metric-card">
              <div className="cp-metric-info">
                <span className="cp-metric-label">TỔNG TIỀN CẦN THU</span>
                <div className="cp-metric-value emerald">{coursePaymentMoney(metrics.totalAmount)}</div>
              </div>
              <div className="cp-metric-icon emerald">
                <svg width="22" height="22" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
              </div>
            </div>

            <div className="cp-metric-card">
              <div className="cp-metric-info">
                <span className="cp-metric-label">SẮP HẾT HẠN (&lt;15P)</span>
                <div className="cp-metric-value rose">
                  {String(metrics.urgentCount).padStart(2, "0")} <small>giao dịch</small>
                </div>
              </div>
              <div className="cp-metric-icon rose">
                <svg width="22" height="22" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
              </div>
            </div>

            <div className="cp-metric-card">
              <div className="cp-metric-info">
                <span className="cp-metric-label">CẦN ĐỐI SOÁT</span>
                <div className="cp-metric-value amber">
                  {String(uncertain.length).padStart(2, "0")} <small>cảnh báo</small>
                </div>
              </div>
              <div className="cp-metric-icon amber">
                <svg width="22" height="22" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
              </div>
            </div>
          </section>

          {/* Outcome Alert */}
          {outcome && (
            <p className={`receipt-outcome ${outcome.kind}`} role={outcome.kind === "exception" ? "alert" : "status"}>
              {outcome.text}
            </p>
          )}

          {/* Special Reconciliation Banner */}
          {uncertain.length > 0 && (
            <section className="receipt-review cp-reconcile-banner" aria-label="Yêu cầu cần đối soát">
              <div className="cp-reconcile-head">
                <div className="cp-reconcile-icon">
                  <svg width="20" height="20" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                  </svg>
                </div>
                <div>
                  <h2>Yêu cầu chưa có kết quả chắc chắn</h2>
                  <p>Không xác nhận lại các giao dịch này cho đến khi đã kiểm tra lịch sử.</p>
                </div>
              </div>
              <div className="cp-reconcile-list">
                {uncertain.map((row) => (
                  <div key={row.paymentId} className="cp-reconcile-item">
                    <span>Thanh toán <strong>#{row.paymentId}</strong> · Đăng ký #{row.registrationId} · Thành viên #{row.memberId}</span>
                    <button type="button" className="cp-btn-amber" disabled={busy} onClick={() => void controller.current?.check(row)}>
                      <span>Kiểm tra lịch sử #{row.paymentId}</span>
                    </button>
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* Search & Filter Bar */}
          <section className="cp-filter-bar" aria-label="Bộ lọc tìm kiếm">
            <div className="cp-search-box">
              <svg width="16" height="16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
              </svg>
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Tìm theo Tên hội viên, Mã TV, Mã thanh toán (#10002), hoặc Khóa học..."
              />
            </div>
            <div className="cp-filter-chips">
              <div className="cp-chip-group">
                <button
                  type="button"
                  className={`cp-chip ${selectedCourseFilter === "ALL" ? "active" : ""}`}
                  onClick={() => setSelectedCourseFilter("ALL")}
                >
                  Tất cả ({rows.length})
                </button>
                {metrics.courseChips.map(([courseLabel, count]) => (
                  <button
                    key={courseLabel}
                    type="button"
                    className={`cp-chip ${selectedCourseFilter === courseLabel ? "active" : ""}`}
                    onClick={() => setSelectedCourseFilter(courseLabel)}
                  >
                    {courseLabel} ({count})
                  </button>
                ))}
              </div>
              <button
                type="button"
                className={`cp-urgent-toggle ${urgentOnly ? "active" : ""}`}
                onClick={() => setUrgentOnly((prev) => !prev)}
              >
                <span>Hạn &lt; 30 phút</span>
              </button>
            </div>
          </section>

          {/* Queue Content */}
          {queue.loading ? (
            <div className="cp-state-card" role="status">Đang tải hàng chờ thanh toán…</div>
          ) : queue.error ? (
            <div className="cp-state-card error" role="alert">
              <p>{queue.error}</p>
              <button type="button" className="cp-btn-refresh" disabled={busy} onClick={() => void controller.current?.refresh()}>Thử tải lại</button>
            </div>
          ) : queue.data && (
            <section className="cp-table-card">
              <div className="cp-table-header">
                <h2>Hàng chờ thanh toán tại quầy</h2>
                <div className="cp-total-badge">
                  Tổng: <strong>{queue.data.totalElements} giao dịch</strong>
                </div>
              </div>

              {filteredRows.length === 0 ? (
                <p className="cp-empty-msg" role="status">
                  {rows.length === 0
                    ? "Không có thanh toán khóa học tại quầy còn hiệu lực."
                    : "Không tìm thấy giao dịch khớp với bộ lọc hiện tại."}
                </p>
              ) : (
                <div className="course-payment-table">
                  <table>
                    <caption className="sr-only">Thanh toán khóa học chờ thu, cũ nhất trước</caption>
                    <thead>
                      <tr>
                        <th>Hội viên</th>
                        <th>Khóa học / Lớp học</th>
                        <th>Mã hệ thống</th>
                        <th className="text-right">Số tiền cần thu</th>
                        <th>Hạn giữ chỗ</th>
                        <th className="text-center">Trạng thái</th>
                        <th className="text-center">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredRows.map((row, index) => {
                        const expired = coursePaymentExpired(row, now);
                        const blocked = uncertain.some((item) => item.paymentId === row.paymentId);
                        const remaining = Math.max(0, Math.ceil(((vietnamDate(row.paymentDeadline)?.getTime() || 0) - now) / 60000));
                        const isUrgent = !expired && remaining <= 15;
                        const tone = AVATAR_TONES[index % AVATAR_TONES.length];

                        return (
                          <tr key={row.paymentId} className={isUrgent ? "cp-row-urgent" : ""}>
                            <td>
                              <div className="cp-member-cell">
                                <div className={`cp-avatar ${tone}`}>{getInitials(row.memberName)}</div>
                                <div>
                                  <div className="cp-member-name">{row.memberName}</div>
                                  <div className="cp-member-sub">#{row.memberId}</div>
                                </div>
                              </div>
                            </td>
                            <td>
                              <div className="cp-course-title">{row.courseName || `Khóa #${row.courseId}`}</div>
                              <div className="cp-course-sub">{row.className || `Lớp #${row.classId}`}</div>
                            </td>
                            <td>
                              <span className="cp-id-pill">#{row.paymentId}</span>
                              <div className="cp-id-sub">ĐK: <span>#{row.registrationId}</span></div>
                            </td>
                            <td className="text-right">
                              <div className="cp-amount">{coursePaymentMoney(row.amount)}</div>
                            </td>
                            <td>
                              <div className={`cp-time-line ${isUrgent ? "urgent-text" : ""}`}>
                                <span>{coursePaymentTime(row.paymentDeadline)}</span>
                              </div>
                              <div className="cp-countdown-wrap">
                                <span className={`cp-countdown-pill ${expired ? "expired" : isUrgent ? "urgent" : "safe"}`}>
                                  <span>
                                    {expired
                                      ? "Đã quá hạn"
                                      : isUrgent
                                        ? `⚠️ Còn ${remaining} phút`
                                        : `Còn ~${remaining} phút`}
                                  </span>
                                </span>
                              </div>
                            </td>
                            <td className="text-center">
                              <span className="cp-status-badge">{row.paymentStatus}</span>
                            </td>
                            <td className="text-center">
                              <button
                                type="button"
                                className={`cp-action-btn ${isUrgent ? "urgent" : ""}`}
                                disabled={busy || expired || blocked || row.paymentStatus !== "PENDING" || row.registrationStatus !== "PENDING_PAYMENT"}
                                onClick={() => void controller.current?.receive(row)}
                              >
                                <svg width="14" height="14" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path d="M5 13l4 4L19 7" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.2" />
                                </svg>
                                <span>Xác nhận #{row.paymentId}</span>
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}

              <nav className="course-payment-pagination cp-table-footer" aria-label="Trang hàng chờ khóa học">
                <div className="cp-footer-summary">
                  Hiển thị <strong>{filteredRows.length}</strong> / <strong>{queue.data.totalElements}</strong> giao dịch
                </div>
                <div className="cp-pagination-controls">
                  <button
                    type="button"
                    disabled={busy || queue.page === 0}
                    onClick={() => void controller.current?.load(queue.page - 1)}
                  >
                    Trang trước
                  </button>
                  <span className="cp-page-indicator">
                    Trang {queue.page + 1} / {Math.max(1, queue.data.totalPages)}
                  </span>
                  <button
                    type="button"
                    disabled={busy || queue.data.last}
                    onClick={() => void controller.current?.load(queue.page + 1)}
                  >
                    Trang sau
                  </button>
                </div>
              </nav>
            </section>
          )}
        </div>
      </main>
    </div>
  );
}
