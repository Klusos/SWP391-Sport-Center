import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import Img from "../components/Img.jsx";
import {
  getPublicCoursesBySportApi,
  getPublicSportsApi,
} from "../../api.js";

function getSportIcon(name = "") {
  const value = name.toLowerCase();

  if (value.includes("badminton")) return "🏸";
  if (value.includes("basketball")) return "🏀";
  if (value.includes("swim")) return "🏊";
  if (value.includes("tennis")) return "🎾";
  if (value.includes("yoga")) return "🧘";
  if (value.includes("football")) return "⚽";

  return "🏆";
}

export default function Disciplines() {
  const { sportId } = useParams();

  const [sports, setSports] = useState([]);
  const [courses, setCourses] = useState([]);

  const [sportLoading, setSportLoading] = useState(true);
  const [courseLoading, setCourseLoading] = useState(false);

  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    getPublicSportsApi()
      .then((rows) => {
        if (active) {
          setSports(Array.isArray(rows) ? rows : []);
        }
      })
      .catch((err) => {
        if (active) {
          setError(
            err.message ||
              "Không tải được danh sách bộ môn."
          );
        }
      })
      .finally(() => {
        if (active) {
          setSportLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!sportId) {
      setCourses([]);
      return undefined;
    }

    let active = true;

    Promise.resolve()
      .then(() => {
        if (active) {
          setCourseLoading(true);
          setError("");
        }

        return getPublicCoursesBySportApi(sportId);
      })
      .then((rows) => {
        if (active) {
          setCourses(
            Array.isArray(rows) ? rows : []
          );
        }
      })
      .catch((err) => {
        if (active) {
          setCourses([]);

          setError(
            err.message ||
              "Không tải được khóa học."
          );
        }
      })
      .finally(() => {
        if (active) {
          setCourseLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [sportId]);

  const selectedSport = sports.find(
    (sport) =>
      String(sport.id) === String(sportId)
  );

  /*
   * =====================================================
   * SPORT DETAIL / COURSE LIST
   * =====================================================
   */
  if (sportId) {
    return (
      <main className="public-discipline-page">
        {/* HERO */}

        <section className="discipline-detail-hero">
          <div className="container-xl discipline-detail-hero-inner">
            <div className="discipline-detail-content">
              <Link
                to="/bo-mon"
                className="discipline-back-link"
              >
                <span>←</span>
                Tất cả bộ môn
              </Link>

              <span className="discipline-eyebrow">
                SPORTS CENTER
              </span>

              <h1>
                {selectedSport?.name ||
                  "Bộ môn"}
              </h1>

              <p>
                Khám phá các khóa học được
                thiết kế phù hợp với trình độ
                và mục tiêu luyện tập của bạn.
              </p>

              <div className="discipline-hero-stats">
                <div>
                  <strong>
                    {courses.length}
                  </strong>
                  <span>Khóa học</span>
                </div>

                <div>
                  <strong>01</strong>
                  <span>Bộ môn</span>
                </div>
              </div>
            </div>

            <div className="discipline-detail-visual">
              {selectedSport?.imageUrl ? (
                <Img
                  src={selectedSport.imageUrl}
                  alt={selectedSport.name}
                  label={selectedSport.name}
                  className="discipline-hero-image"
                />
              ) : (
                <div className="discipline-hero-fallback">
                  {getSportIcon(
                    selectedSport?.name
                  )}
                </div>
              )}

              <div className="discipline-floating-label">
                <span>●</span>
                Đang hoạt động
              </div>
            </div>
          </div>
        </section>

        {/* COURSES */}

        <section className="public-courses-section">
          <div className="container-xl">
            <div className="public-section-heading">
              <div>
                <span className="public-section-kicker">
                  CHƯƠNG TRÌNH ĐÀO TẠO
                </span>

                <h2>
                  Khóa học{" "}
                  {selectedSport?.name || ""}
                </h2>

                <p>
                  Lựa chọn chương trình phù hợp
                  với độ tuổi và trình độ của bạn.
                </p>
              </div>

              {!courseLoading &&
                courses.length > 0 && (
                  <div className="public-course-count">
                    {courses.length}
                    <span> khóa học</span>
                  </div>
                )}
            </div>

            {error && (
              <div
                className="public-state public-state-error"
                role="alert"
              >
                {error}
              </div>
            )}

            {courseLoading ? (
              <div className="public-state">
                <div className="public-loader" />
                <p>Đang tải khóa học...</p>
              </div>
            ) : courses.length === 0 ? (
              <div className="public-empty-state">
                <div className="public-empty-icon">
                  🎓
                </div>

                <h3>
                  Chưa có khóa học
                </h3>

                <p>
                  Bộ môn này hiện chưa có khóa
                  học đang hoạt động.
                </p>
              </div>
            ) : (
              <div className="public-course-grid">
                {courses.map(
                  (course, index) => (
                    <article
                      className="public-course-card"
                      key={course.id}
                    >
                      <div className="public-course-card-top">
                        <span className="public-course-number">
                          {String(
                            index + 1
                          ).padStart(2, "0")}
                        </span>

                        <span className="public-course-level">
                          {course.level ||
                            "COURSE"}
                        </span>
                      </div>

                      <div className="public-course-card-body">
                        <div className="public-course-sport">
                          {getSportIcon(
                            selectedSport?.name
                          )}

                          <span>
                            {selectedSport?.name ||
                              course.sportName ||
                              "Sports Center"}
                          </span>
                        </div>

                        <h3>{course.name}</h3>

                        <p>
                          {course.description ||
                            course.learningOutcome ||
                            "Thông tin chi tiết về chương trình đào tạo."}
                        </p>

                        {(course.targetAgeMin !=
                          null ||
                          course.targetAgeMax !=
                            null) && (
                          <div className="public-course-meta">
                            <span>
                              Độ tuổi
                            </span>

                            <strong>
                              {course.targetAgeMin ??
                                "Không giới hạn"}

                              {" – "}

                              {course.targetAgeMax ??
                                "Không giới hạn"}
                            </strong>
                          </div>
                        )}
                      </div>

                      <div className="public-course-card-footer">
                        <Link
                          to={`/khoa-hoc/${course.id}`}
                          className="public-course-link"
                        >
                          Xem chi tiết
                          <span>→</span>
                        </Link>
                      </div>
                    </article>
                  )
                )}
              </div>
            )}
          </div>
        </section>
      </main>
    );
  }

  /*
   * =====================================================
   * ALL SPORTS
   * =====================================================
   */
  return (
    <main className="public-discipline-page">
      {/* HERO */}

      <section className="disciplines-main-hero">
        <div className="container-xl disciplines-main-hero-inner">
          <div className="disciplines-main-copy">
            <span className="discipline-eyebrow">
              SPORTS CENTER
            </span>

            <h1>
              Tìm bộ môn
              <br />
              <span>phù hợp với bạn.</span>
            </h1>

            <p>
              Từ những bước đầu tiên đến mục
              tiêu nâng cao, Sports Center mang
              đến các chương trình luyện tập
              dành cho mọi trình độ.
            </p>

            <a
              href="#danh-sach-bo-mon"
              className="discipline-explore-button"
            >
              Khám phá bộ môn
              <span>↓</span>
            </a>
          </div>

          <div className="disciplines-main-decoration">
            <div className="sport-orbit sport-orbit-one">
              🏸
            </div>

            <div className="sport-orbit sport-orbit-two">
              🏀
            </div>

            <div className="sport-orbit sport-orbit-three">
              🎾
            </div>

            <div className="sport-orbit sport-orbit-main">
              🏆
            </div>
          </div>
        </div>
      </section>

      {/* SPORT LIST */}

      <section
        className="public-sports-section"
        id="danh-sach-bo-mon"
      >
        <div className="container-xl">
          <div className="public-section-heading">
            <div>
              <span className="public-section-kicker">
                DANH MỤC THỂ THAO
              </span>

              <h2>
                Khám phá các bộ môn
              </h2>

              <p>
                Chọn một bộ môn để xem các
                chương trình và khóa học hiện có.
              </p>
            </div>

            {!sportLoading &&
              sports.length > 0 && (
                <div className="public-course-count">
                  {sports.length}
                  <span> bộ môn</span>
                </div>
              )}
          </div>

          {error && (
            <div
              className="public-state public-state-error"
              role="alert"
            >
              {error}
            </div>
          )}

          {sportLoading ? (
            <div className="public-state">
              <div className="public-loader" />
              <p>Đang tải bộ môn...</p>
            </div>
          ) : sports.length === 0 ? (
            <div className="public-empty-state">
              <div className="public-empty-icon">
                🏆
              </div>

              <h3>
                Chưa có bộ môn
              </h3>

              <p>
                Hiện chưa có bộ môn nào đang
                hoạt động.
              </p>
            </div>
          ) : (
            <div className="public-sports-grid">
              {sports.map(
                (sport, index) => (
                  <Link
                    key={sport.id}
                    to={`/bo-mon/${sport.id}`}
                    className="public-sport-card"
                  >
                    <div className="public-sport-image-wrap">
                      <Img
                        src={sport.imageUrl}
                        alt={sport.name}
                        label={sport.name}
                        className="public-sport-image"
                      />

                      <div className="public-sport-overlay" />

                      <span className="public-sport-index">
                        {String(
                          index + 1
                        ).padStart(2, "0")}
                      </span>

                      <div className="public-sport-icon">
                        {getSportIcon(
                          sport.name
                        )}
                      </div>
                    </div>

                    <div className="public-sport-card-body">
                      <span className="public-sport-small-label">
                        BỘ MÔN
                      </span>

                      <h3>
                        {sport.name}
                      </h3>

                      <div className="public-sport-card-action">
                        <span>
                          Xem khóa học
                        </span>

                        <span className="public-sport-arrow">
                          →
                        </span>
                      </div>
                    </div>
                  </Link>
                )
              )}
            </div>
          )}
        </div>
      </section>
    </main>
  );
}