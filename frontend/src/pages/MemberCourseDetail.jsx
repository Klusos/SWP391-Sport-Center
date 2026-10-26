import { useEffect, useMemo, useState } from "react";
import {
  useLocation,
  useNavigate,
  useParams,
} from "react-router-dom";

import Sidebar from "../components/Sidebar";
import "./Portal.css";

import {
  createCourseRegistrationApi,
  createMemberConsultationRequestApi,
  getClassesByOfferingApi,
  getCurrentUserApi,
  getMyMemberSportLevelsApi,
  getCourseOfferingsByCourseApi,
  getPublicCourseApi,
} from "../api";

const INSUFFICIENT_LEVEL_MESSAGE =
  "Verified sport level is insufficient. Request a consultation and COACH assessment before registering.";

const BUSINESS_ZONE = "Asia/Ho_Chi_Minh";

/*
 * Offering được hiển thị khi:
 * 1. status = OPEN
 * 2. chưa hết hạn đăng ký
 *
 * Không còn giới hạn "chỉ mở 30 ngày trước startDate".
 */
function isOfferingOpenNow(offering) {
  if (
    String(offering.status).toUpperCase() !== "OPEN"
  ) {
    return false;
  }

  /*
   * Nếu backend không trả deadline
   * thì vẫn cho Member thấy lớp.
   */
  if (!offering.registrationDeadline) {
    return true;
  }

  /*
   * Backend dùng Asia/Ho_Chi_Minh.
   */
  const deadline = new Date(
    `${offering.registrationDeadline}+07:00`
  );

  /*
   * Nếu backend trả format deadline không hợp lệ
   * thì không nên vô tình ẩn class.
   */
  if (Number.isNaN(deadline.getTime())) {
    return true;
  }

  /*
   * Chỉ ẩn offering khi đã hết deadline.
   */
  return Date.now() < deadline.getTime();
}

function formatDate(value) {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString("vi-VN", {
    timeZone: BUSINESS_ZONE,
  });
}

function formatSimpleDate(value) {
  if (!value) return "—";

  const date = new Date(
    `${value}T00:00:00`
  );

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleDateString("vi-VN");
}

function formatPrice(value) {
  if (
    value == null ||
    Number.isNaN(Number(value))
  ) {
    return "—";
  }

  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
  }).format(Number(value));
}

function currentAge(dateOfBirth) {
  if (!dateOfBirth) return "";

  const date = new Date(
    `${dateOfBirth}T00:00:00`
  );

  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const today = new Date();

  let age =
    today.getFullYear() -
    date.getFullYear();

  if (
    today.getMonth() < date.getMonth() ||
    (today.getMonth() ===
      date.getMonth() &&
      today.getDate() < date.getDate())
  ) {
    age -= 1;
  }

  return age > 0 && age <= 120
    ? String(age)
    : "";
}

function isInsufficientLevel(error) {
  return (
    error?.status === 409 &&
    error.message ===
      INSUFFICIENT_LEVEL_MESSAGE
  );
}

export default function MemberCourseDetail({
  token,
  role,
  onLogout,
  onNavigate,
}) {
  const { courseId } = useParams();

  const navigate = useNavigate();
  const location = useLocation();

  /*
   * COURSE
   */
  const [course, setCourse] =
    useState(null);

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState("");

  /*
   * CLASSES
   */
  const [classes, setClasses] =
    useState([]);

  const [
    classesLoading,
    setClassesLoading,
  ] = useState(false);

  const [
    classesError,
    setClassesError,
  ] = useState("");

  /*
   * MEMBER LEVEL
   */
  const [levels, setLevels] =
    useState(null);

  const [levelsError, setLevelsError] =
    useState("");

  /*
   * PROFILE
   */
  const [profile, setProfile] =
    useState(null);

  const [
    profileError,
    setProfileError,
  ] = useState("");

  /*
   * SELECT CLASS
   */
  const [
    selectedClassId,
    setSelectedClassId,
  ] = useState("");

  /*
   * REGISTRATION
   */
  const [
    registering,
    setRegistering,
  ] = useState(false);

  const [
    registrationError,
    setRegistrationError,
  ] = useState("");

  const [
    registration,
    setRegistration,
  ] = useState(null);

  /*
   * CONSULTATION
   */
  const [
    consultationAvailable,
    setConsultationAvailable,
  ] = useState(false);

  const [
    consultationOpen,
    setConsultationOpen,
  ] = useState(false);

  const [
    consultationSubmitting,
    setConsultationSubmitting,
  ] = useState(false);

  const [
    consultationError,
    setConsultationError,
  ] = useState("");

  const [
    consultationSuccess,
    setConsultationSuccess,
  ] = useState(null);

  const [
    sessionExpired,
    setSessionExpired,
  ] = useState(false);

  const [
    consultation,
    setConsultation,
  ] = useState({
    name: "",
    phone: "",
    email: "",
    age: "",
    experience: "",
    note: "",
  });

  /*
   * MEMBER
   */
  const isMember =
    role === "MEMBER" &&
    Boolean(token);

  /*
   * LEVEL CỦA MEMBER TRONG BỘ MÔN
   */
  const level = useMemo(
    () =>
      levels?.find(
        (item) =>
          Number(item.sportId) ===
          Number(course?.sportId)
      ) || null,
    [levels, course?.sportId]
  );

  const hasVerifiedLevel = Boolean(
    level?.verifiedByCoachId != null &&
      level?.verifiedAt
  );

  const currentLevel =
    hasVerifiedLevel
      ? level.level
      : "BEGINNER";

  /*
   * =====================================================
   * LOAD COURSE
   * =====================================================
   */
  useEffect(() => {
    let active = true;

    Promise.resolve()
      .then(() => {
        if (active) {
          setLoading(true);
          setError("");
          setCourse(null);

          setRegistration(null);
          setSelectedClassId("");
          setRegistrationError("");

          setConsultationAvailable(false);
          setConsultationOpen(false);
          setConsultationSuccess(null);
        }

        return getPublicCourseApi(
          courseId
        );
      })
      .then((data) => {
        if (active) {
          setCourse(data);
        }
      })
      .catch((requestError) => {
        if (active) {
          setError(
            requestError.message ||
              "Không tải được thông tin khóa học."
          );
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [courseId]);

  /*
   * =====================================================
   * LOAD OFFERINGS
   * LOAD CLASSES
   * LOAD MEMBER LEVEL
   * LOAD PROFILE
   * =====================================================
   */
  useEffect(() => {
    if (!isMember || !course) {
      return undefined;
    }

    let active = true;

    Promise.resolve()
      .then(() => {
        if (!active) {
          return null;
        }

        setClassesLoading(true);

        setClassesError("");
        setLevelsError("");
        setProfileError("");

        setSessionExpired(false);

        return Promise.allSettled([
          getCourseOfferingsByCourseApi(
            token,
            course.id
          ),

          getMyMemberSportLevelsApi(
            token
          ),

          getCurrentUserApi(token),
        ]);
      })
      .then(async (results) => {
        if (!results) {
          return;
        }

        const [
          offeringsResult,
          levelsResult,
          profileResult,
        ] = results;

        if (!active) {
          return;
        }

        /*
         * =========================
         * MEMBER LEVEL
         * =========================
         */
        if (
          levelsResult.status ===
            "fulfilled" &&
          Array.isArray(
            levelsResult.value
          )
        ) {
          setLevels(
            levelsResult.value
          );
        } else {
          setLevels(null);

          setLevelsError(
            levelsResult.reason
              ?.message ||
              "Không tải được trình độ hiện tại."
          );

          if (
            levelsResult.reason
              ?.status === 401
          ) {
            setSessionExpired(true);
          }
        }

        /*
         * =========================
         * PROFILE
         * =========================
         */
        if (
          profileResult.status ===
          "fulfilled"
        ) {
          setProfile(
            profileResult.value
          );
        } else {
          setProfile(null);

          setProfileError(
            profileResult.reason
              ?.message ||
              "Không tải được thông tin hồ sơ."
          );

          if (
            profileResult.reason
              ?.status === 401
          ) {
            setSessionExpired(true);
          }
        }

        /*
         * =========================
         * OFFERINGS
         * =========================
         */
        if (
          offeringsResult.status !==
            "fulfilled" ||
          !Array.isArray(
            offeringsResult.value
          )
        ) {
          setClasses([]);

          setClassesError(
            offeringsResult.reason
              ?.message ||
              "Không tải được các đợt mở lớp."
          );

          if (
            offeringsResult.reason
              ?.status === 401
          ) {
            setSessionExpired(true);
          }

          setClassesLoading(false);

          return;
        }

        /*
         * Chỉ lấy offering:
         *
         * status = OPEN
         * và chưa hết hạn đăng ký.
         *
         * KHÔNG còn kiểm tra
         * "30 ngày trước ngày bắt đầu".
         */
        const eligibleOfferings =
          offeringsResult.value.filter(
            isOfferingOpenNow
          );

        /*
         * Load class của từng offering.
         */
        const classResults =
          await Promise.allSettled(
            eligibleOfferings.map(
              async (offering) => ({
                offering,

                classes:
                  await getClassesByOfferingApi(
                    token,
                    offering.id
                  ),
              })
            )
          );

        if (!active) {
          return;
        }

        const nextClasses = [];

        /*
         * Kiểm tra có API class nào fail không.
         */
        const failed =
          classResults.some(
            (result) =>
              result.status ===
              "rejected"
          );

        /*
         * Nếu session hết hạn.
         */
        if (
          classResults.some(
            (result) =>
              result.status ===
                "rejected" &&
              result.reason?.status ===
                401
          )
        ) {
          setSessionExpired(true);
        }

        /*
         * =========================
         * FILTER CLASS
         * =========================
         */
        classResults.forEach(
          (result) => {
            if (
              result.status ===
                "fulfilled" &&
              Array.isArray(
                result.value.classes
              )
            ) {
              result.value.classes
                .filter(
                  (item) =>
                    /*
                     * Class phải ACTIVE.
                     */
                    String(
                      item.status
                    ).toUpperCase() ===
                      "ACTIVE" &&

                    /*
                     * Class phải thuộc
                     * course đang xem.
                     */
                    Number(
                      item.courseId
                    ) ===
                      Number(
                        course.id
                      ) &&

                    /*
                     * Class phải thuộc
                     * đúng offering.
                     */
                    Number(
                      item.courseOfferingId
                    ) ===
                      Number(
                        result.value
                          .offering.id
                      )
                )
                .forEach(
                  (item) => {
                    nextClasses.push({
                      ...item,

                      offering:
                        result.value
                          .offering,
                    });
                  }
                );
            }
          }
        );

        setClasses(nextClasses);

        if (failed) {
          setClassesError(
            "Một số lịch lớp không tải được. Hãy làm mới trang trước khi đăng ký."
          );
        }

        setClassesLoading(false);
      });

    return () => {
      active = false;
    };
  }, [token, course, isMember]);

  /*
   * =====================================================
   * REGISTER CLASS
   * =====================================================
   */
  async function registerForClass(
    event
  ) {
    event.preventDefault();

    if (
      !isMember ||
      !selectedClassId ||
      registering
    ) {
      return;
    }

    setRegistering(true);

    setRegistrationError("");

    setConsultationAvailable(false);

    setConsultationOpen(false);

    setConsultationSuccess(null);

    try {
      const result =
        await createCourseRegistrationApi(
          token,
          Number(selectedClassId)
        );

      setRegistration(result);

      setRegistrationError("");
    } catch (requestError) {
      /*
       * Trình độ không đủ.
       */
      if (
        isInsufficientLevel(
          requestError
        )
      ) {
        setConsultationAvailable(
          true
        );

        setRegistrationError(
          "Trình độ thể thao đã xác minh chưa đáp ứng yêu cầu khóa học."
        );
      }

      /*
       * Session hết hạn.
       */
      else if (
        requestError.status === 401
      ) {
        setRegistrationError(
          "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để tiếp tục."
        );

        setSessionExpired(true);
      }

      /*
       * Các lỗi khác.
       */
      else {
        setRegistrationError(
          requestError.message ||
            "Không thể đăng ký lớp đã chọn."
        );
      }
    } finally {
      setRegistering(false);
    }
  }

  /*
   * =====================================================
   * OPEN CONSULTATION
   * =====================================================
   */
  function openConsultation() {
    setConsultationError("");

    setConsultationSuccess(null);

    setConsultation(
      (current) => ({
        ...current,

        name:
          current.name ||
          profile?.name ||
          "",

        email:
          current.email ||
          profile?.email ||
          "",

        phone:
          current.phone ||
          profile?.phone ||
          "",

        age:
          current.age ||
          currentAge(
            profile?.dateOfBirth
          ),
      })
    );

    setConsultationOpen(true);
  }

  /*
   * =====================================================
   * SUBMIT CONSULTATION
   * =====================================================
   */
  async function submitConsultation(
    event
  ) {
    event.preventDefault();

    if (
      !isMember ||
      !course ||
      !selectedClassId ||
      consultationSubmitting
    ) {
      return;
    }

    const selectedClass =
      classes.find(
        (item) =>
          String(item.id) ===
          selectedClassId
      );

    if (!selectedClass) {
      setConsultationError(
        "Vui lòng chọn lại lớp trước khi gửi yêu cầu tư vấn."
      );

      return;
    }

    setConsultationSubmitting(true);

    setConsultationError("");

    const contextNote =
      `Yêu cầu tư vấn về trình độ để đăng ký khóa học: ` +
      `${course.name} (ID ${course.id}); ` +
      `lớp: ${selectedClass.name} (ID ${selectedClass.id}); ` +
      `đợt: ${selectedClass.offering.name} ` +
      `(ID ${selectedClass.offering.id}).`;

    const personalNote =
      consultation.note.trim();

    try {
      const result =
        await createMemberConsultationRequestApi(
          token,
          {
            name:
              consultation.name.trim(),

            phone:
              consultation.phone.trim(),

            email:
              consultation.email.trim() ||
              null,

            sportId:
              course.sportId,

            age: consultation.age
              ? Number(
                  consultation.age
                )
              : null,

            experience:
              consultation.experience.trim() ||
              null,

            note: personalNote
              ? `${contextNote}\n\nGhi chú của Member: ${personalNote}`
              : contextNote,
          }
        );

      setConsultationSuccess(
        result
      );

      setConsultationOpen(false);
    } catch (requestError) {
      setConsultationError(
        requestError.status === 401
          ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
          : requestError.message ||
              "Không gửi được yêu cầu tư vấn. Vui lòng thử lại."
      );

      if (
        requestError.status === 401
      ) {
        setSessionExpired(true);
      }
    } finally {
      setConsultationSubmitting(
        false
      );
    }
  }

  /*
   * =====================================================
   * LOGIN AGAIN
   * =====================================================
   */
  function loginAgain() {
    if (onLogout) {
      onLogout(
        location.pathname
      );
    } else {
      navigate("/login", {
        state: {
          from:
            location.pathname,
        },
      });
    }
  }

  /*
   * =====================================================
   * LOADING PAGE
   * =====================================================
   */
  if (loading) {
    return (
      <div className="layout">
        <Sidebar
          role={role}
          activePage="member-courses"
          onLogout={onLogout}
          onNavigate={onNavigate}
        />

        <main className="main member-course-detail-page">
          <div className="detail-loading">
            <div className="detail-spinner" />

            <span>
              Đang tải thông tin
              khóa học...
            </span>
          </div>
        </main>
      </div>
    );
  }

  /*
   * =====================================================
   * ERROR PAGE
   * =====================================================
   */
  if (error || !course) {
    return (
      <div className="layout">
        <Sidebar
          role={role}
          activePage="member-courses"
          onLogout={onLogout}
          onNavigate={onNavigate}
        />

        <main className="main member-course-detail-page">
          <button
            type="button"
            className="course-back-button"
            onClick={() =>
              navigate(
                "/member/courses"
              )
            }
          >
            ← Quay lại khóa học
          </button>

          <div className="course-detail-error">
            {error ||
              "Không tìm thấy khóa học."}
          </div>
        </main>
      </div>
    );
  }

  /*
   * =====================================================
   * MAIN
   * =====================================================
   */
  return (
    <div className="layout">
      <Sidebar
        role={role}
        activePage="member-courses"
        onLogout={onLogout}
        onNavigate={onNavigate}
      />

      <main className="main member-course-detail-page">
        {/* ========================
            BACK
        ======================== */}

        <button
          type="button"
          className="course-back-button"
          onClick={() =>
            navigate(
              "/member/courses"
            )
          }
        >
          <span className="back-arrow">
            ←
          </span>

          Quay lại khóa học
        </button>

        {/* ========================
            HERO
        ======================== */}

        <section className="member-course-hero">
          <div className="course-hero-content">
            <div className="course-hero-badges">
              <span className="detail-course-badge">
                {course.sportName ||
                  "SPORTS CENTER"}
              </span>

              {course.level && (
                <span className="detail-level-badge">
                  {course.level}
                </span>
              )}
            </div>

            <h1>
              {course.name}
            </h1>

            <p className="course-hero-description">
              {course.description ||
                "Chương trình đào tạo tại Sports Center được thiết kế phù hợp với trình độ và mục tiêu của học viên."}
            </p>

            <div className="course-information">
              {/* LEVEL */}

              {course.level && (
                <div className="course-info-item">
                  <div className="course-info-icon">
                    ★
                  </div>

                  <div>
                    <span>
                      Trình độ yêu cầu
                    </span>

                    <strong>
                      {course.level}
                    </strong>
                  </div>
                </div>
              )}

              {/* AGE */}

              {(course.targetAgeMin !=
                null ||
                course.targetAgeMax !=
                  null) && (
                <div className="course-info-item">
                  <div className="course-info-icon">
                    ♙
                  </div>

                  <div>
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
                </div>
              )}

              {/* OUTCOME */}

              {course.learningOutcome && (
                <div className="course-info-item">
                  <div className="course-info-icon">
                    ✓
                  </div>

                  <div>
                    <span>
                      Kết quả mong đợi
                    </span>

                    <strong>
                      Có chương trình đầu ra
                    </strong>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="course-hero-decoration">
            <div className="hero-decoration-circle">
              <span>
                🏸
              </span>
            </div>
          </div>
        </section>

        {/* ========================
            LEARNING OUTCOME
        ======================== */}

        {course.learningOutcome && (
          <section className="course-outcome-card">
            <div className="course-outcome-icon">
              ✓
            </div>

            <div>
              <span className="section-label">
                MỤC TIÊU KHÓA HỌC
              </span>

              <h3>
                Kết quả mong đợi
              </h3>

              <p>
                {
                  course.learningOutcome
                }
              </p>
            </div>
          </section>
        )}

        {/* ========================
            REGISTRATION
        ======================== */}

        <section className="course-registration-section">
          <div className="registration-heading">
            <div>
              <span className="section-label">
                ĐĂNG KÝ
              </span>

              <h2>
                Chọn lớp học
              </h2>

              <p>
                Lựa chọn một lớp đang
                hoạt động thuộc đợt mở
                đăng ký.
              </p>
            </div>

            {!classesLoading && (
              <div className="registration-count">
                {classes.length}

                <span>
                  {" "}
                  lớp đang mở
                </span>
              </div>
            )}
          </div>

          {/* LOADING CLASS */}

          {classesLoading ? (
            <div className="detail-loading class-loading">
              <div className="detail-spinner" />

              <span>
                Đang tải các lớp có
                thể đăng ký...
              </span>
            </div>
          ) : classesError ? (
            /*
             * ERROR CLASS
             */
            <div
              className="course-detail-error"
              role="alert"
            >
              {classesError}
            </div>
          ) : classes.length ===
            0 ? (
            /*
             * NO CLASS
             */
            <div className="no-open-classes">
              <div className="no-class-icon">
                ⌛
              </div>

              <h3>
                Chưa có lớp đang mở
                đăng ký
              </h3>

              <p>
                Hiện tại khóa học này
                chưa có lớp nào nằm
                trong đợt đăng ký.
              </p>

              <p className="no-class-hint">
                Vui lòng quay lại sau
                để kiểm tra các đợt mở
                lớp mới.
              </p>
            </div>
          ) : (
            /*
             * =======================
             * CLASS LIST
             * =======================
             */
            <form
              onSubmit={
                registerForClass
              }
              className="member-registration-form"
            >
              <div className="member-class-grid">
                {classes.map(
                  (item) => {
                    const selected =
                      selectedClassId ===
                      String(item.id);

                    return (
                      <label
                        key={item.id}
                        className={`member-class-card ${
                          selected
                            ? "selected"
                            : ""
                        }`}
                      >
                        <input
                          className="class-radio-input"
                          type="radio"
                          name="courseClass"
                          value={
                            item.id
                          }
                          checked={
                            selected
                          }
                          disabled={Boolean(
                            registration
                          )}
                          onChange={() => {
                            setSelectedClassId(
                              String(
                                item.id
                              )
                            );

                            setRegistrationError(
                              ""
                            );

                            setConsultationAvailable(
                              false
                            );

                            setConsultationOpen(
                              false
                            );

                            setRegistration(
                              null
                            );
                          }}
                        />

                        {/* HEADER */}

                        <div className="class-card-header">
                          <div>
                            <span className="class-status">
                              Đang mở
                            </span>

                            <h3>
                              {
                                item.name
                              }
                            </h3>
                          </div>

                          <div className="class-select-indicator">
                            {selected
                              ? "✓"
                              : ""}
                          </div>
                        </div>

                        {/* INFORMATION */}

                        <div className="class-card-information">
                          <div className="class-info-row">
                            <span>
                              Đợt học
                            </span>

                            <strong>
                              {
                                item
                                  .offering
                                  .name
                              }
                            </strong>
                          </div>

                          <div className="class-info-row">
                            <span>
                              Huấn luyện
                              viên
                            </span>

                            <strong>
                              {item.coachName ||
                                "—"}
                            </strong>
                          </div>

                          <div className="class-info-row">
                            <span>
                              Sức chứa
                            </span>

                            <strong>
                              {item.capacity ??
                                "—"}
                            </strong>
                          </div>

                          <div className="class-info-row">
                            <span>
                              Thời gian
                              khóa
                            </span>

                            <strong>
                              {formatSimpleDate(
                                item
                                  .offering
                                  .startDate
                              )}

                              {" – "}

                              {formatSimpleDate(
                                item
                                  .offering
                                  .endDate
                              )}
                            </strong>
                          </div>

                          <div className="class-info-row">
                            <span>
                              Hạn đăng ký
                            </span>

                            <strong>
                              {formatDate(
                                item
                                  .offering
                                  .registrationDeadline
                              )}
                            </strong>
                          </div>
                        </div>
                      </label>
                    );
                  }
                )}
              </div>

              {/* REGISTER ERROR */}

              {registrationError && (
                <div
                  className="registration-message error"
                  role="alert"
                >
                  {
                    registrationError
                  }
                </div>
              )}

              {/* REGISTER BUTTON */}

              <div className="registration-actions">
                <button
                  type="submit"
                  className="register-class-button main-register-button"
                  disabled={
                    !selectedClassId ||
                    registering ||
                    Boolean(
                      classesError
                    ) ||
                    Boolean(
                      registration
                    )
                  }
                >
                  <span>
                    {registering
                      ? "Đang đăng ký..."
                      : "Đăng ký lớp đã chọn"}
                  </span>

                  {!registering && (
                    <span>
                      →
                    </span>
                  )}
                </button>
              </div>
            </form>
          )}

          {/* ========================
              REGISTRATION SUCCESS
          ======================== */}

          {registration && (
            <div
              className="member-registration-success"
              role="status"
            >
              <div className="success-icon">
                ✓
              </div>

              <div>
                <h3>
                  Đăng ký thành công
                </h3>

                <p>
                  Mã đăng ký #
                  {registration.id}

                  {" · "}

                  {
                    registration.courseName
                  }

                  {" · "}

                  {
                    registration.courseOfferingName
                  }

                  {" · "}

                  {
                    registration.className
                  }
                </p>

                <div className="registration-price">
                  <span>
                    Giá gốc{" "}

                    <strong>
                      {formatPrice(
                        registration.originalPrice
                      )}
                    </strong>
                  </span>

                  <span>
                    Ưu đãi{" "}

                    <strong>
                      {formatPrice(
                        registration.membershipDiscount
                      )}
                    </strong>
                  </span>

                  <span>
                    Thành tiền{" "}

                    <strong>
                      {formatPrice(
                        registration.finalPrice
                      )}
                    </strong>
                  </span>
                </div>
              </div>
            </div>
          )}

          {/* ========================
              SESSION EXPIRED
          ======================== */}

          {sessionExpired && (
            <button
              type="button"
              className="session-login-button"
              onClick={
                loginAgain
              }
            >
              Đăng nhập lại để tiếp tục
            </button>
          )}

          {/* ========================
              INSUFFICIENT LEVEL
          ======================== */}

          {consultationAvailable && (
            <div className="member-level-help">
              <span className="section-label">
                TRÌNH ĐỘ
              </span>

              <h3>
                Bạn cần được đánh giá
                trước khi đăng ký
              </h3>

              <div className="level-comparison">
                <div>
                  <span>
                    Khóa học yêu cầu
                  </span>

                  <strong>
                    {course.level ||
                      "—"}
                  </strong>
                </div>

                <div>
                  <span>
                    Trình độ hiện tại
                  </span>

                  <strong>
                    {levelsError
                      ? "Không xác định"
                      : currentLevel}
                  </strong>
                </div>
              </div>

              <p>
                Hãy gửi yêu cầu tư vấn
                để Receptionist tiếp
                nhận và phân công Coach
                đánh giá trình độ.
              </p>

              <button
                type="button"
                className="consultation-button"
                onClick={
                  openConsultation
                }
              >
                Yêu cầu tư vấn / đánh
                giá
              </button>
            </div>
          )}

          {/* ========================
              CONSULT SUCCESS
          ======================== */}

          {consultationSuccess && (
            <div className="member-registration-success">
              <div className="success-icon">
                ✓
              </div>

              <div>
                <h3>
                  Đã gửi yêu cầu tư vấn
                </h3>

                <p>
                  Mã yêu cầu #
                  {
                    consultationSuccess.id
                  }
                  . Receptionist sẽ tiếp
                  nhận yêu cầu của bạn.
                </p>
              </div>
            </div>
          )}

          {/* ========================
              CONSULTATION FORM
          ======================== */}

          {consultationOpen && (
            <form
              className="member-consultation-form"
              onSubmit={
                submitConsultation
              }
            >
              <div className="consult-form-heading">
                <span className="section-label">
                  TƯ VẤN TRÌNH ĐỘ
                </span>

                <h3>
                  Gửi yêu cầu đánh giá
                </h3>

                <p>
                  Khóa học:{" "}

                  <strong>
                    {course.name}
                  </strong>

                  {" · "}

                  Bộ môn:{" "}

                  <strong>
                    {
                      course.sportName
                    }
                  </strong>
                </p>

                {profileError && (
                  <p className="consult-note">
                    Không tải được hồ
                    sơ. Vui lòng nhập
                    thông tin liên hệ.
                  </p>
                )}
              </div>

              {/* CONTACT */}

              <div className="consult-form-grid">
                <label>
                  <span>
                    Họ và tên
                  </span>

                  <input
                    required
                    maxLength={100}
                    value={
                      consultation.name
                    }
                    onChange={(
                      event
                    ) =>
                      setConsultation({
                        ...consultation,

                        name:
                          event.target
                            .value,
                      })
                    }
                  />
                </label>

                <label>
                  <span>
                    Số điện thoại
                  </span>

                  <input
                    required
                    maxLength={20}
                    value={
                      consultation.phone
                    }
                    onChange={(
                      event
                    ) =>
                      setConsultation({
                        ...consultation,

                        phone:
                          event.target
                            .value,
                      })
                    }
                  />
                </label>

                <label>
                  <span>
                    Email
                  </span>

                  <input
                    type="email"
                    maxLength={150}
                    value={
                      consultation.email
                    }
                    onChange={(
                      event
                    ) =>
                      setConsultation({
                        ...consultation,

                        email:
                          event.target
                            .value,
                      })
                    }
                  />
                </label>

                <label>
                  <span>
                    Tuổi
                  </span>

                  <input
                    type="number"
                    min="1"
                    max="120"
                    value={
                      consultation.age
                    }
                    onChange={(
                      event
                    ) =>
                      setConsultation({
                        ...consultation,

                        age:
                          event.target
                            .value,
                      })
                    }
                  />
                </label>
              </div>

              {/* EXPERIENCE */}

              <label className="consult-full-field">
                <span>
                  Kinh nghiệm thể thao
                </span>

                <textarea
                  value={
                    consultation.experience
                  }
                  onChange={(
                    event
                  ) =>
                    setConsultation({
                      ...consultation,

                      experience:
                        event.target
                          .value,
                    })
                  }
                />
              </label>

              {/* NOTE */}

              <label className="consult-full-field">
                <span>
                  Ghi chú
                </span>

                <textarea
                  maxLength={1000}
                  value={
                    consultation.note
                  }
                  onChange={(
                    event
                  ) =>
                    setConsultation({
                      ...consultation,

                      note:
                        event.target
                          .value,
                    })
                  }
                />
              </label>

              {/* CONSULT ERROR */}

              {consultationError && (
                <div
                  className="registration-message error"
                  role="alert"
                >
                  {
                    consultationError
                  }
                </div>
              )}

              {/* BUTTONS */}

              <div className="consult-actions">
                <button
                  type="submit"
                  className="consultation-button"
                  disabled={
                    consultationSubmitting
                  }
                >
                  {consultationSubmitting
                    ? "Đang gửi..."
                    : "Gửi yêu cầu tư vấn"}
                </button>

                <button
                  type="button"
                  className="consult-cancel-button"
                  disabled={
                    consultationSubmitting
                  }
                  onClick={() =>
                    setConsultationOpen(
                      false
                    )
                  }
                >
                  Hủy
                </button>
              </div>
            </form>
          )}
        </section>
      </main>
    </div>
  );
}