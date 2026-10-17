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
    <>
      <PageHero title={course?.name || "CHI TIẾT KHÓA HỌC"} subtitle={course?.sportName || "Thông tin chương trình học"} />
      <section className="section"><div className="container-xl">
        <p className="mb-4"><Link to={course ? "/bo-mon/" + course.sportId : "/bo-mon"} className="link-arrow">← Quay lại danh sách khóa học</Link></p>
        {loading ? <p role="status">Đang tải thông tin khóa học…</p> : error ? <p className="banner error" role="alert">{error}</p> : course && <>
          <article className="course-detail-card">
            <p className="label-blue">{course.sportName}</p>
            <h2>{course.name}</h2>
            {course.level && <p><strong>Trình độ yêu cầu:</strong> {course.level}</p>}
            {course.description && <p>{course.description}</p>}
            {course.learningOutcome && <div><h3>Kết quả mong đợi</h3><p>{course.learningOutcome}</p></div>}
            {(course.targetAgeMin != null || course.targetAgeMax != null) && <p><strong>Độ tuổi:</strong> {course.targetAgeMin ?? "Không giới hạn"} – {course.targetAgeMax ?? "Không giới hạn"}</p>}
            <Link to="/tuyen-sinh" className="btn btn-cyan">Đăng ký tư vấn</Link>
          </article>

          {isMember ? <section className="course-registration-panel" aria-labelledby="course-registration-title">
            <h2 id="course-registration-title">Đăng ký lớp học</h2>
            <p>Chọn một lớp đang hoạt động thuộc đợt mở đăng ký. Quyền tham gia được xác nhận theo quy định của hệ thống.</p>
            {classesLoading ? <p role="status">Đang tải các lớp có thể đăng ký…</p>
              : classesError ? <p className="banner error" role="alert">{classesError}</p>
                : classes.length === 0 ? <p className="queue-state">Hiện không có lớp đang mở đăng ký cho khóa học này.</p>
                  : <form className="course-registration-form" onSubmit={registerForClass}>
                    {classes.map((item) => <label className="course-class-option" key={item.id}>
                      <input type="radio" name="courseClass" value={item.id} checked={selectedClassId === String(item.id)} disabled={Boolean(registration)}
                        onChange={() => { setSelectedClassId(String(item.id)); setRegistrationError(""); setConsultationAvailable(false); setConsultationOpen(false); setRegistration(null); }} />
                      <span><strong>{item.name}</strong><small>{item.offering.name} · Coach {item.coachName} · Sức chứa {item.capacity}</small>
                        <small>Thời gian khóa: {item.offering.startDate} – {item.offering.endDate} · Hạn đăng ký: {formatDate(item.offering.registrationDeadline)}</small></span>
                    </label>)}
                    {registrationError && <p className="banner error" role="alert">{registrationError}</p>}
                    <button className="btn btn-cyan" type="submit" disabled={!selectedClassId || registering || Boolean(classesError) || Boolean(registration)}>
                      {registering ? "Đang đăng ký…" : "Đăng ký lớp này"}
                    </button>
                  </form>}
            {registration && <div className="course-registration-success" role="status">
              <strong>Đăng ký thành công — {registration.status}</strong>
              <p>Mã đăng ký #{registration.id} · {registration.courseName} · {registration.courseOfferingName} · {registration.className}</p>
              <p>Giá gốc {formatPrice(registration.originalPrice)} · Ưu đãi {formatPrice(registration.membershipDiscount)} · Thành tiền {formatPrice(registration.finalPrice)}</p>
            </div>}
            {sessionExpired && <button type="button" className="btn btn-cyan" onClick={goToLogin}>Đăng nhập lại và tiếp tục khóa học</button>}
            {consultationAvailable && <div className="course-level-help">
              <p><strong>Khóa học yêu cầu:</strong> {course.level || "—"}</p>
              {levelsError ? <p role="status">Không tải được trình độ hiện tại nên không thể xác định mức cụ thể.</p>
                : levels ? <p><strong>Trình độ hiện tại:</strong> {currentLevel}{hasVerifiedLevel
                  ? ` (Coach #${level.verifiedByCoachId} xác minh${level.verifiedAt ? ` · ${formatDate(level.verifiedAt)}` : ""})`
                  : " (Beginner mặc định; chưa có trình độ được Coach xác minh)"}</p>
                  : <p role="status">Đang tải trình độ hiện tại…</p>}
              <p>Hãy gửi yêu cầu tư vấn để Receptionist tiếp nhận và phân công Coach đánh giá. Yêu cầu này không tự tăng trình độ và không đăng ký khóa học.</p>
              <button type="button" className="btn btn-cyan" onClick={openConsultation}>Yêu cầu tư vấn / đánh giá trình độ</button>
            </div>}
            {consultationSuccess && <div className="course-registration-success" role="status">
              <strong>Đã gửi yêu cầu tư vấn #{consultationSuccess.id}.</strong>
              <p>Yêu cầu sẽ được Receptionist tiếp nhận. Việc gửi yêu cầu không tự thay đổi trình độ và không đăng ký bạn vào khóa học.</p>
            </div>}
            {consultationOpen && <form className="course-member-consult-form" onSubmit={submitConsultation}>
              <div><h3>Yêu cầu tư vấn trình độ</h3><p>Khóa học: <strong>{course.name}</strong> · Bộ môn: <strong>{course.sportName}</strong></p>
                <p>{profileError ? "Không tải được hồ sơ; vui lòng nhập thông tin liên hệ." : "Thông tin liên hệ được lấy từ hồ sơ của bạn nếu có và có thể chỉnh sửa."}</p></div>
              <label>Họ và tên<input required maxLength={100} value={consultation.name} onChange={(event) => setConsultation({ ...consultation, name: event.target.value })} /></label>
              <label>Số điện thoại<input required maxLength={20} value={consultation.phone} onChange={(event) => setConsultation({ ...consultation, phone: event.target.value })} /></label>
              <label>Email<input type="email" maxLength={150} value={consultation.email} onChange={(event) => setConsultation({ ...consultation, email: event.target.value })} /></label>
              <label>Tuổi (không bắt buộc)<input type="number" min="1" max="120" value={consultation.age} onChange={(event) => setConsultation({ ...consultation, age: event.target.value })} /></label>
              <label>Kinh nghiệm thể thao<textarea value={consultation.experience} onChange={(event) => setConsultation({ ...consultation, experience: event.target.value })} /></label>
              <label>Ghi chú<input maxLength={1000} value={consultation.note} onChange={(event) => setConsultation({ ...consultation, note: event.target.value })} /></label>
              {consultationError && <p className="banner error" role="alert">{consultationError}</p>}
              <div className="course-consult-actions">
                <button type="submit" className="btn btn-cyan" disabled={consultationSubmitting}>{consultationSubmitting ? "Đang gửi…" : "Gửi yêu cầu tư vấn"}</button>
                <button type="button" className="btn btn-outline-secondary" onClick={() => setConsultationOpen(false)} disabled={consultationSubmitting}>Đóng</button>
              </div>
            </form>}
          </section> : auth ? <p className="course-registration-panel">Đăng ký khóa học chỉ dành cho tài khoản Member. Hãy đăng nhập bằng tài khoản Member để tiếp tục.</p>
            : <div className="course-registration-panel"><p>Đăng nhập bằng tài khoản Member để xem lớp và đăng ký khóa học.</p>
              <button type="button" className="btn btn-cyan" onClick={goToLogin}>Đăng nhập để đăng ký</button>
            </div>}
        </>}
      </div></section>
    </>
  );
}
