import { useCallback, useEffect, useMemo, useState } from "react";
import WeeklyPlanEditor from "./WeeklyPlanEditor.jsx";
import {
  createIndividualTrainingPlanApi,
  getClassIndividualTrainingPlansApi,
  getIndividualTrainingPlanApi,
  getIndividualTrainingPlanRevisionsApi,
  getMyIndividualTrainingPlansApi,
  getTrainingProgressApi,
  updateIndividualTrainingPlanApi,
} from "../api";
import {
  canCreateIndividualPlan,
  createLatestRequestGuard,
  individualPlanEligibilityMessage,
  savePlanAndRefreshOpenHistory,
  toIndividualTrainingPlanPayload,
} from "./individualTrainingPlanHelpers";

function messageFor(error, fallback) {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Không đủ quyền cho thao tác này. Việc tạo hoặc chỉnh sửa yêu cầu bạn là HLV được giao lớp và học viên có gói hội viên đủ điều kiện hiện tại.";
  if (error?.status === 404) return "Không tìm thấy giáo án hoặc lớp học.";
  if (error?.status === 409) return error.message || "Học viên chưa đủ điều kiện đăng ký hoặc đã có giáo án cá nhân.";
  if (error?.status === 400) return error.message || "Thông tin chưa hợp lệ. Kiểm tra mục tiêu, tuần và bài tập.";
  return error?.message || fallback;
}

export default function IndividualTrainingPlans({ token, mode, classId, canManageTraining = false }) {
  const isCoach = mode === "coach";
  const isAdminReadOnly = mode === "admin-readonly";
  const isClassView = isCoach || isAdminReadOnly;
  const [plans, setPlans] = useState([]);
  const [members, setMembers] = useState([]);
  const [eligibilityLoaded, setEligibilityLoaded] = useState(false);
  const [eligibilityError, setEligibilityError] = useState(false);
  const [selected, setSelected] = useState(null);
  const [history, setHistory] = useState(null);
  const [historyPlanId, setHistoryPlanId] = useState(null);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [busy, setBusy] = useState(false);
  const [creatingFor, setCreatingFor] = useState(null);
  const [editing, setEditing] = useState(false);
  const [editorDirty, setEditorDirty] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [historyRequests] = useState(() => createLatestRequestGuard());
  const [detailRequests] = useState(() => createLatestRequestGuard());
  const [loadRequests] = useState(() => createLatestRequestGuard());

  const selectedClassKey = classId == null || String(classId).trim() === "" ? null : String(classId);
  const visiblePlans = useMemo(() => {
    if (isClassView || selectedClassKey === null) return plans;
    return plans.filter((plan) => String(plan.classId) === selectedClassKey);
  }, [plans, isClassView, selectedClassKey]);
  const visibleSelected = selected && (
    isClassView || selectedClassKey === null || (
      String(selected.classId) === selectedClassKey &&
      visiblePlans.some((plan) => String(plan.id) === String(selected.id))
    )
  ) ? selected : null;

  const load = useCallback(async () => {
    const requestId = loadRequests.start();
    setLoading(true);
    setError("");
    setEligibilityLoaded(false);
    setEligibilityError(false);
    try {
      if (isCoach) {
        const [plansResult, progressResult] = await Promise.allSettled([
          getClassIndividualTrainingPlansApi(token, classId),
          getTrainingProgressApi(token, classId),
        ]);
        if (!loadRequests.isCurrent(requestId)) return;
        if (plansResult.status === "rejected") throw plansResult.reason;
        setPlans(Array.isArray(plansResult.value) ? plansResult.value : []);
        if (progressResult.status === "rejected") {
          setMembers([]);
          setEligibilityError(true);
          setError(messageFor(progressResult.reason, "Could not verify attendance eligibility. Retry loading the class."));
        } else {
          setMembers(Array.isArray(progressResult.value) ? progressResult.value : []);
          setEligibilityLoaded(Array.isArray(progressResult.value));
        }
      } else if (isAdminReadOnly) {
        const classPlans = await getClassIndividualTrainingPlansApi(token, classId);
        if (!loadRequests.isCurrent(requestId)) return;
        setPlans(Array.isArray(classPlans) ? classPlans : []);
        setMembers([]);
      } else {
        const ownPlans = await getMyIndividualTrainingPlansApi(token);
        if (!loadRequests.isCurrent(requestId)) return;
        setPlans(Array.isArray(ownPlans) ? ownPlans : []);
        setMembers([]);
      }
    } catch (requestError) {
      if (!loadRequests.isCurrent(requestId)) return;
      if (isCoach) {
        setMembers([]);
        setEligibilityError(true);
      }
      setError(messageFor(requestError, "Không tải được danh sách giáo án cá nhân."));
    } finally {
      if (loadRequests.isCurrent(requestId)) setLoading(false);
    }
  }, [token, isCoach, isAdminReadOnly, classId, loadRequests]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) void load(); });
    return () => { active = false; loadRequests.invalidate(); };
  }, [load, loadRequests]);

  useEffect(() => () => {
    detailRequests.invalidate();
    historyRequests.invalidate();
  }, [classId, detailRequests, historyRequests]);

  const planByMember = useMemo(() => new Map(plans.map((plan) => [plan.memberId, plan])), [plans]);

  function mayLeaveEditor() {
    if (editorDirty && !window.confirm("Bạn có thay đổi chưa lưu. Tiếp tục và bỏ các thay đổi này?")) return false;
    setEditorDirty(false);
    return true;
  }

  async function openPlan(plan) {
    if (!mayLeaveEditor()) return;
    if (isClassView && selectedClassKey !== null && String(plan.classId) !== selectedClassKey) return;
    const requestId = detailRequests.start();
    historyRequests.invalidate();
    setSelected(null);
    setHistory(null);
    setHistoryPlanId(null);
    setLoadingHistory(false);
    setEditing(false);
    setLoadingDetail(true);
    setError("");
    try {
      const detail = await getIndividualTrainingPlanApi(token, plan.id);
      if (!detailRequests.isCurrent(requestId)) return;
      if (isClassView && selectedClassKey !== null && String(detail?.classId) !== selectedClassKey) {
        setError("Giáo án này không thuộc lớp đang chọn.");
        return;
      }
      setSelected(detail);
    } catch (requestError) {
      if (detailRequests.isCurrent(requestId)) setError(messageFor(requestError, "Không tải được chi tiết giáo án."));
    } finally {
      if (detailRequests.isCurrent(requestId)) setLoadingDetail(false);
    }
  }

  function clearHistory() {
    historyRequests.invalidate();
    setHistory(null);
    setHistoryPlanId(null);
    setLoadingHistory(false);
  }

  async function showHistory(planId) {
    const requestId = historyRequests.start();
    setHistoryPlanId(planId);
    setError("");
    setLoadingHistory(true);
    try {
      const rows = await getIndividualTrainingPlanRevisionsApi(token, planId);
      if (historyRequests.isCurrent(requestId)) setHistory(Array.isArray(rows) ? rows : []);
    } catch (requestError) {
      if (historyRequests.isCurrent(requestId)) {
        setError(messageFor(requestError, "Không tải được lịch sử chỉnh sửa."));
      }
    } finally {
      if (historyRequests.isCurrent(requestId)) setLoadingHistory(false);
    }
  }

  async function submit(memberId, form, planId = null) {
    if (!canManageTraining) return;
    if (busy) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const payload = toIndividualTrainingPlanPayload(form);
      const saved = await savePlanAndRefreshOpenHistory(
        () => planId
          ? updateIndividualTrainingPlanApi(token, planId, payload)
          : createIndividualTrainingPlanApi(token, classId, memberId, payload),
        {
          planId,
          historyIsOpen: planId != null && historyPlanId === planId,
          refreshHistory: showHistory,
        },
      );
      setNotice(planId ? "Đã lưu phiên bản giáo án mới." : "Đã tạo giáo án cá nhân.");
      setCreatingFor(null);
      if (planId == null) clearHistory();
      setEditing(false);
      setEditorDirty(false);
      setSelected(saved);
      await load();
    } catch (requestError) {
      const failureMessage = messageFor(requestError, "Không lưu được giáo án cá nhân.");
      if (planId == null && requestError?.status === 409) await load();
      setError(failureMessage);
    } finally {
      setBusy(false);
    }
  }

  return <section className="individual-plans" aria-labelledby={`individual-plan-heading-${mode}`}>
    <div className="individual-plans-heading">
      <div>
        <h2 id={`individual-plan-heading-${mode}`}>{isClassView ? "Danh sách giáo án cá nhân" : "Giáo án cá nhân"}</h2>
        <p className="training-muted">{isClassView
          ? "Chỉ hiển thị giáo án cá nhân thuộc lớp bạn đang xem."
          : "Kế hoạch riêng theo từng học viên; các phiên bản cũ được lưu trong lịch sử."}</p>
      </div>
      <button type="button" className="secondary" onClick={() => void load()} disabled={loading}>Làm mới</button>
    </div>
    {error && <div className="banner error" role="alert">{error}</div>}
    {isCoach && eligibilityError && <div className="banner error" role="alert">
      Không thể xác minh điều kiện điểm danh. <button type="button" className="secondary" onClick={() => void load()} disabled={loading}>Thử tải lại</button>
    </div>}
    {notice && <div className="banner success" role="status">{notice}</div>}
    {loading ? <p role="status">Đang tải giáo án cá nhân…</p>
      : isAdminReadOnly ? visiblePlans.length === 0 ? <p className="dash-empty">Lớp đang xem chưa có giáo án cá nhân.</p>
        : <div className="individual-plan-list">{visiblePlans.map((plan) => <article className="individual-plan-row" key={plan.id}>
          <div><strong>{plan.memberName}</strong><small>{plan.className} · Phiên bản {plan.currentRevision?.revisionNumber ?? "—"}</small></div>
          <button type="button" className="secondary" onClick={() => void openPlan(plan)}>Xem giáo án</button>
        </article>)}</div>
      : isCoach ? <>
        {members.length === 0 ? plans.length > 0 ? <div className="individual-plan-list">{plans.map((plan) => <article className="individual-plan-row" key={plan.id}>
          <div><strong>{plan.memberName}</strong><small>{plan.className} · Phiên bản {plan.currentRevision?.revisionNumber ?? "—"}</small></div>
          <button type="button" className="secondary" onClick={() => void openPlan(plan)}>Mở giáo án</button>
        </article>)}</div> : <p className="dash-empty">Lớp hiện chưa có học viên đăng ký đã xác nhận.</p> : <div className="individual-plan-list">
          {members.map((member) => {
            const plan = planByMember.get(member.memberId);
            const canCreate = canCreateIndividualPlan(member, eligibilityLoaded);
            const eligibilityHint = individualPlanEligibilityMessage(member, eligibilityLoaded, eligibilityError);
            return <article className="individual-plan-row" key={member.memberId}>
              <div><strong>{member.memberName}</strong><small>{plan ? `Phiên bản ${plan.currentRevision?.revisionNumber ?? "—"}` : "Chưa có giáo án cá nhân"}</small></div>
              <div className="training-actions">
                {plan ? <button type="button" className="secondary" onClick={() => void openPlan(plan)}>Mở giáo án</button>
                  : <>
                    <button type="button" disabled={!canCreate} title={eligibilityHint || undefined}
                      onClick={() => { if (!mayLeaveEditor()) return; clearHistory(); setCreatingFor(member.memberId); setSelected(null); }}>Tạo giáo án</button>
                    {!canCreate && <small className="training-muted">{eligibilityHint}</small>}
                  </>}
              </div>
            </article>;
          })}
        </div>}
        {creatingFor != null && <WeeklyPlanEditor key={`create-${creatingFor}`} busy={busy} onDirtyChange={setEditorDirty} onCancel={() => { setEditorDirty(false); setCreatingFor(null); }}
          onSubmit={(form) => submit(creatingFor, form)} />}
      </> : visiblePlans.length === 0 ? <p className="dash-empty">{selectedClassKey !== null
          ? "Lớp đang chọn chưa có giáo án cá nhân. Giáo án sẽ xuất hiện khi huấn luyện viên tạo cho bạn."
          : "Bạn chưa có giáo án cá nhân. Giáo án sẽ hiển thị tại đây khi huấn luyện viên tạo cho bạn."}</p>
        : <div className="individual-plan-list">{visiblePlans.map((plan) => <article className="individual-plan-row" key={plan.id}>
          <div><strong>{plan.className}</strong><small>Phiên bản {plan.currentRevision?.revisionNumber ?? "—"}</small></div>
          <button type="button" className="secondary" onClick={() => void openPlan(plan)}>Xem giáo án</button>
        </article>)}</div>}

    {loadingDetail && <p role="status">Đang tải chi tiết…</p>}
    {visibleSelected && <section className="individual-plan-detail" aria-label="Chi tiết giáo án cá nhân">
      <div className="individual-plans-heading">
        <div><h3>{visibleSelected.className} · {visibleSelected.memberName}</h3>
          <p className="training-muted">Phiên bản {visibleSelected.currentRevision?.revisionNumber ?? "—"} · cập nhật {formatDate(visibleSelected.updatedAt)}</p></div>
        {canManageTraining && <button type="button" onClick={() => setEditing(true)}>Chỉnh sửa</button>}
      </div>
      {editing && canManageTraining ? <WeeklyPlanEditor key={`edit-${visibleSelected.id}-${visibleSelected.currentRevision?.id}`} initial={visibleSelected.currentRevision}
        busy={busy} onDirtyChange={setEditorDirty} onCancel={() => { setEditorDirty(false); setEditing(false); }} onSubmit={(form) => submit(visibleSelected.memberId, form, visibleSelected.id)} />
        : <PlanRevision revision={visibleSelected.currentRevision} />}
      <div className="training-actions">
        <button type="button" className="secondary" onClick={() => void showHistory(visibleSelected.id)}>Lịch sử phiên bản</button>
        {canManageTraining && <button type="button" className="secondary" onClick={() => { if (!mayLeaveEditor()) return; clearHistory(); setSelected(null); }}>Đóng chi tiết</button>}
      </div>
      {historyPlanId === visibleSelected.id && history && <div className="individual-plan-history"><h4>Lịch sử chỉnh sửa</h4>
        {history.length === 0 ? <p className="training-muted">Chưa có lịch sử phiên bản.</p> : history.map((revision) => <details key={revision.id}>
          <summary>Phiên bản {revision.revisionNumber} · {formatDate(revision.createdAt)} · {revision.revisedByName}</summary>
          <PlanRevision revision={revision} />
        </details>)}
      </div>}
      {loadingHistory && <p role="status">Đang tải lịch sử phiên bản…</p>}
    </section>}
  </section>;
}

function PlanRevision({ revision }) {
  if (!revision) return <p className="training-muted">Chưa có nội dung giáo án.</p>;
  return <div className="individual-plan-revision"><h4>{revision.goal}</h4>
    {revision.description && <p>{revision.description}</p>}
    {(revision.weeks || []).map((week) => <section className="individual-plan-week-view" key={`${week.weekNumber}-${week.title}`}>
      <h5>Tuần {week.weekNumber}{week.title ? ` · ${week.title}` : ""}</h5>
      {week.description && <p>{week.description}</p>}
      {!week.exercises?.length ? <p className="training-muted">Chưa có bài tập trong tuần này.</p> : <ul className="training-exercises">
        {week.exercises.map((exercise) => <li key={exercise.id ?? `${exercise.sortOrder}-${exercise.name}`}><div><b>{exercise.name}</b>
          <small>{[exercise.sets != null && `${exercise.sets} hiệp`, exercise.reps != null && `${exercise.reps} lần`, exercise.durationMinutes != null && `${exercise.durationMinutes} phút`].filter(Boolean).join(" · ") || "Chưa đặt số lượng"}</small>
          {exercise.notes && <small>{exercise.notes}</small>}</div></li>)}
      </ul>}
    </section>)}
  </div>;
}

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(date);
}
