import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import PageHero from "../components/PageHero.jsx";
import {
  createCourseRegistrationApi,
  createMemberConsultationRequestApi,
  getClassesByOfferingApi,
  getCurrentUserApi,
  getMyMemberSportLevelsApi,
  getCourseOfferingsByCourseApi,
  getPublicCourseApi,
} from "../../api.js";

const INSUFFICIENT_LEVEL_MESSAGE = "Verified sport level is insufficient. Request a consultation and COACH assessment before registering.";
const BUSINESS_ZONE = "Asia/Ho_Chi_Minh";

function businessDate() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: BUSINESS_ZONE, year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function registrationOpeningDate(startDate) {
  if (!startDate || !/^\d{4}-\d{2}-\d{2}$/.test(startDate)) return "";
  const [year, month, day] = startDate.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day - 30)).toISOString().slice(0, 10);
}

function isOfferingOpenNow(offering) {
  if (String(offering.status).toUpperCase() !== "OPEN") return false;
  const opening = registrationOpeningDate(offering.startDate);
  const deadline = offering.registrationDeadline;
  if (!opening || !deadline) return false;
  // Backend LocalDateTime values use the project's Asia/Ho_Chi_Minh business zone.
  const parsedDeadline = new Date(`${deadline}+07:00`);
  return businessDate() >= opening && !Number.isNaN(parsedDeadline.getTime()) && Date.now() < parsedDeadline.getTime();
}

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("vi-VN", { timeZone: BUSINESS_ZONE });
}

function formatPrice(value) {
  if (value == null || Number.isNaN(Number(value))) return "—";
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(Number(value));
}

function currentAge(dateOfBirth) {
  if (!dateOfBirth) return "";
  const date = new Date(`${dateOfBirth}T00:00:00`);
  if (Number.isNaN(date.getTime())) return "";
  const today = new Date();
  let age = today.getFullYear() - date.getFullYear();
  if (today.getMonth() < date.getMonth() || (today.getMonth() === date.getMonth() && today.getDate() < date.getDate())) age -= 1;
  return age > 0 && age <= 120 ? String(age) : "";
}

function isInsufficientLevel(error) {
  return error?.status === 409 && error.message === INSUFFICIENT_LEVEL_MESSAGE;
}

export default function CourseDetail({ auth, onLogout }) {
  const { courseId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [course, setCourse] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [classes, setClasses] = useState([]);
  const [classesLoading, setClassesLoading] = useState(false);
  const [classesError, setClassesError] = useState("");
  const [levels, setLevels] = useState(null);
  const [levelsError, setLevelsError] = useState("");
  const [profile, setProfile] = useState(null);
  const [profileError, setProfileError] = useState("");
  const [selectedClassId, setSelectedClassId] = useState("");
  const [registering, setRegistering] = useState(false);
  const [registrationError, setRegistrationError] = useState("");
  const [registration, setRegistration] = useState(null);
  const [consultationAvailable, setConsultationAvailable] = useState(false);
  const [consultationOpen, setConsultationOpen] = useState(false);
  const [consultationSubmitting, setConsultationSubmitting] = useState(false);
  const [consultationError, setConsultationError] = useState("");
  const [consultationSuccess, setConsultationSuccess] = useState(null);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [consultation, setConsultation] = useState({ name: "", phone: "", email: "", age: "", experience: "", note: "" });

  const isMember = auth?.role === "MEMBER" && Boolean(auth?.accessToken);
  const level = useMemo(() => levels?.find((item) => Number(item.sportId) === Number(course?.sportId)) || null,
    [levels, course?.sportId]);
  const hasVerifiedLevel = Boolean(level?.verifiedByCoachId != null && level?.verifiedAt);
  const currentLevel = hasVerifiedLevel ? level.level : "BEGINNER";

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => {
      if (active) {
        setLoading(true); setError(""); setCourse(null); setRegistration(null); setSelectedClassId("");
        setRegistrationError(""); setConsultationAvailable(false); setConsultationOpen(false); setConsultationSuccess(null);
      }
      return getPublicCourseApi(courseId);
    }).then((data) => { if (active) setCourse(data); })
      .catch((requestError) => { if (active) setError(requestError.message || "Không tải được thông tin khóa học."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [courseId]);

  useEffect(() => {
    if (!isMember || !course) return undefined;
    let active = true;
    Promise.resolve().then(() => {
      if (!active) return null;
      setClassesLoading(true); setClassesError(""); setLevelsError(""); setProfileError(""); setSessionExpired(false);
      return Promise.allSettled([
        getCourseOfferingsByCourseApi(auth.accessToken, course.id),
        getMyMemberSportLevelsApi(auth.accessToken),
        getCurrentUserApi(auth.accessToken),
      ]);
    }).then(async (results) => {
      if (!results) return;
      const [offeringsResult, levelsResult, profileResult] = results;
      if (!active) return;
      if (levelsResult.status === "fulfilled" && Array.isArray(levelsResult.value)) setLevels(levelsResult.value);
      else { setLevels(null); setLevelsError(levelsResult.reason?.message || "Không tải được trình độ hiện tại."); if (levelsResult.reason?.status === 401) setSessionExpired(true); }
      if (profileResult.status === "fulfilled") setProfile(profileResult.value);
      else { setProfile(null); setProfileError(profileResult.reason?.message || "Không tải được thông tin hồ sơ."); if (profileResult.reason?.status === 401) setSessionExpired(true); }

      if (offeringsResult.status !== "fulfilled" || !Array.isArray(offeringsResult.value)) {
        setClasses([]); setClassesError(offeringsResult.reason?.message || "Không tải được các đợt mở lớp."); if (offeringsResult.reason?.status === 401) setSessionExpired(true);
        setClassesLoading(false); return;
      }
      const eligibleOfferings = offeringsResult.value.filter(isOfferingOpenNow);
      const classResults = await Promise.allSettled(eligibleOfferings.map(async (offering) => ({
        offering,
        classes: await getClassesByOfferingApi(auth.accessToken, offering.id),
      })));
      if (!active) return;
      const nextClasses = [];
      const failed = classResults.some((result) => result.status === "rejected");
      if (classResults.some((result) => result.status === "rejected" && result.reason?.status === 401)) setSessionExpired(true);
      classResults.forEach((result) => {
        if (result.status === "fulfilled" && Array.isArray(result.value.classes)) {
          result.value.classes.filter((item) => String(item.status).toUpperCase() === "ACTIVE"
            && Number(item.courseId) === Number(course.id)
            && Number(item.courseOfferingId) === Number(result.value.offering.id))
            .forEach((item) => nextClasses.push({ ...item, offering: result.value.offering }));
        }
      });
      setClasses(nextClasses);
      if (failed) setClassesError("Một số lịch lớp không tải được. Hãy làm mới trang trước khi đăng ký.");
      setClassesLoading(false);
    });
    return () => { active = false; };
  }, [auth?.accessToken, auth?.userId, course, isMember]);

  function openConsultation() {
    setConsultationError(""); setConsultationSuccess(null);
    setConsultation((current) => ({
      ...current,
      name: current.name || profile?.name || "",
      email: current.email || profile?.email || "",
      phone: current.phone || profile?.phone || "",
      age: current.age || currentAge(profile?.dateOfBirth),
    }));
    setConsultationOpen(true);
  }

  function goToLogin() {
    if (auth && onLogout) onLogout(location.pathname);
    else navigate("/login", { state: { from: location.pathname } });
  }

  async function registerForClass(event) {
    event.preventDefault();
    if (!isMember || !selectedClassId || registering) return;
    setRegistering(true); setRegistrationError(""); setConsultationAvailable(false); setConsultationOpen(false); setConsultationSuccess(null);
    try {
      setRegistration(await createCourseRegistrationApi(auth.accessToken, Number(selectedClassId)));
      setRegistrationError("");
    } catch (requestError) {
      if (isInsufficientLevel(requestError)) {
        setConsultationAvailable(true);
        setRegistrationError("Trình độ thể thao đã xác minh chưa đáp ứng yêu cầu khóa học.");
      } else if (requestError.status === 401) {
        setRegistrationError("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để tiếp tục.");
        setSessionExpired(true);
      } else {
        setRegistrationError(requestError.message || "Không thể đăng ký lớp đã chọn.");
      }
    } finally { setRegistering(false); }
  }

  async function submitConsultation(event) {
    event.preventDefault();
    if (!isMember || !course || !selectedClassId || consultationSubmitting) return;
    const selectedClass = classes.find((item) => String(item.id) === selectedClassId);
    if (!selectedClass) { setConsultationError("Vui lòng chọn lại lớp trước khi gửi yêu cầu tư vấn."); return; }
    setConsultationSubmitting(true); setConsultationError("");
    const contextNote = `Yêu cầu tư vấn về trình độ để đăng ký khóa học: ${course.name} (ID ${course.id}); lớp: ${selectedClass.name} (ID ${selectedClass.id}); đợt: ${selectedClass.offering.name} (ID ${selectedClass.offering.id}).`;
    const personalNote = consultation.note.trim();
    try {
      const result = await createMemberConsultationRequestApi(auth.accessToken, {
        name: consultation.name.trim(),
        phone: consultation.phone.trim(),
        email: consultation.email.trim() || null,
        sportId: course.sportId,
        age: consultation.age ? Number(consultation.age) : null,
        experience: consultation.experience.trim() || null,
        note: personalNote ? `${contextNote}\n\nGhi chú của Member: ${personalNote}` : contextNote,
      });
      setConsultationSuccess(result);
      setConsultationOpen(false);
    } catch (requestError) {
      setConsultationError(requestError.status === 401
        ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.message || "Không gửi được yêu cầu tư vấn. Vui lòng kiểm tra thông tin và thử lại.");
      if (requestError.status === 401) setSessionExpired(true);
    } finally { setConsultationSubmitting(false); }
  }

  return (
  <main className="public-course-detail-page">
    {/* HERO */}
    <section className="public-course-detail-hero">
      <div className="container-xl public-course-detail-hero-inner">
        <div className="public-course-detail-copy">
          <Link
            to={course ? `/bo-mon/${course.sportId}` : "/bo-mon"}
            className="public-course-back"
          >
            <span>←</span>
            Quay lại danh sách khóa học
          </Link>

          {loading ? (
            <div className="public-course-loading">
              <div className="public-loader" />
              <span>Đang tải thông tin khóa học...</span>
            </div>
          ) : error ? (
            <div className="public-state public-state-error">
              {error}
            </div>
          ) : course ? (
            <>
              <div className="public-course-detail-badges">
                <span className="public-course-sport-badge">
                  {course.sportName || "SPORTS CENTER"}
                </span>

                {course.level && (
                  <span className="public-course-level-badge">
                    {course.level}
                  </span>
                )}
              </div>

              <h1>{course.name}</h1>

              <p className="public-course-detail-description">
                {course.description ||
                  "Chương trình đào tạo được thiết kế bài bản nhằm giúp học viên phát triển kỹ năng và nâng cao thể lực."}
              </p>

              <div className="public-course-hero-meta">
                {course.level && (
                  <div className="public-course-meta-box">
                    <div className="public-course-meta-icon">
                      ★
                    </div>

                    <div>
                      <span>Trình độ yêu cầu</span>
                      <strong>{course.level}</strong>
                    </div>
                  </div>
                )}

                {(course.targetAgeMin != null ||
                  course.targetAgeMax != null) && (
                  <div className="public-course-meta-box">
                    <div className="public-course-meta-icon">
                      ♙
                    </div>

                    <div>
                      <span>Độ tuổi</span>

                      <strong>
                        {course.targetAgeMin ?? "Không giới hạn"}
                        {" – "}
                        {course.targetAgeMax ?? "Không giới hạn"}
                      </strong>
                    </div>
                  </div>
                )}
              </div>
            </>
          ) : null}
        </div>

        <div className="public-course-hero-visual">
          <div className="public-course-visual-circle">
            <span>🏸</span>
          </div>

          <div className="public-course-visual-text">
            SPORTS CENTER
          </div>
        </div>
      </div>
    </section>

    {!loading && !error && course && (
      <>
        {/* COURSE INFO */}
        <section className="public-course-info-section">
          <div className="container-xl">
            <div className="public-course-info-grid">
              <div className="public-course-main-info">
                <span className="public-section-kicker">
                  THÔNG TIN KHÓA HỌC
                </span>

                <h2>Tổng quan chương trình</h2>

                <p>
                  {course.description ||
                    "Thông tin chi tiết về chương trình đào tạo tại Sports Center."}
                </p>

                {course.learningOutcome && (
                  <div className="public-learning-outcome">
                    <div className="public-learning-icon">
                      ✓
                    </div>

                    <div>
                      <span>KẾT QUẢ MONG ĐỢI</span>

                      <h3>
                        Mục tiêu sau khóa học
                      </h3>

                      <p>
                        {course.learningOutcome}
                      </p>
                    </div>
                  </div>
                )}
              </div>

              <aside className="public-course-summary-card">
                <span className="public-summary-label">
                  THÔNG TIN NHANH
                </span>

                <div className="public-summary-row">
                  <span>Bộ môn</span>
                  <strong>
                    {course.sportName || "—"}
                  </strong>
                </div>

                <div className="public-summary-row">
                  <span>Trình độ</span>
                  <strong>
                    {course.level || "—"}
                  </strong>
                </div>

                <div className="public-summary-row">
                  <span>Độ tuổi</span>

                  <strong>
                    {course.targetAgeMin ?? "Không giới hạn"}
                    {" – "}
                    {course.targetAgeMax ?? "Không giới hạn"}
                  </strong>
                </div>

                <Link
                  to="/tuyen-sinh"
                  className="public-consult-button"
                >
                  Đăng ký tư vấn
                  <span>→</span>
                </Link>
              </aside>
            </div>
          </div>
        </section>

        {/* REGISTER SECTION */}
        <section className="public-course-register-section">
          <div className="container-xl">
            <div className="public-course-register-card">
              <div className="public-register-heading">
                <div>
                  <span className="public-section-kicker">
                    ĐĂNG KÝ HỌC
                  </span>

                  <h2>Chọn lớp phù hợp với bạn</h2>

                  <p>
                    Chọn một lớp đang hoạt động thuộc đợt mở đăng ký.
                  </p>
                </div>

                {isMember && !classesLoading && (
                  <div className="public-register-count">
                    {classes.length}
                    <span> lớp đang mở</span>
                  </div>
                )}
              </div>

              {isMember ? (
                <>
                  {classesLoading ? (
                    <div className="public-register-state">
                      <div className="public-loader" />

                      <span>
                        Đang tải các lớp có thể đăng ký...
                      </span>
                    </div>
                  ) : classesError ? (
                    <div
                      className="public-state public-state-error"
                      role="alert"
                    >
                      {classesError}
                    </div>
                  ) : classes.length === 0 ? (
                    <div className="public-register-empty">
                      <div className="public-register-empty-icon">
                        ⌛
                      </div>

                      <h3>
                        Chưa có lớp đang mở đăng ký
                      </h3>

                      <p>
                        Hiện tại khóa học này chưa có lớp nào trong đợt đăng ký.
                      </p>
                    </div>
                  ) : (
                    <form
                      className="public-course-registration-form"
                      onSubmit={registerForClass}
                    >
                      <div className="public-class-grid">
                        {classes.map((item) => {
                          const selected =
                            selectedClassId === String(item.id);

                          return (
                            <label
                              key={item.id}
                              className={`public-class-card ${
                                selected ? "selected" : ""
                              }`}
                            >
                              <input
                                type="radio"
                                name="courseClass"
                                value={item.id}
                                checked={selected}
                                disabled={Boolean(registration)}
                                onChange={() => {
                                  setSelectedClassId(String(item.id));
                                  setRegistrationError("");
                                  setConsultationAvailable(false);
                                  setConsultationOpen(false);
                                  setRegistration(null);
                                }}
                              />

                              <div className="public-class-card-header">
                                <div>
                                  <span className="public-class-status">
                                    ĐANG MỞ
                                  </span>

                                  <h3>{item.name}</h3>
                                </div>

                                <span className="public-class-check">
                                  {selected ? "✓" : ""}
                                </span>
                              </div>

                              <div className="public-class-info">
                                <div>
                                  <span>Đợt học</span>
                                  <strong>
                                    {item.offering.name}
                                  </strong>
                                </div>

                                <div>
                                  <span>Huấn luyện viên</span>
                                  <strong>
                                    {item.coachName || "—"}
                                  </strong>
                                </div>

                                <div>
                                  <span>Sức chứa</span>
                                  <strong>
                                    {item.capacity ?? "—"}
                                  </strong>
                                </div>

                                <div>
                                  <span>Thời gian</span>
                                  <strong>
                                    {item.offering.startDate} –{" "}
                                    {item.offering.endDate}
                                  </strong>
                                </div>

                                <div>
                                  <span>Hạn đăng ký</span>
                                  <strong>
                                    {formatDate(
                                      item.offering.registrationDeadline
                                    )}
                                  </strong>
                                </div>
                              </div>
                            </label>
                          );
                        })}
                      </div>

                      {registrationError && (
                        <div
                          className="public-registration-error"
                          role="alert"
                        >
                          {registrationError}
                        </div>
                      )}

                      <div className="public-registration-actions">
                        <button
                          type="submit"
                          className="public-register-button"
                          disabled={
                            !selectedClassId ||
                            registering ||
                            Boolean(classesError) ||
                            Boolean(registration)
                          }
                        >
                          {registering
                            ? "Đang đăng ký..."
                            : "Đăng ký lớp đã chọn"}

                          {!registering && <span>→</span>}
                        </button>
                      </div>
                    </form>
                  )}

                  {registration && (
                    <div className="public-registration-success">
                      <div className="public-success-icon">
                        ✓
                      </div>

                      <div>
                        <h3>Đăng ký thành công</h3>

                        <p>
                          #{registration.id} ·{" "}
                          {registration.courseName} ·{" "}
                          {registration.courseOfferingName} ·{" "}
                          {registration.className}
                        </p>

                        <div className="public-price-summary">
                          <span>
                            Giá gốc
                            <strong>
                              {formatPrice(
                                registration.originalPrice
                              )}
                            </strong>
                          </span>

                          <span>
                            Ưu đãi
                            <strong>
                              {formatPrice(
                                registration.membershipDiscount
                              )}
                            </strong>
                          </span>

                          <span>
                            Thành tiền
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

                  {sessionExpired && (
                    <button
                      type="button"
                      className="public-register-button"
                      onClick={goToLogin}
                    >
                      Đăng nhập lại để tiếp tục
                    </button>
                  )}

                  {consultationAvailable && (
                    <div className="public-level-warning">
                      <span className="public-section-kicker">
                        TRÌNH ĐỘ
                      </span>

                      <h3>
                        Trình độ hiện tại chưa đáp ứng khóa học
                      </h3>

                      <div className="public-level-comparison">
                        <div>
                          <span>Khóa học yêu cầu</span>
                          <strong>
                            {course.level || "—"}
                          </strong>
                        </div>

                        <div>
                          <span>Trình độ hiện tại</span>

                          <strong>
                            {levelsError
                              ? "Không xác định"
                              : currentLevel}
                          </strong>
                        </div>
                      </div>

                      <p>
                        Bạn có thể gửi yêu cầu tư vấn để Coach đánh giá trình độ.
                      </p>

                      <button
                        type="button"
                        className="public-register-button public-warning-button"
                        onClick={openConsultation}
                      >
                        Yêu cầu tư vấn / đánh giá
                      </button>
                    </div>
                  )}

                  {consultationSuccess && (
                    <div className="public-registration-success">
                      <div className="public-success-icon">
                        ✓
                      </div>

                      <div>
                        <h3>
                          Đã gửi yêu cầu tư vấn
                        </h3>

                        <p>
                          Mã yêu cầu #{consultationSuccess.id}. Receptionist sẽ tiếp nhận yêu cầu của bạn.
                        </p>
                      </div>
                    </div>
                  )}

                  {consultationOpen && (
                    <form
                      className="public-consult-form"
                      onSubmit={submitConsultation}
                    >
                      <div className="public-consult-heading">
                        <span className="public-section-kicker">
                          TƯ VẤN TRÌNH ĐỘ
                        </span>

                        <h3>
                          Gửi yêu cầu đánh giá
                        </h3>

                        <p>
                          Khóa học: <strong>{course.name}</strong>
                          {" · "}
                          Bộ môn: <strong>{course.sportName}</strong>
                        </p>

                        {profileError && (
                          <p>
                            Không tải được hồ sơ. Vui lòng nhập thông tin liên hệ.
                          </p>
                        )}
                      </div>

                      <div className="public-consult-grid">
                        <label>
                          <span>Họ và tên</span>

                          <input
                            required
                            maxLength={100}
                            value={consultation.name}
                            onChange={(event) =>
                              setConsultation({
                                ...consultation,
                                name: event.target.value,
                              })
                            }
                          />
                        </label>

                        <label>
                          <span>Số điện thoại</span>

                          <input
                            required
                            maxLength={20}
                            value={consultation.phone}
                            onChange={(event) =>
                              setConsultation({
                                ...consultation,
                                phone: event.target.value,
                              })
                            }
                          />
                        </label>

                        <label>
                          <span>Email</span>

                          <input
                            type="email"
                            maxLength={150}
                            value={consultation.email}
                            onChange={(event) =>
                              setConsultation({
                                ...consultation,
                                email: event.target.value,
                              })
                            }
                          />
                        </label>

                        <label>
                          <span>Tuổi</span>

                          <input
                            type="number"
                            min="1"
                            max="120"
                            value={consultation.age}
                            onChange={(event) =>
                              setConsultation({
                                ...consultation,
                                age: event.target.value,
                              })
                            }
                          />
                        </label>
                      </div>

                      <label className="public-consult-full">
                        <span>
                          Kinh nghiệm thể thao
                        </span>

                        <textarea
                          value={consultation.experience}
                          onChange={(event) =>
                            setConsultation({
                              ...consultation,
                              experience: event.target.value,
                            })
                          }
                        />
                      </label>

                      <label className="public-consult-full">
                        <span>Ghi chú</span>

                        <textarea
                          maxLength={1000}
                          value={consultation.note}
                          onChange={(event) =>
                            setConsultation({
                              ...consultation,
                              note: event.target.value,
                            })
                          }
                        />
                      </label>

                      {consultationError && (
                        <div
                          className="public-registration-error"
                          role="alert"
                        >
                          {consultationError}
                        </div>
                      )}

                      <div className="public-consult-actions">
                        <button
                          type="submit"
                          className="public-register-button"
                          disabled={consultationSubmitting}
                        >
                          {consultationSubmitting
                            ? "Đang gửi..."
                            : "Gửi yêu cầu tư vấn"}
                        </button>

                        <button
                          type="button"
                          className="public-consult-cancel"
                          onClick={() =>
                            setConsultationOpen(false)
                          }
                          disabled={consultationSubmitting}
                        >
                          Hủy
                        </button>
                      </div>
                    </form>
                  )}
                </>
              ) : auth ? (
                <div className="public-register-empty">
                  <div className="public-register-empty-icon">
                    👤
                  </div>

                  <h3>
                    Chỉ dành cho Member
                  </h3>

                  <p>
                    Đăng ký khóa học chỉ dành cho tài khoản Member.
                  </p>
                </div>
              ) : (
                <div className="public-login-register-card">
                  <div>
                    <span className="public-section-kicker">
                      THÀNH VIÊN
                    </span>

                    <h3>
                      Đăng nhập để xem lớp và đăng ký
                    </h3>

                    <p>
                      Thành viên Sports Center có thể xem lịch lớp, chọn lớp và đăng ký trực tiếp.
                    </p>
                  </div>

                  <button
                    type="button"
                    className="public-register-button"
                    onClick={goToLogin}
                  >
                    Đăng nhập để đăng ký
                    <span>→</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </section>
      </>
    )}
  </main>
);
}