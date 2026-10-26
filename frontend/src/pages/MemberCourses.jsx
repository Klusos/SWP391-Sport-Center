import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import { getPublicSportsApi, getPublicCoursesBySportApi } from "../api";

const list = (d) => (Array.isArray(d) ? d : d?.content ?? []);

export default function MemberCourses({ role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]);
  const [sportId, setSportId] = useState(null);
  const [courses, setCourses] = useState([]);

  const [loadingSports, setLoadingSports] = useState(true);
  const [loadingCourses, setLoadingCourses] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    getPublicSportsApi()
      .then((data) => {
        const rows = list(data);

        if (active) {
          setSports(rows);
          setSportId(rows[0]?.id ?? null);
        }
      })
      .catch((e) => {
        if (active) {
          setError(e.message || "Không tải được bộ môn.");
        }
      })
      .finally(() => {
        if (active) setLoadingSports(false);
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (sportId == null) return;

    let active = true;

    setLoadingCourses(true);
    setError("");

    getPublicCoursesBySportApi(sportId)
      .then((data) => {
        if (active) {
          setCourses(list(data));
        }
      })
      .catch((e) => {
        if (active) {
          setCourses([]);
          setError(e.message || "Không tải được khóa học.");
        }
      })
      .finally(() => {
        if (active) setLoadingCourses(false);
      });

    return () => {
      active = false;
    };
  }, [sportId]);

  return (
    <div className="layout">
      <Sidebar
        role={role}
        activePage="member-courses"
        onLogout={onLogout}
        onNavigate={onNavigate}
      />

      <main className="main member-courses-page">
        <div className="courses-header">
          <div>
            <span className="page-eyebrow">SPORTS CENTER</span>
            <h1>Khóa học</h1>
            <p className="subtitle">
              Khám phá các khóa học phù hợp và đăng ký lớp bạn yêu thích.
            </p>
          </div>
        </div>

        {error && (
          <div className="banner error" role="alert">
            {error}
          </div>
        )}

        <section className="courses-section">
          <div className="section-heading">
            <div>
              <h2>Chọn bộ môn</h2>
              <p>Lựa chọn môn thể thao bạn muốn tham gia.</p>
            </div>
          </div>

          {loadingSports ? (
            <div className="queue-state">Đang tải bộ môn…</div>
          ) : sports.length === 0 ? (
            <div className="courses-empty">
              Chưa có bộ môn công khai.
            </div>
          ) : (
            <div
              className="sport-tabs"
              role="tablist"
              aria-label="Bộ môn"
            >
              {sports.map((sport) => (
                <button
                  key={sport.id}
                  type="button"
                  role="tab"
                  aria-selected={sport.id === sportId}
                  className={`sport-tab ${
                    sport.id === sportId ? "active" : ""
                  }`}
                  onClick={() => setSportId(sport.id)}
                >
                  {sport.name}
                </button>
              ))}
            </div>
          )}
        </section>

        <section className="courses-section course-list-section">
          <div className="section-heading">
            <div>
              <h2>Khóa học dành cho bạn</h2>
              <p>Xem thông tin chi tiết trước khi đăng ký.</p>
            </div>

            {!loadingCourses && courses.length > 0 && (
              <span className="course-count">
                {courses.length} khóa học
              </span>
            )}
          </div>

          {loadingCourses ? (
            <div className="courses-loading">
              <div className="loading-spinner" />
              <span>Đang tải khóa học…</span>
            </div>
          ) : !loadingSports &&
            sportId != null &&
            courses.length === 0 ? (
            <div className="courses-empty">
              <div className="empty-icon">🏸</div>
              <h3>Chưa có khóa học</h3>
              <p>Bộ môn này hiện chưa có khóa học để đăng ký.</p>
            </div>
          ) : (
            <div className="course-grid">
              {courses.map((course) => (
                <article className="course-card" key={course.id}>
                  <div className="course-card-top">
                    <span className="course-badge">Khóa học</span>
                  </div>

                  <div className="course-card-body">
                    <h3>{course.name}</h3>

                    <p>
                      {course.description ||
                        "Khám phá khóa học và lựa chọn lớp phù hợp với bạn."}
                    </p>
                  </div>

                  <div className="course-card-footer">
                    <Link
                      className="course-detail-btn"
                      to={`/member/courses/${course.id}`}
                    >
                      <span>Xem chi tiết</span>
                      <span aria-hidden="true">→</span>
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}