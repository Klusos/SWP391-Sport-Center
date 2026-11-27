import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import { getMyAttendanceOverviewApi } from "../api";
import { attendanceDonutShares, buildMemberAttendanceOverview, filterMemberAttendanceClasses, memberAttendanceYears, paginateAttendance } from "../memberAttendance.js";
import "./MemberAttendance.css";

const STATUS = { PRESENT: "Có mặt", ABSENT: "Vắng mặt", EXCUSED: "Vắng có phép", CANCELLED: "Buổi học đã hủy", SCHEDULED: "Đã lên lịch", COMPLETED: "Đã hoàn thành", UNRECORDED: "Chưa ghi nhận" };
const instant = (value) => {
  if (!value) return null;
  const text = String(value);
  const date = new Date(/[zZ]|[+-]\d{2}:\d{2}$/.test(text) ? text : `${text}+07:00`);
  return Number.isNaN(date.getTime()) ? null : date;
};
function formatDate(value) {
  if (!value) return "Chưa có ngày";
  const date = /^\d{4}-\d{2}-\d{2}$/.test(String(value)) ? new Date(`${value}T00:00:00+07:00`) : instant(value);
  return date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", day: "2-digit", month: "2-digit", year: "numeric" }).format(date) : "Chưa có ngày";
}
function formatTime(value) {
  const date = instant(value);
  return date ? new Intl.DateTimeFormat("vi-VN", { timeZone: "Asia/Ho_Chi_Minh", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(date) : "—";
}
function errorText(error) {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Tài khoản không có quyền xem dữ liệu điểm danh.";
  return error?.message || "Không tải được dữ liệu điểm danh. Vui lòng thử lại.";
}
function Pager({ page, totalPages, onPage, label }) {
  if (totalPages <= 1) return null;
  return <nav className="member-attendance-pager" aria-label={label}>
    <button type="button" onClick={() => onPage(page - 1)} disabled={page <= 1}>Trước</button>
    <span>Trang {page}/{totalPages}</span>
    <button type="button" onClick={() => onPage(page + 1)} disabled={page >= totalPages}>Sau</button>
  </nav>;
}

export default function MemberAttendance({ token, role, onLogout, onNavigate }) {
  const [overview, setOverview] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [yearFilter, setYearFilter] = useState("ALL");
  const [sportFilter, setSportFilter] = useState("ALL");
  const [classPage, setClassPage] = useState(1);
  const [sessionPage, setSessionPage] = useState(1);
  const [selectedClassId, setSelectedClassId] = useState(null);
  const classCardRefs = useRef(new Map());

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const rows = await getMyAttendanceOverviewApi(token);
      setOverview(Array.isArray(rows) ? rows : []);
    } catch (failure) {
      setError(errorText(failure));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) void load(); });
    return () => { active = false; };
  }, [load]);

  const classes = useMemo(() => buildMemberAttendanceOverview(overview), [overview]);
  const years = useMemo(() => memberAttendanceYears(classes), [classes]);
  const sports = useMemo(() => [...new Map(classes.filter((item) => item.courseClass.sportId != null)
    .map((item) => [String(item.courseClass.sportId), item.courseClass.sportName || "Bộ môn"]))], [classes]);
  const filteredClasses = useMemo(() => filterMemberAttendanceClasses(classes, { year: yearFilter, sportId: sportFilter }), [classes, yearFilter, sportFilter]);
  const classPageData = paginateAttendance(filteredClasses, classPage, 6);
  const selectedClass = classes.find((item) => item.classId === Number(selectedClassId)) || null;
  const sessionPageData = paginateAttendance(selectedClass?.sessions || [], sessionPage, 10);

  function openClass(item, button) {
    classCardRefs.current.set(item.classId, button);
    setSessionPage(1);
    setSelectedClassId(item.classId);
  }
  function returnToClasses() {
    const returnId = Number(selectedClassId);
    setSelectedClassId(null);
    requestAnimationFrame(() => classCardRefs.current.get(returnId)?.focus());
  }

  const classContext = selectedClass?.courseClass || {};
  const header = <header className="member-attendance-header">
    <div><h1>{selectedClass ? "Lịch sử điểm danh lớp học" : "Lịch sử điểm danh"}</h1>
      <p>{selectedClass ? "Theo dõi các buổi học và trạng thái điểm danh của lớp." : "Xem tình hình điểm danh theo từng lớp học."}</p></div>
      <button type="button" onClick={() => void load()} disabled={loading}>{loading ? "Đang tải…" : "Làm mới"}</button>
    </header>;

  return <div className="layout">
    <Sidebar role={role} activePage="member-attendance" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main member-attendance-main">
      <div className="member-attendance-page">
        {header}
        {error && <div className="member-attendance-alert" role="alert">{error}<button type="button" onClick={() => void load()} disabled={loading}>Thử lại</button></div>}
        {loading ? <p className="member-attendance-empty" role="status">Đang tải lịch sử điểm danh…</p> : error ? null : selectedClass ? <>
          <button className="member-attendance-back" type="button" onClick={returnToClasses}>← Quay lại danh sách lớp</button>
          <section className="member-attendance-class-context" aria-label="Thông tin lớp học">
            <div><p className="member-attendance-eyebrow">{classContext.sportName || "Bộ môn"}</p><h2>{classContext.courseName || "Khóa học"}</h2>
              <p><strong>Huấn luyện viên:</strong> {classContext.coachName || "Chưa cập nhật"}</p>
              <p><strong>Lớp:</strong> {classContext.className || `Lớp học #${selectedClass.classId}`}</p>
              <p><strong>Đợt học:</strong> {classContext.courseOfferingName || "Chưa cập nhật tên đợt học"}</p>
              <p><strong>Thời gian:</strong> {classContext.startDate || classContext.endDate ? `${formatDate(classContext.startDate)} – ${formatDate(classContext.endDate)}` : "Chưa cập nhật"}</p></div>
            <AttendanceSummary summary={selectedClass.summary} percent={selectedClass.attendancePercent} />
          </section>
          <div className="member-attendance-session-counts">
            <span><b>{selectedClass.summary.future}</b> buổi sắp tới</span>
            <span><b>{selectedClass.summary.unrecorded}</b> buổi chưa ghi nhận</span>
            <span><b>{selectedClass.summary.recorded}/{selectedClass.summary.eligibleElapsed}</b> buổi đã diễn ra được điểm danh</span>
          </div>
          {selectedClass.summary.unrecorded > 0 && <p className="member-attendance-provisional" role="status">
            Còn {selectedClass.summary.unrecorded} buổi chưa được huấn luyện viên điểm danh. Tỷ lệ chuyên cần hiện tại chưa phản ánh đầy đủ toàn bộ các buổi học.
          </p>}
          {selectedClass.sessions.length === 0 ? <p className="member-attendance-empty">Chưa có dữ liệu điểm danh cho lớp này.</p> : <>
            <div className="member-attendance-table-wrap"><table className="member-attendance-table"><thead><tr><th>Buổi học</th><th>Ngày</th><th>Thời gian</th><th>Điểm danh</th><th>Trạng thái buổi</th><th>Ghi chú</th></tr></thead><tbody>
              {sessionPageData.items.map((session) => {
                const cancelled = String(session.status || "").toUpperCase() === "CANCELLED";
                const status = cancelled ? "CANCELLED" : String(session.attendanceStatus || "UNRECORDED").toUpperCase();
                const future = session.future;
                return <tr key={session.classSessionId} className={cancelled ? "is-cancelled" : ""}>
                  <td>{session.className || classContext.className || `Buổi #${session.classSessionId}`}</td>
                  <td>{formatDate(session.startTime)}</td>
                  <td>{formatTime(session.startTime)} – {formatTime(session.endTime)}</td>
                  <td><span className={`member-attendance-status status-${status === "UNRECORDED" ? "unrecorded" : status.toLowerCase()}`}>{STATUS[status] || "Chưa xác định"}</span></td>
                  <td>{STATUS[String(session.status || "").toUpperCase()] || session.status || "Chưa cập nhật"}{future && !cancelled ? " · Sắp diễn ra" : ""}</td>
                  <td>{session.attendance?.note || "—"}</td>
                </tr>;
              })}
            </tbody></table></div>
            <Pager page={sessionPageData.page} totalPages={sessionPageData.totalPages} onPage={setSessionPage} label="Phân trang buổi học" />
          </>}
        </> : <>
          <div className="member-attendance-filter-row">
            <label>Năm khai giảng<select value={yearFilter} onChange={(event) => { setYearFilter(event.target.value); setClassPage(1); }}><option value="ALL">Tất cả năm</option>{years.map((year) => <option value={year} key={year}>{year}</option>)}</select></label>
            <label>Bộ môn<select value={sportFilter} onChange={(event) => { setSportFilter(event.target.value); setClassPage(1); }}><option value="ALL">Tất cả bộ môn</option>{sports.map(([id, name]) => <option value={id} key={id}>{name}</option>)}</select></label>
            <span className="member-attendance-class-count">{filteredClasses.length} / {classes.length} lớp học</span>
          </div>
          <div className="member-attendance-divider" />
          {classes.length === 0 ? <p className="member-attendance-empty">Chưa có lớp học hoặc dữ liệu điểm danh.</p>
            : filteredClasses.length === 0 ? <p className="member-attendance-empty">Không có lớp học phù hợp với bộ lọc.</p>
              : <div className="member-attendance-class-grid">{classPageData.items.map((item) => {
                const info = item.courseClass;
                return <button type="button" key={item.classId} className="member-attendance-class-card" ref={(node) => node ? classCardRefs.current.set(item.classId, node) : classCardRefs.current.delete(item.classId)} onClick={(event) => openClass(item, event.currentTarget)}>
                  <span className="member-attendance-class-card-title"><span><small>{info.sportName || "Bộ môn"}</small><strong>{info.courseName || "Khóa học"}</strong></span><AttendanceDonut percent={item.attendancePercent} summary={item.summary} /></span>
                  <span className="member-attendance-offering"><span><b>Lớp:</b> {info.className || `Lớp học #${item.classId}`}</span><span><b>Huấn luyện viên:</b> {info.coachName || "Chưa cập nhật"}</span><span><b>Đợt học:</b> {info.courseOfferingName || "Chưa cập nhật tên đợt học"}</span><span><b>Thời gian:</b> {info.startDate || info.endDate ? `${formatDate(info.startDate)} – ${formatDate(info.endDate)}` : "Chưa cập nhật"}</span></span>
                  <span className="member-attendance-card-counts"><span className="attendance-count-present">Có mặt <b>{item.summary.present}</b></span><span className="attendance-count-absent">Vắng <b>{item.summary.absent}</b></span>{item.summary.excused > 0 && <span className="attendance-count-excused">Có phép <b>{item.summary.excused}</b></span>}</span>
                  <span className="member-attendance-card-footer">{item.attendancePercent == null ? "Chưa có dữ liệu điểm danh" : `${item.attendancePercent}% số buổi có mặt${item.summary.unrecorded > 0 ? " · Tạm tính" : ""}`} · {item.summary.recorded}/{item.summary.eligibleElapsed} buổi đã diễn ra được điểm danh</span>
                </button>;
              })}</div>}
          <Pager page={classPageData.page} totalPages={classPageData.totalPages} onPage={setClassPage} label="Phân trang lớp học" />
        </>}
      </div>
    </main>
  </div>;
}

function AttendanceDonut({ percent, summary = { present: 0, absent: 0, excused: 0, recorded: 0 } }) {
  const recorded = Number(summary.recorded || 0);
  const shares = attendanceDonutShares(summary);
  const style = { "--present-share": shares.present + "%", "--absent-share": shares.absent + "%", "--excused-share": shares.excused + "%" };
  return <span className={"member-attendance-donut" + (recorded === 0 ? " is-empty" : "")} style={style} role="img" aria-label={recorded === 0 ? "Ch\u01b0a c\u00f3 d\u1eef li\u1ec7u \u0111i\u1ec3m danh" : "C\u00f3 m\u1eb7t " + percent + "%"}><b>{percent == null ? "\u2014" : percent + "%"}</b></span>;
}

function AttendanceSummary({ summary, percent }) {
  return <div className="member-attendance-detail-summary"><AttendanceDonut percent={percent} summary={summary} /><div><span className="attendance-count-present">Có mặt <b>{summary.present}</b></span><span className="attendance-count-absent">Vắng mặt <b>{summary.absent}</b></span><span className="attendance-count-excused">Vắng có phép <b>{summary.excused}</b></span><small>{summary.recorded === 0 ? "Chưa có dữ liệu điểm danh" : `${summary.recorded} buổi đã ghi nhận`}</small></div></div>;
}
