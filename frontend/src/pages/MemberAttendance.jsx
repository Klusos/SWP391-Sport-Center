import { useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import { getMyAttendanceApi } from "../api";
import "./MemberAttendance.css";

const STATUS_LABELS = { PRESENT: "Có mặt", ABSENT: "Vắng mặt", EXCUSED: "Vắng có phép" };
const SESSION_LABELS = { SCHEDULED: "Đã lên lịch", COMPLETED: "Đã hoàn thành", CANCELLED: "Đã hủy" };
function parseDate(value) {
  if (typeof value !== "string" || !value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}
function formatDate(value) {
  return parseDate(value)?.toLocaleString("vi-VN") || "Chưa có thời gian";
}
function errorText(error) {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Tài khoản không có quyền xem lịch sử điểm danh.";
  return error?.message || "Không tải được lịch sử điểm danh. Vui lòng thử lại.";
}

export default function MemberAttendance({ token, role, onLogout, onNavigate }) {
  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  useEffect(() => {
    let active = true;
    getMyAttendanceApi(token)
      .then((result) => { if (active) setRecords(Array.isArray(result) ? result : []); })
      .catch((failure) => { if (active) { setRecords([]); setError(errorText(failure)); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [token]);

  const sortedRecords = useMemo(() => records
    .map((record, index) => ({ record, index }))
    .filter(({ record }) => !statusFilter || String(record?.status || "").toUpperCase() === statusFilter)
    .sort((a, b) => {
      const aDate = parseDate(a.record?.startTime)?.getTime() ?? parseDate(a.record?.markedAt)?.getTime() ?? 0;
      const bDate = parseDate(b.record?.startTime)?.getTime() ?? parseDate(b.record?.markedAt)?.getTime() ?? 0;
      return bDate - aDate || a.index - b.index;
    })
    .map(({ record }) => record), [records, statusFilter]);

  const summary = useMemo(() => records.reduce((counts, item) => {
    if (item?.validForStatistics !== true) return counts;
    const status = String(item?.status || "").toUpperCase();
    if (status === "PRESENT") counts.present += 1;
    if (status === "ABSENT") counts.absent += 1;
    if (status === "EXCUSED") counts.excused += 1;
    return counts;
  }, { present: 0, absent: 0, excused: 0 }), [records]);

  return <div className="layout">
    <Sidebar role={role} activePage="member-attendance" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main member-attendance-main">
      <div className="member-attendance-page">
        <header className="member-attendance-header">
          <p className="eyebrow">SPORTCENTER / MEMBER</p>
          <h1>Lịch sử điểm danh</h1>
          <p>Xem các buổi học bạn đã được ghi nhận điểm danh. Bạn chỉ có thể xem dữ liệu của tài khoản đang đăng nhập.</p>
        </header>

        {error && <p className="member-attendance-alert" role="alert">{error}</p>}
        {!error && !loading && records.length > 0 && <section className="member-attendance-summary" aria-label="Tổng kết điểm danh">
          <article><span>Có mặt</span><strong>{summary.present}</strong></article>
          <article><span>Vắng mặt</span><strong>{summary.absent}</strong></article>
          <article><span>Vắng có phép</span><strong>{summary.excused}</strong></article>
          <small>Chỉ tính các bản ghi thuộc buổi học hợp lệ; buổi đã hủy không được tính.</small>
        </section>}

        <section className="member-attendance-panel" aria-labelledby="member-attendance-list-title">
          <div className="member-attendance-toolbar">
            <div><h2 id="member-attendance-list-title">Các buổi đã điểm danh</h2><p>{loading ? "Đang tải…" : `${sortedRecords.length} bản ghi`}</p></div>
            <label>Lọc theo trạng thái
              <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)} disabled={loading || Boolean(error)}>
                <option value="">Tất cả trạng thái</option>
                <option value="PRESENT">Có mặt</option>
                <option value="ABSENT">Vắng mặt</option>
                <option value="EXCUSED">Vắng có phép</option>
              </select>
            </label>
          </div>
          {loading ? <p className="member-attendance-empty" role="status">Đang tải lịch sử điểm danh…</p>
            : error ? null
              : records.length === 0 ? <p className="member-attendance-empty">Bạn chưa có bản ghi điểm danh nào.</p>
                : sortedRecords.length === 0 ? <p className="member-attendance-empty">Không có bản ghi phù hợp với trạng thái đã chọn.</p>
                  : <div className="member-attendance-list">{sortedRecords.map((item, index) => {
                    const cancelled = String(item?.sessionStatus || "").toUpperCase() === "CANCELLED";
                    const status = String(item?.status || "").toUpperCase();
                    return <article className={`member-attendance-card${cancelled ? " is-cancelled" : ""}`} key={item?.attendanceId ?? `${item?.classSessionId}-${item?.markedAt}-${index}`}>
                      <div className="member-attendance-card-heading">
                        <div>{(item?.sportName || item?.sportId != null) && <p className="eyebrow">{item?.sportName || `Môn #${item.sportId}`}</p>}<p className="eyebrow">{item?.courseName || item?.courseOfferingName || "Lớp học"}</p><h3>{item?.courseClassName || (item?.courseClassId != null ? `Lớp #${item.courseClassId}` : "Thông tin lớp chưa có")}</h3></div>
                        <span className={`member-attendance-badge attendance-${status.toLowerCase()}`}>{STATUS_LABELS[status] || status || "Chưa xác định"}</span>
                      </div>
                      {cancelled && <p className="member-attendance-cancelled">Buổi học đã hủy · Không tính vào thống kê điểm danh hợp lệ.</p>}
                      <dl className="member-attendance-facts">
                        <div><dt>Ngày học</dt><dd>{formatDate(item?.startTime)}</dd></div>
                        <div><dt>Thời gian</dt><dd>{item?.startTime ? new Date(item.startTime).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) : "—"} – {item?.endTime ? new Date(item.endTime).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) : "—"}</dd></div>
                        <div><dt>Trạng thái buổi</dt><dd>{SESSION_LABELS[item?.sessionStatus] || item?.sessionStatus || "Chưa xác định"}</dd></div>
                        <div><dt>Ghi nhận lúc</dt><dd>{formatDate(item?.markedAt)}</dd></div>
                      </dl>
                      {item?.note && <p className="member-attendance-note"><strong>Ghi chú:</strong> {item.note}</p>}
                    </article>;
                  })}</div>}
        </section>
      </div>
    </main>
  </div>;
}