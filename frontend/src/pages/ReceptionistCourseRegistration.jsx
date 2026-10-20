import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import {
  createPaymentApi, createStaffCourseRegistrationApi, getAllCourseRegistrationsApi,
  getClassesByOfferingApi, getCourseOfferingsByCourseApi, getCoursesBySportApi,
  getMemberPaymentsApi, getMembershipsByMemberApi, getMemberSportLevelsApi,
  getPublicMembershipPlansApi, getSportsApi, getUsersApi, processCounterPaymentApi,
} from "../api";
import { activeSeatCount, counterCoursePaymentPayload, eligibilityIssues, estimateCoursePrice, formatVietnamTime, membershipDiscount, vietnamTime } from "../receptionistRegistration";
import "./Portal.css";
import "./ReceptionistCourseRegistration.css";

const list = (data) => Array.isArray(data) ? data : data?.content ?? [];
const money = (value) => value == null ? "—" : `${Number(value).toLocaleString("vi-VN")} ₫`;
const isStaff = (role) => ["ADMIN", "RECEPTIONIST"].includes(String(role || "").toUpperCase());

export default function ReceptionistCourseRegistration({ token, role, onLogout, onNavigate }) {
  const [members, setMembers] = useState([]), [sports, setSports] = useState([]);
  const [courses, setCourses] = useState([]), [offerings, setOfferings] = useState([]), [classes, setClasses] = useState([]);
  const [allRegistrations, setAllRegistrations] = useState([]), [memberRegistrations, setMemberRegistrations] = useState([]);
  const [payments, setPayments] = useState([]), [memberships, setMemberships] = useState([]), [levels, setLevels] = useState([]);
  const [plans, setPlans] = useState([]), [search, setSearch] = useState("");
  const [memberId, setMemberId] = useState(""), [sportId, setSportId] = useState(""), [courseId, setCourseId] = useState("");
  const [offeringId, setOfferingId] = useState(""), [classId, setClassId] = useState("");
  const [loading, setLoading] = useState(true), [memberLoading, setMemberLoading] = useState(false);
  const [busy, setBusy] = useState(false), [error, setError] = useState(""), [notice, setNotice] = useState("");
  const [uncertainRegistrationId, setUncertainRegistrationId] = useState(null);
  const [now, setNow] = useState(Date.now);
  const lock = useRef(false);
  const memberRequest = useRef(0);

  const refreshAll = useCallback(async () => {
    const [userRows, sportRows, registrationRows, membershipPlans] = await Promise.all([
      getUsersApi(token), getSportsApi(token), getAllCourseRegistrationsApi(token), getPublicMembershipPlansApi(),
    ]);
    setMembers(list(userRows).filter((user) => user.role === "MEMBER" && user.status === "ACTIVE"));
    setSports(list(sportRows).filter((sport) => sport.status === "ACTIVE"));
    setAllRegistrations(list(registrationRows));
    setPlans(list(membershipPlans).filter((plan) => plan.status === "ACTIVE"));
  }, [token]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(async () => {
      try { await refreshAll(); }
      catch (requestError) { if (active) setError(requestError.message || "Không tải được dữ liệu đăng ký."); }
      finally { if (active) setLoading(false); }
    });
    const timer = window.setInterval(() => setNow(Date.now()), 30000);
    return () => { active = false; window.clearInterval(timer); };
  }, [refreshAll]);

  useEffect(() => {
    if (!sportId) return undefined;
    let active = true;
    getCoursesBySportApi(token, sportId).then((data) => { if (active) setCourses(list(data).filter((course) => course.status === "ACTIVE")); })
      .catch((requestError) => { if (active) setError(requestError.message || "Không tải được khóa học."); });
    return () => { active = false; };
  }, [token, sportId]);

  useEffect(() => {
    if (!courseId) return undefined;
    let active = true;
    getCourseOfferingsByCourseApi(token, courseId).then((data) => { if (active) setOfferings(list(data).filter((offering) => offering.status === "OPEN")); })
      .catch((requestError) => { if (active) setError(requestError.message || "Không tải được đợt mở lớp."); });
    return () => { active = false; };
  }, [token, courseId]);

  useEffect(() => {
    if (!offeringId) return undefined;
    let active = true;
    getClassesByOfferingApi(token, offeringId).then((data) => { if (active) setClasses(list(data).filter((item) => item.status === "ACTIVE")); })
      .catch((requestError) => { if (active) setError(requestError.message || "Không tải được danh sách lớp."); });
    return () => { active = false; };
  }, [token, offeringId]);

  const selectedMember = members.find((member) => Number(member.id) === Number(memberId));
  const selectedCourse = courses.find((course) => Number(course.id) === Number(courseId));
  const selectedOffering = offerings.find((offering) => Number(offering.id) === Number(offeringId));
  const selectedClass = classes.find((item) => Number(item.id) === Number(classId));
  const activeLevel = levels.find((level) => Number(level.sportId) === Number(sportId));
  const discountPercent = membershipDiscount(memberships, plans);
  const estimate = selectedOffering ? estimateCoursePrice(selectedOffering.price, discountPercent) : null;
  const issues = selectedMember && selectedCourse ? eligibilityIssues(selectedMember, selectedCourse, activeLevel, selectedOffering, memberRegistrations) : [];
  const occupied = selectedClass ? activeSeatCount(allRegistrations, selectedClass.id, now) : 0;
  const matchingPending = useMemo(() => memberRegistrations.filter((registration) => registration.status === "PENDING_PAYMENT"), [memberRegistrations]);
  const visibleMembers = members.filter((member) => `${member.name} ${member.email} ${member.id}`.toLowerCase().includes(search.trim().toLowerCase())).slice(0, 30);

  const refreshMember = useCallback(async (id) => {
    if (!id) return;
    const requestId = ++memberRequest.current;
    setMemberLoading(true);
    try {
      const [registrationRows, paymentRows, membershipRows, levelRows] = await Promise.all([
        getAllCourseRegistrationsApi(token), getMemberPaymentsApi(token, id), getMembershipsByMemberApi(token, id), getMemberSportLevelsApi(token, id),
      ]);
      if (requestId !== memberRequest.current) return false;
      const own = list(registrationRows).filter((item) => Number(item.memberId) === Number(id));
      const refreshedPayments = list(paymentRows);
      setAllRegistrations(list(registrationRows)); setMemberRegistrations(own); setPayments(list(paymentRows));
      setMemberships(list(membershipRows)); setLevels(list(levelRows));
      setUncertainRegistrationId((current) => current && !refreshedPayments.some((payment) => payment.purpose === "COURSE_REGISTRATION"
        && Number(payment.referenceId) === Number(current) && payment.status === "PENDING") ? null : current);
      return true;
    } catch (requestError) { if (requestId === memberRequest.current) setError(requestError.message || "Không tải được hồ sơ đăng ký của thành viên."); }
    finally { if (requestId === memberRequest.current) setMemberLoading(false); }
    return false;
  }, [token]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active && memberId) void refreshMember(memberId); });
    return () => { active = false; };
  }, [memberId, refreshMember]);

  async function createRegistration() {
    if (!selectedMember || !selectedClass || issues.length || lock.current) return;
    lock.current = true; setBusy(true); setError(""); setNotice("");
    try {
      const response = await createStaffCourseRegistrationApi(token, selectedMember.id, selectedClass.id);
      setMemberRegistrations((current) => [response, ...current]);
      setAllRegistrations((current) => [response, ...current]);
      setNotice(`Đã tạo đăng ký #${response.id} ở trạng thái ${response.status}. Giá gốc ${money(response.originalPrice)}, ưu đãi hội viên ${money(response.membershipDiscount)}, cần thanh toán ${money(response.finalPrice)} trước ${formatVietnamTime(response.paymentDeadline)}.`);
    } catch (requestError) { setError(requestError.message || "Không tạo được đăng ký. Máy chủ đã kiểm tra điều kiện cuối cùng."); }
    finally { lock.current = false; setBusy(false); }
  }

  async function createOrReusePayment(registration) {
    if (!selectedMember || lock.current) return;
    if (Number(uncertainRegistrationId) === Number(registration.id)) {
      setNotice("Kết quả tạo thanh toán trước chưa rõ. Hãy làm mới lịch sử để đối chiếu trước khi tạo lại.");
      return;
    }
    const priorPending = payments.find((payment) => payment.purpose === "COURSE_REGISTRATION"
      && Number(payment.referenceId) === Number(registration.id) && payment.status === "PENDING");
    if (priorPending) {
      setNotice(priorPending.method === "COUNTER"
        ? `Đang tiếp tục yêu cầu COUNTER #${priorPending.id}; thanh toán vẫn chờ xác nhận tại quầy.`
        : `Đã có thanh toán ONLINE #${priorPending.id} đang chờ. Không tạo yêu cầu trùng; kiểm tra trạng thái giao dịch trước.`);
      return;
    }
    lock.current = true; setBusy(true); setError(""); setNotice("");
    let createStarted = false;
    try {
      createStarted = true;
      const payment = await createPaymentApi(token, counterCoursePaymentPayload(selectedMember.id, registration.id));
      setUncertainRegistrationId(null);
      setPayments((current) => [payment, ...current]);
      setNotice(`Đã tạo yêu cầu COUNTER #${payment.id} cho đăng ký #${registration.id}. Trạng thái ${payment.status}; chưa xác nhận đã nhận tiền.`);
      await refreshMember(selectedMember.id);
    } catch (requestError) {
      if (createStarted && (!requestError.status || requestError.status >= 500)) setUncertainRegistrationId(registration.id);
      setError(requestError.message || "Không tạo được yêu cầu thanh toán.");
      await refreshMember(selectedMember.id);
    }
    finally { lock.current = false; setBusy(false); }
  }

  async function confirmReceipt(registration, payment) {
    if (payment.method !== "COUNTER" || payment.status !== "PENDING" || lock.current) return;
    const late = Number.isFinite(vietnamTime(registration.paymentDeadline)) && vietnamTime(registration.paymentDeadline) <= now;
    const text = `Chỉ xác nhận nếu đã thực sự nhận đủ ${money(payment.amount)} từ ${selectedMember?.name} cho khóa ${registration.courseName || "này"}, lớp ${registration.className || "này"} (thanh toán #${payment.id}).${late ? " Hạn giữ chỗ có thể đã qua; máy chủ có thể chuyển khoản thu muộn sang REFUND_REQUIRED mà không khôi phục chỗ." : ""}`;
    if (!window.confirm(text)) return;
    lock.current = true; setBusy(true); setError(""); setNotice("");
    try {
      const result = await processCounterPaymentApi(token, payment.id, true, "Course payment received at reception");
      setNotice(result.status === "REFUND_REQUIRED"
        ? `Ngoại lệ: thanh toán #${payment.id} ở trạng thái REFUND_REQUIRED. Cần nhân viên đối soát/hoàn tiền; đăng ký không được xác nhận.`
        : result.status === "SUCCESS"
          ? `Máy chủ xác nhận thanh toán #${payment.id} thành công. Đang làm mới trạng thái đăng ký.`
          : `Máy chủ trả về trạng thái ${result.status} cho thanh toán #${payment.id}.`);
      await refreshMember(selectedMember.id);
    } catch (requestError) {
      setError(`${requestError.message || "Không nhận được kết quả settlement."} Đang làm mới lịch sử; nếu kết quả chưa rõ, không gửi lại trước khi đối chiếu.`);
      await refreshMember(selectedMember.id);
    } finally { lock.current = false; setBusy(false); }
  }

  if (!token) return <Navigate to="/login" replace />;
  if (!isStaff(role)) return <Navigate to="/" replace />;

  return <div className="layout"><Sidebar role={role} activePage="course-registration" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main receptionist-registration">
      <header className="receptionist-registration-header"><div><p>SPORTCENTER / TIẾP NHẬN HỘI VIÊN</p><h1>Đăng ký khóa học tại quầy</h1><p className="subtitle">Tạo giữ chỗ cho thành viên; chỉ xác nhận lớp khi máy chủ ghi nhận thanh toán thành công.</p></div>
        <button type="button" disabled={loading || busy} onClick={() => { setLoading(true); setError(""); refreshAll().catch((e) => setError(e.message || "Không làm mới được dữ liệu.")).finally(() => setLoading(false)); if (memberId) void refreshMember(memberId); }}>Làm mới</button></header>
      {error && <p className="banner error" role="alert">{error}</p>}{notice && <p className="banner info" role="status">{notice}</p>}
      {loading ? <p role="status">Đang tải dữ liệu…</p> : <>
      <section className="dash-card receptionist-step"><h2>1. Tìm thành viên</h2><label htmlFor="receptionist-member-search">Tên, email hoặc mã thành viên</label><input id="receptionist-member-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm hội viên đang hoạt động" />
          <label htmlFor="receptionist-member">Chọn thành viên</label><select id="receptionist-member" value={memberId} disabled={busy} onChange={(event) => { memberRequest.current++; setMemberId(event.target.value); setMemberLoading(false); setMemberRegistrations([]); setPayments([]); setMemberships([]); setLevels([]); setUncertainRegistrationId(null); setNotice(""); }}><option value="">Chọn thành viên…</option>{visibleMembers.map((member) => <option key={member.id} value={member.id}>{member.name} · #{member.id} · {member.email}</option>)}</select>
          {selectedMember && <p className="receptionist-selected">Đang phục vụ: <strong>{selectedMember.name}</strong> · #{selectedMember.id} · {selectedMember.email}</p>}
        </section>
        <section className="dash-card receptionist-step"><h2>2. Chọn Sport → Course → Offering → Class</h2><div className="receptionist-course-selects">
          <label>Sport<select value={sportId} onChange={(event) => { setSportId(event.target.value); setCourseId(""); setOfferingId(""); setClassId(""); }}><option value="">Chọn Sport…</option>{sports.map((sport) => <option key={sport.id} value={sport.id}>{sport.name}</option>)}</select></label>
          <label>Course<select value={courseId} disabled={!sportId} onChange={(event) => { setCourseId(event.target.value); setOfferingId(""); setClassId(""); }}><option value="">Chọn Course…</option>{courses.map((course) => <option key={course.id} value={course.id}>{course.name}</option>)}</select></label>
          <label>Offering<select value={offeringId} disabled={!courseId} onChange={(event) => { setOfferingId(event.target.value); setClassId(""); }}><option value="">Chọn Offering…</option>{offerings.map((offering) => <option key={offering.id} value={offering.id}>{offering.name} · {money(offering.price)}</option>)}</select></label>
          <label>Class<select value={classId} disabled={!offeringId} onChange={(event) => setClassId(event.target.value)}><option value="">Chọn Class…</option>{classes.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.coachName || "Chưa phân HLV"}</option>)}</select></label>
        </div>
          {selectedClass && <div className="receptionist-class-summary"><h3>{selectedCourse?.name} / {selectedOffering?.name} / {selectedClass.name}</h3>
            <p>{selectedClass.coachName || "Chưa phân HLV"} · Sức chứa {occupied}/{selectedClass.capacity} · {selectedClass.status}</p>
            {selectedOffering && <p>Đăng ký đến: {formatVietnamTime(selectedOffering.registrationDeadline)} · Thời gian khóa: {selectedOffering.startDate} – {selectedOffering.endDate}</p>}
            {selectedMember && <><p>Ưu đãi hội viên hiện tại: {discountPercent}% · Giá dự kiến: {estimate ? money(estimate.finalPrice) : "—"}</p><small>Giá và điều kiện cuối cùng do máy chủ tính khi tạo đăng ký.</small></>}
            {issues.length > 0 ? <ul className="receptionist-eligibility-error" role="alert">{issues.map((issue) => <li key={issue}>{issue}</li>)}</ul>
              : selectedMember && <p className="receptionist-eligibility-ok">Điều kiện hồ sơ sơ bộ đạt. Máy chủ sẽ kiểm tra lại lịch đăng ký, trình độ, chỗ trống và lịch học.</p>}
            <button type="button" disabled={!selectedMember || !selectedClass || issues.length > 0 || busy || memberLoading || occupied >= selectedClass.capacity} onClick={() => void createRegistration()}>{busy ? "Đang xử lý…" : "Tạo giữ chỗ PENDING_PAYMENT"}</button>
          </div>}
        </section>

        <section className="dash-card receptionist-step"><h2>3. Đăng ký đang chờ của thành viên</h2>
          {!selectedMember ? <p>Chọn thành viên để xem các giữ chỗ còn chờ và tiếp tục thanh toán.</p> : memberLoading ? <p role="status">Đang tải đăng ký và thanh toán…</p>
            : matchingPending.length === 0 ? <p className="dash-empty">Thành viên không có đăng ký PENDING_PAYMENT nào.</p>
              : <div className="receptionist-pending-list">{matchingPending.map((registration) => {
                const payment = payments.find((item) => item.purpose === "COURSE_REGISTRATION" && Number(item.referenceId) === Number(registration.id) && item.status === "PENDING");
                const expired = Number.isFinite(vietnamTime(registration.paymentDeadline)) && vietnamTime(registration.paymentDeadline) <= now;
                return <article key={registration.id} className={expired ? "is-expired" : ""}>
                  <div><strong>{registration.courseName} · {registration.courseOfferingName}</strong><span>{registration.className} · Đăng ký #{registration.id}</span>
                    <span>Giá gốc {money(registration.originalPrice)} · Ưu đãi {money(registration.membershipDiscount)} · Cần thu {money(registration.finalPrice)}</span>
                    <span>Hạn thanh toán: {formatVietnamTime(registration.paymentDeadline)} · Trạng thái đăng ký: {registration.status}{expired ? " (có thể đã hết hạn)" : ""}</span>
                    {payment && <span>Thanh toán #{payment.id} · {payment.method} · {payment.status}</span>}
                  </div>
                  {payment?.status === "PENDING" && payment.method === "COUNTER"
                    ? <button type="button" disabled={busy} onClick={() => void confirmReceipt(registration, payment)}>Xác nhận đã nhận tiền #{payment.id}</button>
                    : payment?.status === "PENDING" ? <p role="status">Đang có giao dịch ONLINE #{payment.id}; không tạo thanh toán trùng.</p>
                      : <button type="button" disabled={busy || expired} onClick={() => uncertainRegistrationId === registration.id ? void refreshMember(selectedMember.id) : void createOrReusePayment(registration)}>{expired ? "Giữ chỗ có thể đã hết hạn" : uncertainRegistrationId === registration.id ? "Làm mới để kiểm tra thanh toán" : "Tạo / tiếp tục yêu cầu COUNTER"}</button>}
                </article>;
              })}</div>}
          <p className="subtitle">Giữ chỗ chưa thanh toán tự hết hạn theo hạn hiển thị. Nếu đã nhận tiền sau hạn, chỉ ghi nhận khi tiền thực sự được nhận; máy chủ có thể trả REFUND_REQUIRED và không khôi phục chỗ.</p>
          {payments.some((payment) => payment.purpose === "COURSE_REGISTRATION" && payment.status === "REFUND_REQUIRED") && <div className="receptionist-refund-alert" role="alert"><strong>Cần đối soát thanh toán</strong><ul>{payments.filter((payment) => payment.purpose === "COURSE_REGISTRATION" && payment.status === "REFUND_REQUIRED").map((payment) => <li key={payment.id}>Thanh toán #{payment.id} · {money(payment.amount)} · đăng ký #{payment.referenceId}. Liên hệ người phụ trách để xử lý hoàn tiền.</li>)}</ul></div>}
        </section>
      </>}
    </main>
  </div>;
}
