import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import IndividualTrainingPlans from "../components/IndividualTrainingPlans";
import { getMyTrainingApi } from "../api";
import { Stars } from "./CoachTraining";
import "./Training.css";

const formatDateTime = (value) =>
  value
    ? new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";

const formatDateOnly = (value) =>
  value
    ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "short" }).format(
        new Date(value)
      )
    : "—";

export default function MemberTraining({ token, role, onLogout, onNavigate }) {
  const [classes, setClasses] = useState(null);
  const [selectedClassId, setSelectedClassId] = useState(null);
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [completedDrills, setCompletedDrills] = useState(new Set([0])); // default drill 1 checked
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    setError("");
    setRefreshing(true);
    try {
      const rows = await getMyTrainingApi(token);
      const data = Array.isArray(rows) ? rows : [];
      setClasses(data);
      if (data.length > 0) {
        setSelectedClassId((prev) =>
          prev && data.some((c) => c.courseClass.classId === prev)
            ? prev
            : data[0].courseClass.classId
        );
      }
    } catch (requestError) {
      setClasses([]);
      setError(requestError.message || "Không tải được kết quả tập luyện.");
    } finally {
      setRefreshing(false);
    }
  }, [token]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => active && load());
    return () => {
      active = false;
    };
  }, [load]);

  const showToast = (msg) => {
    setNotice(msg);
    setTimeout(() => setNotice(""), 3500);
  };

  const toggleDrill = (index) => {
    setCompletedDrills((prev) => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
        showToast(`Đã hoàn tác trạng thái bài tập số ${index + 1}`);
      } else {
        next.add(index);
        showToast(`Đã đánh dấu hoàn thành bài tập số ${index + 1}!`);
      }
      return next;
    });
  };

  const selectedClass = useMemo(() => {
    if (!classes || classes.length === 0) return null;
    return (
      classes.find((c) => c.courseClass.classId === selectedClassId) ||
      classes[0]
    );
  }, [classes, selectedClassId]);

  return (
    <div className="layout">
      <Sidebar
        role={role}
        activePage="member-training"
        onLogout={onLogout}
        onNavigate={onNavigate}
      />
      <main className="main training-page training-vip-container">
        {/* Breadcrumb & Page Header */}
        <header className="training-vip-header">
          <div>
            <div className="training-vip-breadcrumb">
              <span className="breadcrumb-pill">
                <span className="breadcrumb-dot"></span>
                SPORTCENTER / HỘI VIÊN
              </span>
              <span className="breadcrumb-sep">/</span>
              <span className="breadcrumb-sub">TIẾN ĐỘ THỂ THAO</span>
            </div>
            <h1 className="training-vip-title">Kết quả tập luyện &amp; Giáo án</h1>
            <p className="training-vip-subtitle">
              Theo dõi mức độ chuyên cần, điểm số từng buổi, đánh giá của Huấn luyện viên và kế hoạch tập cá nhân.
            </p>
          </div>

          <div className="training-header-actions">
            <button
              type="button"
              className={`training-btn-refresh ${refreshing ? "loading" : ""}`}
              onClick={() => {
                void load();
                showToast("Đang làm mới dữ liệu tập luyện từ ban huấn luyện...");
              }}
              disabled={refreshing}
            >
              <svg
                className={`training-icon-spin ${refreshing ? "spinning" : ""}`}
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                />
              </svg>
              <span>{refreshing ? "Đang tải..." : "Làm mới kết quả"}</span>
            </button>
          </div>
        </header>

        {notice && (
          <div className="training-toast-alert" role="status">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
            <span>{notice}</span>
          </div>
        )}

        {error && (
          <div className="banner error" role="alert">
            {error}
          </div>
        )}

        {!classes ? (
          <div className="training-loading-state" role="status">
            <div className="training-loading-spinner"></div>
            <p>Đang tải tiến độ tập luyện của bạn...</p>
          </div>
        ) : classes.length === 0 ? (
          <div className="dash-card training-empty-box">
            <div className="training-empty-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
              </svg>
            </div>
            <h3>Bạn chưa có lớp học đã xác nhận</h3>
            <p>
              Kết quả tập luyện, giáo án và nhận xét của Huấn luyện viên sẽ xuất hiện tự động ngay sau khi bạn đăng ký và hoàn tất học phí khóa học.
            </p>
            <button
              type="button"
              className="training-btn-primary"
              onClick={() => onNavigate && onNavigate("member-courses")}
            >
              Xem danh sách khóa học mở ghi danh
            </button>
          </div>
        ) : (
          <>
            {/* Class Selector Tabs */}
            <nav className="training-class-tabs" aria-label="Lựa chọn lớp học">
              {classes.map((cls) => {
                const isSelected = cls.courseClass.classId === selectedClass?.courseClass.classId;
                return (
                  <button
                    key={cls.courseClass.classId}
                    type="button"
                    className={`training-class-tab-pill ${isSelected ? "active" : ""}`}
                    onClick={() => {
                      setSelectedClassId(cls.courseClass.classId);
                      showToast(`Đã chuyển sang lớp: ${cls.courseClass.className}`);
                    }}
                  >
                    <span className="sport-dot"></span>
                    <span className="class-name">{cls.courseClass.className}</span>
                    <span className="class-sport">({cls.courseClass.sportName})</span>
                    {isSelected && <span className="active-dot"></span>}
                  </button>
                );
              })}
            </nav>

            {/* Selected Class Dashboard View */}
            {selectedClass && (
              <ClassTrainingDetail
                item={selectedClass}
                completedDrills={completedDrills}
                onToggleDrill={toggleDrill}
                onNavigate={onNavigate}
              />
            )}
          </>
        )}

        {/* Individual Training Plans Section */}
        {classes && <IndividualTrainingPlans mode="member" token={token} />}
      </main>
    </div>
  );
}

function ClassTrainingDetail({ item, completedDrills, onToggleDrill, onNavigate }) {
  const { courseClass, plan, results = [], evaluations = [] } = item;
  const latestEvaluation = evaluations[0];

  const sessionsPresent = Number(item.sessionsPresent || 0);
  const sessionsMarked = Number(item.sessionsMarked || 0);
  const attendancePercent =
    sessionsMarked > 0 ? Math.round((sessionsPresent / sessionsMarked) * 100) : 0;

  const totalSessions = Math.max(Number(courseClass.totalSessions || 12), 1);
  const completedSessions = Math.min(
    Number(courseClass.completedSessions || 0),
    totalSessions
  );
  const remainingSessions = Math.max(0, totalSessions - completedSessions);

  const averageScoreDisplay =
    item.averageScore != null ? Number(item.averageScore).toFixed(1) : "—";
  const coachRatingDisplay = latestEvaluation
    ? Number(latestEvaluation.rating || 5).toFixed(1)
    : "5.0";

  // Pre-configured default drills if teacher has not added specific drills yet
  const defaultExercises = [
    {
      name: "1. Khởi động & Bước chân di chuyển 6 góc sân",
      notes: "Luyện bộ pháp chuyển dịch trọng tâm, tăng tốc độ phản xạ và tiếp cận góc lưới.",
      sets: 3,
      durationMinutes: 15,
    },
    {
      name: "2. Đập cầu bật nhảy (Jump Smash) liên hoàn",
      notes: "Tập trung tư thế vung vợt đỉnh đầu, gập cổ tay cắm cầu sát biên đôi bên đối phương.",
      sets: 4,
      reps: 12,
    },
    {
      name: "3. Phông cầu cao sâu & Thủ cầu phản tạt",
      notes: "Kéo giãn đội hình đối thủ, giữ thế cân bằng phòng ngự phản công nhanh hai góc lưới.",
      sets: 3,
      reps: 20,
    },
  ];

  const exercises =
    plan?.exercises && plan.exercises.length > 0
      ? plan.exercises
      : defaultExercises;

  return (
    <div className="training-detail-wrapper">
      {/* 4 Core KPI Metrics */}
      <section className="training-kpi-grid" aria-label="Chỉ số hiệu quả tập luyện">
        {/* Metric 1: Attendance */}
        <article className="training-kpi-card">
          <div className="kpi-info">
            <span className="kpi-label">Tỷ lệ Chuyên cần</span>
            <div className="kpi-value-wrap">
              <strong className="kpi-value">
                {sessionsPresent}/{sessionsMarked || totalSessions}
              </strong>
              <span className="kpi-unit">buổi</span>
            </div>
            <p className="kpi-status-text text-emerald">
              <span className="status-dot-emerald"></span>
              Buổi có mặt / Đã điểm danh
            </p>
          </div>
          <div className="kpi-radial-wrap">
            <svg className="kpi-radial-svg" viewBox="0 0 36 36">
              <path
                className="kpi-circle-bg"
                d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              />
              <path
                className="kpi-circle-val"
                strokeDashoffset={100 - attendancePercent}
                d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              />
            </svg>
            <span className="kpi-radial-text">{attendancePercent}%</span>
          </div>
        </article>

        {/* Metric 2: Average Technical Score */}
        <article className="training-kpi-card">
          <div className="kpi-info">
            <span className="kpi-label">Điểm trung bình kỹ thuật</span>
            <div className="kpi-value-wrap">
              <strong className="kpi-value">{averageScoreDisplay}</strong>
              <span className="kpi-unit">/ 10</span>
            </div>
            <p className="kpi-status-text">
              Dựa trên {results.length} buổi đánh giá gần nhất
            </p>
          </div>
          <div className="kpi-badge-icon badge-amber">
            <svg width="22" height="22" viewBox="0 0 20 20" fill="currentColor">
              <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
            </svg>
          </div>
        </article>

        {/* Metric 3: Coach Rating */}
        <article className="training-kpi-card">
          <div className="kpi-info">
            <span className="kpi-label">Đánh giá của Huấn luyện viên</span>
            <div className="kpi-value-wrap flex-baseline">
              <strong className="kpi-value">{coachRatingDisplay}</strong>
              <div className="coach-stars-row">
                <Stars value={latestEvaluation ? latestEvaluation.rating : 5} />
              </div>
            </div>
            <p className="kpi-status-text">
              HLV {courseClass.coachName || "Hoàng Nam"} ·{" "}
              {latestEvaluation
                ? formatDateTime(latestEvaluation.evaluatedAt)
                : "Cập nhật gần đây"}
            </p>
          </div>
          <div className="kpi-badge-icon badge-blue">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
          </div>
        </article>

        {/* Metric 4: Course Progress */}
        <article className="training-kpi-card kpi-column">
          <div className="kpi-header-row">
            <span className="kpi-label">Tiến độ khóa học</span>
            <span className="kpi-pill-blue">
              Buổi {completedSessions}/{totalSessions}
            </span>
          </div>

          {/* 12-segment progress bar */}
          <div className="training-segment-bar">
            {Array.from({ length: 12 }, (_, i) => {
              const sessionIndex = i + 1;
              const isFilled = sessionIndex <= completedSessions;
              const isCurrent = sessionIndex === completedSessions;
              return (
                <div
                  key={i}
                  className={`segment-unit ${
                    isFilled
                      ? isCurrent
                        ? "unit-current"
                        : "unit-completed"
                      : "unit-pending"
                  }`}
                  title={`Buổi ${sessionIndex}`}
                />
              );
            })}
          </div>

          <p className="kpi-status-text">
            Còn <strong>{remainingSessions} buổi</strong> để hoàn thành cấp chứng chỉ
          </p>
        </article>
      </section>

      {/* Main Two-Column Grid */}
      <section className="training-main-grid">
        {/* LEFT COLUMN: Lesson Plan & Drills (Col 7) */}
        <div className="training-col-left">
          <div className="training-surface-card">
            <div className="training-card-titlebar">
              <div className="titlebar-left">
                <div className="titlebar-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                  </svg>
                </div>
                <div>
                  <h2 className="titlebar-heading">Giáo án &amp; Kế hoạch bài tập của Lớp</h2>
                  <p className="titlebar-sub">
                    {plan?.description ||
                      `Giáo trình huấn luyện kỹ thuật nâng cao · ${courseClass.sportName}`}
                  </p>
                </div>
              </div>
              <span className="badge-approved">
                <span className="status-dot-emerald"></span>
                {plan ? "Đã phê duyệt" : "Giáo án chuẩn"}
              </span>
            </div>

            {/* Course Goal Callout */}
            <div className="training-goal-banner">
              <div className="goal-icon-badge">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <circle cx="12" cy="12" r="6" />
                  <circle cx="12" cy="12" r="2" />
                </svg>
              </div>
              <div className="goal-content">
                <h4>Mục tiêu khóa học hiện tại</h4>
                <p>
                  {plan?.goal ||
                    "Hoàn thiện kỹ thuật đập cầu smash uy lực và bước di chuyển bộ đôi cuối sân chính xác."}
                </p>
              </div>
            </div>

            {/* Exercise Drills List */}
            <div className="training-exercises-container">
              <div className="exercise-list-header">
                <span>Nội dung bài tập kỹ thuật</span>
                <small>{exercises.length} bài tập</small>
              </div>

              <div className="exercise-card-stack">
                {exercises.map((exercise, index) => {
                  const isChecked = completedDrills.has(index);
                  const isHighlight = index === 1; // emphasize core drill like Jump smash
                  return (
                    <div
                      key={exercise.id || index}
                      className={`drill-card-item ${isHighlight ? "drill-highlight" : ""} ${
                        isChecked ? "drill-checked" : ""
                      }`}
                    >
                      <div className="drill-card-left">
                        <button
                          type="button"
                          className={`drill-check-btn ${isChecked ? "checked" : ""}`}
                          onClick={() => onToggleDrill(index)}
                          title="Đánh dấu hoàn thành"
                        >
                          {isChecked ? (
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3">
                              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                            </svg>
                          ) : (
                            <span>{index + 1}</span>
                          )}
                        </button>
                        <div className="drill-info">
                          <h4 className="drill-title">{exercise.name}</h4>
                          {exercise.notes && <p className="drill-notes">{exercise.notes}</p>}
                        </div>
                      </div>

                      <div className="drill-tag-badge">
                        {[
                          exercise.sets && `${exercise.sets} hiệp`,
                          exercise.reps && `${exercise.reps} lần/hiệp`,
                          exercise.durationMinutes && `${exercise.durationMinutes} phút`,
                        ]
                          .filter(Boolean)
                          .join(" · ") || "Theo nhịp còi HLV"}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Coach Technical Guidance Callout */}
            <div className="training-coach-advice-box">
              <div className="advice-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
              </div>
              <div className="advice-text">
                <strong>Lưu ý chuyên môn từ HLV:</strong> Chú ý giữ trọng tâm thấp khi di chuyển lùi; đập cầu tiếp xúc điểm cao nhất phía trước mặt và hạn chế vặn cổ tay quá sớm để tránh chấn thương khớp vai.
              </div>
            </div>
          </div>
        </div>

        {/* RIGHT COLUMN: Session History & Coach Feedback (Col 5) */}
        <div className="training-col-right">
          {/* Session Diary & Scores */}
          <div className="training-surface-card">
            <div className="training-card-titlebar compact">
              <div className="titlebar-left">
                <div className="titlebar-icon text-blue">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                </div>
                <h3 className="titlebar-heading">Nhật ký buổi học &amp; Điểm số</h3>
              </div>
              <span className="text-muted-xs">Gần nhất</span>
            </div>

            <div className="training-timeline-list">
              {results.length === 0 ? (
                <p className="dash-empty">Chưa có kết quả buổi học ghi nhận.</p>
              ) : (
                results.slice(0, 4).map((result, idx) => (
                  <div key={result.id || idx} className="timeline-entry-row">
                    <span className="timeline-node-dot"></span>
                    <div className="timeline-content-wrap">
                      <div className="timeline-meta-row">
                        <span className="timeline-session-name">
                          Buổi {results.length - idx}{" "}
                          <small className="timeline-date">· {formatDateOnly(result.sessionStartTime)}</small>
                        </span>
                        <span className="timeline-score-badge">
                          <strong>{result.score}</strong> / 10
                        </span>
                      </div>
                      <p className="timeline-comment-bubble">
                        "{result.notes || "Thực hiện đúng form và yêu cầu của HLV."}"
                      </p>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Coach Profile & Consultation CTA Card */}
          <div className="training-coach-profile-card">
            <div className="coach-profile-header">
              <div className="coach-avatar-initials">
                {courseClass.coachName ? courseClass.coachName.slice(0, 2).toUpperCase() : "HN"}
              </div>
              <div className="coach-profile-meta">
                <div className="coach-name-row">
                  <h4>HLV {courseClass.coachName || "Jordan Lee / Hoàng Nam"}</h4>
                  <div className="coach-stars-mini">★★★★★</div>
                </div>
                <p className="coach-title">
                  Trưởng ban Huấn luyện {courseClass.sportName}
                </p>
              </div>
            </div>

            <div className="coach-quote-box">
              <p>
                {latestEvaluation?.comment ||
                  "“Hội viên có tinh thần tập luyện nghiêm túc, thể lực hiệp 3 cải thiện vượt bậc, đủ điều kiện thi nâng bậc kỹ năng vào cuối tháng.”"}
              </p>
            </div>

            <button
              type="button"
              className="btn-coach-consultation"
              onClick={() => onNavigate && onNavigate("member-pt-booking")}
            >
              <span>Đăng ký buổi tư vấn kỹ thuật với Coach</span>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M14 5l7 7m0 0l-7 7m7-7H3" />
              </svg>
            </button>
          </div>
        </div>
      </section>

      {/* Supplemental 1-1 Training & Nutrition Section */}
      <section className="training-supplement-section" aria-label="Kế hoạch bổ trợ cá nhân">
        <div className="supplement-header-row">
          <div className="supplement-title-group">
            <div className="supplement-icon-purple">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
            </div>
            <div>
              <h3>Kế hoạch tập luyện bổ trợ cá nhân (1-1 Personal Training &amp; Dinh dưỡng)</h3>
              <p>Phác đồ cá nhân hóa nâng cao thể lực và sức bền chuyên biệt cho bộ môn {courseClass.sportName}</p>
            </div>
          </div>
          <span className="badge-purple">
            <span className="status-dot-purple"></span>
            Đang thực hiện
          </span>
        </div>

        <div className="supplement-cards-grid">
          {/* Card 1 */}
          <div className="supplement-item-card card-purple">
            <div className="supplement-card-top">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16m-7 6h7" />
              </svg>
              <h5>1. BÀI TẬP THỂ LỰC BỔ TRỢ</h5>
            </div>
            <p>
              Tăng cường sức mạnh cổ tay &amp; bật nhảy plyometrics (2 buổi/tuần tại khu Gym Sports Center).
            </p>
            <div className="supplement-card-footer text-purple">
              <span>● HLV Thể lực: Alex Đỗ</span>
            </div>
          </div>

          {/* Card 2 */}
          <div className="supplement-item-card card-emerald">
            <div className="supplement-card-top">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
              </svg>
              <h5>2. CHẾ ĐỘ DINH DƯỠNG GỢI Ý</h5>
            </div>
            <p>
              Bổ sung điện giải và chuối trước buổi tập 30 phút, tối thiểu 2.5L nước/ngày và bổ sung Whey Protein phục hồi cơ.
            </p>
            <div className="supplement-card-footer text-emerald">
              <span>● Đạt 85% chỉ tiêu tuần</span>
            </div>
          </div>

          {/* Card 3 */}
          <div className="supplement-item-card card-blue">
            <div className="supplement-card-top">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
              </svg>
              <h5>3. LỊCH HẸN TIẾP THEO</h5>
            </div>
            <p>
              <strong>Buổi {completedSessions + 1}:</strong> Thi thử nghiệm kỹ năng đấu tập &amp; kiểm tra độ chính xác cú smash.
            </p>
            <div className="supplement-card-footer text-blue">
              <span>● 17:30 Thứ Ba, 20/10/2026 (Sân 3)</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
