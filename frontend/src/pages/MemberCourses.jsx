import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import "./MemberCourses.css";
import { getPublicSportsApi, getPublicCoursesBySportApi } from "../api";

const list = (d) => (Array.isArray(d) ? d : d?.content ?? []);

export default function MemberCourses({ role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]);
  const [sportId, setSportId] = useState(null);
  const [courses, setCourses] = useState([]);

  const [loadingSports, setLoadingSports] = useState(true);
  const [loadingCourses, setLoadingCourses] = useState(false);
  const [error, setError] = useState("");

  const selectedSport = sports.find((sport) => sport.id === sportId) ?? null;

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
        <div className="member-courses-shell">
          <header className="member-courses-hero">
            <div className="member-courses-hero-copy">
              <span className="page-eyebrow">SPORTS CENTER</span>
              <h1>Khám phá khóa học</h1>
              <p>
                Khám phá các chương trình đào tạo theo bộ môn và tìm khóa học phù hợp với mục tiêu của bạn.
              </p>

            </div>

            <div className="member-courses-hero-summary" aria-label="Bộ môn đang chọn">
              <span className="hero-summary-label">Đang xem</span>
              <strong>{selectedSport?.name || "Khóa học thể thao"}</strong>
              <small>
                {loadingCourses
                  ? "Đang cập nhật danh sách…"
                  : `${courses.length} khóa học hiện có`}
              </small>
            </div>
          </header>

          {error && (
            <div className="banner error member-courses-error" role="alert">
              {error}
            </div>
          )}

          <section className="member-course-filter-card" aria-labelledby="sport-filter-title">
            <div className="member-course-section-head">
              <div>
                <h2 id="sport-filter-title">Bộ môn</h2>
                <p>Khám phá khóa học theo môn thể thao bạn quan tâm.</p>
              </div>

              {!loadingSports && sports.length > 0 && (
                <span className="member-course-total">
                  {sports.length} bộ môn
                </span>
              )}
            </div>

            {loadingSports ? (
              <div className="member-course-inline-loading">
                <span className="member-course-spinner" />
                Đang tải bộ môn…
              </div>
            ) : sports.length === 0 ? (
              <div className="member-course-empty compact">
                Chưa có bộ môn công khai.
              </div>
            ) : (
              <div
                className="member-sport-tabs"
                role="tablist"
                aria-label="Bộ môn"
              >
                {sports.map((sport) => {
                  const active = sport.id === sportId;

                  return (
                    <button
                      key={sport.id}
                      type="button"
                      role="tab"
                      aria-selected={active}
                      className={`member-sport-tab ${active ? "is-active" : ""}`}
                      onClick={() => setSportId(sport.id)}
                    >
                      <span className="member-sport-tab-dot" aria-hidden="true" />
                      <span>{sport.name}</span>
                    </button>
                  );
                })}
              </div>
            )}
          </section>

          <section
            className="member-course-list-card"
            aria-labelledby="member-course-list-title"
          >
            <div className="member-course-section-head list-head">
              <div>
                <h2 id="member-course-list-title">Danh sách khóa học</h2>
                <p>
                  {selectedSport
                    ? `Các chương trình hiện có thuộc bộ môn ${selectedSport.name}.`
                    : "Khám phá các khóa học hiện có."}
                </p>
              </div>

              {!loadingCourses && courses.length > 0 && (
                <span className="member-course-count">
                  {courses.length} khóa học
                </span>
              )}
            </div>

            {loadingCourses ? (
              <div className="member-course-loading-panel">
                <span className="member-course-spinner large" />
                <strong>Đang tải khóa học</strong>
                <small>Danh sách sẽ xuất hiện ngay khi dữ liệu sẵn sàng.</small>
              </div>
            ) : !loadingSports &&
              sportId != null &&
              courses.length === 0 ? (
              <div className="member-course-empty">
                <div className="member-course-empty-icon" aria-hidden="true">
                  ○
                </div>
                <h3>Chưa có khóa học</h3>
                <p>Bộ môn này hiện chưa có khóa học để đăng ký.</p>
              </div>
            ) : (
              <div className="member-course-grid">
                {courses.map((course, index) => (
                  <article className="member-course-card" key={course.id}>
                    <div className="member-course-card-accent" aria-hidden="true" />

                    <div className="member-course-card-top">
                      <span className="member-course-badge">Khóa học</span>
                      <span className="member-course-number" aria-hidden="true">
                        {String(index + 1).padStart(2, "0")}
                      </span>
                    </div>

                    <div className="member-course-card-body">
                      <h3>{course.name}</h3>
                      <p>
                        {course.description ||
                          "Khám phá khóa học và lựa chọn lớp phù hợp với bạn."}
                      </p>
                    </div>

                    <div className="member-course-card-footer">
                      <Link
                        className="member-course-detail-btn"
                        to={`/member/courses/${course.id}`}
                      >
                        <span>
                          <small>Tìm hiểu thêm</small>
                          <strong>Xem chi tiết khóa học</strong>
                        </span>
                        <span className="member-course-arrow" aria-hidden="true">
                          →
                        </span>
                      </Link>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}