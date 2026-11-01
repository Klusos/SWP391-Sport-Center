import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  completeSkillAssessmentApi,
  getAssessmentMemberLevelApi,
  getMySkillAssessmentAssignmentsApi,
  getSportsApi,
} from "../api";
import "./CoachPt.css";

const LEVELS = ["BEGINNER", "INTERMEDIATE", "ADVANCED"];
const LEVEL_RANK = { BEGINNER: 0, INTERMEDIATE: 1, ADVANCED: 2 };
const LEVEL_META = {
  BEGINNER: { vi: "Cơ bản", en: "Beginner", desc: "Nắm kiến thức và kỹ thuật nền tảng, đang làm quen với bài tập." },
  INTERMEDIATE: { vi: "Trung cấp", en: "Intermediate", desc: "Kỹ thuật ổn định, kiểm soát tốt và bắt đầu vận dụng chiến thuật." },
  ADVANCED: { vi: "Nâng cao", en: "Advanced", desc: "Kỹ thuật, thể lực và chiến thuật vững, sẵn sàng thi đấu đối kháng." },
};
const STATUS = {
  PENDING: ["Chờ đánh giá", "warn"],
  COMPLETED: ["Đã hoàn tất", "ok"],
  CANCELLED: ["Đã hủy", "gray"],
};
const TABS = [["pending", "Đang chờ đánh giá"], ["history", "Lịch sử"]];

const ICON = {
  refresh: "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16 M8 16H3v5",
  arrow: "M5 12h14 M12 5l7 7-7 7",
  eye: "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6",
  trend: "M22 7l-8.5 8.5-5-5L2 17 M16 7h6v6",
  check: "M20 6 9 17l-5-5",
  info: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M12 16v-4 M12 8h.01",
  award: "M12 15a6 6 0 1 0 0-12 6 6 0 0 0 0 12 M8.21 13.89 7 23l5-3 5 3-1.21-9.12",
  close: "M18 6 6 18 M6 6l12 12",
  done: "M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4 12 14.01l-3-3",
};
const Ic = ({ d, size = 18 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {d.split(" M").map((p, i) => <path key={i} d={(i ? "M" : "") + p} />)}
  </svg>
);

const dmy = (v) => {
  if (!v) return "—";
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
};
const dmyHm = (v) => {
  if (!v) return "—";
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? String(v) : d.toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
};
const byNewest = (a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0);
const levelName = (l) => (LEVEL_META[l] ? LEVEL_META[l].vi + " (" + LEVEL_META[l].en + ")" : l || "—");
const code = (id) => "#SA-" + String(id).padStart(3, "0");

// Bản nháp chỉ lưu trên trình duyệt này (API chưa có endpoint lưu nháp).
const draftKey = (id) => "coach-assessment-draft-" + id;
function readDraft(id) {
  try { const raw = window.localStorage.getItem(draftKey(id)); return raw ? JSON.parse(raw) : null; } catch { return null; }
}
function writeDraft(id, value) {
  try { window.localStorage.setItem(draftKey(id), JSON.stringify(value)); return true; } catch { return false; }
}
function clearDraft(id) {
  try { window.localStorage.removeItem(draftKey(id)); } catch { /* bỏ qua */ }
}

export default function CoachSkillAssessments({ token, role, onLogout, onNavigate }) {
  const [assignments, setAssignments] = useState([]);
  const [sports, setSports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [sportsError, setSportsError] = useState("");
  const [tab, setTab] = useState("pending");
  const [sportFilter, setSportFilter] = useState(null);
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
  const [notice, setNotice] = useState(null);

  const sportNames = useMemo(() => new Map(sports.map((sport) => [Number(sport.id), sport.name])), [sports]);
  const sportLabel = (item) => sportNames.get(Number(item.sportId)) || "Bộ môn #" + item.sportId;
  const selectedId = selected?.id;
  const currentLevel = levelRecord?.level || "BEGINNER";
  const levelDiff = assessedLevel ? LEVEL_RANK[assessedLevel] - LEVEL_RANK[currentLevel] : 0;
  const isDowngrade = levelDiff < 0;

  const matchSport = useCallback((item) => sportFilter == null || Number(item.sportId) === sportFilter, [sportFilter]);
  const pending = useMemo(() => assignments.filter((i) => i.status === "PENDING").sort(byNewest), [assignments]);
  const history = useMemo(() => assignments.filter((i) => i.status === "COMPLETED" || i.status === "CANCELLED").sort(byNewest), [assignments]);
  const pendingShown = useMemo(() => pending.filter(matchSport), [pending, matchSport]);
  const historyShown = useMemo(() => history.filter(matchSport), [history, matchSport]);
  const sportOptions = useMemo(() => [...new Set(assignments.map((a) => Number(a.sportId)))], [assignments]);

  const loadAssignments = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
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
    else setSportsError("Không tải được tên bộ môn; danh sách vẫn hiển thị theo mã bộ môn.");
    setLoading(false);
  }, [token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadAssignments(), 0);
    return () => window.clearTimeout(timer);
  }, [loadAssignments]);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = window.setTimeout(() => setNotice(null), 7000);
    return () => window.clearTimeout(timer);
  }, [notice]);

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
    const draft = assessment.status === "PENDING" ? readDraft(assessment.id) : null;
    setSelected(assessment);
    setLevelRecord(null);
    setLevelLoading(true);
    setLevelError("");
    setAssessedLevel(draft && LEVELS.includes(draft.assessedLevel) ? draft.assessedLevel : "");
    setNotes(draft ? String(draft.notes || "").slice(0, 1000) : assessment.notes || "");
    setConfirmDowngrade(false);
    setSubmitError("");
    setNotice(null);
  }

  function closeAssessment() {
    if (submitting) return;
    setSelected(null);
    setAssessedLevel("");
    setNotes("");
    setConfirmDowngrade(false);
    setSubmitError("");
  }

  function saveDraft() {
    if (!selected) return;
    const ok = writeDraft(selected.id, { assessedLevel, notes });
    setNotice(ok
      ? { title: "Đã lưu bản nháp", sub: "Bản nháp chỉ được lưu trên trình duyệt này." }
      : { title: "Không lưu được bản nháp", sub: "Trình duyệt đang chặn bộ nhớ cục bộ." });
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
    setNotice(null);
    try {
      const updated = await completeSkillAssessmentApi(token, selected.id, {
        assessedLevel,
        notes: notes.trim() || null,
      });
      clearDraft(selected.id);
      setSelected(updated);
      setAssignments((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setNotice({
        title: "Đã hoàn tất đánh giá và cập nhật trình độ!",
        sub: "Học viên #" + updated.memberId + " → " + (LEVEL_META[updated.assessedLevel]?.vi || updated.assessedLevel),
      });
      setReloadLevel((current) => current + 1);
      await loadAssignments(true);
    } catch (requestError) {
      setSubmitError(requestError.status === 401 ? "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403 ? "Chỉ Coach được phân công mới có thể hoàn tất assessment này."
          : requestError.status === 409 ? "Assessment không còn ở trạng thái PENDING hoặc đã được cập nhật. Hãy làm mới danh sách."
            : requestError.message || "Không thể hoàn tất assessment.");
    } finally {
      setSubmitting(false);
    }
  }

  const onKey = (fn) => (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); fn(); } };

  function renderPending(item) {
    const on = item.id === selectedId;
    return (
      <article key={item.id} className={"ca-card" + (on ? " on" : "")} role="button" tabIndex={0} aria-pressed={on}
        onClick={() => openAssessment(item)} onKeyDown={onKey(() => openAssessment(item))}>
        <div className="ca-card-top">
          <span className="ca-avatar">#{item.memberId}</span>
          <div className="ca-card-name">
            <strong>Học viên #{item.memberId}{on && <i />}</strong>
            <span>{sportLabel(item)}</span>
          </div>
          <span className="cp-chip warn">Chờ đánh giá</span>
        </div>
        <div className="ca-card-foot">
          <span>Ngày giao: <b>{dmy(item.createdAt)}</b></span>
          <span className="ca-open">{on ? "Đang mở" : "Mở đánh giá"} <Ic d={ICON.arrow} size={15} /></span>
        </div>
      </article>
    );
  }

  function renderHistory(item) {
    const on = item.id === selectedId;
    const [label, kind] = STATUS[item.status] || [item.status, "gray"];
    return (
      <article key={item.id} className={"ca-hist" + (item.status === "CANCELLED" ? " dim" : "") + (on ? " on" : "")}
        role="button" tabIndex={0} aria-pressed={on} onClick={() => openAssessment(item)} onKeyDown={onKey(() => openAssessment(item))}>
        <span className={"ca-avatar sm " + (item.status === "COMPLETED" ? "green" : "gray")}>#{item.memberId}</span>
        <div className="ca-hist-name">
          <strong>Học viên #{item.memberId}</strong>
          <small>{sportLabel(item)} · {dmy(item.assessedAt || item.cancelledAt || item.createdAt)}</small>
        </div>
        <span className={"cp-chip " + kind}>{label}</span>
        {item.status === "COMPLETED" && <span className="ca-eye"><Ic d={ICON.eye} size={17} /></span>}
      </article>
    );
  }

  const [selLabel, selKind] = selected ? STATUS[selected.status] || [selected.status, "gray"] : [];
  const shiftClass = levelDiff > 0 ? "up" : levelDiff < 0 ? "down" : "same";

  return (
    <div className="layout">
      <Sidebar role={role} activePage="coach-assessments" onNavigate={onNavigate} onLogout={onLogout} badges={{ "coach-assessments": pending.length }} />
      <main className="main cp-main">
        <header className="cp-topbar">
          <nav className="cp-crumb" aria-label="Breadcrumb"><span>Sports Center</span><i>›</i><span>Huấn luyện viên</span><i>›</i><b>Đánh giá kỹ năng</b></nav>
        </header>

        <div className="cp-content">
          <section className="ca-hero">
            <div>
              <h1>Đánh giá kỹ năng</h1>
              <p>Các đợt đánh giá được phân công và kết quả đã hoàn tất</p>
              <div className="ca-pills">
                <span className="ca-pill"><i />{pending.length} đang chờ</span>
                <span className="ca-pill"><i className="green" />{history.length} đã xử lý</span>
              </div>
            </div>
            <button type="button" className="ca-refresh" onClick={() => void loadAssignments()} disabled={loading}>
              <Ic d={ICON.refresh} size={17} /> {loading ? "Đang tải…" : "Làm mới"}
            </button>
          </section>

          {error && <p className="banner error" role="alert">{error}</p>}
          {sportsError && <p className="banner" role="status">{sportsError}</p>}

          {loading ? <div className="queue-state" role="status">Đang tải assignment…</div> : !error && (
            <div className="ca-grid">
              {/* ===== Cột trái: danh sách ===== */}
              <section className="ca-box">
                <div className="ca-tabs" role="tablist" aria-label="Danh sách đánh giá">
                  {TABS.map(([k, label]) => (
                    <button key={k} type="button" role="tab" aria-selected={tab === k} className={tab === k ? "on" : ""} onClick={() => setTab(k)}>
                      {label} ({k === "pending" ? pending.length : history.length})
                    </button>
                  ))}
                </div>

                {sportOptions.length > 1 && (
                  <div className="ca-filters">
                    <button type="button" className={sportFilter == null ? "on" : ""} onClick={() => setSportFilter(null)}>Tất cả</button>
                    {sportOptions.map((id) => (
                      <button key={id} type="button" className={sportFilter === id ? "on" : ""} onClick={() => setSportFilter(id)}>
                        {sportNames.get(id) || "Bộ môn #" + id}
                      </button>
                    ))}
                  </div>
                )}

                {tab === "pending" ? (
                  <>
                    {pendingShown.length === 0
                      ? <p className="ca-empty">Không có đợt đánh giá nào đang chờ.</p>
                      : <div className="ca-list">{pendingShown.map(renderPending)}</div>}
                    {historyShown.length > 0 && (
                      <>
                        <div className="ca-divider"><span>Đợt đánh giá đã xong gần đây</span></div>
                        <div className="ca-list">{historyShown.slice(0, 3).map(renderHistory)}</div>
                      </>
                    )}
                  </>
                ) : historyShown.length === 0
                  ? <p className="ca-empty">Chưa có đợt đánh giá nào đã hoàn tất hoặc đã hủy.</p>
                  : <div className="ca-list">{historyShown.map(renderHistory)}</div>}

                <div className="ca-reminder">
                  <Ic d={ICON.done} size={22} />
                  <p>{pending.length > 0
                    ? <><b>Lời nhắc:</b> Bạn còn {pending.length} đợt đánh giá cần giải quyết.</>
                    : <><b>Tuyệt vời!</b> Bạn đã xử lý hết các đợt đánh giá được giao.</>}
                  <small>Hệ thống chỉ cung cấp mã học viên nên danh sách hiển thị theo “Học viên #mã”.</small></p>
                </div>
              </section>

              {/* ===== Cột phải: chi tiết ===== */}
              <section className="ca-box ca-panel" aria-labelledby="ca-detail-title">
                {!selected ? (
                  <div className="ca-placeholder">
                    <i><Ic d={ICON.award} size={28} /></i>
                    <h3>Chọn một đợt đánh giá</h3>
                    <p>Chọn học viên ở danh sách bên trái để xem trình độ hiện tại và nhập kết quả đánh giá.</p>
                  </div>
                ) : (
                  <>
                    <header className="ca-dhead">
                      <span className="ca-dicon"><Ic d={ICON.award} size={26} /></span>
                      <div className="ca-dtitle">
                        <h2 id="ca-detail-title">Đánh giá trình độ: Học viên #{selected.memberId} <em>{code(selected.id)}</em></h2>
                        <p>Phân công ngày: {dmy(selected.createdAt)} · Bộ môn {sportLabel(selected)}</p>
                      </div>
                      <span className={"cp-chip " + selKind}>{selLabel}</span>
                      <button type="button" className="ca-close" onClick={closeAssessment} disabled={submitting} aria-label="Đóng"><Ic d={ICON.close} size={16} /></button>
                    </header>

                    {levelLoading || (!levelError && !levelRecord) ? <p className="ca-muted" role="status">Đang tải trình độ hiện tại…</p>
                      : levelError ? <p className="banner error" role="alert">{levelError}</p>
                        : (
                          <section className="ca-level" aria-label="Trình độ hiện tại">
                            <div className="ca-level-head">
                              <strong><Ic d={ICON.award} size={17} /> Trình độ hiện tại của học viên</strong>
                              <small>{levelRecord.defaultLevel === true ? "Hồ sơ ban đầu" : "Đã xác minh"}</small>
                            </div>
                            <div className="ca-steps">
                              {LEVELS.map((lv, i) => {
                                const state = LEVEL_RANK[lv] === LEVEL_RANK[currentLevel] ? "cur" : LEVEL_RANK[lv] < LEVEL_RANK[currentLevel] ? "done" : "todo";
                                return (
                                  <div key={lv} className={"ca-step " + state}>
                                    <header><small>CẤP ĐỘ {i + 1}</small>{state !== "todo" ? <Ic d={ICON.check} size={16} /> : <span className="ca-ring" />}</header>
                                    <b>{LEVEL_META[lv].vi} ({LEVEL_META[lv].en})</b>
                                    <span>{state === "cur" ? (levelRecord.defaultLevel === true ? "● Mặc định" : "● Hiện tại") : state === "done" ? "Đã đạt" : "Chưa đạt"}</span>
                                  </div>
                                );
                              })}
                            </div>
                            <p className="ca-level-note"><Ic d={ICON.info} size={14} />
                              {levelRecord.defaultLevel === true
                                ? "Mặc định Beginner (chưa có bản ghi xác minh trước đó)."
                                : levelRecord.verifiedByCoachId != null
                                  ? "Đã xác minh bởi Coach #" + levelRecord.verifiedByCoachId + (levelRecord.verifiedAt ? " · " + dmyHm(levelRecord.verifiedAt) : "")
                                  : "Bản ghi hiện tại chưa có thông tin Coach xác minh."}
                            </p>
                          </section>
                        )}

                    {selected.status === "PENDING" ? (
                      <form className="ca-form" onSubmit={submitAssessment}>
                        <div>
                          <h3>Xác định trình độ mới</h3>
                          <p className="ca-muted">Lựa chọn cấp độ phản ánh chính xác kỹ năng thực tế sau chuỗi buổi tập</p>
                        </div>
                        <div className="ca-opts" role="radiogroup" aria-label="Trình độ đánh giá">
                          {LEVELS.map((lv) => (
                            <label key={lv} className={"ca-opt" + (assessedLevel === lv ? " on" : "")}>
                              <input className="ca-radio" type="radio" name="assessedLevel" value={lv} checked={assessedLevel === lv} disabled={submitting}
                                onChange={() => { setAssessedLevel(lv); setConfirmDowngrade(false); setSubmitError(""); }} />
                              <span className="ca-opt-top">
                                <span><b>{LEVEL_META[lv].vi}</b><small>{LEVEL_META[lv].en}</small></span>
                                <span className="ca-mark">{assessedLevel === lv && <Ic d={ICON.check} size={14} />}</span>
                              </span>
                              <span className="ca-opt-desc">{LEVEL_META[lv].desc}</span>
                            </label>
                          ))}
                        </div>

                        {assessedLevel && !levelError && !levelLoading && levelRecord && (
                          <div className={"ca-shift " + shiftClass}>
                            <span><Ic d={ICON.trend} size={20} />
                              {levelDiff > 0 ? "Thăng cấp: " + LEVEL_META[currentLevel].en + " → " + LEVEL_META[assessedLevel].en
                                : levelDiff < 0 ? "Giảm cấp: " + LEVEL_META[currentLevel].en + " → " + LEVEL_META[assessedLevel].en
                                  : "Giữ nguyên trình độ " + LEVEL_META[currentLevel].en}
                            </span>
                            {levelDiff !== 0 && <em>{levelDiff > 0 ? "Tăng " : "Giảm "}{Math.abs(levelDiff)} bậc</em>}
                          </div>
                        )}

                        <div>
                          <div className="ca-notes-head">
                            <label htmlFor="assessmentResultNotes">Nhận xét &amp; đánh giá năng lực của Huấn luyện viên{isDowngrade ? " (bắt buộc khi giảm cấp)" : ""}</label>
                            <small>{notes.length} / 1000 ký tự</small>
                          </div>
                          <textarea id="assessmentResultNotes" className="ca-textarea" maxLength={1000} rows={5} value={notes} disabled={submitting}
                            placeholder="Ghi chú chi tiết kỹ năng, kỹ thuật, thể lực, thái độ tập luyện…" onChange={(event) => setNotes(event.target.value)} />
                        </div>

                        {isDowngrade && (
                          <label className="ca-confirm">
                            <input type="checkbox" checked={confirmDowngrade} onChange={(event) => setConfirmDowngrade(event.target.checked)} disabled={submitting} />
                            <span>Tôi xác nhận đã trao đổi với Member và đồng ý ghi nhận mức giảm trình độ.</span>
                          </label>
                        )}

                        {submitError && <p className="banner error" role="alert">{submitError}</p>}
                        {levelError && <p className="ca-muted">Không thể hoàn tất khi chưa xác minh được trình độ hiện tại.</p>}

                        <div className="ca-actions">
                          <button type="button" className="ca-ghost" onClick={saveDraft} disabled={submitting}>Lưu bản nháp</button>
                          <button type="submit" className="ca-primary"
                            disabled={submitting || levelLoading || Boolean(levelError) || !levelRecord || !assessedLevel || (isDowngrade && (!confirmDowngrade || !notes.trim()))}>
                            <Ic d={ICON.done} size={17} /> {submitting ? "Đang lưu kết quả…" : "Hoàn tất đánh giá"}
                          </button>
                        </div>
                      </form>
                    ) : (
                      <div className="ca-result">
                        <header>
                          <span>{selected.status === "COMPLETED" ? "Kết quả đánh giá" : "Đợt đánh giá đã hủy"}</span>
                          <small>{dmyHm(selected.status === "COMPLETED" ? selected.assessedAt : selected.cancelledAt)}</small>
                        </header>
                        {selected.status === "COMPLETED" && <p className="ca-result-level">Kết quả: <b>{levelName(selected.assessedLevel)}</b></p>}
                        {selected.notes && <blockquote>{selected.notes}</blockquote>}
                        <p className="ca-muted">Kết quả đã hoàn tất chỉ đọc; lịch sử đánh giá được giữ nguyên.</p>
                      </div>
                    )}
                  </>
                )}
              </section>
            </div>
          )}
        </div>

        {notice && (
          <div className="ca-toast" role="status">
            <span className="ca-toast-ok"><Ic d={ICON.check} size={18} /></span>
            <div><b>{notice.title}</b><small>{notice.sub}</small></div>
            <button type="button" onClick={() => setNotice(null)} aria-label="Đóng thông báo"><Ic d={ICON.close} size={16} /></button>
          </div>
        )}
      </main>
    </div>
  );
}