import { useEffect, useMemo, useState } from "react";
import { getAssignedPtBookingsApi } from "../api";
import Sidebar from "../components/Sidebar";
import { formatPtTime, PT_HOLD_MINUTES, ptStatusClass, ptStatusLabel } from "../ptBooking";
import { coachPtDayCounts, coachPtSessionGroups, coachPtStatusClass, coachPtWeekDays, vietnamDateKey } from "../coachPtCalendar.js";
import "./PtBooking.css";
import "./CoachPtSessions.css";

const PT_STATUS_FILTERS = [
  ["ALL", "Tất cả trạng thái"],
  ["PENDING_PAYMENT", "Chờ thanh toán"],
  ["CONFIRMED", "Đã xác nhận"],
  ["CANCELLED", "Đã hủy"],
  ["EXPIRED", "Hết hạn"],
];

function calendarDate(key, options) {
  return new Date(`${key}T00:00:00Z`).toLocaleDateString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", ...options });
}

export default function CoachPtSessions({ token, role, onLogout, onNavigate }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [weekOffset, setWeekOffset] = useState(0);
  const [selectedDay, setSelectedDay] = useState(null);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [selectedBooking, setSelectedBooking] = useState(null);

  useEffect(() => {
    if (!selectedBooking) return undefined;
    const closeOnEscape = (event) => {
      if (event.key === "Escape") setSelectedBooking(null);
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [selectedBooking]);

  useEffect(() => {
    getAssignedPtBookingsApi(token)
      .then((data) => setRows(Array.isArray(data) ? data : []))
      .catch((requestError) => setError(requestError.message || "Không tải được danh sách buổi PT."))
      .finally(() => setLoading(false));
  }, [token]);

  const days = useMemo(() => coachPtWeekDays(new Date(), weekOffset), [weekOffset]);
  const todayKey = vietnamDateKey(new Date());
  const dayCounts = useMemo(() => coachPtDayCounts(rows, days, statusFilter), [rows, days, statusFilter]);
  const groups = useMemo(() => coachPtSessionGroups(rows, days, selectedDay, statusFilter), [rows, days, selectedDay, statusFilter]);
  const weekLabel = days.length ? `${calendarDate(days[0].key, { day: "2-digit", month: "short", year: "numeric" })} – ${calendarDate(days[6].key, { day: "2-digit", month: "short", year: "numeric" })}` : "";

  return <div className="layout">
    <Sidebar role={role} activePage="coach-pt-sessions" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main coach-pt-main">
      <div className="pt-page">
        <header className="pt-header"><div><p className="pt-eyebrow">SPORTCENTER / HUẤN LUYỆN VIÊN</p><h1>Buổi PT được phân công</h1>
          <p className="pt-description">Chỉ buổi "Đã xác nhận" là hội viên đã thanh toán; buổi "Chờ thanh toán" có thể tự hủy sau {PT_HOLD_MINUTES} phút.</p></div></header>
        {error && <p className="pt-alert pt-error" role="alert">{error}</p>}
        <section className="pt-card coach-pt-calendar" aria-labelledby="coach-pt-calendar-title">
          <div className="coach-pt-calendar-heading">
            <div><h2 id="coach-pt-calendar-title">Lịch buổi PT</h2><p>Chọn ngày để xem các buổi được phân công trong tuần.</p></div>
            <div className="coach-pt-calendar-controls">
              <label htmlFor="coach-pt-status-filter">Trạng thái</label>
              <select id="coach-pt-status-filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                {PT_STATUS_FILTERS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </div>
          </div>

          <div className="coach-pt-week-navigation">
            <div className="coach-pt-week-title"><h3>{weekLabel}</h3>{weekOffset === 0 && <span>Tuần này</span>}</div>
            <div className="coach-pt-week-actions">
              <button type="button" onClick={() => { setWeekOffset((value) => value - 1); setSelectedDay(null); }} aria-label="Tuần trước">←</button>
              <button type="button" onClick={() => { setWeekOffset(0); setSelectedDay(null); }} disabled={weekOffset === 0}>Tuần này</button>
              <button type="button" onClick={() => { setWeekOffset((value) => value + 1); setSelectedDay(null); }} aria-label="Tuần sau">→</button>
            </div>
          </div>

          <div className="coach-pt-week-grid" aria-label="Lịch tuần từ thứ Hai đến Chủ nhật">
            {days.map(({ key }) => {
              const count = dayCounts[key] || 0;
              const pendingCount = statusFilter === "ALL" || statusFilter === "PENDING_PAYMENT"
                ? rows.filter((booking) => booking.status === "PENDING_PAYMENT" && vietnamDateKey(booking.startTime) === key).length
                : 0;
              return <button key={key} type="button" className={`coach-pt-day${selectedDay === key ? " is-selected" : ""}${key === todayKey ? " is-today" : ""}${count === 0 ? " is-empty" : ""}`}
                aria-pressed={selectedDay === key} onClick={() => setSelectedDay((current) => current === key ? null : key)}>
                <span className="coach-pt-day-name">{calendarDate(key, { weekday: "short" })}</span>
                <strong>{calendarDate(key, { day: "numeric" })}</strong>
                <span className="coach-pt-day-count">{count} buổi</span>
                {pendingCount > 0 && <span className="coach-pt-pending-count">{pendingCount} chờ thanh toán</span>}
              </button>;
            })}
          </div>

          {selectedDay && <div className="coach-pt-selected-day"><span>Đang xem {calendarDate(selectedDay, { weekday: "long", day: "2-digit", month: "long", year: "numeric" })}</span>
            <button type="button" onClick={() => setSelectedDay(null)}>Hiện cả tuần</button></div>}

          {loading ? <p className="pt-empty" role="status">Đang tải lịch buổi PT…</p>
            : groups.length === 0 ? <p className="pt-empty">{rows.length === 0 ? "Chưa có buổi PT nào được phân công." : "Không có buổi PT phù hợp với ngày hoặc trạng thái đã chọn."}</p>
              : <div className="coach-pt-session-groups">{groups.map(({ key, sessions }) => <section key={key} className="coach-pt-session-day">
                <h3>{calendarDate(key, { weekday: "long", day: "2-digit", month: "long", year: "numeric" })}<span>{sessions.length} buổi</span></h3>
                <div className="pt-history">{sessions.map((booking) => <article className={`pt-history-row ${coachPtStatusClass(booking.status)}`} key={booking.id}>
                  <div className="pt-history-main"><strong>{booking.memberName} · {booking.sportName}</strong>
                    <span>{formatPtTime(booking.startTime, false)} – {formatPtTime(booking.endTime, false)}</span></div>
                  <div className="pt-history-meta"><span className={`pt-status ${ptStatusClass[booking.status] || ""} ${booking.status === "PENDING_PAYMENT" ? "pending-payment" : ""}`}>{ptStatusLabel[booking.status] || booking.status}</span>
                    <small>{booking.durationMinutes} phút</small></div>
                  <button type="button" className="coach-pt-detail-button" onClick={() => setSelectedBooking(booking)} aria-haspopup="dialog" aria-label={`Chi tiết buổi PT với ${booking.memberName}`}>
                    Chi tiết
                  </button>
                </article>)}</div>
              </section>)}</div>}
        </section>
      </div>
    </main>
    {selectedBooking && <div className="coach-pt-detail-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setSelectedBooking(null); }}>
      <section className="coach-pt-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="coach-pt-detail-title">
        <h2 id="coach-pt-detail-title">Chi tiết buổi PT</h2>
        <dl>
          <div><dt>Học viên</dt><dd>{selectedBooking.memberName}</dd></div>
          <div><dt>Bộ môn</dt><dd>{selectedBooking.sportName}</dd></div>
          <div><dt>Thời gian</dt><dd>{formatPtTime(selectedBooking.startTime)} – {formatPtTime(selectedBooking.endTime, false)}</dd></div>
          <div><dt>Trạng thái</dt><dd><span className={`pt-status ${ptStatusClass[selectedBooking.status] || ""} ${selectedBooking.status === "PENDING_PAYMENT" ? "pending-payment" : ""}`}>{ptStatusLabel[selectedBooking.status] || selectedBooking.status}</span></dd></div>
          <div><dt>Thời lượng</dt><dd>{selectedBooking.durationMinutes} phút</dd></div>
          {selectedBooking.paymentExpiresAt && selectedBooking.status === "PENDING_PAYMENT" && <div><dt>Hạn thanh toán</dt><dd>{formatPtTime(selectedBooking.paymentExpiresAt)}</dd></div>}
        </dl>
        <button type="button" onClick={() => setSelectedBooking(null)}>Đóng</button>
      </section>
    </div>}
  </div>;
}
