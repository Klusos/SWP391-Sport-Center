import { useCallback, useEffect, useMemo, useState } from "react";
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
  const [selectedClassId, setSelectedClassId] = useState("");
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setRefreshing(true);
    setError("");

    try {
      const response = await getMyTrainingApi(token);
      const rows = safeArray(response);
      setClasses(rows);
      setSelectedClassId((current) =>
        rows.some((item) => classKey(item) === current) ? current : "",
      );
    } catch (requestError) {
      setError(requestError?.message || "Không tải được kết quả tập luyện.");
      // Khi làm mới thất bại, giữ dữ liệu đã tải để không làm mất lựa chọn.
      setClasses((current) => current ?? []);
    } finally {
      setRefreshing(false);
    }
  }, [token]);

  useEffect(() => {
    // Chỉ gọi API khi component mount hoặc token thay đổi.
    void load();
  }, [load]);

  const selectedClass = useMemo(
    () => safeArray(classes).find((item) => classKey(item) === selectedClassId) ?? null,
    [classes, selectedClassId],
  );

  return (
    <div className="layout">
      <Sidebar
        role={role}
        activePage="member-training"
        onLogout={onLogout}
        onNavigate={onNavigate}
      />

      <main className="main training-page member-training-page">
        <div className="member-training-shell">
          <header className="member-training-header">
            <div>
              <span className="member-training-eyebrow">SPORTS CENTER</span>
              <h1>Kết quả tập luyện</h1>
              <p>
                Theo dõi kết quả học tập, đánh giá của huấn luyện viên và giáo án
                cá nhân theo từng lớp của bạn.
              </p>
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

          <section className="member-training-class-section" aria-labelledby="member-training-classes-title">
            <div className="member-training-section-heading">
              <div>
                <h2 id="member-training-classes-title">Lớp học của tôi</h2>
                <p>Chọn lớp để xem kết quả và giáo án tương ứng.</p>
              </div>
              {classes !== null && (
                <span className="member-training-count">
                  {classes.length} lớp học
                </span>
              )}
            </div>

            {classes === null ? (
              <div className="member-training-empty" role="status">
                Đang tải danh sách lớp học…
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
                  const active = id === selectedClassId;

                  return (
                    <button
                      key={id}
                      type="button"
                      aria-pressed={active}
                      className={`member-training-class-option ${active ? "is-active" : ""}`}
                      onClick={() => setSelectedClassId(id)}
                    >
                      <span className="member-training-class-top">
                        <span className="member-training-sport-name">
                          {info.sportName || "Bộ môn"}
                        </span>
                        <span className="member-training-select-mark" aria-hidden="true">
                          {active ? "✓" : "→"}
                        </span>
                      </span>
                      <strong>{info.className || `Lớp #${id}`}</strong>
                      <span className="member-training-class-course">
                        {info.courseName || "Khóa học"}
                      </span>
                      <span className="member-training-class-coach">
                        HLV: {info.coachName || "Chưa cập nhật"}
                      </span>
                    </button>
                  );
                })}
              </div>
            )}
          </section>

          {classes !== null && classes.length > 0 && !selectedClass && (
            <div className="member-training-pick-hint" role="status">
              <span aria-hidden="true">↖</span>
              Chọn một lớp học phía trên để xem kết quả tập luyện.
            </div>
          )}

          {selectedClass && (
            <>
              <ClassResults key={`results-${selectedClassId}`} item={selectedClass} />

              <section
                className="member-training-personal-section"
                aria-labelledby="member-training-personal-title"
              >
                <div className="member-training-section-heading">
                  <div>
                    <h2 id="member-training-personal-title">Giáo án cá nhân</h2>
                    <p>
                      Dành cho lớp <strong>{selectedClass.courseClass?.className}</strong>.
                    </p>
                  </div>
                </div>

                {/*
                  LƯU Ý: IndividualTrainingPlans phải sử dụng classId để lọc.
                  Chỉ truyền prop không đủ nếu component con chưa hỗ trợ nó.
                */}
                <IndividualTrainingPlans
                  key={`personal-${selectedClassId}`}
                  mode="member"
                  token={token}
                  classId={Number(selectedClassId)}
                />
              </section>
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

  return (
    <section className="member-training-results-section" aria-labelledby="member-training-results-title">
      <div className="member-training-section-heading">
        <div>
          <span className="member-training-context-label">KẾT QUẢ LỚP ĐÃ CHỌN</span>
          <h2 id="member-training-results-title">
            {courseClass.className || "Kết quả tập luyện"}
          </h2>
          <p>
            {[courseClass.courseName, courseClass.sportName, courseClass.coachName && `HLV ${courseClass.coachName}`]
              .filter(Boolean)
              .join(" · ")}
          </p>
        </div>
      </div>

      <div className="member-training-stat-grid">
        <div className="member-training-stat-card">
          <span>Chuyên cần</span>
          <strong>
            {item.sessionsMarked ? `${item.sessionsPresent ?? 0}/${item.sessionsMarked}` : "—"}
          </strong>
          <small>Buổi có mặt / đã điểm danh</small>
        </div>
        <div className="member-training-stat-card">
          <span>Điểm trung bình</span>
          <strong>{item.averageScore ?? "—"}</strong>
          <small>Thang 10 · {results.length} buổi có kết quả</small>
        </div>
        <div className="member-training-stat-card">
          <span>Đánh giá tiến độ</span>
          <strong className="member-training-stars">
            {latest ? <Stars value={latest.rating} /> : "—"}
          </strong>
          <small>{latest ? formatDateTime(latest.evaluatedAt) : "Chưa được đánh giá"}</small>
        </div>
        <div className="member-training-stat-card">
          <span>Buổi học</span>
          <strong>
            {courseClass.completedSessions ?? 0}/{courseClass.totalSessions ?? 0}
          </strong>
          <small>Buổi đã hoàn thành / tổng số</small>
        </div>
      </div>

      <div className="member-training-detail-grid">
        <section className="member-training-panel">
          <div className="member-training-panel-heading">
            <h3>Kết quả từng buổi</h3>
            <span>{results.length} buổi</span>
          </div>
          {results.length === 0 ? (
            <p className="member-training-no-data">Chưa có kết quả buổi học.</p>
          ) : (
            <div className="member-training-table-wrap">
              <table className="member-training-table">
                <thead>
                  <tr>
                    <th>Buổi học</th>
                    <th>Điểm</th>
                    <th>Nhận xét</th>
                  </tr>
                </thead>
                <tbody>
                  {results.map((result) => (
                    <tr key={result.id}>
                      <td>{formatDateTime(result.sessionStartTime)}</td>
                      <td>
                        <strong>{result.score ?? "—"}</strong>
                        {result.score != null && <span>/10</span>}
                      </td>
                      <td>{result.notes || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="member-training-panel">
          <div className="member-training-panel-heading">
            <h3>Đánh giá từ huấn luyện viên</h3>
            <span>{evaluations.length} đánh giá</span>
          </div>
          {evaluations.length === 0 ? (
            <p className="member-training-no-data">Chưa có đánh giá tiến độ.</p>
          ) : (
            <div className="member-training-evaluation-list">
              {evaluations.map((evaluation) => (
                <article className="member-training-evaluation" key={evaluation.id}>
                  <div className="member-training-evaluation-top">
                    <Stars value={evaluation.rating} />
                    <small>{formatDateTime(evaluation.evaluatedAt)}</small>
                  </div>
                  <p>{evaluation.comment || "Chưa có nhận xét."}</p>
                  <span>HLV: {evaluation.evaluatedByName || "—"}</span>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>

      <section className="member-training-panel member-training-class-plan">
        <div className="member-training-panel-heading">
          <h3>Giáo án chung & mục tiêu của lớp</h3>
        </div>
        {!plan ? (
          <p className="member-training-no-data">Huấn luyện viên chưa công bố giáo án chung của lớp.</p>
        ) : (
          <>
            <p className="member-training-goal"><strong>Mục tiêu:</strong> {plan.goal || "—"}</p>
            {plan.description && <p className="member-training-plan-description">{plan.description}</p>}
            {exercises.length > 0 && (
              <ol className="member-training-exercises">
                {exercises.map((exercise, index) => (
                  <li key={exercise.id ?? index}>
                    <strong>{exercise.name || "Bài tập"}</strong>
                    <small>
                      {[
                        exercise.sets && `${exercise.sets} hiệp`,
                        exercise.reps && `${exercise.reps} lần`,
                        exercise.durationMinutes && `${exercise.durationMinutes} phút`,
                      ]
                        .filter(Boolean)
                        .join(" · ") || "Chưa có thông số"}
                    </small>
                    {exercise.notes && <p>{exercise.notes}</p>}
                  </li>
                ))}
              </ol>
            )}
          </>
        )}
      </section>
    </section>
  );
}
