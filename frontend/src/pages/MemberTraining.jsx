import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import IndividualTrainingPlans from "../components/IndividualTrainingPlans";
import { getMyTrainingApi } from "../api";
import { filterMemberTrainingRows, progressLevelLabel, trainingStartYears } from "../trainingPresentation.js";
import { todayInVietnam } from "../membershipDates.js";
import { formatDateVN } from "../uiPresentation.js";
import "./Training.css";
import "./MemberTraining.css";

const safeArray = (value) => (Array.isArray(value) ? value : []);

const formatDateTime = (value) => {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(date);
};

const classKey = (item) => String(item?.courseClass?.classId ?? "");

export default function MemberTraining({ token, role, onLogout, onNavigate }) {
  const [classes, setClasses] = useState(null);
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [yearFilter, setYearFilter] = useState("ALL");
  const [sportFilter, setSportFilter] = useState("ALL");
  const [lifecycleFilter, setLifecycleFilter] = useState("ALL");
  const [page, setPage] = useState(1);
  const latestRequest = useRef(0);
  const { classId } = useParams();
  const detailRoute = classId !== undefined;
  const validClassId = /^\d+$/.test(String(classId || "")) && Number(classId) > 0;

  const load = useCallback(async () => {
    const requestId = ++latestRequest.current;
    setRefreshing(true);
    setError("");

    try {
      const response = await getMyTrainingApi(token);
      const rows = safeArray(response);
      if (requestId !== latestRequest.current) return;
      setClasses(rows);
    } catch (requestError) {
      if (requestId !== latestRequest.current) return;
      setError(requestError?.message || "Không tải được kết quả tập luyện.");
    } finally {
      if (requestId === latestRequest.current) setRefreshing(false);
    }
  }, [token]);

  useEffect(() => {
    // Chỉ gọi API khi component mount hoặc token thay đổi.
    let active = true;
    Promise.resolve().then(() => { if (active) void load(); });
    return () => { active = false; latestRequest.current += 1; };
  }, [load]);

  const selectedClass = detailRoute && validClassId
    ? safeArray(classes).find((item) => classKey(item) === String(classId)) ?? null
    : null;
  const trainingRows = safeArray(classes);
  const years = trainingStartYears(trainingRows);
  const sports = [...new Map(trainingRows.filter((item) => item.courseClass?.sportId != null).map((item) => [String(item.courseClass.sportId), item.courseClass.sportName || "Bộ môn"])).entries()];
  const filteredTrainingRows = filterMemberTrainingRows(trainingRows, {
    year: yearFilter,
    sportId: sportFilter,
    lifecycle: lifecycleFilter,
    today: todayInVietnam(),
  });
  const pages = Math.max(1, Math.ceil(filteredTrainingRows.length / 10));
  const currentPage = Math.min(page, pages);
  const visibleTrainingRows = filteredTrainingRows.slice((currentPage - 1) * 10, currentPage * 10);
  const changeTrainingFilter = (setter) => (event) => { setter(event.target.value); setPage(1); };

  return (
    <div className="layout">
      <Sidebar
        role={role}
        activePage="member-training"
        onLogout={onLogout}
        onNavigate={onNavigate}
      />

      <main className={`main training-page${detailRoute ? "" : " member-training-page"}`}>
        <div className={detailRoute ? "training-detail-shell" : "member-training-shell"}>
          {detailRoute && (
            <Link className="training-back-button" to="/member/training" aria-label="Quay lại danh sách lớp">
              <span aria-hidden="true">←</span> Danh sách lớp
            </Link>
          )}
          <header className={detailRoute ? "training-header member-training-detail-header" : "member-training-header"}>
            <div>
              {detailRoute ? (
                <>
                  <h1>{selectedClass?.courseClass?.className || "Kết quả tập luyện"}</h1>
                  {selectedClass && <p className="training-class-meta">
                    {[selectedClass.courseClass?.courseName, selectedClass.courseClass?.sportName, selectedClass.courseClass?.coachName && `HLV ${selectedClass.courseClass.coachName}`].filter(Boolean).join(" · ")}
                  </p>}
                </>
              ) : <h1>Kết quả tập luyện</h1>}
            </div>
            <button
              type="button"
              className="member-training-refresh"
              onClick={() => void load()}
              disabled={refreshing}
            >
              {refreshing ? "Đang tải…" : "↻ Làm mới"}
            </button>
          </header>

          {error && (
            <div className="member-training-error" role="alert">
              {error}
            </div>
          )}

          {!detailRoute && <section className="member-training-class-section" aria-label="Lớp học của tôi">
            {classes === null ? (
              <div className="member-training-empty" role="status">
                {error ? "Chưa thể tải danh sách lớp học. Hãy thử làm mới." : "Đang tải danh sách lớp học…"}
              </div>
            ) : classes.length === 0 ? (
              <div className="member-training-empty">
                Bạn chưa có lớp đã xác nhận. Kết quả sẽ hiển thị sau khi đăng ký
                và thanh toán lớp học.
              </div>
            ) : (
              <>
              <div className="member-training-filters" aria-label="Lọc kết quả tập luyện">
                <label>Năm khai giảng<select value={yearFilter} onChange={changeTrainingFilter(setYearFilter)}><option value="ALL">Tất cả năm</option>{years.map((year) => <option key={year} value={year}>{year}</option>)}</select></label>
                <label>Bộ môn<select value={sportFilter} onChange={changeTrainingFilter(setSportFilter)}><option value="ALL">Tất cả bộ môn</option>{sports.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label>
                <label>Tình trạng đợt học<select value={lifecycleFilter} onChange={changeTrainingFilter(setLifecycleFilter)}><option value="ALL">Tất cả</option><option value="NOT_ENDED">Chưa kết thúc</option><option value="COMPLETED">Đã kết thúc</option></select></label>
                <span className="member-training-count" aria-live="polite">{filteredTrainingRows.length} / {classes.length} lớp học</span>
              </div>
              <div className="member-training-divider" />
              {filteredTrainingRows.length === 0 ? <div className="member-training-empty">Không có kết quả phù hợp với bộ lọc. Các đợt học thiếu ngày kết thúc không được tự xếp vào mục đã kết thúc.</div> : <div className="member-training-class-list">
                {visibleTrainingRows.map((item) => {
                  const info = item.courseClass || {};
                  const id = classKey(item);

                  return (
                    <Link
                      key={id}
                      to={`/member/training/${encodeURIComponent(id)}`}
                      className="member-training-class-option"
                    >
                      <span className="member-training-class-top">
                        <span className="member-training-sport-name">
                          {info.sportName || "Bộ môn"}
                        </span>
                        <span className="member-training-class-id">Lớp học</span>
                      </span>
                      <strong>{info.courseName || "Khóa học"}</strong>
                      {info.className && <span className="member-training-class-course">{info.className}</span>}
                      <span className="member-training-class-coach">
                        HLV: {info.coachName || "Chưa cập nhật"}
                      </span>
                      <span className="member-training-class-progress"><span>Tiến độ buổi học <strong>{info.completedSessions ?? 0}/{info.totalSessions ?? 0}</strong></span><span className="member-training-progress-track" role="progressbar" aria-label={`Tiến độ buổi học ${info.courseName || info.className || ""}`} aria-valuemin={0} aria-valuemax={Math.max(0, Number(info.totalSessions) || 0)} aria-valuenow={Math.min(Math.max(0, Number(info.completedSessions) || 0), Math.max(0, Number(info.totalSessions) || 0))}><i style={{ width: `${Number(info.totalSessions) > 0 ? Math.min(100, Math.max(0, (Number(info.completedSessions) || 0) / Number(info.totalSessions) * 100)) : 0}%` }} /></span></span>
                      {(info.courseOfferingName || info.startDate || info.endDate) && <span className="member-training-class-secondary">{info.courseOfferingName || "Đợt học"}{(info.startDate || info.endDate) && ` · ${formatDateVN(info.startDate)} – ${formatDateVN(info.endDate)}`}</span>}
                    </Link>
                  );
                })}
              </div>}
              {filteredTrainingRows.length > 10 && <nav className="member-training-pager" aria-label="Phân trang kết quả tập luyện"><span>Trang {currentPage}/{pages}</span><button type="button" disabled={currentPage <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))}>Trước</button><button type="button" disabled={currentPage >= pages} onClick={() => setPage((value) => Math.min(pages, value + 1))}>Sau</button></nav>}
              </>
            )}
          </section>}

          {detailRoute && classes === null && <div className="member-training-empty" role="status">{error ? "Chưa thể tải lớp học. Hãy thử làm mới." : "Đang tải kết quả lớp học…"}</div>}
          {detailRoute && classes !== null && !validClassId && <div className="member-training-empty" role="alert">Mã lớp không hợp lệ. Hãy quay lại danh sách lớp.</div>}
          {detailRoute && classes !== null && validClassId && !selectedClass && <div className="member-training-empty" role="status">Không tìm thấy lớp này trong các lớp bạn được phép xem.</div>}

          {selectedClass && (
            <>
              <ClassResults key={`results-${classId}`} item={selectedClass} />

              <div className="training-detail-plans">
                <IndividualTrainingPlans
                  key={`personal-${classId}`}
                  mode="member"
                  token={token}
                  classId={Number(classId)}
                />
              </div>
            </>
          )}
        </div>
      </main>
    </div>
  );
}

function ClassResults({ item }) {
  const { courseClass = {}, plan } = item;
  const results = safeArray(item.results);
  const evaluations = safeArray(item.evaluations);
  const latest = evaluations[0];
  const exercises = safeArray(plan?.exercises);
  const weeklySections = [...safeArray(plan?.weeks)].sort((left, right) => Number(left.weekNumber) - Number(right.weekNumber));
  return <section className="dash-card training-member-class">
    <div className="training-stats">
      <div><span>Chuyên cần</span><strong>{item.sessionsMarked ? item.sessionsPresent + "/" + item.sessionsMarked : "—"}</strong><small>buổi có mặt / đã điểm danh</small></div>
      <div><span>Điểm trung bình</span><strong>{item.averageScore ?? "—"}</strong><small>thang 10, {results.length} buổi có kết quả</small></div>
      <div><span>Tiến độ</span><strong>{latest ? `${latest.rating}/5 · ${progressLevelLabel(latest.rating)}` : "—"}</strong><small>{latest ? formatDateTime(latest.evaluatedAt) : "Chưa được đánh giá"}</small></div>
      <div><span>Buổi học</span><strong>{courseClass.completedSessions ?? 0}/{courseClass.totalSessions ?? 0}</strong><small>đã hoàn thành</small></div>
    </div>
    <div className="training-grid">
      <div>
        <h3>Nội dung &amp; mục tiêu lớp học</h3>
        {!plan ? <p className="dash-empty">Chưa có nội dung lớp học.</p> : <>
          <p><b>Mục tiêu:</b> {plan.goal || "—"}</p>
          {plan.description && <p className="training-muted">{plan.description}</p>}
          {exercises.length > 0 && <section className="member-training-legacy-exercises"><h4>Bài tập chưa phân tuần</h4><ol className="training-exercises">{exercises.map((exercise) => <li key={exercise.id}>
            <div><b>{exercise.name}</b><small>{[exercise.sets && exercise.sets + " hiệp", exercise.reps && exercise.reps + " lần", exercise.durationMinutes && exercise.durationMinutes + " phút"].filter(Boolean).join(" · ") || "—"}</small>{exercise.notes && <small>{exercise.notes}</small>}</div>
          </li>)}</ol></section>}
          {weeklySections.map((week) => <details className="member-training-week" key={week.id}>
            <summary><span><strong>Tuần {week.weekNumber}{week.title ? ` · ${week.title}` : ""}</strong>{week.startDate && week.endDate && <small>{formatDateOnly(week.startDate)} – {formatDateOnly(week.endDate)}</small>}</span><span>{week.exercises?.length || 0} bài tập</span></summary>
            {week.outsideOffering && <p className="member-training-week-warning">Tuần này nằm ngoài thời hạn đợt học hiện tại; nội dung vẫn được giữ.</p>}
            {week.goal && <p><b>Mục tiêu tuần:</b> {week.goal}</p>}{week.description && <p className="member-training-plan-description">{week.description}</p>}
            {!week.exercises?.length ? <p className="member-training-no-data">Tuần này chưa có bài tập.</p> : <ol className="training-exercises">{week.exercises.map((exercise) => <li key={exercise.id}><div><b>{exercise.name}</b>
              {[(exercise.sets != null && `${exercise.sets} hiệp`), (exercise.reps != null && `${exercise.reps} lần`), (exercise.durationMinutes != null && `${exercise.durationMinutes} phút`)].filter(Boolean).length > 0 && <small>{[(exercise.sets != null && `${exercise.sets} hiệp`), (exercise.reps != null && `${exercise.reps} lần`), (exercise.durationMinutes != null && `${exercise.durationMinutes} phút`)].filter(Boolean).join(" · ")}</small>}
              {exercise.notes && <small>{exercise.notes}</small>}</div></li>)}</ol>}
          </details>)}
        </>}
      </div>
      <div>
        <h3>Kết quả từng buổi</h3>
        {results.length === 0 ? <p className="dash-empty">Chưa có kết quả.</p> : <table className="training-table">
          <thead><tr><th>Buổi</th><th>Điểm</th><th>Nhận xét</th></tr></thead>
          <tbody>{results.map((result) => <tr key={result.id}><td>{formatDateTime(result.sessionStartTime)}</td><td><b>{result.score ?? "—"}</b>{result.score != null && "/10"}</td><td>{result.notes || "—"}</td></tr>)}</tbody>
        </table>}
        <h3>Đánh giá tiến độ</h3>
        {evaluations.length === 0 ? <p className="dash-empty">Chưa có đánh giá.</p> : <ul className="training-evaluations">{evaluations.map((evaluation) => <li key={evaluation.id}>
          <b>{evaluation.rating}/5 · {progressLevelLabel(evaluation.rating)}</b> — {evaluation.comment || "—"}<small className="training-muted"> — {evaluation.evaluatedByName || "Huấn luyện viên"}, {formatDateTime(evaluation.evaluatedAt)}</small>
        </li>)}</ul>}
      </div>
    </div>
  </section>;
}

function formatDateOnly(value) {
  if (!value) return "—";
  const [year, month, day] = value.split("-").map(Number);
  return Number.isFinite(year) && Number.isFinite(month) && Number.isFinite(day)
    ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeZone: "Asia/Ho_Chi_Minh" }).format(new Date(year, month - 1, day))
    : "—";
}
