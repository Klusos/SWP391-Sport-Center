import { useCallback, useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import IndividualTrainingPlans from "../components/IndividualTrainingPlans";
import { getMyTrainingApi } from "../api";
import { Stars } from "./CoachTraining";
import "./Training.css";

const formatDateTime = (value) => value
  ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(value))
  : "—";

export default function MemberTraining({ token, role, onLogout, onNavigate }) {
  const [classes, setClasses] = useState(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setError("");
    try {
      const rows = await getMyTrainingApi(token);
      setClasses(Array.isArray(rows) ? rows : []);
    } catch (requestError) {
      setClasses([]);
      setError(requestError.message || "Không tải được kết quả tập luyện.");
    }
  }, [token]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => active && load());
    return () => { active = false; };
  }, [load]);

  return <div className="layout">
    <Sidebar role={role} activePage="member-training" onLogout={onLogout} onNavigate={onNavigate} />
    <main className="main training-page">
      <header className="training-header">
        <div>
          <h1>Kết quả tập luyện</h1>
          <p className="subtitle">Giáo án, mục tiêu, kết quả từng buổi và đánh giá tiến độ của huấn luyện viên cho các lớp bạn đã đăng ký.</p>
        </div>
        <button type="button" onClick={() => void load()}>Làm mới</button>
      </header>
      {error && <div className="banner error" role="alert">{error}</div>}
      {!classes ? <p role="status">Đang tải…</p>
        : classes.length === 0 ? <p className="dash-empty">Bạn chưa có lớp đã xác nhận. Kết quả tập luyện sẽ xuất hiện sau khi đăng ký và thanh toán lớp học.</p>
          : classes.map((item) => <ClassCard key={item.courseClass.classId} item={item} />)}
      {classes && <IndividualTrainingPlans mode="member" token={token} />}
    </main>
  </div>;
}

function ClassCard({ item }) {
  const { courseClass, plan, results, evaluations } = item;
  const latest = evaluations[0];
  return <section className="dash-card training-member-class">
    <h2>{courseClass.className} <small>{courseClass.courseName} · {courseClass.sportName} · HLV {courseClass.coachName || "—"}</small></h2>
    <div className="training-stats">
      <div><span>Chuyên cần</span><strong>{item.sessionsMarked ? `${item.sessionsPresent}/${item.sessionsMarked}` : "—"}</strong><small>buổi có mặt / đã điểm danh</small></div>
      <div><span>Điểm trung bình</span><strong>{item.averageScore ?? "—"}</strong><small>thang 10, {results.length} buổi có kết quả</small></div>
      <div><span>Tiến độ</span><strong>{latest ? <Stars value={latest.rating} /> : "—"}</strong><small>{latest ? formatDateTime(latest.evaluatedAt) : "chưa được đánh giá"}</small></div>
      <div><span>Buổi học</span><strong>{courseClass.completedSessions}/{courseClass.totalSessions}</strong><small>đã hoàn thành</small></div>
    </div>

    <div className="training-grid">
      <div>
        <h3>Giáo án &amp; mục tiêu</h3>
        {!plan ? <p className="dash-empty">Huấn luyện viên chưa công bố giáo án.</p> : <>
          <p><b>Mục tiêu:</b> {plan.goal}</p>
          {plan.description && <p className="training-muted">{plan.description}</p>}
          {plan.exercises.length > 0 && <ol className="training-exercises">{plan.exercises.map((exercise) => <li key={exercise.id}>
            <div>
              <b>{exercise.name}</b>
              <small>{[exercise.sets && `${exercise.sets} hiệp`, exercise.reps && `${exercise.reps} lần`, exercise.durationMinutes && `${exercise.durationMinutes} phút`].filter(Boolean).join(" · ") || "—"}</small>
              {exercise.notes && <small>{exercise.notes}</small>}
            </div>
          </li>)}</ol>}
        </>}
      </div>
      <div>
        <h3>Kết quả từng buổi</h3>
        {results.length === 0 ? <p className="dash-empty">Chưa có kết quả buổi học.</p>
          : <table className="training-table">
            <thead><tr><th>Buổi</th><th>Điểm</th><th>Nhận xét</th></tr></thead>
            <tbody>{results.map((result) => <tr key={result.id}>
              <td>{formatDateTime(result.sessionStartTime)}</td>
              <td><b>{result.score}</b>/10</td>
              <td>{result.notes || "—"}</td>
            </tr>)}</tbody>
          </table>}
        <h3>Đánh giá tiến độ</h3>
        {evaluations.length === 0 ? <p className="dash-empty">Chưa có đánh giá.</p>
          : <ul className="training-evaluations">{evaluations.map((evaluation) => <li key={evaluation.id}>
            <Stars value={evaluation.rating} /> {evaluation.comment}
            <small className="training-muted"> — {evaluation.evaluatedByName || "HLV"}, {formatDateTime(evaluation.evaluatedAt)}</small>
          </li>)}</ul>}
      </div>
    </div>
  </section>;
}
