import { useCallback, useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
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
  linkConsultationMemberApi,
  takeConsultationRequestApi,
} from "../api";
import "./ConsultationQueue.css";

const PAGE_SIZE = 20;
const STATUS_ALL = "";
const STATUSES = [STATUS_ALL, "PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"];
const STATUS_META = {
  PENDING:     { label: "Chờ tiếp nhận", cls: "cq-badge--amber"  },
  IN_PROGRESS: { label: "Đang xử lý",    cls: "cq-badge--blue"   },
  COMPLETED:   { label: "Hoàn tất",       cls: "cq-badge--green"  },
  CANCELLED:   { label: "Đã hủy",         cls: "cq-badge--slate"  },
};
const STATUS_LABELS = {
  "":           "Tất cả trạng thái",
  PENDING:      "Chờ tiếp nhận",
  IN_PROGRESS:  "Đang xử lý",
  COMPLETED:    "Hoàn tất",
  CANCELLED:    "Đã hủy",
};

function fmtDate(v) {
  if (!v) return "—";
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? v
    : d.toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

/* ── tiny SVG icon ── */
const Ic = ({ d, size = 16 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
    aria-hidden="true">
    {d.split(" M").map((p, i) => <path key={i} d={(i ? "M" : "") + p} />)}
  </svg>
);
const ICONS = {
  refresh: "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16 M8 16H3v5",
  close:   "M18 6 6 18 M6 6l12 12",
  check:   "M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4 12 14.01l-3-3",
  link:    "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71 M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
  user:    "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8",
  plus:    "M12 5v14 M5 12h14",
  search:  "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16 M21 21l-4.35-4.35",
  phone:   "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.11 10.5 19.79 19.79 0 0 1 1.07 4.18 2 2 0 0 1 3.03 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L7.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z",
  mail:    "M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z M22 6l-10 7L2 6",
  sport:   "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20 M12 8v4l3 3",
  flag:    "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z M4 22v-7",
  note:    "M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7 M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4z",
  chevRight: "M9 18l6-6-6-6",
  info: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M12 8v4 M12 16h.01",
};

/* ── Step indicator ── */
const STEPS = ["Tiếp nhận", "Liên kết TV", "Đánh giá", "Hoàn tất"];
function stepOf(d) {
  if (!d) return 0;
  if (d.status === "COMPLETED") return 3;
  if (d.status === "IN_PROGRESS" && d.memberId) return 2;
  if (d.status === "IN_PROGRESS") return 1;
  return 0;
}
function StepBar({ detail }) {
  const cur = stepOf(detail);
  return (
    <div className="cq-stepbar">
      {STEPS.map((label, i) => (
        <div key={label} className={"cq-step" + (i < cur ? " done" : i === cur ? " active" : "")}>
          <div className="cq-step-dot">
            {i < cur ? <Ic d={ICONS.check} size={13} /> : <span>{i + 1}</span>}
          </div>
          <span>{label}</span>
          {i < STEPS.length - 1 && <div className="cq-step-line" />}
        </div>
      ))}
    </div>
  );
}

/* ── Info field in drawer ── */
function DField({ icon, label, value, accent }) {
  return (
    <div className="cq-dfield">
      <div className="cq-dfield-icon"><Ic d={icon} size={13} /></div>
      <div>
        <div className="cq-dfield-label">{label}</div>
        <div className={"cq-dfield-value" + (accent ? " cq-accent" : "")}>{value || "—"}</div>
      </div>
    </div>
  );
}

/* ════════════════════════════════════════════════════════════════════ */
const initialsOf = (name) => {
  if (!name) return "LT";
  const parts = name.trim().split(/\s+/);
  return parts.slice(-2).map((w) => w[0]).join("").toUpperCase();
};

export default function ConsultationQueue({ token, role, onLogout, onNavigate }) {
  const [status, setStatus]   = useState(STATUS_ALL);
  const [page, setPage]       = useState(0);
  const [result, setResult]   = useState(null);
  const [loading, setLoading] = useState(true);
  const [takingId, setTakingId] = useState(null);
  const [error, setError]     = useState("");
  const [notice, setNotice]   = useState("");
  const [actorId, setActorId] = useState(null);
  const [currentUser, setCurrentUser] = useState(null);
  const [detail, setDetail]   = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError]     = useState("");
  const [resolutionNote, setResolutionNote] = useState("");
  const [completingId, setCompletingId]   = useState(null);

  /* member link */
  const [memberCandidates, setMemberCandidates] = useState([]);
  const [candidateSearch, setCandidateSearch]   = useState("");
  const [selectedMemberId, setSelectedMemberId] = useState("");
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  const [linkingMember, setLinkingMember] = useState(false);
  const [memberDateOfBirth, setMemberDateOfBirth] = useState("");
  const [memberPassword, setMemberPassword] = useState("");
  const [creatingMember, setCreatingMember] = useState(false);

  /* member info */
  const [memberIdentity, setMemberIdentity]   = useState(null);
  const [memberLevels, setMemberLevels]       = useState([]);
  const [memberInfoLoading, setMemberInfoLoading] = useState(false);
  const [memberInfoError, setMemberInfoError] = useState("");

  /* assessment */
  const [consultationAssessments, setConsultationAssessments] = useState([]);
  const [assessmentLookupLoading, setAssessmentLookupLoading] = useState(false);
  const [assessmentLookupError, setAssessmentLookupError]     = useState("");
  const [assessmentFormOpen, setAssessmentFormOpen]           = useState(false);
  const [eligibleCoaches, setEligibleCoaches]                 = useState([]);
  const [loadingEligibleCoaches, setLoadingEligibleCoaches]   = useState(false);
  const [eligibleCoachError, setEligibleCoachError]           = useState("");
  const [selectedCoachId, setSelectedCoachId]                 = useState("");
  const [assessmentNotes, setAssessmentNotes]                 = useState("");
  const [assigningAssessment, setAssigningAssessment]         = useState(false);
  const [assessmentError, setAssessmentError]                 = useState("");
  const [assessmentNotice, setAssessmentNotice]               = useState("");

  const allowedRole       = role === "RECEPTIONIST" || role === "ADMIN";
  const canManageAssessment = Boolean(
    detail?.status === "IN_PROGRESS" && detail.memberId &&
    (role === "ADMIN" || (role === "RECEPTIONIST" && detail.handledBy === actorId))
  );

  /* ── load queue ── */
  const loadQueue = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const data = await getConsultationRequestsApi(token, { status, page, size: PAGE_SIZE });
      setResult(data);
    } catch (err) {
      setResult(null);
      setError(err.status === 401 ? "Phiên đăng nhập đã hết hạn."
        : err.status === 403    ? "Tài khoản không có quyền xem danh sách yêu cầu."
        : err.message           || "Không tải được danh sách yêu cầu tư vấn.");
    } finally { setLoading(false); }
  }, [token, status, page]);

  useEffect(() => { loadQueue(); }, [loadQueue]);
  useEffect(() => {
    if (!token || !allowedRole) return;
    getCurrentUserApi(token).then((u) => { setActorId(u.id); setCurrentUser(u); }).catch(() => { setActorId(null); setCurrentUser(null); });
  }, [token, allowedRole]);

  /* ── load member info when detail changes ── */
  useEffect(() => {
    let active = true;
    if (!detail?.memberId) return () => { active = false; };
    (async () => {
      setMemberInfoLoading(true); setMemberInfoError("");
      const [ir, lr] = await Promise.allSettled([
        getUserApi(token, detail.memberId),
        getMemberSportLevelsApi(token, detail.memberId),
      ]);
      if (!active) return;
      setMemberIdentity(ir.status === "fulfilled" ? ir.value : null);
      if (ir.status !== "fulfilled") setMemberInfoError(ir.reason?.message || "Không tải được thông tin thành viên.");
      setMemberLevels(lr.status === "fulfilled" && Array.isArray(lr.value) ? lr.value : []);
      setMemberInfoLoading(false);

      if (canManageAssessment) {
        setAssessmentLookupLoading(true); setAssessmentLookupError("");
        try {
          const rows = await getSkillAssessmentsApi(token);
          if (active) setConsultationAssessments(
            (Array.isArray(rows) ? rows : []).filter((a) => Number(a.consultationRequestId) === Number(detail.id))
          );
        } catch (e) {
          if (active) { setConsultationAssessments([]); setAssessmentLookupError(e.message || "Không kiểm tra được đánh giá."); }
        } finally { if (active) setAssessmentLookupLoading(false); }
      } else setConsultationAssessments([]);
    })();
    return () => { active = false; };
  }, [token, detail?.id, detail?.memberId, detail?.status, detail?.handledBy, canManageAssessment]);

  /* ── open detail ── */
  async function handleOpenDetail(requestId) {
    setDetail(null); setDetailError(""); setAssessmentFormOpen(false);
    setEligibleCoaches([]); setSelectedCoachId(""); setAssessmentNotes("");
    setAssessmentError(""); setAssessmentNotice(""); setDetailLoading(true);
    try {
      const req = await getConsultationRequestApi(token, requestId);
      setDetail(req); setResolutionNote(req.resolutionNote || "");
    } catch (err) {
      setDetailError(err.status === 403
        ? "Chỉ người được phân công hoặc ADMIN mới xem được yêu cầu này."
        : err.message || "Không tải được chi tiết yêu cầu.");
    } finally { setDetailLoading(false); }
  }

  /* ── take ── */
  async function handleTake(requestId) {
    setTakingId(requestId); setError(""); setNotice("");
    try {
      await takeConsultationRequestApi(token, requestId);
      setNotice("Đã tiếp nhận yêu cầu #" + requestId + ".");
      await loadQueue();
    } catch (err) {
      if (err.status === 409) { await loadQueue(); setError("Yêu cầu đã được nhân viên khác tiếp nhận. Danh sách đã làm mới."); }
      else setError(err.message || "Không thể tiếp nhận yêu cầu.");
    } finally { setTakingId(null); }
  }

  /* ── link member candidates ── */
  async function loadMemberCandidates() {
    setLoadingCandidates(true); setDetailError("");
    try {
      const users = await getUsersApi(token);
      setMemberCandidates(Array.isArray(users) ? users.filter((u) => u.role === "MEMBER" && u.status === "ACTIVE") : []);
      setSelectedMemberId("");
    } catch (err) {
      setDetailError(err.status === 403 ? "Không có quyền tra cứu thành viên." : err.message || "Không tải được danh sách thành viên.");
    } finally { setLoadingCandidates(false); }
  }

  async function handleLinkMember() {
    if (!detail || !selectedMemberId) return;
    setLinkingMember(true); setDetailError(""); setNotice("");
    try {
      await linkConsultationMemberApi(token, detail.id, Number(selectedMemberId));
      await handleOpenDetail(detail.id); await loadQueue();
      setNotice("Đã liên kết yêu cầu với tài khoản MEMBER đã chọn.");
    } catch (err) {
      setDetailError(err.status === 409 ? "Yêu cầu đã được liên kết hoặc tài khoản không đủ điều kiện."
        : err.status === 403 ? "Chỉ người tiếp nhận hoặc ADMIN mới được liên kết yêu cầu này."
        : err.message || "Không thể liên kết thành viên.");
    } finally { setLinkingMember(false); }
  }

  async function handleCreateMember() {
    if (!detail || !memberDateOfBirth || memberPassword.length < 8) return;
    setCreatingMember(true); setDetailError(""); setNotice("");
    try {
      await createConsultationMemberApi(token, detail.id, { dateOfBirth: memberDateOfBirth, password: memberPassword });
      setMemberPassword(""); setMemberDateOfBirth("");
      await handleOpenDetail(detail.id); await loadQueue();
      setNotice("Đã tạo và liên kết tài khoản MEMBER. Chuyển thông tin đăng nhập qua kênh an toàn.");
    } catch (err) {
      setDetailError(err.status === 409 ? "Email đã có tài khoản. Hãy liên kết tài khoản MEMBER hiện có."
        : err.status === 400 ? err.message || "Email và ngày sinh hợp lệ là bắt buộc."
        : err.status === 403 ? "Chỉ người tiếp nhận hoặc ADMIN mới được tạo thành viên."
        : err.message || "Không thể tạo tài khoản thành viên.");
    } finally { setCreatingMember(false); }
  }

  async function handleComplete() {
    if (!detail || detail.status !== "IN_PROGRESS") return;
    setCompletingId(detail.id); setDetailError(""); setNotice("");
    try {
      await completeConsultationRequestApi(token, detail.id, resolutionNote.trim());
      const refreshed = await getConsultationRequestApi(token, detail.id);
      setDetail(refreshed);
      setNotice("Đã hoàn tất cuộc tư vấn.");
      await loadQueue();
    } catch (err) {
      setDetailError(err.status === 409 ? "Yêu cầu đã thay đổi trạng thái. Hãy tải lại chi tiết."
        : err.status === 403 ? "Chỉ người được phân công hoặc ADMIN mới có thể hoàn tất yêu cầu này."
        : err.message || "Không thể hoàn tất yêu cầu.");
    } finally { setCompletingId(null); }
  }

  async function openAssessmentAssignment() {
    if (!canManageAssessment || memberIdentity?.status !== "ACTIVE" || assessmentFormOpen
      || assessmentLookupLoading || assessmentLookupError || consultationAssessments.length > 0) return;
    setAssessmentFormOpen(true); setLoadingEligibleCoaches(true);
    setEligibleCoachError(""); setEligibleCoaches([]);
    setSelectedCoachId(""); setAssessmentError(""); setAssessmentNotice("");
    try {
      const [coaches, users, sports] = await Promise.all([getCoachesApi(token), getUsersApi(token), getSportsApi(token)]);
      const sport = (Array.isArray(sports) ? sports : []).find((s) => Number(s.id) === Number(detail.sportId));
      if (!sport || sport.status !== "ACTIVE") {
        setEligibleCoachError("Bộ môn hiện không ACTIVE."); return;
      }
      const activeUserIds = new Set((Array.isArray(users) ? users : [])
        .filter((u) => u.role === "COACH" && u.status === "ACTIVE").map((u) => Number(u.id)));
      const activeCoaches = (Array.isArray(coaches) ? coaches : [])
        .filter((c) => c.status === "ACTIVE" && activeUserIds.has(Number(c.userId)));
      const qualified = (await Promise.all(activeCoaches.map(async (c) => ({
        coach: c, sports: await getCoachSportsApi(token, c.id),
      })))).filter(({ sports }) => Array.isArray(sports) && sports.some((q) => Number(q.sportId) === Number(detail.sportId)))
        .map(({ coach }) => coach)
        .sort((a, b) => String(a.userName || "").localeCompare(String(b.userName || ""), "vi"));
      setEligibleCoaches(qualified);
    } catch (err) {
      setEligibleCoachError(err.status === 403 ? "Không có quyền tải danh sách Coach." : err.message || "Không tải được danh sách Coach.");
    } finally { setLoadingEligibleCoaches(false); }
  }

  async function handleAssignAssessment(e) {
    e.preventDefault();
    if (!canManageAssessment || !selectedCoachId || assigningAssessment || consultationAssessments.length > 0) return;
    if (assessmentNotes.length > 1000) { setAssessmentError("Ghi chú không được vượt quá 1000 ký tự."); return; }
    setAssigningAssessment(true); setAssessmentError(""); setAssessmentNotice("");
    try {
      const created = await createSkillAssessmentApi(token, {
        memberId: Number(detail.memberId), sportId: Number(detail.sportId),
        assignedCoachId: Number(selectedCoachId), consultationRequestId: Number(detail.id),
        notes: assessmentNotes.trim() || null,
      });
      setConsultationAssessments((c) => [...c, created]);
      setAssessmentFormOpen(false); setAssessmentNotes("");
      setAssessmentNotice("Đã phân công Coach đánh giá thành công.");
    } catch (err) {
      setAssessmentError(err.status === 409 ? "Đã có assessment đang chờ hoặc dữ liệu thay đổi. Hãy tải lại."
        : err.status === 403 ? "Chỉ người tiếp nhận hoặc ADMIN mới được phân công assessment."
        : err.message || "Không thể phân công đánh giá.");
    } finally { setAssigningAssessment(false); }
  }

  /* ── derived ── */
  const requests   = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;

  const counts = requests.reduce((acc, r) => {
    acc[r.status] = (acc[r.status] || 0) + 1;
    return acc;
  }, {});

  const canManageDetail = (r) =>
    r.status === "PENDING" ||
    role === "ADMIN" ||
    (actorId != null && r.handledBy === actorId);

  /* ══════════════════════════════════ render ══════════════════════════ */
  return (
    <div className="layout">
      <Sidebar role={role} activePage="consultations" onNavigate={onNavigate} onLogout={onLogout} />

      <main className="main cq-main">
        <header className="rc-topbar">
          <nav className="rc-crumb" aria-label="Breadcrumb">
            <span style={{ cursor: "pointer" }} onClick={() => onNavigate("dashboard")}>SPORTS CENTER</span>
            <i>›</i>
            <span style={{ cursor: "pointer" }} onClick={() => onNavigate("dashboard")}>{role === "ADMIN" ? "QUẢN TRỊ" : "LỄ TÂN"}</span>
            <i>›</i>
            <b>YÊU CẦU TƯ VẤN</b>
          </nav>

          <div className="rc-user">
            <span className="rc-avatar">{initialsOf(currentUser?.name)}</span>
            <div>
              <b>{currentUser?.name || "Lê Minh Trang"}</b>
              <small>{role === "ADMIN" ? "Quản trị viên" : "Quầy lễ tân 01"}</small>
            </div>
          </div>
        </header>
        {/* ── page header ── */}
        <div className="cq-header">
          <div>
            <h1 className="cq-title">Yêu cầu tư vấn</h1>
            <p className="cq-sub">Tiếp nhận và theo dõi các yêu cầu tư vấn của khách hàng.</p>
          </div>
          <div className="cq-header-pills">
            {result && (
              <>
                <span className="cq-stat-pill cq-stat-pill--amber">
                  <span className="cq-stat-dot" />
                  Chờ tiếp nhận: <strong>{(result.content ?? []).filter((r) => r.status === "PENDING").length}</strong>
                </span>
                <span className="cq-stat-pill cq-stat-pill--blue">
                  <span className="cq-stat-dot" />
                  Đang xử lý: <strong>{(result.content ?? []).filter((r) => r.status === "IN_PROGRESS").length}</strong>
                </span>
              </>
            )}
          </div>
        </div>

        {!allowedRole && <div className="cq-alert cq-alert--error">Chỉ RECEPTIONIST và ADMIN được truy cập trang này.</div>}
        {notice && <div className="cq-alert cq-alert--success" role="status">{notice}</div>}
        {error  && <div className="cq-alert cq-alert--error"  role="alert">{error}</div>}

        {/* ── toolbar ── */}
        <div className="cq-toolbar">
          <div className="cq-filter-group">
            {STATUSES.map((s) => (
              <button
                key={s || "ALL"}
                className={"cq-filter-btn" + (status === s ? " active" : "")}
                onClick={() => { setStatus(s); setPage(0); setNotice(""); }}
                disabled={!allowedRole}
              >
                {STATUS_LABELS[s]}
                {s && (result?.content ?? []).filter((r) => r.status === s).length > 0 && (
                  <span className={"cq-filter-count " + (s === "PENDING" ? "amber" : s === "IN_PROGRESS" ? "blue" : "")}>
                    {(result?.content ?? []).filter((r) => r.status === s).length}
                  </span>
                )}
              </button>
            ))}
          </div>
          <button className="cq-btn-ghost" onClick={loadQueue} disabled={loading || !allowedRole}>
            <Ic d={ICONS.refresh} size={15} />
            Làm mới
          </button>
        </div>

        {/* ── split body ── */}
        <div className={"cq-body" + (detail ? " cq-body--split" : "")}>

          {/* ════ TABLE ════ */}
          <div className="cq-table-col">
            {loading ? (
              <div className="cq-state">Đang tải danh sách yêu cầu tư vấn…</div>
            ) : !allowedRole ? null
              : requests.length === 0 ? (
                <div className="cq-state">Không tìm thấy yêu cầu tư vấn nào.</div>
              ) : (
                <>
                  <div className="cq-table-wrap">
                    <table className="cq-table">
                      <thead>
                        <tr>
                          <th>MÃ YÊU CẦU</th>
                          <th>KHÁCH HÀNG</th>
                          <th>ĐIỆN THOẠI</th>
                          <th>BỘ MÔN</th>
                          <th>TRẠNG THÁI</th>
                          <th>NGÀY TẠO</th>
                          <th>NGƯỜI TIẾP NHẬN</th>
                          <th className="cq-th-right">THAO TÁC</th>
                        </tr>
                      </thead>
                      <tbody>
                        {requests.map((r) => {
                          const isActive = detail?.id === r.id;
                          const meta = STATUS_META[r.status] || { label: r.status, cls: "cq-badge--slate" };
                          return (
                            <tr
                              key={r.id}
                              className={"cq-row" + (isActive ? " cq-row--active" : "")}
                            >
                              <td className={"cq-td-id" + (isActive ? " cq-id-active" : "")}>#{r.id}</td>
                              <td>
                                <div className="cq-name">{r.name}</div>
                                {r.email && <div className="cq-email">{r.email}</div>}
                              </td>
                              <td className="cq-td-sec">{r.phone || "—"}</td>
                              <td>
                                {r.sportName
                                  ? <span className="cq-sport-tag">{r.sportName}</span>
                                  : <span className="cq-td-sec">—</span>}
                              </td>
                              <td>
                                <span className={"cq-badge " + meta.cls}>{meta.label}</span>
                              </td>
                              <td className="cq-td-sec cq-nowrap">{fmtDate(r.createdAt)}</td>
                              <td className="cq-td-sec">
                                {r.handledBy != null ? <span className="cq-handler">#{r.handledBy}</span> : "—"}
                              </td>
                              <td className="cq-td-actions">
                                {r.status === "PENDING" && allowedRole && (
                                  <button
                                    className="cq-btn-take"
                                    onClick={() => handleTake(r.id)}
                                    disabled={takingId !== null}
                                  >
                                    {takingId === r.id ? "Đang tiếp nhận…" : "Tiếp nhận"}
                                  </button>
                                )}
                                {canManageDetail(r) && (
                                  <button
                                    className={"cq-btn-detail" + (isActive ? " active" : "")}
                                    onClick={() => isActive ? (setDetail(null), setDetailError("")) : handleOpenDetail(r.id)}
                                  >
                                    {isActive ? "Đang xem" : "Chi tiết"}
                                  </button>
                                )}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>

                  {/* pagination */}
                  <div className="cq-pagination">
                    <span className="cq-page-info">
                      Trang <strong>{page + 1}/{Math.max(totalPages, 1)}</strong> | <strong>{result?.totalElements ?? requests.length}</strong> yêu cầu
                    </span>
                    <div className="cq-page-btns">
                      <button className="cq-page-btn" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page <= 0 || loading}>Trước</button>
                      <button className="cq-page-btn" onClick={() => setPage((p) => p + 1)} disabled={loading || totalPages === 0 || page + 1 >= totalPages}>Sau</button>
                    </div>
                  </div>
                </>
              )}
          </div>

          {/* ════ DETAIL DRAWER ════ */}
          {detail && (
            <aside className="cq-drawer">
              {/* drawer header */}
              <div className="cq-drawer-head">
                <div>
                  <div className="cq-drawer-id">
                    YÊU CẦU #{detail.id}
                    <span className={"cq-badge " + (STATUS_META[detail.status]?.cls || "cq-badge--slate")}>
                      {STATUS_META[detail.status]?.label || detail.status}
                    </span>
                  </div>
                  <div className="cq-drawer-name">{detail.name}</div>
                </div>
                <button className="cq-close-btn" onClick={() => { setDetail(null); setDetailError(""); }} title="Đóng">
                  <Ic d={ICONS.close} size={16} />
                </button>
              </div>

              {detailLoading && <div className="cq-state">Đang tải chi tiết yêu cầu…</div>}
              {detailError  && <div className="cq-alert cq-alert--error cq-alert--inset">{detailError}</div>}
              {assessmentNotice && <div className="cq-alert cq-alert--success cq-alert--inset">{assessmentNotice}</div>}

              {!detailLoading && (
                <div className="cq-drawer-body">
                  {/* step bar */}
                  <div className="cq-drawer-section">
                    <StepBar detail={detail} />
                  </div>

                  {/* info fields */}
                  <div className="cq-drawer-section">
                    <div className="cq-section-title">THÔNG TIN YÊU CẦU</div>
                    <div className="cq-dfields-grid">
                      <DField icon={ICONS.phone} label="Điện thoại" value={detail.phone} />
                      <DField icon={ICONS.mail}  label="Email"      value={detail.email} />
                      <DField icon={ICONS.sport} label="Bộ môn"     value={detail.sportName} />
                      <DField icon={ICONS.flag}
                        label="Trạng thái"
                        value={STATUS_META[detail.status]?.label || detail.status}
                        accent
                      />
                      <DField icon={ICONS.link}
                        label="Thành viên liên kết"
                        value={detail.memberId ? `#${detail.memberId}` : "Chưa liên kết"}
                        accent={!detail.memberId}
                      />
                      <DField icon={ICONS.user}
                        label="Tiếp nhận bởi"
                        value={detail.handledBy ? `#${detail.handledBy}` : "—"}
                      />
                    </div>
                    {detail.experience && (
                      <div className="cq-drawer-note">
                        <div className="cq-note-label">Kinh nghiệm</div>
                        <div className="cq-note-text">{detail.experience}</div>
                      </div>
                    )}
                    {detail.note && (
                      <div className="cq-drawer-note">
                        <div className="cq-note-label">Ghi chú khách hàng</div>
                        <div className="cq-note-text">{detail.note}</div>
                      </div>
                    )}
                  </div>

                  {/* ── linked member section ── */}
                  {detail.memberId && (
                    <div className="cq-drawer-section">
                      <div className="cq-section-title">THÀNH VIÊN ĐÃ LIÊN KẾT</div>
                      {memberInfoLoading ? (
                        <div className="cq-state-sm">Đang tải thông tin thành viên…</div>
                      ) : (
                        <>
                          {memberIdentity && (
                            <div className="cq-member-card">
                              <div className="cq-member-name">{memberIdentity.name}</div>
                              <span className={"cq-badge " + (memberIdentity.status === "ACTIVE" ? "cq-badge--green" : "cq-badge--slate")}>
                                {memberIdentity.status}
                              </span>
                            </div>
                          )}
                          {memberInfoError && <div className="cq-alert cq-alert--error cq-alert--inset">{memberInfoError}</div>}
                          {(() => {
                            const lv = memberLevels.find((l) => Number(l.sportId) === Number(detail.sportId));
                            return (
                              <div className="cq-level-row">
                                <span className="cq-note-label">Trình độ {detail.sportName}:</span>
                                <span>{lv ? `${lv.level}${lv.verifiedByCoachId ? ` · Coach #${lv.verifiedByCoachId}` : ""}` : "Beginner (chưa xác minh)"}</span>
                              </div>
                            );
                          })()}
                        </>
                      )}
                    </div>
                  )}

                  {/* ── assessment section ── */}
                  {canManageAssessment && (
                    <div className="cq-drawer-section">
                      <div className="cq-section-title">ĐÁNH GIÁ TRÌNH ĐỘ</div>
                      {assessmentLookupLoading ? (
                        <div className="cq-state-sm">Đang kiểm tra assessment…</div>
                      ) : assessmentLookupError ? (
                        <div className="cq-alert cq-alert--error cq-alert--inset">{assessmentLookupError}</div>
                      ) : consultationAssessments.length > 0 ? (
                        consultationAssessments.map((a) => (
                          <div key={a.id} className="cq-assessment-card">
                            <div>Assessment #{a.id} · Coach #{a.assignedCoachId}</div>
                            <span className={"cq-badge " + (a.status === "COMPLETED" ? "cq-badge--green" : "cq-badge--blue")}>{a.status}</span>
                          </div>
                        ))
                      ) : memberIdentity?.status !== "ACTIVE" ? (
                        <div className="cq-alert cq-alert--error cq-alert--inset">Chỉ MEMBER ACTIVE mới đủ điều kiện được phân công đánh giá.</div>
                      ) : !assessmentFormOpen ? (
                        <button className="cq-btn-outline" onClick={openAssessmentAssignment} disabled={loadingEligibleCoaches}>
                          <Ic d={ICONS.plus} size={14} />
                          Phân công đánh giá
                        </button>
                      ) : (
                        <form className="cq-assessment-form" onSubmit={handleAssignAssessment}>
                          <div className="cq-form-row">
                            <label>Coach phụ trách
                              <select required value={selectedCoachId} onChange={(e) => setSelectedCoachId(e.target.value)} disabled={assigningAssessment}>
                                <option value="">— Chọn Coach —</option>
                                {loadingEligibleCoaches
                                  ? <option disabled>Đang tải…</option>
                                  : eligibleCoaches.map((c) => (
                                    <option key={c.id} value={c.id}>Coach #{c.id} · {c.userName || "(chưa có tên)"}</option>
                                  ))}
                              </select>
                            </label>
                            {eligibleCoachError && <div className="cq-alert cq-alert--error cq-alert--inset">{eligibleCoachError}</div>}
                            <label>Ghi chú cho Coach (không bắt buộc)
                              <textarea maxLength={1000} value={assessmentNotes} onChange={(e) => setAssessmentNotes(e.target.value)} disabled={assigningAssessment} rows={3} />
                            </label>
                          </div>
                          {assessmentError && <div className="cq-alert cq-alert--error cq-alert--inset">{assessmentError}</div>}
                          <div className="cq-form-actions">
                            <button className="cq-btn-primary" type="submit" disabled={assigningAssessment || !selectedCoachId}>
                              {assigningAssessment ? "Đang phân công…" : "Xác nhận phân công"}
                            </button>
                            <button type="button" className="cq-btn-ghost" onClick={() => { setAssessmentFormOpen(false); setAssessmentError(""); }} disabled={assigningAssessment}>Hủy</button>
                          </div>
                        </form>
                      )}
                    </div>
                  )}

                  {/* ── link member section ── */}
                  {detail.status === "IN_PROGRESS" &&
                    (role === "ADMIN" || (role === "RECEPTIONIST" && detail.handledBy === actorId)) &&
                    !detail.memberId && (
                    <div className="cq-drawer-section">
                      <div className="cq-link-header">
                        <Ic d={ICONS.link} size={18} />
                        <div>
                          <div className="cq-link-title">Liên kết tài khoản thành viên</div>
                          <div className="cq-link-sub">Chọn và xác minh trực tiếp tài khoản MEMBER; hệ thống không tự ghép theo email hoặc số điện thoại.</div>
                        </div>
                      </div>

                      <div className="cq-info-tip">
                        <Ic d={ICONS.info} size={13} />
                        Khuyến nghị tìm kiếm theo ID hoặc kiểm tra số điện thoại chính xác trước khi gắn.
                      </div>

                      <div className="cq-link-find">
                        <label className="cq-search-label">Tìm thành viên hiện có
                          <div className="cq-search-row">
                            <label className="cq-search-input-wrap">
                              <Ic d={ICONS.search} size={13} />
                              <input className="cq-search-input" placeholder="Nhập tên, email hoặc ID…"
                                value={candidateSearch} onChange={(e) => setCandidateSearch(e.target.value)} />
                            </label>
                            <button className="cq-btn-ghost" onClick={loadMemberCandidates} disabled={loadingCandidates}>
                              {loadingCandidates ? "Đang tải…" : "Tìm kiếm"}
                            </button>
                          </div>
                        </label>
                      </div>

                      {memberCandidates.length > 0 && (
                        <>
                          <label className="cq-form-label">Tài khoản ACTIVE MEMBER phù hợp
                            <select className="cq-select" value={selectedMemberId} onChange={(e) => setSelectedMemberId(e.target.value)}>
                              <option value="">#{(memberCandidates.find((u) => candidateSearch && String(u.id).includes(candidateSearch.replace("#","")))?.id) || ""} — Demo Member Riley Chen (ACTIVE)</option>
                              {memberCandidates
                                .filter((u) => (u.name + " " + u.email + " " + u.id).toLowerCase().includes(candidateSearch.toLowerCase()))
                                .map((u) => (
                                  <option key={u.id} value={u.id}>#{u.id} · {u.name} ({u.email})</option>
                                ))}
                            </select>
                          </label>
                          <button className="cq-btn-primary cq-btn--full" onClick={handleLinkMember} disabled={linkingMember || !selectedMemberId}>
                            <Ic d={ICONS.link} size={14} />
                            {linkingMember ? "Đang liên kết…" : "Liên kết tài khoản đã chọn"}
                          </button>
                        </>
                      )}
                      {memberCandidates.length === 0 && !loadingCandidates && !candidateSearch && (
                        <p className="cq-hint">Nhập từ khóa và nhấn Tìm kiếm để tra cứu thành viên.</p>
                      )}

                      {detail.email && (
                        <>
                          <div className="cq-divider"><span>HOẶC</span></div>
                          <div className="cq-create-member">
                            <div className="cq-create-header">
                              <div className="cq-create-icon"><Ic d={ICONS.user} size={18} /></div>
                              <div>
                                <div className="cq-create-title">Đăng ký tài khoản MEMBER mới</div>
                                <div className="cq-create-sub">Tạo tài khoản nhanh cho khách chưa có hồ sơ thành viên tại trung tâm.</div>
                              </div>
                            </div>
                            <form onSubmit={(e) => { e.preventDefault(); handleCreateMember(); }}>
                              <label className="cq-form-label">Email dùng để đăng nhập
                                <input className="cq-input" type="email" readOnly value={detail.email} />
                              </label>
                              <label className="cq-form-label">Ngày sinh
                                <input className="cq-input" type="date" required max={new Date().toISOString().slice(0, 10)}
                                  value={memberDateOfBirth} onChange={(e) => setMemberDateOfBirth(e.target.value)} placeholder="dd/mm/yyyy" />
                              </label>
                              <label className="cq-form-label">Mật khẩu khởi tạo (tối thiểu 8 ký tự)
                                <input className="cq-input" type="password" autoComplete="new-password" minLength={8} required
                                  value={memberPassword} onChange={(e) => setMemberPassword(e.target.value)} />
                              </label>
                              <button className="cq-btn-outline cq-btn--full" type="submit"
                                disabled={creatingMember || memberPassword.length < 8 || !memberDateOfBirth}>
                                <Ic d={ICONS.plus} size={14} />
                                {creatingMember ? "Đang tạo tài khoản…" : "Tạo và liên kết thành viên"}
                              </button>
                              <p className="cq-hint">* Lưu ý: Khách sẽ nhận email xác thực để kích hoạt tài khoản sau khi đăng ký.</p>
                            </form>
                          </div>
                        </>
                      )}
                    </div>
                  )}

                  {/* ── complete section ── */}
                  {detail.status === "IN_PROGRESS" &&
                    (role === "ADMIN" || detail.handledBy === actorId) && (
                    <div className="cq-drawer-section">
                      <div className="cq-section-title">HOÀN TẤT TƯ VẤN</div>
                      <label className="cq-form-label">Ghi chú kết quả (không bắt buộc)
                        <textarea className="cq-textarea" maxLength={1000} rows={3}
                          value={resolutionNote} onChange={(e) => setResolutionNote(e.target.value)} />
                      </label>
                      <button className="cq-btn-primary cq-btn--full" onClick={handleComplete} disabled={completingId === detail.id}>
                        <Ic d={ICONS.check} size={14} />
                        {completingId === detail.id ? "Đang hoàn tất…" : "Hoàn tất tư vấn"}
                      </button>
                    </div>
                  )}

                  {/* footer ID */}
                  <div className="cq-drawer-footer">
                    ID Yêu cầu: <strong>REQ-{new Date().getFullYear()}-{String(detail.id).padStart(4, "0")}</strong>
                  </div>
                </div>
              )}
            </aside>
          )}
        </div>
      </main>
    </div>
  );
}
