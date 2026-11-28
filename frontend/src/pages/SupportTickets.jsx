import { useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import AdminTopBar from "../components/AdminTopBar";
import { supportTicketDateTime } from "../supportTicketPresentation";
import {
  assignSupportTicketToMeApi, createSupportTicketApi, getMySupportTicketsApi,
  getCurrentUserApi, getStaffSupportTicketsApi, getSupportTicketApi, replySupportTicketApi,
  updateSupportTicketStatusApi,
} from "../api";
import "./SupportTickets.css";

const CATEGORIES = [
  ["GENERAL", "Chung"], ["MEMBERSHIP", "Hội viên"], ["COURSE", "Khóa học"],
  ["PT_BOOKING", "Huấn luyện cá nhân"], ["PAYMENT", "Thanh toán"], ["OTHER", "Khác"],
];
const STATUSES = [
  ["OPEN", "Mở"], ["IN_PROGRESS", "Đang xử lý"], ["RESOLVED", "Đã giải quyết"], ["CLOSED", "Đã đóng"],
];
const statusName = (status) => STATUSES.find(([value]) => value === status)?.[1] || status;
const categoryName = (category) => CATEGORIES.find(([value]) => value === category)?.[1] || category;
const localizedError = (error, fallback) => ({
  401: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  403: "Bạn không có quyền thực hiện thao tác này.",
  404: "Yêu cầu hỗ trợ không còn tồn tại. Vui lòng tải lại danh sách.",
  409: "Yêu cầu đã thay đổi hoặc trạng thái không còn cho phép thao tác. Vui lòng tải lại.",
}[error?.status] || fallback);

export default function SupportTickets({ token, role, currentUserName, onLogout, onNavigate }) {
  const member = role === "MEMBER";
  const isAdmin = role === "ADMIN";
  const [filters, setFilters] = useState({ status: "", category: "", search: "" });
  const [appliedFilters, setAppliedFilters] = useState(filters);
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [listResult, setListResult] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [detailResult, setDetailResult] = useState(null);
  const [showCreate, setShowCreate] = useState(false);
  const [subject, setSubject] = useState("");
  const [category, setCategory] = useState("GENERAL");
  const [description, setDescription] = useState("");
  const [reply, setReply] = useState("");
  const [viewerId, setViewerId] = useState(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [actionError, setActionError] = useState("");
  const [actionNotice, setActionNotice] = useState("");
  const [feedbackArea, setFeedbackArea] = useState("");
  const createDialogRef = useRef(null);
  const createSubjectRef = useRef(null);
  const createTriggerRef = useRef(null);

  const listKey = useMemo(() => JSON.stringify({ member, page, appliedFilters, refresh }), [member, page, appliedFilters, refresh]);
  const list = listResult?.key === listKey ? listResult.data : null;
  const detail = detailResult?.id === selectedId ? detailResult.data : null;

  useEffect(() => {
    if (!token) return undefined;
    let active = true;
    getCurrentUserApi(token).then((user) => { if (active) setViewerId(user?.id ?? null); }).catch(() => { if (active) setViewerId(null); });
    return () => { active = false; };
  }, [token]);

  useEffect(() => {
    if (!member || !showCreate) return undefined;
    const previousFocus = document.activeElement;
    createSubjectRef.current?.focus();
    const handleKeyDown = (event) => {
      if (event.key === "Escape" && !busyRef.current) { event.preventDefault(); setShowCreate(false); return; }
      if (event.key !== "Tab") return;
      const controls = [...(createDialogRef.current?.querySelectorAll('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])') || [])];
      if (!controls.length) return;
      const first = controls[0]; const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [member, showCreate]);

  function closeCreate() { if (!busyRef.current) setShowCreate(false); }

  useEffect(() => {
    if (!token) return undefined;
    const controller = new AbortController();
    const request = member
      ? getMySupportTicketsApi(token, { page, size: 5 }, controller.signal)
      : getStaffSupportTicketsApi(token, { ...appliedFilters, page, size: 5 }, controller.signal);
    request.then((data) => {
      if (!controller.signal.aborted) {
        setListResult({ key: listKey, data });
        if (Number.isFinite(data?.totalPages) && data.totalPages === 0 && page !== 0) setPage(0);
        else if (Number.isFinite(data?.totalPages) && data.totalPages > 0 && page >= data.totalPages) setPage(data.totalPages - 1);
      }
    }).catch((error) => {
      if (!controller.signal.aborted) setListResult({ key: listKey, error: localizedError(error, "Không thể tải danh sách yêu cầu hỗ trợ.") });
    });
    return () => controller.abort();
  }, [token, member, page, appliedFilters, listKey]);

  useEffect(() => {
    if (!token || selectedId == null) return undefined;
    const controller = new AbortController();
    getSupportTicketApi(token, selectedId, controller.signal).then((data) => {
      if (!controller.signal.aborted) setDetailResult({ id: selectedId, data });
    }).catch((error) => {
      if (!controller.signal.aborted) setDetailResult({ id: selectedId, error: localizedError(error, "Không thể tải chi tiết yêu cầu hỗ trợ.") });
    });
    return () => controller.abort();
  }, [token, selectedId, refresh]);

  async function runAction(action, successMessage = "Đã cập nhật yêu cầu.", area = "management") {
    if (busyRef.current) return false;
    busyRef.current = true;
    setFeedbackArea(area);
    setBusy(true); setActionError(""); setActionNotice("");
    try {
      const updated = await action();
      setDetailResult({ id: selectedId, data: updated });
      setActionNotice(successMessage);
      setRefresh((value) => value + 1);
      return true;
    } catch (error) {
      setActionError(localizedError(error, "Không thể hoàn tất thao tác. Vui lòng thử lại."));
      setRefresh((value) => value + 1);
      return false;
    } finally { busyRef.current = false; setBusy(false); }
  }

  async function submitCreate(event) {
    event.preventDefault();
    if (busyRef.current || !subject.trim() || !description.trim()) return;
    busyRef.current = true;
    setFeedbackArea("create");
    setBusy(true); setActionError(""); setActionNotice("");
    try {
      const created = await createSupportTicketApi(token, {
        subject: subject.trim(), category, description: description.trim(),
      });
      setSelectedId(created.id); setDetailResult({ id: created.id, data: created });
      setSubject(""); setDescription(""); setCategory("GENERAL"); setShowCreate(false);
      setActionNotice("Đã gửi yêu cầu hỗ trợ."); setFeedbackArea("detail"); setPage(0); setRefresh((value) => value + 1);
    } catch (error) { setActionError(localizedError(error, "Không thể gửi yêu cầu hỗ trợ. Vui lòng thử lại.")); }
    finally { busyRef.current = false; setBusy(false); }
  }

  async function submitReply(event) {
    event.preventDefault();
    if (busy || !reply.trim() || !detail) return;
    const wasResolved = detail.status === "RESOLVED";
    const sent = await runAction(() => replySupportTicketApi(token, selectedId, reply.trim()),
      wasResolved ? "Đã gửi phản hồi và mở lại yêu cầu." : "Đã gửi phản hồi.", "reply");
    if (sent) setReply("");
  }

  async function handleStaffStatusAction(event) {
    const next = event.currentTarget.dataset.status;
    if (next === "RESOLVED" && !window.confirm("Đánh dấu yêu cầu hỗ trợ này là đã giải quyết?")) return;
    if (next === "CLOSED" && !window.confirm("Đóng yêu cầu hỗ trợ này? Sau khi đóng, nội dung chỉ có thể được xem.")) return;
    await runAction(() => updateSupportTicketStatusApi(token, selectedId, next));
  }

  const assignedToViewer = detail?.assignedStaffId != null && viewerId != null
    && Number.isFinite(Number(detail.assignedStaffId)) && Number.isFinite(Number(viewerId))
    && Number(detail.assignedStaffId) === Number(viewerId);
  const canClaim = !member && detail && detail.assignedStaffId == null
    && ["OPEN", "IN_PROGRESS"].includes(detail.status);
  const canReply = member || (isAdmin && detail?.assignedStaffId != null) || assignedToViewer;
  const staffStatusActions = detail?.status === "OPEN"
    ? (isAdmin || assignedToViewer ? [["RESOLVED", "Đánh dấu đã giải quyết"]] : [])
    : detail?.status === "IN_PROGRESS" ? (isAdmin || assignedToViewer ? [["RESOLVED", "Đánh dấu đã giải quyết"]] : [])
      : detail?.status === "RESOLVED" && (isAdmin || assignedToViewer) ? [["OPEN", "Mở lại"], ["CLOSED", "Đóng yêu cầu"]] : [];
  return (
    <div className="layout support-layout">
      <Sidebar role={role} activePage="support-tickets" onLogout={onLogout} onNavigate={onNavigate} />
      <main className={`main support-main${isAdmin ? " support-admin-main" : ""}`}>
        {isAdmin && <AdminTopBar pageLabel="YÊU CẦU HỖ TRỢ" displayName={currentUserName} avatarName={currentUserName} />}
        <div className={isAdmin ? "support-admin-content" : `support-nonadmin-content${member ? " support-member-content" : ""}`}>
        <header className={`support-heading${member ? " support-member-heading" : role === "RECEPTIONIST" ? " support-receptionist-heading" : ""}`}>
          <div><h1>{member ? "Hỗ trợ của tôi" : "Quản lý yêu cầu hỗ trợ"}</h1>
            <p className="subtitle">{member ? "Theo dõi yêu cầu và trao đổi với đội ngũ SportCenter." : "Tiếp nhận và theo dõi yêu cầu của hội viên"}</p></div>
          <div className="support-heading-actions">
            <button type="button" className="support-refresh" onClick={() => setRefresh((value) => value + 1)} disabled={Boolean(listResult?.loading)} aria-label="Làm mới yêu cầu">Làm mới</button>
            {member && <button ref={createTriggerRef} className="btn-primary" type="button" onClick={() => { setShowCreate(true); setActionError(""); }}>Gửi yêu cầu hỗ trợ</button>}
          </div>
        </header>

        {showCreate && member && <div className="support-modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) closeCreate(); }}>
          <section ref={createDialogRef} className="support-create-dialog" role="dialog" aria-modal="true" aria-labelledby="support-create-title">
            <div className="support-create-dialog-heading"><div><h2 id="support-create-title">Gửi yêu cầu hỗ trợ</h2><p>Cho chúng tôi biết bạn cần hỗ trợ vấn đề gì.</p></div><button type="button" className="support-dialog-close" onClick={closeCreate} disabled={busy} aria-label="Đóng"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg></button></div>
            <form className="support-create" onSubmit={submitCreate}>
              <label>Tiêu đề<input ref={createSubjectRef} value={subject} onChange={(event) => setSubject(event.target.value)} maxLength={160} required /></label>
              <label>Danh mục<select value={category} onChange={(event) => setCategory(event.target.value)}>
                {CATEGORIES.map(([value, label]) => <option value={value} key={value}>{label}</option>)}
              </select></label>
              <label>Mô tả<textarea value={description} onChange={(event) => setDescription(event.target.value)} maxLength={10000} rows={5} required /></label>
            {feedbackArea === "create" && actionError && <div className="support-alert error" role="alert">{actionError}</div>}
              <div className="support-create-actions"><button type="button" className="btn-light" onClick={closeCreate} disabled={busy}>Hủy</button><button className="btn-primary" type="submit" disabled={busy || !subject.trim() || !description.trim()}>{busy ? "Đang gửi…" : "Gửi yêu cầu"}</button></div>
            </form>
          </section>
        </div>}

        {!member && <form className="support-panel support-filters" onSubmit={(event) => { event.preventDefault(); setPage(0); setAppliedFilters({ ...filters }); }}>
          <label>Tìm kiếm<input value={filters.search} onChange={(event) => setFilters({ ...filters, search: event.target.value })} maxLength={100} placeholder="Tiêu đề hoặc tên hội viên" /></label>
          <label>Trạng thái<select value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}>
            <option value="">Tất cả trạng thái</option>{STATUSES.map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select></label>
          <label>Danh mục<select value={filters.category} onChange={(event) => setFilters({ ...filters, category: event.target.value })}>
            <option value="">Tất cả danh mục</option>{CATEGORIES.map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select></label>
          <button className="btn-primary" type="submit">Lọc</button>
        </form>}

        <div className={`support-columns${member ? " support-member-columns" : ""}`}>
          <section className="support-panel support-list" aria-label="Danh sách yêu cầu hỗ trợ">
            <div className="support-list-heading"><h2>{member ? "Yêu cầu của bạn" : "Danh sách yêu cầu"}</h2>{list && <span>{list.totalElements ?? list.totalItems ?? list.content?.length ?? 0} yêu cầu</span>}</div>
            {!list && !listResult?.error && <p role="status">Đang tải danh sách…</p>}
            {listResult?.key === listKey && listResult.error && <div role="alert">{listResult.error} <button type="button" onClick={() => setRefresh((value) => value + 1)}>Thử lại</button></div>}
            {list?.content?.length === 0 && <p className="support-muted">Chưa có yêu cầu hỗ trợ nào.</p>}
            {list?.content?.map((ticket) => <button type="button" className={`support-ticket-row ${selectedId === ticket.id ? "selected" : ""}`} key={ticket.id} onClick={() => { setSelectedId(ticket.id); setActionError(""); setActionNotice(""); }}>
              <span className={`ticket-status status-${ticket.status.toLowerCase()}`}>{statusName(ticket.status)}</span>
              <strong>{ticket.subject}</strong>
              <span>{categoryName(ticket.category)}{!member ? ` · ${ticket.memberName}` : ""}</span>
              <small>Cập nhật {supportTicketDateTime(ticket.updatedAt)}</small>
            </button>)}
            {list && list.totalPages > 1 && <div className="support-pagination">
              <button type="button" disabled={page <= 0} onClick={() => setPage((value) => value - 1)}>Trước</button>
              <span>{page + 1}/{list.totalPages}</span>
              <button type="button" disabled={page + 1 >= list.totalPages} onClick={() => setPage((value) => value + 1)}>Sau</button>
            </div>}
          </section>

          <section className="support-panel support-detail" aria-label="Chi tiết yêu cầu hỗ trợ">
            {!selectedId && <p className="support-muted">Chọn một yêu cầu để xem nội dung trao đổi.</p>}
            {selectedId && !detail && !detailResult?.error && <p role="status">Đang tải chi tiết…</p>}
            {selectedId && detailResult?.id === selectedId && detailResult.error && <div role="alert">{detailResult.error} <button type="button" onClick={() => setRefresh((value) => value + 1)}>Thử lại</button></div>}
            {detail && <>
              <div className="support-detail-heading"><div><p>Yêu cầu #{detail.id} · {categoryName(detail.category)}</p><h2>{detail.subject}</h2></div>
                <span className={`ticket-status status-${detail.status.toLowerCase()}`}>{statusName(detail.status)}</span></div>
              <div className={`support-ticket-meta${member ? " support-member-meta" : ""}`}>
                {feedbackArea === "detail" && actionNotice && <span className="support-meta-feedback" role="status">{actionNotice}</span>}
                {!member && <span>Hội viên: <strong>{detail.memberName}</strong> (#{detail.memberId})</span>}
                <span>Tạo lúc: {supportTicketDateTime(detail.createdAt)}</span><span>Cập nhật: {supportTicketDateTime(detail.updatedAt)}</span>
                {detail.assignedStaffName && <span>Phụ trách: {detail.assignedStaffName}</span>}
                {detail.resolvedAt && <span>Giải quyết: {supportTicketDateTime(detail.resolvedAt)}</span>}
                {detail.closedAt && <span>Đóng lúc: {supportTicketDateTime(detail.closedAt)}</span>}
              </div>
              <div className="support-thread" aria-label="Lịch sử trao đổi">
                <article className={`support-message original${Number(detail.memberId) === Number(viewerId) ? " is-viewer" : ""}`}><strong>{detail.memberName} · Yêu cầu ban đầu</strong><time>{supportTicketDateTime(detail.createdAt)}</time><p>{detail.description}</p></article>
                {detail.messages?.map((message) => <article className={`support-message ${message.senderRole === "MEMBER" ? "from-member" : "from-staff"}${Number(message.senderId) === Number(viewerId) ? " is-viewer" : ""}`} key={message.id}>
                  <strong>{message.senderName} · {{ MEMBER: "Hội viên", RECEPTIONIST: "Lễ tân", ADMIN: "Quản trị viên" }[message.senderRole] || "Người gửi"}</strong><time>{supportTicketDateTime(message.createdAt)}</time><p>{message.message}</p>
                </article>)}
              </div>
              {detail.status !== "CLOSED" && (canReply || (!member && (canClaim || staffStatusActions.length > 0))) && <form className="support-reply-form" onSubmit={submitReply}>
                {canReply && detail.status === "RESOLVED" && <p>Gửi phản hồi sẽ mở lại yêu cầu để tiếp tục hỗ trợ.</p>}
                {canReply && feedbackArea === "reply" && actionError && <div className="support-alert error" role="alert">{actionError}</div>}
                {canReply && feedbackArea === "reply" && actionNotice && <div className="support-alert success" role="status">{actionNotice}</div>}
                {!member && feedbackArea === "management" && actionError && <div className="support-alert error" role="alert">{actionError}</div>}
                {!member && feedbackArea === "management" && actionNotice && <div className="support-alert success" role="status">{actionNotice}</div>}
                {canReply && <><label htmlFor="supportReply">Phản hồi</label><textarea id="supportReply" value={reply} onChange={(event) => setReply(event.target.value)} maxLength={10000} rows={3} required /></>}
                <div className="support-conversation-actions" aria-label="Thao tác yêu cầu">
                  {canReply && <button className="btn-primary" type="submit" disabled={busy || !reply.trim()}>{busy ? "Đang gửi…" : "Gửi phản hồi"}</button>}
                  {!member && canClaim && <button className="btn-primary" type="button" disabled={busy} onClick={() => runAction(() => assignSupportTicketToMeApi(token, selectedId), "Đã nhận và bắt đầu xử lý yêu cầu.", "management")}>Nhận xử lý</button>}
                  {!member && staffStatusActions.map(([next, label]) => <button type="button" className={next === "CLOSED" ? "btn-light support-destructive" : "btn-light"} key={next} data-status={next} disabled={busy} onClick={handleStaffStatusAction}>{label}</button>)}
                </div>
              </form>}
              {detail.status === "CLOSED" && <p className="support-muted">Yêu cầu đã đóng và chỉ có thể xem.</p>}
            </>}
          </section>
        </div>
        </div>
      </main>
    </div>
  );
}
