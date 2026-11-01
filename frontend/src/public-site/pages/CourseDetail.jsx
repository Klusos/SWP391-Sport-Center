import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import PageHero from "../components/PageHero.jsx";
import Sidebar from "../../components/Sidebar.jsx";
import "../../pages/MemberCourseDetail.css";
import {
  createCourseRegistrationApi,
  createMemberConsultationRequestApi,
  getClassSessionsByClassApi,
  getClassesByOfferingApi,
  getCurrentUserApi,
  getMyMemberSportLevelsApi,
  getMyRegistrationsApi,
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
  if (!offering) return false;
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
  const localDateTime = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value);
  const date = new Date(localDateTime ? `${value}+07:00` : value);
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

export default function CourseDetail({ auth, onLogout, onNavigate, memberRoute = false }) {
  const { courseId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [course, setCourse] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [classes, setClasses] = useState([]);
  const [classesLoading, setClassesLoading] = useState(false);
  const [classesError, setClassesError] = useState("");
  const [offerings, setOfferings] = useState([]);
  const [offeringsLoading, setOfferingsLoading] = useState(true);
  const [selectedOfferingId, setSelectedOfferingId] = useState("");
  const [classSchedules, setClassSchedules] = useState({});
  const [scheduleError, setScheduleError] = useState("");
  const [registrations, setRegistrations] = useState([]);
  const [levels, setLevels] = useState(null);
  const [levelsError, setLevelsError] = useState("");
  const [profile, setProfile] = useState(null);
  const [profileError, setProfileError] = useState("");
  const [selectedClassId, setSelectedClassId] = useState("");
  const [registering, setRegistering] = useState(false);
  const [registrationError, setRegistrationError] = useState("");
  const [registration, setRegistration] = useState(null);
  const [confirmingRegistration, setConfirmingRegistration] = useState(false);
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
  const selectedOffering = offerings.find((item) => String(item.id) === selectedOfferingId) || null;
  const selectedClass = classes.find((item) => String(item.id) === selectedClassId) || null;
  const existingOfferingRegistration = registrations.find((item) =>
    Number(item.courseOfferingId) === Number(selectedOfferingId) && ["CONFIRMED", "PENDING_PAYMENT"].includes(String(item.status).toUpperCase()));
  const canRegister = Boolean(selectedClass && selectedOffering && isOfferingOpenNow(selectedOffering)
    && String(selectedClass.status).toUpperCase() === "ACTIVE" && !existingOfferingRegistration && !classesError);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => {
      if (active) {
        setLoading(true); setError(""); setCourse(null); setRegistration(null); setRegistrations([]); setOfferings([]); setOfferingsLoading(true); setSelectedOfferingId("");
        setClasses([]); setClassSchedules({}); setSelectedClassId(""); setConfirmingRegistration(false);
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
      setOfferingsLoading(true);
      setLevelsError(""); setProfileError(""); setSessionExpired(false);
      return Promise.allSettled([
        getCourseOfferingsByCourseApi(auth.accessToken, course.id),
        getMyMemberSportLevelsApi(auth.accessToken),
        getCurrentUserApi(auth.accessToken),
        getMyRegistrationsApi(auth.accessToken),
      ]);
    }).then((results) => {
      if (!results) return;
      if (!active) return;
      const [offeringsResult, levelsResult, profileResult, registrationsResult] = results;
      if (offeringsResult.status === "fulfilled" && Array.isArray(offeringsResult.value)) {
        const nextOfferings = offeringsResult.value;
        setOfferings(nextOfferings);
        const defaultOffering = nextOfferings.find(isOfferingOpenNow) || nextOfferings[0];
        setSelectedOfferingId(defaultOffering ? String(defaultOffering.id) : "");
      } else {
        setOfferings([]); setSelectedOfferingId("");
        setClassesError(offeringsResult.reason?.message || "Không tải được các đợt mở lớp.");
        if (offeringsResult.reason?.status === 401) setSessionExpired(true);
      }
      if (levelsResult.status === "fulfilled" && Array.isArray(levelsResult.value)) setLevels(levelsResult.value);
      else { setLevels(null); setLevelsError(levelsResult.reason?.message || "Không tải được trình độ hiện tại."); if (levelsResult.reason?.status === 401) setSessionExpired(true); }
      if (profileResult.status === "fulfilled") setProfile(profileResult.value);
      else { setProfile(null); setProfileError(profileResult.reason?.message || "Không tải được thông tin hồ sơ."); if (profileResult.reason?.status === 401) setSessionExpired(true); }
      if (registrationsResult.status === "fulfilled" && Array.isArray(registrationsResult.value)) setRegistrations(registrationsResult.value);
      else { setRegistrations([]); if (registrationsResult.reason?.status === 401) setSessionExpired(true); }
    }).finally(() => { if (active) setOfferingsLoading(false); });
    return () => { active = false; };
  }, [auth?.accessToken, auth?.userId, course, isMember]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => {
      if (!active) return null;
      if (!isMember || !selectedOfferingId) { setClasses([]); setClassSchedules({}); return null; }
      setClassesLoading(true); setClassesError(""); setScheduleError(""); setClassSchedules({}); setSelectedClassId(""); setConfirmingRegistration(false);
      return getClassesByOfferingApi(auth.accessToken, selectedOfferingId);
    }).then(async (data) => {
      if (data === null) return;
      if (!active) return;
      const nextClasses = (Array.isArray(data) ? data : []).filter((item) =>
        Number(item.courseId) === Number(course?.id) && Number(item.courseOfferingId) === Number(selectedOfferingId))
        .map((item) => ({ ...item, offering: offerings.find((offering) => Number(offering.id) === Number(selectedOfferingId)) }));
      setClasses(nextClasses);
      const scheduleResults = await Promise.allSettled(nextClasses.map((item) => getClassSessionsByClassApi(auth.accessToken, item.id)));
      if (!active) return;
      const schedules = {};
      scheduleResults.forEach((result, index) => {
        if (result.status === "fulfilled") schedules[nextClasses[index].id] = Array.isArray(result.value) ? result.value : [];
      });
      setClassSchedules(schedules);
      if (scheduleResults.some((result) => result.status === "rejected")) setScheduleError("Không thể tải lịch cụ thể cho một số lớp.");
    }).catch((requestError) => {
      if (!active) return;
      setClasses([]); setClassesError(requestError.message || "Không tải được danh sách lớp.");
      if (requestError.status === 401) setSessionExpired(true);
    }).finally(() => { if (active) setClassesLoading(false); });
    return () => { active = false; };
  }, [auth?.accessToken, course?.id, isMember, offerings, selectedOfferingId]);

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

  async function confirmRegistration() {
    if (!isMember || !canRegister || !selectedClass || registering) return;
    setRegistering(true); setRegistrationError(""); setConsultationAvailable(false); setConsultationOpen(false); setConsultationSuccess(null);
    try {
      const result = await createCourseRegistrationApi(auth.accessToken, Number(selectedClass.id));
      setRegistration(result);
      setConfirmingRegistration(false);
      setRegistrationError("");
      setRegistrations((current) => [...current.filter((item) => Number(item.courseOfferingId) !== Number(result.courseOfferingId)), result]);
      try { setRegistrations(await getMyRegistrationsApi(auth.accessToken)); }
      catch { /* Keep the pending response if refreshing history fails. */ }
    } catch (requestError) {
      if (isInsufficientLevel(requestError)) {
        setConsultationAvailable(true);
        setRegistrationError("Trình độ thể thao đã xác minh chưa đáp ứng yêu cầu khóa học.");
      } else if (requestError.status === 401) {
        setRegistrationError("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để tiếp tục.");
        setSessionExpired(true);
      } else if (requestError.status === 403) {
        setRegistrationError("Tài khoản hiện không được phép đăng ký lớp này.");
      } else if (requestError.status === 404) {
        setRegistrationError("Khóa học hoặc lớp vừa chọn không còn tồn tại. Hãy tải lại thông tin.");
      } else if (requestError.status === 400) {
        setRegistrationError("Thông tin đăng ký không hợp lệ: " + (requestError.message || "hãy kiểm tra lớp đã chọn."));
      } else if (requestError.status === 409) {
        setRegistrationError("Hiện không thể đăng ký lớp này: " + (requestError.message || "đợt học có thể đã đóng hoặc bạn chưa đủ điều kiện."));
      } else {
        setRegistrationError(requestError.message || "Không thể đăng ký lớp đã chọn.");
      }
    } finally { setRegistering(false); }
  }

  async function submitConsultation(event) {
    event.preventDefault();
    if (!isMember || !course || consultationSubmitting) return;
    setConsultationSubmitting(true); setConsultationError("");
    const contextNote = `Yêu cầu tư vấn về khóa học ${course.name} (ID ${course.id}), bộ môn ${course.sportName} (ID ${course.sportId}).`
      + (selectedClass ? ` Lớp quan tâm: ${selectedClass.name} (ID ${selectedClass.id}); đợt: ${selectedClass.offering?.name || selectedOffering?.name || ""}.` : "");
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

  const detailContent = <>
    <p className="course-detail-back"><Link to={memberRoute ? "/member/courses" : course ? "/bo-mon/" + course.sportId : "/bo-mon"}>
      ← {memberRoute ? "Quay lại danh sách khóa học" : "Quay lại bộ môn"}
    </Link></p>
    {loading ? <p role="status">Đang tải thông tin khóa học…</p>
      : error ? <div className="banner error" role="alert">{error}</div>
        : course && <>
          <article className="course-detail-card">
            <p className="label-blue">{course.sportName || "Bộ môn"}</p>
            <h1>{course.name}</h1>
            {course.level && <p><strong>Trình độ yêu cầu:</strong> {course.level}</p>}
            {course.description && <p>{course.description}</p>}
            {course.learningOutcome && <div><h2>Kết quả mong đợi</h2><p>{course.learningOutcome}</p></div>}
            {(course.targetAgeMin != null || course.targetAgeMax != null) && <p><strong>Độ tuổi:</strong> {course.targetAgeMin ?? "Không giới hạn"} – {course.targetAgeMax ?? "Không giới hạn"}</p>}
            {isMember ? <button type="button" className="btn btn-cyan" onClick={openConsultation}>Yêu cầu tư vấn</button>
              : <Link to="/tuyen-sinh" className="btn btn-cyan">Đăng ký tư vấn</Link>}
          </article>

          {isMember ? <section className="course-registration-panel" aria-labelledby="course-registration-title">
            <div className="course-section-heading"><div><h2 id="course-registration-title">Đợt học và lớp</h2><p>Chọn một đợt để xem các lớp, lịch dự kiến và điều kiện đăng ký.</p></div></div>
            {sessionExpired && <p className="banner error" role="alert">Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.</p>}
            {classesError && <p className="banner error" role="alert">{classesError}</p>}
            {offeringsLoading ? <p className="queue-state" role="status">Đang tải các đợt học…</p>
              : offerings.length === 0 && !classesError ? <p className="queue-state">Khóa học hiện chưa có đợt nào được công bố.</p> : <>
              <div className="member-offering-list" role="tablist" aria-label="Các đợt học">
                {offerings.map((item) => <button type="button" role="tab" aria-selected={String(item.id) === selectedOfferingId}
                  className={String(item.id) === selectedOfferingId ? "selected" : ""} key={item.id}
                  onClick={() => { setSelectedOfferingId(String(item.id)); setRegistrationError(""); setRegistration(null); setConfirmingRegistration(false); }}>
                  <strong>{item.name || `Đợt #${item.id}`}</strong><span>{item.startDate || "—"} – {item.endDate || "—"}</span>
                  <span>{formatPrice(item.price)} · {item.status || "Chưa rõ trạng thái"}</span>
                </button>)}
              </div>
              {selectedOffering && <div className="member-offering-facts">
                <span>Ngày học: <b>{selectedOffering.startDate || "—"} – {selectedOffering.endDate || "—"}</b></span>
                <span>Học phí tham khảo: <b>{formatPrice(selectedOffering.price)}</b></span>
                <span>Hạn đăng ký: <b>{formatDate(selectedOffering.registrationDeadline)}</b></span>
                <span>Số buổi dự kiến: <b>{selectedOffering.plannedSessions ?? "—"}</b></span>
                <span>Thời lượng mỗi buổi: <b>{selectedOffering.sessionDuration != null ? `${selectedOffering.sessionDuration} phút` : "—"}</b></span>
                <span>Trạng thái: <b>{selectedOffering.status || "—"}</b></span>
              </div>}
              {existingOfferingRegistration && <div className="course-registration-success" role="status">
                Bạn đã có đăng ký CONFIRMED cho đợt này: {existingOfferingRegistration.className || `Lớp #${existingOfferingRegistration.classId}`}.
                <Link to="/member/schedule">Xem lịch học</Link>
              </div>}
              {classesLoading ? <p role="status">Đang tải các lớp…</p>
                : !classesError && classes.length === 0 ? <p className="queue-state">Đợt học này chưa có lớp.</p>
                  : !classesError && <div className="member-class-list">
                    {classes.map((item) => {
                      const schedule = classSchedules[item.id] || [];
                      const classAvailable = String(item.status).toUpperCase() === "ACTIVE" && isOfferingOpenNow(selectedOffering);
                      return <label className={`member-class-card${selectedClassId === String(item.id) ? " selected" : ""}`} key={item.id}>
                        <input type="radio" name="selectedCourseClass" value={item.id} checked={selectedClassId === String(item.id)}
                          disabled={!classAvailable || Boolean(existingOfferingRegistration) || registering}
                          onChange={() => { setSelectedClassId(String(item.id)); setRegistrationError(""); setRegistration(null); setConfirmingRegistration(false); setConsultationAvailable(false); }} />
                        <span className="member-class-card-content"><strong>{item.name || `Lớp #${item.id}`}</strong>
                          <span>Coach: {item.coachName || `#${item.coachId ?? "—"}`} · Sức chứa: {item.capacity ?? "—"} người · Trạng thái: {item.status || "—"}</span>
                          <span>{schedule.length ? "Lịch hiện có:" : "Lịch chưa được công bố."}</span>
                          {schedule.map((session) => <small key={session.id}>
                            {formatLocalDateTime(session.startTime)} – {formatLocalDateTime(session.endTime)} · {session.status || "—"}
                          </small>)}
                          {!classAvailable && <small>Lớp hoặc đợt học hiện chưa nhận đăng ký.</small>}
                        </span>
                      </label>;
                    })}
                  </div>}
              {scheduleError && <p className="member-muted" role="status">{scheduleError} Thông tin lịch có thể chưa được công bố.</p>}
              <p className="member-muted">Sức chứa là sức chứa tối đa do hệ thống cung cấp; số chỗ còn lại không được API hiện tại cung cấp. Học phí hiển thị chỉ để tham khảo; máy chủ tính giá và ưu đãi khi đăng ký.</p>
              {registrationError && <p className="banner error" role="alert">{registrationError}</p>}
              {selectedClass && !existingOfferingRegistration && <button type="button" className="btn btn-cyan" disabled={!canRegister || registering}
                onClick={() => setConfirmingRegistration(true)}>Tiếp tục đăng ký lớp đã chọn</button>}
              {selectedClass && confirmingRegistration && canRegister && <section className="member-registration-confirm" aria-labelledby="member-registration-confirm-title">
                <h3 id="member-registration-confirm-title">Xác nhận đăng ký</h3>
                <p><strong>{course.name}</strong> · {course.sportName}</p>
                <p>Đợt: {selectedOffering.name} ({selectedOffering.startDate} – {selectedOffering.endDate})</p>
                <p>Lớp: {selectedClass.name} · Coach {selectedClass.coachName || `#${selectedClass.coachId}`}</p>
                <p>Lịch: {(classSchedules[selectedClass.id] || []).length
                  ? classSchedules[selectedClass.id].map((session) => `${formatLocalDateTime(session.startTime)}–${formatLocalDateTime(session.endTime)}`).join("; ")
                  : "Chưa có lịch cụ thể được công bố."}</p>
                <p>Học phí tham khảo: {formatPrice(selectedOffering.price)}</p>
                <p className="member-muted">Đăng ký sẽ tạm giữ một chỗ ở trạng thái PENDING_PAYMENT. Hạn thanh toán do máy chủ trả về; thao tác này không xác nhận thanh toán.</p>
                <div className="course-consult-actions">
                  <button type="button" className="btn btn-cyan" onClick={() => void confirmRegistration()} disabled={registering}>{registering ? "Đang gửi…" : "Xác nhận đăng ký"}</button>
                  <button type="button" className="btn btn-outline-secondary" onClick={() => setConfirmingRegistration(false)} disabled={registering}>Quay lại</button>
                </div>
              </section>}
            </>}
            {registration && <div className="course-registration-success" role="status">
              <strong>Đăng ký thành công — {registration.status}.</strong>
              <p>Mã đăng ký #{registration.id} · {registration.courseName} · {registration.courseOfferingName} · {registration.className}</p>
              <p>Giá gốc {formatPrice(registration.originalPrice)} · Ưu đãi {formatPrice(registration.membershipDiscount)} · Thành tiền {formatPrice(registration.finalPrice)}</p>
              {registration.status === "PENDING_PAYMENT" && <p role="status">Payment pending. Pay before {formatDate(registration.paymentDeadline)} to keep this seat. Refresh registration history for the current server status.</p>}
              {registration.status === "CONFIRMED" && <p role="status">Payment confirmed and registration active.</p>}
              {registration.status === "EXPIRED" && <p role="status">The payment deadline passed; this registration no longer reserves a seat.</p>}
              <Link to="/member/schedule">Đi đến lịch học</Link>
            </div>}
            {consultationAvailable && <div className="course-level-help">
              <p><strong>Khóa học yêu cầu:</strong> {course.level || "—"}</p>
              {levelsError ? <p role="status">Không tải được trình độ hiện tại.</p>
                : levels ? <p><strong>Trình độ hiện tại:</strong> {currentLevel}{hasVerifiedLevel ? ` (Coach #${level.verifiedByCoachId} xác minh)` : " (Beginner mặc định; chưa được Coach xác minh)"}</p>
                  : <p role="status">Đang tải trình độ hiện tại…</p>}
              <p>Yêu cầu tư vấn không tự thay đổi trình độ và không tạo đăng ký.</p>
              <button type="button" className="btn btn-cyan" onClick={openConsultation}>Yêu cầu tư vấn / đánh giá trình độ</button>
            </div>}
            {consultationSuccess && <div className="course-registration-success" role="status">
              <strong>Đã gửi yêu cầu tư vấn #{consultationSuccess.id}.</strong>
              <p>Yêu cầu này không đăng ký lớp và không tự thay đổi trình độ.</p>
              <Link to="/member/levels-assessments">Xem lịch sử yêu cầu tư vấn</Link>
            </div>}
            {consultationOpen && <form className="course-member-consult-form" onSubmit={submitConsultation}>
              <div><h3>Yêu cầu tư vấn</h3><p>Khóa học: <strong>{course.name}</strong> · Bộ môn: <strong>{course.sportName}</strong></p>
                <p>{profileError ? "Không tải được hồ sơ; vui lòng nhập thông tin liên hệ." : "Thông tin liên hệ được lấy từ hồ sơ nếu có; kiểm tra trước khi gửi."}</p></div>
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
          </section> : auth ? <p className="course-registration-panel">Đăng ký khóa học chỉ dành cho tài khoản Member.</p>
            : <div className="course-registration-panel"><p>Đăng nhập bằng tài khoản Member để xem lớp và đăng ký khóa học.</p>
              <button type="button" className="btn btn-cyan" onClick={goToLogin}>Đăng nhập để đăng ký</button>
            </div>}
        </>}
  </>;

  if (memberRoute) return <div className="layout">
    <Sidebar role="MEMBER" activePage="member-courses" onLogout={onLogout} onNavigate={onNavigate} />
    <main className="main member-course-detail"><header className="member-course-page-header">
      <p className="eyebrow">SPORTCENTER / MEMBER / KHÓA HỌC</p><h1>{course?.name || "Chi tiết khóa học"}</h1>
      {course?.sportName && <p className="subtitle">{course.sportName}</p>}
    </header>{detailContent}</main>
  </div>;

  return <><PageHero title={course?.name || "CHI TIẾT KHÓA HỌC"} subtitle={course?.sportName || "Thông tin chương trình học"} />
    <section className="section"><div className="container-xl course-public-detail">{detailContent}</div></section>
  </>;
}

function formatLocalDateTime(value) {
  if (!value) return "—";
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value);
  return match ? `${match[3]}/${match[2]}/${match[1]} ${match[4]}:${match[5]}` : value;
}
