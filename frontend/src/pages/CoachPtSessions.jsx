import { useEffect, useState } from "react";
import { getAssignedPtBookingsApi } from "../api";
import Sidebar from "../components/Sidebar";
import { formatPtTime, PT_HOLD_MINUTES, ptStatusClass, ptStatusLabel } from "../ptBooking";
import "./PtBooking.css";

export default function CoachPtSessions({ token, role, onLogout, onNavigate }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    getAssignedPtBookingsApi(token)
      .then((data) => setRows(Array.isArray(data) ? data : []))
      .catch((requestError) => setError(requestError.message || "Không tải được danh sách buổi PT."))
      .finally(() => setLoading(false));
  }, [token]);

  return <div className="layout">
    <Sidebar role={role} activePage="coach-pt-sessions" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main coach-pt-main">
      <div className="pt-page">
        <header className="pt-header"><div><p className="pt-eyebrow">SPORTCENTER / HUẤN LUYỆN VIÊN</p><h1>Buổi PT được phân công</h1>
          <p className="pt-description">Chỉ buổi "Đã xác nhận" là hội viên đã thanh toán; buổi "Chờ thanh toán" có thể tự hủy sau {PT_HOLD_MINUTES} phút.</p></div></header>
        {error && <p className="pt-alert pt-error" role="alert">{error}</p>}
        <section className="pt-card"><h2>Lịch buổi PT</h2>{loading ? <p>Đang tải…</p> : rows.length === 0 ? <p className="pt-empty">Chưa có buổi PT nào.</p> :
          <div className="pt-history">{rows.map((booking) => <article className="pt-history-row" key={booking.id}>
            <div className="pt-history-main"><strong>{booking.memberName} · {booking.sportName}</strong>
              <span>{formatPtTime(booking.startTime)} – {formatPtTime(booking.endTime, false)}</span></div>
            <div className="pt-history-meta"><span className={"pt-status " + (ptStatusClass[booking.status] || "")}>{ptStatusLabel[booking.status] || booking.status}</span>
              <small>{booking.durationMinutes} phút</small></div>
          </article>)}</div>}</section>
      </div>
    </main>
  </div>;
}
