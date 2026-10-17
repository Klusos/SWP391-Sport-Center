import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  completeSkillAssessmentApi,
  getAssessmentMemberLevelApi,
  getMySkillAssessmentAssignmentsApi,
  getSportsApi,
} from "../api";

const LEVELS = ["BEGINNER", "INTERMEDIATE", "ADVANCED"];
const LEVEL_RANK = { BEGINNER: 0, INTERMEDIATE: 1, ADVANCED: 2 };
const STATUS_LABEL = { PENDING: "Chờ đánh giá", COMPLETED: "Đã hoàn tất", CANCELLED: "Đã hủy" };

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export default function CoachSkillAssessments({ token, role, onLogout, onNavigate }) {
  const [assignments, setAssignments] = useState([]);
  const [sports, setSports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [sportsError, setSportsError] = useState("");
  const [selected, setSelected] = useState(null);
  const [levelRecord, setLevelRecord] = useState(null);
  const [levelLoading, setLevelLoading] = useState(false);
  const [levelError, setLevelError] = useState("");
  const [reloadLevel, setReloadLevel] = useState(0);
  const [assessedLevel, setAssessedLevel] = useState("");
  const [notes, setNotes] = useState("");
  const [confirmDowngrade, setConfirmDowngrade] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const [notice, setNotice] = useState("");

  const sportNames = useMemo(() => new Map(sports.map((sport) => [Number(sport.id), sport.name])), [sports]);
  const selectedId = selected?.id;
  const currentLevel = levelRecord?.level || "BEGINNER";
  const isDowngrade = Boolean(assessedLevel && LEVEL_RANK[assessedLevel] < LEVEL_RANK[currentLevel]);
  const pending = useMemo(() => assignments.filter((item) => item.status === "PENDING"), [assignments]);
  const history = useMemo(() => assignments.filter((item) => item.status === "COMPLETED" || item.status === "CANCELLED"), [assignments]);

  const loadAssignments = useCallback(async () => {
    setLoading(true);
    setError("");
    setSportsError("");
    const [assignmentResult, sportResult] = await Promise.allSettled([
      getMySkillAssessmentAssignmentsApi(token),
      getSportsApi(token),
    ]);
    if (assignmentResult.status === "fulfilled") {
      setAssignments(Array.isArray(assignmentResult.value) ? assignmentResult.value : []);
    } else {
      const failure = assignmentResult.reason;
      setAssignments([]);
      setError(failure.status === 401 ? "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại."
        : failure.status === 403 ? "Tài khoản hiện tại không có quyền xem assignment của Coach."
          : failure.message || "Không tải được danh sách assessment được phân công.");
    }
    if (sportResult.status === "fulfilled") setSports(Array.isArray(sportResult.value) ? sportResult.value : []);
    else setSportsError("Không tải được tên bộ môn; danh sách vẫn hiển thị theo Sport ID.");
    setLoading(false);
  }, [token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadAssignments(), 0);
    return () => window.clearTimeout(timer);
  }, [loadAssignments]);

  useEffect(() => {
    if (!selectedId) return undefined;
    let active = true;
    Promise.resolve().then(async () => {
      setLevelLoading(true);
      setLevelError("");
      setLevelRecord(null);
      try {
        const record = await getAssessmentMemberLevelApi(token, selectedId);
        if (!active) return;
        if (!record || typeof record !== "object" || typeof record.level !== "string") {
          throw new Error("Phản hồi trình độ không hợp lệ. Vui lòng tải lại assessment.");
        }
        setLevelRecord(record);
      } catch (requestError) {
        if (!active) return;
        setLevelRecord(null);
        setLevelError(requestError?.status === 403
          ? "Bạn không được phân công assessment này nên không thể xem trình độ."
          : requestError?.status === 401
            ? "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại."
            : requestError?.message || "Không tải được trình độ hiện tại của Member.");
      } finally {
        if (active) setLevelLoading(false);
      }
    });
    return () => { active = false; };
  }, [token, selectedId, reloadLevel]);

  function openAssessment(assessment) {
    setSelected(assessment);
    setLevelRecord(null);
    setLevelLoading(true);
    setLevelError("");
    setAssessedLevel("");
    setNotes(assessment.notes || "");
    setConfirmDowngrade(false);
    setSubmitError("");
    setNotice("");
  }

  function closeAssessment() {
    if (submitting) return;
    setSelected(null);
    setAssessedLevel("");
    setNotes("");
    setConfirmDowngrade(false);
    setSubmitError("");
  }

  async function submitAssessment(event) {
    event.preventDefault();
    if (!selected || selected.status !== "PENDING" || submitting || levelLoading || levelError || !levelRecord || !assessedLevel) return;
    if (notes.length > 1000) {
      setSubmitError("Ghi chú không được vượt quá 1000 ký tự.");
      return;
    }
    if (isDowngrade && !confirmDowngrade) {
      setSubmitError("Vui lòng xác nhận đã trao đổi và xác nhận mức giảm trình độ.");
      return;
    }
    if (isDowngrade && !notes.trim()) {
      setSubmitError("Vui lòng ghi lý do đánh giá khi đề xuất giảm trình độ.");
      return;
    }
    setSubmitting(true);
    setSubmitError("");
    setNotice("");
    try {
      const updated = await completeSkillAssessmentApi(token, selected.id, {
        assessedLevel,
        notes: notes.trim() || null,
      });
      setSelected(updated);
      setAssignments((current) => current.map((item) => item.id === updated.id ? updated : item));
      setNotice("Đã hoàn tất assessment và cập nhật trình độ đã xác minh của Member.");
      setReloadLevel((current) => current + 1);
      await loadAssignments();
    } catch (requestError) {
      setSubmitError(requestError.status === 401 ? "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403 ? "Chỉ Coach được phân công mới có thể hoàn tất assessment này."
          : requestError.status === 409 ? "Assessment không còn ở trạng thái PENDING hoặc đã được cập nhật. Hãy làm mới danh sách."
            : requestError.message || "Không thể hoàn tất assessment.");
    } finally {
      setSubmitting(false);
    }
  }

  function renderAssessment(item) {
    const sportName = sportNames.get(Number(item.sportId));
    return <article className="coach-assessment-row" key={item.id}>
      <div className="coach-assessment-row-main">
        <strong>Assessment #{item.id} · Member #{item.memberId}</strong>
        <span>{sportName ? `${sportName} · Sport #${item.sportId}` : `Sport #${item.sportId}`}</span>
      </div>
      <div className="coach-assessment-row-meta">
        <span className={`coach-assessment-status status-${String(item.status).toLowerCase()}`}>{STATUS_LABEL[item.status] || item.status}</span>
        <span>Phân công: {formatDate(item.createdAt)}</span>
      </div>
      {item.notes && <p className="coach-assessment-notes">Ghi chú: {item.notes}</p>}
      <button type="button" className="coach-assessment-open" onClick={() => openAssessment(item)}>
        {item.status === "PENDING" ? "Mở đánh giá" : "Xem kết quả"}
      </button>
    </article>;
  }

  return <div className="layout">
    <Sidebar role={role} activePage="coach-assessments" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main">
      <header className="page-header"><p className="eyebrow">SPORTCENTER / COACH</p><h1>Quản lý đánh giá kỹ năng</h1>
        <p className="subtitle">Xem các assessment được phân công và cập nhật trình độ sau khi đánh giá Member.</p></header>
      {notice && <p className="banner success" role="status">{notice}</p>}
      {error && <p className="banner error" role="alert">{error}</p>}
      {sportsError && <p className="banner" role="status">{sportsError}</p>}
      <div className="coach-assessment-toolbar"><span>{pending.length} assessment đang chờ · {history.length} trong lịch sử</span>
        <button type="button" onClick={() => void loadAssignments()} disabled={loading}>{loading ? "Đang tải…" : "Làm mới"}</button></div>

      {loading ? <div className="queue-state" role="status">Đang tải assignment…</div> : !error && <>
        <section className="coach-assessment-group"><h2>Đang chờ đánh giá</h2>
          {pending.length === 0 ? <p className="queue-state">Không có assessment PENDING được phân công.</p> : <div className="coach-assessment-list">{pending.map(renderAssessment)}</div>}
        </section>
        <section className="coach-assessment-group"><h2>Lịch sử assessment</h2>
          {history.length === 0 ? <p className="queue-state">Chưa có assessment COMPLETED hoặc CANCELLED.</p> : <div className="coach-assessment-list">{history.map(renderAssessment)}</div>}
        </section>
      </>}

      {selected && <section className="coach-assessment-detail" aria-labelledby="coach-assessment-detail-title">
        <header><div><p className="subtitle">Assessment #{selected.id}</p><h2 id="coach-assessment-detail-title">{sportNames.get(Number(selected.sportId)) || `Sport #${selected.sportId}`}</h2></div>
          <button type="button" onClick={closeAssessment} disabled={submitting}>Đóng</button></header>
        <dl className="coach-assessment-facts">
          <div><dt>Member</dt><dd>#{selected.memberId}</dd></div>
          <div><dt>Trạng thái</dt><dd>{STATUS_LABEL[selected.status] || selected.status}</dd></div>
          <div><dt>Ngày phân công</dt><dd>{formatDate(selected.createdAt)}</dd></div>
          {selected.assessedAt && <div><dt>Ngày hoàn tất</dt><dd>{formatDate(selected.assessedAt)}</dd></div>}
        </dl>
        <p className="coach-assessment-member-limit">Assignment API chỉ cung cấp Member ID, không có tên Member. Trang này không gọi endpoint bị giới hạn quyền để tra cứu tên.</p>
        {selected.notes && <div className="coach-assessment-detail-notes"><strong>Ghi chú assignment / kết quả</strong><p>{selected.notes}</p></div>}

        {levelLoading || (!levelError && !levelRecord) ? <p role="status">Đang tải trình độ hiện tại…</p>
          : levelError ? <p className="banner error" role="alert">{levelError}</p>
            : <section className="coach-current-level" aria-label="Trình độ hiện tại">
              <strong>Trình độ hiện tại: {levelRecord.level}</strong>
              {levelRecord.defaultLevel === true ? <p>Beginner (mặc định; chưa có MemberSportLevel cho bộ môn này).</p>
                : <p>{levelRecord.verifiedByCoachId != null
                  ? `Đã xác minh bởi Coach #${levelRecord.verifiedByCoachId}${levelRecord.verifiedAt ? ` · ${formatDate(levelRecord.verifiedAt)}` : ""}`
                  : "Bản ghi hiện tại chưa có thông tin Coach xác minh"}</p>}
            </section>}

        {selected.status === "PENDING" ? <form className="coach-assessment-form" onSubmit={submitAssessment}>
          <label htmlFor="assessedLevel">Trình độ đánh giá</label>
          <select id="assessedLevel" required value={assessedLevel} onChange={(event) => { setAssessedLevel(event.target.value); setConfirmDowngrade(false); setSubmitError(""); }} disabled={submitting}>
            <option value="">Chọn BEGINNER, INTERMEDIATE hoặc ADVANCED</option>
            {LEVELS.map((level) => <option key={level} value={level}>{level}</option>)}
          </select>
          {assessedLevel && !levelError && !levelLoading && levelRecord && <p className={`coach-level-change ${isDowngrade ? "downgrade" : ""}`}>
            {isDowngrade ? `Đề xuất downgrade: ${currentLevel} → ${assessedLevel}.`
              : assessedLevel === currentLevel ? `Giữ nguyên trình độ ${currentLevel}.`
                : `Đề xuất thăng cấp: ${currentLevel} → ${assessedLevel}.`}
          </p>}
          <label htmlFor="assessmentResultNotes">Ghi chú / kết quả đánh giá (tối đa 1000 ký tự){isDowngrade ? " · bắt buộc khi giảm cấp" : " · không bắt buộc"}</label>
          <textarea id="assessmentResultNotes" maxLength={1000} value={notes} onChange={(event) => setNotes(event.target.value)} disabled={submitting} />
          {isDowngrade && <label className="coach-downgrade-confirm"><input type="checkbox" checked={confirmDowngrade}
            onChange={(event) => setConfirmDowngrade(event.target.checked)} disabled={submitting} /> Tôi xác nhận đã trao đổi với Member và đồng ý ghi nhận mức giảm trình độ.</label>}
          {submitError && <p className="banner error" role="alert">{submitError}</p>}
          <button type="submit" className="btn-primary" disabled={submitting || levelLoading || Boolean(levelError) || !levelRecord || !assessedLevel || (isDowngrade && (!confirmDowngrade || !notes.trim()))}>
            {submitting ? "Đang lưu kết quả…" : "Hoàn tất assessment"}
          </button>
          {levelError && <p className="coach-assessment-blocked">Không thể hoàn tất khi chưa xác minh được trình độ hiện tại.</p>}
        </form> : <div className="coach-assessment-readonly">
          <h3>Kết quả assessment</h3>
          <p><strong>Trình độ đánh giá:</strong> {selected.assessedLevel || "—"}</p>
          {selected.notes && <p><strong>Ghi chú:</strong> {selected.notes}</p>}
          <p>Kết quả đã hoàn tất chỉ đọc; lịch sử assessment được giữ nguyên.</p>
        </div>}
      </section>}
    </main>
  </div>;
}
