import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import AdminTopBar from "../components/AdminTopBar";
import { statusLabel } from "../uiPresentation.js";
import "./ConsultationQueue.css";
import {
  completeConsultationRequestApi,
  createConsultationMemberApi,
  getConsultationRequestApi,
  getConsultationRequestsApi,
  getCoachSportsApi,
  getCoachesApi,
  getCurrentUserApi,
  getMemberSportLevelsApi,
  getSkillAssessmentsApi,
  getSportsApi,
  getUsersApi,
  createSkillAssessmentApi,
  getUserApi,
  takeConsultationRequestApi,
} from "../api";

const PAGE_SIZE = 20;
const consultationToday = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const CONSULTATION_MAX_DOB = (() => { const [y, m, d] = consultationToday.split("-").map(Number); const date = new Date(Date.UTC(y, m - 1, d - 1)); return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}-${String(date.getUTCDate()).padStart(2, "0")}`; })();
const STATUSES = ["", "PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"];
const STATUS_LABELS = { PENDING: "Ch\u1edd ti\u1ebfp nh\u1eadn", IN_PROGRESS: "\u0110ang x\u1eed l\u00fd", COMPLETED: "Ho\u00e0n t\u1ea5t", CANCELLED: "\u0110\u00e3 h\u1ee7y" };

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export default function ConsultationQueue({ token, role, currentUserName, onLogout, onNavigate }) {
  const isAdmin = role === "ADMIN";
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedStatus = STATUSES.includes(searchParams.get("status")) ? searchParams.get("status") : "";
  const status = requestedStatus;
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [takingId, setTakingId] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [actorId, setActorId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [resolutionNote, setResolutionNote] = useState("");
  const [completingId, setCompletingId] = useState(null);
  const [memberDateOfBirth, setMemberDateOfBirth] = useState("");
  const [memberPassword, setMemberPassword] = useState("");
  const [creatingMember, setCreatingMember] = useState(false);
  const [memberIdentity, setMemberIdentity] = useState(null);
  const [memberLevels, setMemberLevels] = useState([]);
  const [memberInfoLoading, setMemberInfoLoading] = useState(false);
  const [memberInfoError, setMemberInfoError] = useState("");
  const [consultationAssessments, setConsultationAssessments] = useState([]);
  const [assessmentLookupLoading, setAssessmentLookupLoading] = useState(false);
  const [assessmentLookupError, setAssessmentLookupError] = useState("");
  const [assessmentFormOpen, setAssessmentFormOpen] = useState(false);
  const [eligibleCoaches, setEligibleCoaches] = useState([]);
  const [loadingEligibleCoaches, setLoadingEligibleCoaches] = useState(false);
  const [eligibleCoachError, setEligibleCoachError] = useState("");
  const [selectedCoachId, setSelectedCoachId] = useState("");
  const [assessmentNotes, setAssessmentNotes] = useState("");
  const [assigningAssessment, setAssigningAssessment] = useState(false);
  const [assessmentError, setAssessmentError] = useState("");
  const [assessmentNotice, setAssessmentNotice] = useState("");
  const allowedRole = role === "RECEPTIONIST" || role === "ADMIN";
  const canManageAssessment = Boolean(detail?.status === "IN_PROGRESS" && detail.memberId
    && (role === "ADMIN" || (role === "RECEPTIONIST" && detail.handledBy === actorId)));

  const loadQueue = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getConsultationRequestsApi(token, { status, page, size: PAGE_SIZE });
      setResult(data);
    } catch (requestError) {
      setResult(null);
      if (requestError.status === 401) {
        setError("Your session is missing or expired. Sign in again to view the queue.");
      } else if (requestError.status === 403) {
        setError("Your account is not authorized to view the consultation queue.");
      } else {
        setError(requestError.message || "Could not load consultation requests.");
      }
    } finally {
      setLoading(false);
    }
  }, [token, status, page]);

  useEffect(() => { Promise.resolve().then(loadQueue); }, [loadQueue]);

  useEffect(() => {
    if (!detail && !detailLoading && !detailError) return undefined;
    const closeOnEscape = (event) => { if (event.key === "Escape") { setDetail(null); setDetailError(""); setDetailLoading(false); } };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [detail, detailLoading, detailError]);

  useEffect(() => {
    if (!token || !allowedRole) return;
    getCurrentUserApi(token).then((user) => setActorId(user.id)).catch(() => setActorId(null));
  }, [token, allowedRole]);

  useEffect(() => {
    let active = true;
    if (!detail?.memberId) {
      return () => { active = false; };
    }

    Promise.resolve().then(async () => {
      if (!active) return;
      setMemberInfoLoading(true);
      setMemberInfoError("");
      const [identityResult, levelsResult] = await Promise.allSettled([
        getUserApi(token, detail.memberId),
        getMemberSportLevelsApi(token, detail.memberId),
      ]);
      if (!active) return;
      if (identityResult.status === "fulfilled") setMemberIdentity(identityResult.value);
      else {
        setMemberIdentity(null);
        setMemberInfoError(identityResult.reason?.message || "Không tải được thông tin tài khoản thành viên.");
      }
      if (levelsResult.status === "fulfilled") setMemberLevels(Array.isArray(levelsResult.value) ? levelsResult.value : []);
      else {
        setMemberLevels([]);
        setMemberInfoError((current) => [current, levelsResult.reason?.message || "Không tải được trình độ theo bộ môn."].filter(Boolean).join(" "));
      }
      setMemberInfoLoading(false);

      if (canManageAssessment) {
        setAssessmentLookupLoading(true);
        setAssessmentLookupError("");
        try {
          const rows = await getSkillAssessmentsApi(token);
          if (active) setConsultationAssessments((Array.isArray(rows) ? rows : []).filter(
            (assessment) => Number(assessment.consultationRequestId) === Number(detail.id),
          ));
        } catch (lookupError) {
          if (active) {
            setConsultationAssessments([]);
            setAssessmentLookupError(lookupError.message || "Không kiểm tra được đánh giá đã phân công.");
          }
        } finally {
          if (active) setAssessmentLookupLoading(false);
        }
      } else {
        setConsultationAssessments([]);
      }
    });

    return () => { active = false; };
  }, [token, detail?.id, detail?.memberId, detail?.status, detail?.sportId, detail?.handledBy, canManageAssessment]);

  async function handleOpenDetail(requestId) {
    setDetail(null);
    setDetailError("");
    setAssessmentFormOpen(false);
    setEligibleCoaches([]);
    setSelectedCoachId("");
    setAssessmentNotes("");
    setAssessmentError("");
    setAssessmentNotice("");
    setDetailLoading(true);
    try {
      const request = await getConsultationRequestApi(token, requestId);
      setDetail(request);
      setMemberDateOfBirth(request.dateOfBirth || "");
      setResolutionNote(request.resolutionNote || "");
    } catch (requestError) {
      setDetailError(requestError.status === 403
        ? "Chỉ người được phân công hoặc ADMIN mới xem được yêu cầu này."
        : requestError.message || "Không tải được chi tiết yêu cầu.");
    } finally {
      setDetailLoading(false);
    }
  }

  async function handleCreateMember() {
    if (!detail || !memberDateOfBirth || memberPassword.length < 8) return;
    setCreatingMember(true);
    setDetailError("");
    setNotice("");
    try {
      await createConsultationMemberApi(token, detail.id, {
        dateOfBirth: memberDateOfBirth,
        password: memberPassword,
      });
      setMemberPassword("");
      setMemberDateOfBirth("");
      await handleOpenDetail(detail.id);
      await loadQueue();
      setNotice("Đã tạo và liên kết tài khoản MEMBER. Hãy chuyển thông tin đăng nhập cho khách qua kênh an toàn.");
    } catch (requestError) {
      setDetailError(requestError.status === 409
        ? "Email n\u00e0y \u0111\u00e3 c\u00f3 t\u00e0i kho\u1ea3n Member. H\u00e3y h\u01b0\u1edbng d\u1eabn kh\u00e1ch \u0111\u0103ng nh\u1eadp v\u00e0 t\u1ef1 g\u1eedi y\u00eau c\u1ea7u; h\u1ec7 th\u1ed1ng kh\u00f4ng t\u1ef1 li\u00ean k\u1ebft t\u00e0i kho\u1ea3n."
        : requestError.status === 400
          ? requestError.message || "Yêu cầu email và ngày sinh hợp lệ để tạo tài khoản."
          : requestError.status === 403
            ? "Chỉ người tiếp nhận hoặc ADMIN mới được tạo thành viên từ yêu cầu này."
            : requestError.message || "Không thể tạo tài khoản thành viên.");
    } finally {
      setCreatingMember(false);
    }
  }

  async function handleComplete() {
    if (!detail || detail.status !== "IN_PROGRESS") return;
    setCompletingId(detail.id);
    setDetailError("");
    setNotice("");
    try {
      await completeConsultationRequestApi(token, detail.id, resolutionNote.trim());
      const refreshed = await getConsultationRequestApi(token, detail.id);
      setDetail(refreshed);
      setNotice("Đã hoàn tất cuộc tư vấn. Việc đăng ký khóa học hoặc thanh toán là quy trình riêng.");
      await loadQueue();
    } catch (requestError) {
      setDetailError(requestError.status === 409
        ? "Yêu cầu đã thay đổi trạng thái. Hãy tải lại chi tiết trước khi tiếp tục."
        : requestError.status === 403
          ? "Chỉ người được phân công hoặc ADMIN mới có thể hoàn tất yêu cầu này."
          : requestError.message || "Không thể hoàn tất yêu cầu.");
    } finally {
      setCompletingId(null);
    }
  }

  async function openAssessmentAssignment() {
    if (!canManageAssessment || memberIdentity?.status !== "ACTIVE" || assessmentFormOpen
        || assessmentLookupLoading || assessmentLookupError || consultationAssessments.length > 0) return;
    setAssessmentFormOpen(true);
    setLoadingEligibleCoaches(true);
    setEligibleCoachError("");
    setEligibleCoaches([]);
    setSelectedCoachId("");
    setAssessmentError("");
    setAssessmentNotice("");
    try {
      const [coaches, users, sports] = await Promise.all([getCoachesApi(token), getUsersApi(token), getSportsApi(token)]);
      const consultationSport = (Array.isArray(sports) ? sports : []).find(
        (sport) => Number(sport.id) === Number(detail.sportId),
      );
      if (!consultationSport || consultationSport.status !== "ACTIVE") {
        setEligibleCoachError("Bộ môn của consultation hiện không ACTIVE; backend sẽ không nhận assessment mới.");
        return;
      }
      const activeUserIds = new Set((Array.isArray(users) ? users : [])
        .filter((user) => user.role === "COACH" && user.status === "ACTIVE")
        .map((user) => Number(user.id)));
      const activeCoaches = (Array.isArray(coaches) ? coaches : []).filter(
        (coach) => coach.status === "ACTIVE" && activeUserIds.has(Number(coach.userId)),
      );
      const coachQualifications = await Promise.all(activeCoaches.map(async (coach) => ({
        coach,
        sports: await getCoachSportsApi(token, coach.id),
      })));
      const qualified = coachQualifications
        .filter(({ sports }) => Array.isArray(sports) && sports.some(
          (qualification) => Number(qualification.sportId) === Number(detail.sportId),
        ))
        .map(({ coach }) => coach)
        .sort((left, right) => String(left.userName || "").localeCompare(String(right.userName || ""), "vi"));
      setEligibleCoaches(qualified);
    } catch (requestError) {
      setEligibleCoachError(requestError.status === 403
        ? "Tài khoản không có quyền tải danh sách Coach và chuyên môn."
        : requestError.message || "Không tải được danh sách Coach đủ điều kiện.");
    } finally {
      setLoadingEligibleCoaches(false);
    }
  }

  async function handleAssignAssessment(event) {
    event.preventDefault();
    if (!canManageAssessment || memberIdentity?.status !== "ACTIVE" || !selectedCoachId
        || assigningAssessment || consultationAssessments.length > 0) return;
    if (assessmentNotes.length > 1000) {
      setAssessmentError("Ghi chú không được vượt quá 1000 ký tự.");
      return;
    }
    setAssigningAssessment(true);
    setAssessmentError("");
    setAssessmentNotice("");
    try {
      const created = await createSkillAssessmentApi(token, {
        memberId: Number(detail.memberId),
        sportId: Number(detail.sportId),
        assignedCoachId: Number(selectedCoachId),
        consultationRequestId: Number(detail.id),
        notes: assessmentNotes.trim() || null,
      });
      setConsultationAssessments((current) => [...current, created]);
      setAssessmentFormOpen(false);
      setAssessmentNotes("");
      setAssessmentNotice("Đã phân công Coach đánh giá. Trình độ xác nhận chỉ được cập nhật khi Coach hoàn tất đánh giá.");
    } catch (requestError) {
      setAssessmentError(requestError.status === 409
        ? "Không thể phân công: consultation hoặc assessment đã thay đổi, hoặc đã có assessment đang chờ. Hãy tải lại chi tiết."
        : requestError.status === 403
          ? "Chỉ người tiếp nhận consultation hoặc ADMIN mới được phân công assessment này."
          : requestError.message || "Không thể phân công đánh giá.");
    } finally {
      setAssigningAssessment(false);
    }
  }

  async function handleTake(requestId) {
    setTakingId(requestId);
    setError("");
    setNotice("");
    try {
      await takeConsultationRequestApi(token, requestId);
      setNotice("Request #" + requestId + " was taken.");
      await loadQueue();
    } catch (requestError) {
      if (requestError.status === 409) {
        await loadQueue();
        setError("This request was already taken or changed by another staff member. The queue has been refreshed.");
      } else if (requestError.status === 401) {
        setError("Your session is missing or expired. Sign in again to continue.");
      } else if (requestError.status === 403) {
        setError("Your account is not authorized to take this request.");
      } else {
        setError(requestError.message || "Could not take this request.");
      }
    } finally {
      setTakingId(null);
    }
  }

  const requests = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;

  return (
    <div className="layout">
      <Sidebar role={role} activePage="consultations" onNavigate={onNavigate} onLogout={onLogout} />
      <main className={`main consultation-queue-page${isAdmin ? " consultation-queue-page--admin" : " consultation-queue-page--receptionist"}`}>
        {isAdmin && <AdminTopBar pageLabel="YÊU CẦU TƯ VẤN" displayName={currentUserName} avatarName={currentUserName} />}
        <div className={isAdmin ? "consultation-admin-content" : "consultation-receptionist-content"}>
        <header className="consultation-page-header">
          <div><h1>{"Y\u00eau c\u1ea7u t\u01b0 v\u1ea5n"}</h1><p className="subtitle">{"Ti\u1ebfp nh\u1eadn v\u00e0 theo d\u00f5i c\u00e1c y\u00eau c\u1ea7u t\u01b0 v\u1ea5n c\u1ee7a kh\u00e1ch h\u00e0ng."}</p></div>
          <div className="consultation-summary" aria-live="polite"><strong>{result ? result.totalElements : "\u2014"}</strong><span>{status ? STATUS_LABELS[status] : "Y\u00eau c\u1ea7u theo b\u1ed9 l\u1ecdc"}</span></div>
        </header>
        {!allowedRole && <div className="banner error" role="alert">Chỉ tài khoản Lễ tân hoặc Quản trị viên mới có thể xem danh sách này.</div>}
        {notice && <div className="banner success" role="status">{notice}</div>}
        {error && <div className="banner error" role="alert">{error}</div>}
        <div className="queue-toolbar">
          <div className="consultation-filter-segments" role="group" aria-label={"L\u1ecdc theo tr\u1ea1ng th\u00e1i"}>
            {STATUSES.map((value) => <button key={value || "ALL"} type="button"
              className={status === value ? "active" : ""} aria-pressed={status === value}
              onClick={() => { setSearchParams(value ? { status: value } : {}); setPage(0); setNotice(""); }} disabled={!allowedRole}>
              {value ? STATUS_LABELS[value] : "T\u1ea5t c\u1ea3"}
            </button>)}
          </div>
          <button className="queue-refresh" type="button" onClick={loadQueue} disabled={loading || !allowedRole}>
            {loading ? "\u0110ang t\u1ea3i\u2026" : "L\u00e0m m\u1edbi"}
          </button>
        </div>
        <div className="consultation-workspace">
          <section className="consultation-list-panel" aria-label={"Danh s\u00e1ch y\u00eau c\u1ea7u t\u01b0 v\u1ea5n"}>

        {loading ? (
          <div className="queue-state" role="status">{"\u0110ang t\u1ea3i danh s\u00e1ch y\u00eau c\u1ea7u t\u01b0 v\u1ea5n\u2026"}</div>
        ) : error && !result ? (
          <div className="queue-state">{"Kh\u00f4ng th\u1ec3 t\u1ea3i danh s\u00e1ch. H\u00e3y ki\u1ec3m tra quy\u1ec1n truy c\u1eadp ho\u1eb7c th\u1eed l\u00e0m m\u1edbi."}</div>
        ) : requests.length === 0 ? (
          <div className="queue-state">{"Kh\u00f4ng t\u00ecm th\u1ea5y y\u00eau c\u1ea7u t\u01b0 v\u1ea5n n\u00e0o."}</div>
        ) : (
          <>
            <div className="queue-table-wrap">
              <table className="queue-table">
                <thead><tr>
                  <th>{"M\u00e3 y\u00eau c\u1ea7u"}</th><th>{"Kh\u00e1ch h\u00e0ng"}</th><th>{"\u0110i\u1ec7n tho\u1ea1i"}</th><th>{"B\u1ed9 m\u00f4n"}</th>
                  <th>{"Tr\u1ea1ng th\u00e1i"}</th><th>{"Ng\u00e0y t\u1ea1o"}</th><th>{"Ng\u01b0\u1eddi ti\u1ebfp nh\u1eadn"}</th><th>{"Thao t\u00e1c"}</th>
                </tr></thead>
                <tbody>
                  {requests.map((request) => (
                    <tr key={request.id}>
                      <td>#{request.id}</td>
                      <td>{request.name}</td>
                      <td>{request.phone}</td>
                      <td>{request.sportName || "—"}</td>
                      <td><span className={"status-pill status-" + String(request.status).toLowerCase()}>{STATUS_LABELS[request.status] || request.status}</span></td>
                      <td>{formatDate(request.createdAt)}</td>
                      <td className="consultation-assignee" title={request.handledByName || undefined}>{request.handledBy == null ? "Chưa phân công" : <div><span>{request.handledByName || "Tài khoản nhân viên"}</span><small>#{request.handledBy}</small></div>}</td>
                      <td className="queue-actions">
                        {(role === "ADMIN" || (actorId != null && request.handledBy === actorId)) && (
                          <button type="button" onClick={() => handleOpenDetail(request.id)}>Chi tiết</button>
                        )}
                        {request.status === "PENDING" && allowedRole && (
                          <button className="queue-take" type="button" onClick={() => handleTake(request.id)} disabled={takingId !== null}>
                            {takingId === request.id ? "Đang tiếp nhận…" : "Tiếp nhận"}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="queue-pagination">
              <span>{"Trang "}{page + 1}/{Math.max(totalPages, 1)} | {result?.totalElements ?? requests.length}{" y\u00eau c\u1ea7u"}</span>
              <div>
                <button type="button" onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={page <= 0 || loading}>{"Tr\u01b0\u1edbc"}</button>
                <button type="button" onClick={() => setPage((current) => current + 1)} disabled={loading || totalPages === 0 || page + 1 >= totalPages}>Sau</button>
              </div>
            </div>
          </>
        )}
          </section>
          {(detail || detailLoading || detailError) && <div className="consultation-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) { setDetail(null); setDetailError(""); } }}>
          <section className="consultation-modal" role="dialog" aria-modal="true" aria-labelledby="consultation-detail-title">

        {detailLoading && <div className="queue-state" role="status">Đang tải chi tiết yêu cầu… <button type="button" onClick={() => setDetailLoading(false)}>Đóng</button></div>}
        {detailError && <div className="banner error" role="alert">{detailError}<button type="button" onClick={() => { setDetailError(""); setDetail(null); }}>Đóng</button></div>}
        {detail && (
          <section className="consultation-detail" aria-labelledby="consultation-detail-title">
            <div className="consultation-detail-heading">
              <div><p className="subtitle">Yêu cầu #{detail.id}</p><h2 id="consultation-detail-title">{detail.name}</h2></div>
              <button type="button" onClick={() => { setDetail(null); setDetailError(""); }}>Đóng</button>
            </div>
            <dl className="consultation-detail-grid">
              <div><dt>Điện thoại</dt><dd>{detail.phone}</dd></div>
              <div><dt>Email</dt><dd>{detail.email || "—"}</dd></div>
              <div><dt>Ngày sinh</dt><dd>{detail.dateOfBirth || "Chưa cung cấp"}</dd></div>
              <div><dt>Bộ môn</dt><dd>{detail.sportName}</dd></div>
              <div><dt>Trạng thái</dt><dd><span className={"status-pill status-" + String(detail.status).toLowerCase()}>{STATUS_LABELS[detail.status] || detail.status}</span></dd></div>
              <div><dt>Thành viên liên kết</dt><dd>{detail.memberId ? `#${detail.memberId}` : "Chưa liên kết"}</dd></div>
              <div><dt>Người tiếp nhận hiện tại</dt><dd title={detail.handledByName || undefined}>{detail.handledBy ? <>{detail.handledByName || "Tài khoản nhân viên"} · #{detail.handledBy}</> : "Chưa phân công"}</dd></div>
              <div><dt>Kinh nghiệm</dt><dd>{detail.experience || "—"}</dd></div>
              <div><dt>Ghi chú khách hàng</dt><dd>{detail.note || "—"}</dd></div>
            </dl>
            {detail.memberId && <section className="consultation-linked-member" aria-labelledby="linked-member-title">
              <h3 id="linked-member-title">Thông tin thành viên liên kết</h3>
              <p><strong>Mã thành viên:</strong> #{detail.memberId}</p>
              {memberInfoLoading ? <p role="status">Đang tải thông tin thành viên và trình độ…</p> : <>
                {memberIdentity && <p><strong>Tài khoản:</strong> {memberIdentity.name || "(chưa có tên)"} · {memberIdentity.status || "Chưa rõ trạng thái"}</p>}
                {memberInfoError && <p className="banner error" role="alert">{memberInfoError}</p>}
                {(() => {
                  const level = memberLevels.find((item) => Number(item.sportId) === Number(detail.sportId));
                  if (!level) return <p><strong>Trình độ {detail.sportName}:</strong> Cơ bản (mặc định; chưa có mức đã xác minh cho bộ môn này).</p>;
                  return <div className="consultation-level-result">
                    <p><strong>Trình độ {detail.sportName}:</strong> {level.level}
                      {level.verifiedByCoachId != null
                        ? " · Đã xác minh bởi huấn luyện viên #" + level.verifiedByCoachId
                        : " · Chưa có thông tin xác minh của huấn luyện viên"}</p>
                    {level.verifiedAt && <p><strong>Thời điểm xác minh:</strong> {formatDate(level.verifiedAt)}</p>}
                  </div>;
                })()}
              </>}
            </section>}

            {canManageAssessment && <section className="consultation-assessment-section" aria-labelledby="assessment-assignment-title">
              <h3 id="assessment-assignment-title">Đánh giá trình độ</h3>
              <p>Huấn luyện viên cần hoàn tất đánh giá để xác nhận trình độ. Hoàn tất yêu cầu tư vấn không tự xác nhận trình độ.</p>
              {assessmentNotice && <p className="banner success" role="status">{assessmentNotice}</p>}
              {assessmentLookupLoading ? <p role="status">Đang kiểm tra lượt đánh giá đã liên kết…</p>
                : assessmentLookupError ? <p className="banner error" role="alert">{assessmentLookupError} Để tránh phân công trùng, thao tác đang bị khóa.</p>
                  : consultationAssessments.length > 0 ? <div className="consultation-assessment-existing">
                    <p>Đã có {consultationAssessments.length} lượt đánh giá gắn với yêu cầu tư vấn này:</p>
                    {consultationAssessments.map((assessment) => <p key={assessment.id}>
                      Lượt đánh giá #{assessment.id} · {statusLabel(assessment.status, "assessment")} · Huấn luyện viên #{assessment.assignedCoachId}
                      {assessment.assessedLevel ? ` · ${assessment.assessedLevel}` : ""}
                    </p>)}
                  </div>
                    : memberInfoLoading ? <p role="status">Đang xác minh trạng thái tài khoản MEMBER…</p>
                      : memberIdentity?.status !== "ACTIVE" ? <p className="banner error" role="alert">Chỉ hội viên có tài khoản đang hoạt động mới đủ điều kiện được phân công đánh giá.</p>
                        : !assessmentFormOpen ? <button type="button" onClick={openAssessmentAssignment}
                          disabled={loadingEligibleCoaches || !canManageAssessment}>
                          Phân công đánh giá
                        </button>
                          : <form className="consultation-assessment-form" onSubmit={handleAssignAssessment}>
                            <p>Yêu cầu #{detail.id} · Member #{detail.memberId} · {detail.sportName}</p>
                            {loadingEligibleCoaches ? <p role="status">Đang tải huấn luyện viên đang hoạt động có chuyên môn phù hợp…</p>
                              : eligibleCoachError ? <p className="banner error" role="alert">{eligibleCoachError}</p>
                                : eligibleCoaches.length === 0 ? <p>Không tìm thấy huấn luyện viên đang hoạt động được phân công cho bộ môn này.</p>
                                  : <>
                                    <label htmlFor="assessmentCoach">Huấn luyện viên phụ trách</label>
                                    <select id="assessmentCoach" required value={selectedCoachId}
                                      onChange={(event) => setSelectedCoachId(event.target.value)} disabled={assigningAssessment}>
                                      <option value="">Chọn huấn luyện viên</option>
                                      {eligibleCoaches.map((coach) => <option key={coach.id} value={coach.id}>
                                        Huấn luyện viên #{coach.id} · {coach.userName || "(chưa có tên)"}
                                      </option>)}
                                    </select>
                                    <label htmlFor="assessmentNotes">Ghi chú cho huấn luyện viên (không bắt buộc)</label>
                                    <textarea id="assessmentNotes" maxLength={1000} value={assessmentNotes}
                                      onChange={(event) => setAssessmentNotes(event.target.value)} disabled={assigningAssessment} />
                                    {assessmentError && <p className="banner error" role="alert">{assessmentError}</p>}
                                    <div className="assessment-assignment-actions">
                                      <button type="submit" disabled={assigningAssessment || !selectedCoachId}>
                                        {assigningAssessment ? "Đang phân công…" : "Xác nhận phân công"}
                                      </button>
                                      <button type="button" onClick={() => { setAssessmentFormOpen(false); setAssessmentError(""); }} disabled={assigningAssessment}>Đóng</button>
                                    </div>
                                  </>}
                            {eligibleCoachError && assessmentError && <p className="banner error" role="alert">{assessmentError}</p>}
                          </form>}
            </section>}
            {detail.status === "IN_PROGRESS" && (role === "ADMIN" || (role === "RECEPTIONIST" && detail.handledBy === actorId)) && !detail.memberId && (
              <div className="consultation-member-link-form">
                <h3>{"Y\u00eau c\u1ea7u Guest ch\u01b0a li\u00ean k\u1ebft"}</h3>
                <p>{"Kh\u00f4ng th\u1ec3 li\u00ean k\u1ebft y\u00eau c\u1ea7u Guest v\u1edbi t\u00e0i kho\u1ea3n Member hi\u1ec7n c\u00f3. N\u1ebfu kh\u00e1ch \u0111\u00e3 c\u00f3 t\u00e0i kho\u1ea3n, h\u00e3y h\u01b0\u1edbng d\u1eabn h\u1ecd \u0111\u0103ng nh\u1eadp v\u00e0 t\u1ef1 g\u1eedi y\u00eau c\u1ea7u."}</p>
                {detail.email ? (
                  <form onSubmit={(event) => { event.preventDefault(); handleCreateMember(); }}>
                    <h4>{"T\u1ea1o t\u00e0i kho\u1ea3n Member m\u1edbi"}</h4>
                    <p>{"Email \u0111\u0103ng nh\u1eadp:"} <strong>{detail.email}</strong></p>
                    {detail.dateOfBirth ? (
                      <div className="consultation-member-dob-readonly">
                        <span className="consultation-member-dob-label">{"Ng\u00e0y sinh"}</span>
                        <strong>{detail.dateOfBirth}</strong>
                      </div>
                    ) : (
                      <>
                        <label htmlFor="newMemberDob">{"Ng\u00e0y sinh"}</label>
                        <input id="newMemberDob" type="date" required max={CONSULTATION_MAX_DOB} value={memberDateOfBirth || ""} onChange={(event) => setMemberDateOfBirth(event.target.value)} />
                      </>
                    )}
                    <label htmlFor="newMemberPassword">{"M\u1eadt kh\u1ea9u kh\u1edfi t\u1ea1o (t\u1ed1i thi\u1ec3u 8 k\u00fd t\u1ef1)"}</label>
                    <input id="newMemberPassword" type="password" autoComplete="new-password" minLength={8} required value={memberPassword} onChange={(event) => setMemberPassword(event.target.value)} />
                    <button type="submit" disabled={creatingMember || memberPassword.length < 8 || !memberDateOfBirth}>
                      {creatingMember ? "\u0110ang t\u1ea1o t\u00e0i kho\u1ea3n..." : "T\u1ea1o v\u00e0 li\u00ean k\u1ebft th\u00e0nh vi\u00ean"}
                    </button>
                  </form>
                ) : <p>{"Y\u00eau c\u1ea7u ch\u01b0a c\u00f3 email n\u00ean ch\u01b0a th\u1ec3 t\u1ea1o t\u00e0i kho\u1ea3n Member."}</p>}
              </div>
            )}
            {detail.status === "IN_PROGRESS" && (role === "ADMIN" || detail.handledBy === actorId) && (
              <div className="consultation-complete-form">
                <label htmlFor="resolutionNote">Ghi chú kết quả tư vấn (không bắt buộc)</label>
                <textarea id="resolutionNote" maxLength={1000} value={resolutionNote} onChange={(event) => setResolutionNote(event.target.value)} />
                <button type="button" onClick={handleComplete} disabled={completingId === detail.id}>
                  {completingId === detail.id ? "Đang hoàn tất…" : "Hoàn tất tư vấn"}
                </button>
              </div>
            )}
          </section>
        )}
          </section></div>}
        </div>
        </div>
      </main>
    </div>
  );
}

