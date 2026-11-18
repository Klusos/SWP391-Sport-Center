import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import IndividualTrainingPlans from "../components/IndividualTrainingPlans";
import { getMyTrainingApi } from "../api";
import { Stars } from "./CoachTraining";
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
            <div className="member-training-section-heading">
              {classes !== null && (
                <span className="member-training-count">
                  {classes.length} lớp học
                </span>
              )}
            </div>

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
              <div className="member-training-class-list">
                {classes.map((item) => {
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
                        <span className="member-training-class-id">Mã lớp {id}</span>
                      </span>
                      <strong>{info.className || `Lớp #${id}`}</strong>
                      <span className="member-training-class-course">
                        {info.courseName || "Khóa học"}
                      </span>
                      <span className="member-training-class-coach">
                        HLV: {info.coachName || "Chưa cập nhật"}
                      </span>
                      <span className="member-training-class-progress">Tiến độ buổi học <strong>{info.completedSessions ?? 0}/{info.totalSessions ?? 0}</strong></span>
                      {info.courseOfferingName && <span className="member-training-class-secondary">{info.courseOfferingName}{(info.startDate || info.endDate) && ` · ${info.startDate || "—"} → ${info.endDate || "—"}`}</span>}
                    </Link>
                  );
                })}
              </div>
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
  return <section className="dash-card training-member-class">
    <div className="training-stats">
      <div><span>Chuyên cần</span><strong>{item.sessionsMarked ? item.sessionsPresent + "/" + item.sessionsMarked : "—"}</strong><small>buổi có mặt / đã điểm danh</small></div>
      <div><span>Điểm trung bình</span><strong>{item.averageScore ?? "—"}</strong><small>thang 10, {results.length} buổi có kết quả</small></div>
      <div><span>Tiến độ</span><strong>{latest ? <Stars value={latest.rating} /> : "—"}</strong><small>{latest ? formatDateTime(latest.evaluatedAt) : "chưa được đánh giá"}</small></div>
      <div><span>Buổi học</span><strong>{courseClass.completedSessions ?? 0}/{courseClass.totalSessions ?? 0}</strong><small>đã hoàn thành</small></div>
    </div>
    <div className="training-grid">
      <div>
        <h3>Nội dung &amp; mục tiêu lớp học</h3>
        {!plan ? <p className="dash-empty">Chưa có nội dung lớp học.</p> : <>
          <p><b>Mục tiêu:</b> {plan.goal || "—"}</p>
          {plan.description && <p className="training-muted">{plan.description}</p>}
          {exercises.length > 0 && <ol className="training-exercises">{exercises.map((exercise) => <li key={exercise.id}>
            <div><b>{exercise.name}</b><small>{[exercise.sets && exercise.sets + " hiệp", exercise.reps && exercise.reps + " lần", exercise.durationMinutes && exercise.durationMinutes + " phút"].filter(Boolean).join(" · ") || "—"}</small>{exercise.notes && <small>{exercise.notes}</small>}</div>
          </li>)}</ol>}
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
          <Stars value={evaluation.rating} /> {evaluation.comment || "—"}<small className="training-muted"> — {evaluation.evaluatedByName || "HLV"}, {formatDateTime(evaluation.evaluatedAt)}</small>
        </li>)}</ul>}
      </div>
    </div>
  </section>;
}
