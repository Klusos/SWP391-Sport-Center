import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import { formatAuditDescription } from "../auditDescription.js";
import AdminTopBar from "../components/AdminTopBar";
import { correctAdminPtBookingOutcomeApi, getAdminAuditLogsApi, getAdminPtBookingOutcomeApi } from "../api";
import "./AdminAuditLogs.css";

const PAGE_SIZE = 20;
const emptyFilters = { actorUserId: "", action: "", entityType: "", entityId: "", from: "", to: "" };

function localDateInputToUtc(value) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function formatUtcAsLocal(value) {
  if (typeof value !== "string" || !value.trim()) return "—";
  const raw = value.trim();
  const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(raw);
  const date = new Date(hasZone ? raw : `${raw}Z`);
  return Number.isNaN(date.getTime()) ? "Thời gian không hợp lệ" : date.toLocaleString();
}

function displayError(error) {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Tài khoản không có quyền xem nhật ký hoạt động.";
  return error?.message || "Không thể tải nhật ký hoạt động. Vui lòng thử lại.";
}

function displayValue(value) {
  return value === null || value === undefined || value === "" ? "—" : value;
}

export default function AdminAuditLogs({ token, role, currentUserName, onLogout, onNavigate }) {
  const [draft, setDraft] = useState(emptyFilters);
  const [filters, setFilters] = useState(emptyFilters);
  const [pageNumber, setPageNumber] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filterError, setFilterError] = useState("");
  const [ptBookingId, setPtBookingId] = useState("");
  const [ptOutcome, setPtOutcome] = useState(null);
  const [ptCorrection, setPtCorrection] = useState("NO_SHOW");
  const [ptReason, setPtReason] = useState("");
  const [ptBusy, setPtBusy] = useState(false);
  const [ptError, setPtError] = useState("");
  const [ptNotice, setPtNotice] = useState("");

  async function lookupPtOutcome(event) {
    event.preventDefault();
    if (!/^\d+$/.test(ptBookingId) || Number(ptBookingId) <= 0) {
      setPtError("Nhập ID buổi PT hợp lệ.");
      return;
    }
    setPtBusy(true);
    setPtError("");
    setPtNotice("");
    try {
      const outcome = await getAdminPtBookingOutcomeApi(token, ptBookingId);
      setPtOutcome(outcome);
      setPtCorrection(outcome.status === "COMPLETED" ? "NO_SHOW" : "COMPLETED");
    } catch (requestError) {
      setPtOutcome(null);
      setPtError(requestError.message || "Không thể tải kết quả buổi PT.");
    } finally {
      setPtBusy(false);
    }
  }

  async function correctPtOutcome(event) {
    event.preventDefault();
    if (!ptOutcome || ptReason.trim().length === 0 || ptReason.trim().length > 300) {
      setPtError("Lý do điều chỉnh là bắt buộc và tối đa 300 ký tự.");
      return;
    }
    setPtBusy(true);
    setPtError("");
    setPtNotice("");
    try {
      const outcome = await correctAdminPtBookingOutcomeApi(token, ptOutcome.id, ptCorrection, ptReason.trim());
      setPtOutcome(outcome);
      setPtReason("");
      setPtNotice("Đã cập nhật kết quả. Lịch sử điều chỉnh được ghi trong Audit Logs.");
      setDraft((current) => ({ ...current, entityType: "PT_BOOKING_OUTCOME", entityId: String(outcome.id) }));
      setFilters((current) => ({ ...current, entityType: "PT_BOOKING_OUTCOME", entityId: String(outcome.id) }));
      setPageNumber(0);
      setLoading(true);
      setReloadKey((value) => value + 1);
    } catch (requestError) {
      setPtError(requestError.message || "Không thể điều chỉnh kết quả buổi PT.");
    } finally {
      setPtBusy(false);
    }
  }

  const queryFilters = useMemo(() => filters, [filters]);
  const load = useCallback(async (signal, isCurrent) => {
    try {
      const response = await getAdminAuditLogsApi(token, {
        page: pageNumber,
        size: PAGE_SIZE,
        ...queryFilters,
      }, signal);
      if (isCurrent()) setResult(response && Array.isArray(response.content) ? response : { content: [], totalElements: 0, totalPages: 0, number: pageNumber });
    } catch (requestError) {
      if (isCurrent() && requestError?.name !== "AbortError") {
        setResult(null);
        setError(displayError(requestError));
      }
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, [token, pageNumber, queryFilters]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    void Promise.resolve().then(() => {
      if (active) return load(controller.signal, () => active);
      return undefined;
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [load, reloadKey]);

  function changeFilter(event) {
    const { name, value } = event.target;
    setDraft((current) => ({ ...current, [name]: value }));
    setFilterError("");
  }

  function applyFilters(event) {
    event.preventDefault();
    setFilterError("");
    const actor = draft.actorUserId.trim();
    if (actor && (!/^\d+$/.test(actor) || !Number.isSafeInteger(Number(actor)) || Number(actor) <= 0)) {
      setFilterError("Actor User ID phải là số nguyên dương hợp lệ.");
      return;
    }
    const from = localDateInputToUtc(draft.from);
    const to = localDateInputToUtc(draft.to);
    if (from === null || to === null) {
      setFilterError("Vui lòng nhập khoảng thời gian hợp lệ.");
      return;
    }
    if (from && to && new Date(from).getTime() > new Date(to).getTime()) {
      setFilterError("Thời điểm bắt đầu không được sau thời điểm kết thúc.");
      return;
    }
    setLoading(true);
    setError("");
    setFilters({
      actorUserId: actor,
      action: draft.action.trim(),
      entityType: draft.entityType.trim(),
      entityId: draft.entityId.trim(),
      from,
      to,
    });
    setPageNumber(0);
  }

  function clearFilters() {
    setLoading(true);
    setError("");
    setDraft(emptyFilters);
    setFilters(emptyFilters);
    setFilterError("");
    setPageNumber(0);
  }

  function refresh() {
    setLoading(true);
    setError("");
    setReloadKey((value) => value + 1);
  }

  const entries = Array.isArray(result?.content) ? result.content : [];
  const currentPage = Number.isInteger(result?.number) ? result.number : pageNumber;
  const totalPages = Number.isFinite(result?.totalPages) ? result.totalPages : 0;
  const totalElements = Number.isFinite(result?.totalElements) ? result.totalElements : entries.length;
  const hasPrevious = result?.first === false || currentPage > 0;
  const hasNext = result?.last === false || currentPage + 1 < totalPages;

  return (
    <div className="layout">
      <Sidebar role={role} activePage="audit-logs" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main admin-audit-main">
        <AdminTopBar pageLabel={"NH\u1eacT K\u00dd"} displayName={currentUserName} avatarName={currentUserName} />
        <div className="admin-audit-content">
        <header className="admin-audit-header">
          <div>
            <h1>Audit Logs</h1>
            <p className="subtitle">Nhật ký các thao tác quản trị và nghiệp vụ đã được ghi nhận.</p>
          </div>
          <button className="admin-audit-button is-secondary" type="button" onClick={refresh} disabled={loading}>
            <i className="bi bi-arrow-clockwise" aria-hidden="true" /> Làm mới
          </button>
        </header>

        <section className="admin-audit-filters" aria-labelledby="pt-outcome-correction-title">
          <h2 id="pt-outcome-correction-title">Điều chỉnh kết quả buổi PT</h2>
          <form onSubmit={lookupPtOutcome}>
            <label>ID buổi PT<input inputMode="numeric" value={ptBookingId} onChange={(event) => setPtBookingId(event.target.value)} /></label>
            <button className="admin-audit-button is-secondary" type="submit" disabled={ptBusy}>Tìm kết quả</button>
          </form>
          {ptOutcome && <form onSubmit={correctPtOutcome}>
            <p>Buổi #{ptOutcome.id} hiện ở trạng thái <strong>{ptOutcome.status}</strong>.</p>
            {(ptOutcome.status === "COMPLETED" || ptOutcome.status === "NO_SHOW") ? <>
              <label>Kết quả mới<select value={ptCorrection} onChange={(event) => setPtCorrection(event.target.value)}>
                <option value="COMPLETED">Hoàn thành</option><option value="NO_SHOW">Vắng mặt (buổi đã sử dụng)</option>
              </select></label>
              <label>Lý do điều chỉnh<textarea maxLength={300} required value={ptReason} onChange={(event) => setPtReason(event.target.value)} /></label>
              <button className="admin-audit-button is-primary" type="submit" disabled={ptBusy || !ptReason.trim()}>Lưu điều chỉnh</button>
            </> : <p>Chỉ có thể điều chỉnh kết quả COMPLETED hoặc NO_SHOW đã được ghi nhận.</p>}
          </form>}
          {ptError && <p className="admin-audit-error" role="alert">{ptError}</p>}
          {ptNotice && <p role="status">{ptNotice}</p>}
        </section>

        <form className="admin-audit-filters" onSubmit={applyFilters}>
          <div className="admin-audit-filter-grid">
            <label>Actor User ID<input name="actorUserId" inputMode="numeric" value={draft.actorUserId} onChange={changeFilter} placeholder="Ví dụ: 12" /></label>
            <label>Action<input name="action" value={draft.action} onChange={changeFilter} placeholder="Ví dụ: STATUS_CHANGE" /></label>
            <label>Entity Type<input name="entityType" value={draft.entityType} onChange={changeFilter} placeholder="Ví dụ: USER" /></label>
            <label>Entity ID<input name="entityId" value={draft.entityId} onChange={changeFilter} placeholder="ID đối tượng" /></label>
            <label>Từ thời điểm<input type="datetime-local" name="from" value={draft.from} onChange={changeFilter} /></label>
            <label>Đến thời điểm (không bao gồm)<input type="datetime-local" name="to" value={draft.to} onChange={changeFilter} /></label>
          </div>
          <p className="admin-audit-filter-hint">Thời gian nhập theo múi giờ thiết bị. Mốc “Từ” được tính gồm; mốc “Đến” không được tính.</p>
          {filterError && <p className="admin-audit-error" role="alert">{filterError}</p>}
          <div className="admin-audit-filter-actions">
            <button className="admin-audit-button is-primary" type="submit">Áp dụng bộ lọc</button>
            <button className="admin-audit-button is-secondary" type="button" onClick={clearFilters}>Xóa bộ lọc</button>
          </div>
        </form>

        <section className="admin-audit-results" aria-label="Audit log results">
          <div className="admin-audit-results-heading">
            <h2>Hoạt động</h2>
            {!loading && !error && <span>{totalElements.toLocaleString()} bản ghi</span>}
          </div>
          {loading ? <div className="admin-audit-state" role="status"><span className="admin-audit-spinner" />Đang tải nhật ký…</div>
            : error ? <div className="admin-audit-state is-error" role="alert"><p>{error}</p><button className="admin-audit-button is-secondary" type="button" onClick={refresh}>Thử lại</button></div>
              : entries.length === 0 ? <div className="admin-audit-state">Không tìm thấy hoạt động phù hợp.</div>
                : <div className="admin-audit-table-wrap"><table className="admin-audit-table">
                  <thead><tr><th>Thời gian</th><th>Tác nhân</th><th>Vai trò</th><th>Hành động</th><th>Loại đối tượng</th><th>ID đối tượng</th><th>Mô tả</th></tr></thead>
                  <tbody>{entries.map((entry) => {
                    const description = formatAuditDescription(entry);
                    return <tr key={entry?.id}>
                      <td data-label="Thời gian">{formatUtcAsLocal(entry?.createdAt)}</td>
                      <td data-label="Tác nhân" className="admin-audit-actor" title={entry?.actorCurrentDisplayName || undefined}>{entry?.actorUserId == null ? <><span>SYSTEM</span><small>Tác nhân nội bộ</small></> : <><span>{entry.actorCurrentDisplayName || "Tài khoản không còn truy cập được"}</span><small>User #{entry.actorUserId}</small></>}</td>
                      <td data-label="Vai trò"><span className="admin-audit-role">{displayValue(entry?.actorRole)}</span></td>
                      <td data-label="Hành động">{displayValue(entry?.action)}</td>
                      <td data-label="Loại đối tượng">{displayValue(entry?.entityType)}</td>
                      <td data-label="ID đối tượng">{displayValue(entry?.entityId)}</td>
                      <td data-label="Mô tả" className="admin-audit-description"><span>{description.text}</span>{description.formatted && <details><summary>Chi tiết kỹ thuật</summary><pre>{description.raw}</pre></details>}</td>
                    </tr>;
                  })}</tbody>
                </table></div>}
          {!loading && !error && totalPages > 1 && <nav className="admin-audit-pagination" aria-label="Phân trang audit logs">
            <button type="button" onClick={() => { setLoading(true); setError(""); setPageNumber((value) => Math.max(0, value - 1)); }} disabled={!hasPrevious || loading}>Trước</button>
            <span>Trang {currentPage + 1} / {totalPages}</span>
            <button type="button" onClick={() => { setLoading(true); setError(""); setPageNumber((value) => value + 1); }} disabled={!hasNext || loading}>Sau</button>
          </nav>}
        </section>
        </div>
      </main>
    </div>
  );
}

