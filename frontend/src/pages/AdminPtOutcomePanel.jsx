import { useCallback, useEffect, useMemo, useState } from "react";
import { correctAdminPtBookingOutcomeApi, getAdminPtBookingsApi } from "../api";
import "./AdminPtOutcomePanel.css";

const PAGE_SIZE = 10;
const STATUS_LABEL = {
  PENDING_PAYMENT: "Chờ thanh toán",
  CONFIRMED: "Đã xác nhận",
  CANCELLED: "Đã hủy",
  EXPIRED: "Hết hạn",
  COMPLETED: "Hoàn thành",
  NO_SHOW: "Vắng mặt (buổi đã sử dụng)",
};
const formatDate = (value) => value ? new Date(value).toLocaleString("vi-VN") : "—";

export default function AdminPtOutcomePanel({ token }) {
  const [rows, setRows] = useState([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try { setRows(await getAdminPtBookingsApi(token)); }
    catch (e) { setError(e?.message || "Không tải được danh sách buổi PT."); }
    finally { setLoading(false); }
  }, [token]);

  useEffect(() => {
    let active = true;
    getAdminPtBookingsApi(token)
      .then((data) => { if (active) setRows(data); })
      .catch((e) => { if (active) setError(e?.message || "Không tải được danh sách buổi PT."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [token]);

  const visible = useMemo(() => {
    const term = query.trim().toLocaleLowerCase("vi");
    return rows.filter((row) => (!statusFilter || row.status === statusFilter)
      && (!term || [row.id, row.memberName, row.coachName, row.sportName, STATUS_LABEL[row.status] || row.status]
        .some((value) => String(value ?? "").toLocaleLowerCase("vi").includes(term))));
  }, [rows, query, statusFilter]);
  const pageCount = Math.max(1, Math.ceil(visible.length / PAGE_SIZE));
  const pageRows = visible.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  function updateQuery(setter, value) {
    setter(value);
    setPage(0);
  }

  async function correct() {
    if (!selected || !reason.trim() || reason.trim().length > 300 || busy) return;
    const outcome = selected.status === "COMPLETED" ? "NO_SHOW" : "COMPLETED";
    setBusy(true); setError(""); setNotice("");
    try {
      await correctAdminPtBookingOutcomeApi(token, selected.id, outcome, reason.trim());
      setRows((current) => current.map((item) => item.id === selected.id ? { ...item, status: outcome } : item));
      setSelected(null); setReason("");
      setNotice(`Đã cập nhật kết quả buổi PT #${selected.id}. Audit Logs lưu lịch sử điều chỉnh.`);
    } catch (e) { setError(e?.message || "Không thể điều chỉnh kết quả buổi PT."); }
    finally { setBusy(false); }
  }

  return <section className="coach-attendance-panel admin-pt-outcomes" aria-labelledby="admin-pt-outcomes-title">
    <div className="coach-attendance-section-heading">
      <div><h2 id="admin-pt-outcomes-title">Giám sát kết quả huấn luyện cá nhân</h2><p>Tìm theo mã buổi, hội viên, huấn luyện viên hoặc môn thể thao.</p></div>
      <button type="button" className="coach-attendance-refresh" onClick={() => void load()} disabled={loading}>{loading ? "Đang tải…" : "Làm mới"}</button>
    </div>
    <div className="admin-pt-filters">
      <label className="admin-pt-search">Tìm buổi PT<input value={query} onChange={(event) => updateQuery(setQuery, event.target.value)} placeholder="Mã buổi, tên hội viên hoặc huấn luyện viên" /></label>
      <label className="admin-pt-status-filter">Trạng thái<select value={statusFilter} onChange={(event) => updateQuery(setStatusFilter, event.target.value)}>
        <option value="">Tất cả trạng thái</option>
        {Object.entries(STATUS_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
    </div>
    {error && <p className="coach-attendance-alert is-error" role="alert">{error}</p>}
    {notice && <p className="coach-attendance-alert is-success" role="status">{notice}</p>}
    {loading ? <p role="status">Đang tải buổi PT…</p> : visible.length === 0 ? <p className="coach-attendance-empty">Không tìm thấy buổi PT phù hợp.</p> : <>
      <div className="coach-attendance-table-wrap admin-pt-table-wrap"><table className="coach-attendance-table admin-pt-table"><thead><tr><th>Mã buổi</th><th>Hội viên</th><th>Huấn luyện viên</th><th>Ngày / giờ</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
        {pageRows.map((row) => <tr key={row.id}>
          <td>#{row.id}</td><td>{row.memberName || "—"}</td><td>{row.coachName || "—"}</td><td>{formatDate(row.startTime)}</td>
          <td><span className={`coach-attendance-badge status-${String(row.status || "").toLowerCase()}`}>{STATUS_LABEL[row.status] || row.status}</span></td>
          <td className="admin-pt-actions"><button type="button" className="coach-attendance-history-button" onClick={() => { setSelected(row); setReason(""); }}>Chi tiết</button></td>
        </tr>)}
      </tbody></table></div>
      <div className="admin-pt-pagination"><span>{visible.length} buổi · Trang {page + 1}/{pageCount}</span>
        <div><button type="button" onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={page === 0}>Trước</button>
          <button type="button" onClick={() => setPage((current) => Math.min(pageCount - 1, current + 1))} disabled={page + 1 >= pageCount}>Sau</button></div>
      </div>
    </>}
    {selected && <div className="admin-pt-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) setSelected(null); }}>
      <section className="admin-pt-dialog" role="dialog" aria-modal="true" aria-labelledby="admin-pt-detail-title">
        <header><div><h3 id="admin-pt-detail-title">Buổi PT #{selected.id}</h3><p>{selected.memberName || "—"} · {selected.coachName || "—"}</p></div><button type="button" className="coach-attendance-history-button" onClick={() => setSelected(null)} disabled={busy}>Đóng</button></header>
        <dl><div><dt>Môn thể thao</dt><dd>{selected.sportName || "—"}</dd></div><div><dt>Bắt đầu</dt><dd>{formatDate(selected.startTime)}</dd></div><div><dt>Kết thúc</dt><dd>{formatDate(selected.endTime)}</dd></div><div><dt>Trạng thái</dt><dd><span className={`coach-attendance-badge status-${String(selected.status || "").toLowerCase()}`}>{STATUS_LABEL[selected.status] || selected.status}</span></dd></div></dl>
        {(selected.status === "COMPLETED" || selected.status === "NO_SHOW") && <form onSubmit={(event) => { event.preventDefault(); void correct(); }} className="admin-pt-correction">
          <label htmlFor="admin-pt-correction-reason">Lý do điều chỉnh *<textarea id="admin-pt-correction-reason" value={reason} maxLength={300} onChange={(event) => setReason(event.target.value)} required /></label>
          <div className="admin-pt-transition" aria-label="Chuyển trạng thái">
            <span className={`coach-attendance-badge status-${String(selected.status).toLowerCase()}`}>{selected.status === "COMPLETED" ? "Hoàn thành" : "Vắng mặt"}</span>
            <span className="admin-pt-transition-arrow" aria-hidden="true">→</span>
            <span className={`coach-attendance-badge status-${selected.status === "COMPLETED" ? "no_show" : "completed"}`}>{selected.status === "COMPLETED" ? "Vắng mặt" : "Hoàn thành"}</span>
          </div>
          <p>Điều chỉnh được ghi vào Audit Logs. Buổi PT vẫn được tính đã sử dụng và không tạo khoản hoàn tiền.</p>
          <footer><button type="button" className="coach-attendance-history-button" onClick={() => setSelected(null)} disabled={busy}>Hủy</button><button type="submit" className="coach-attendance-save" disabled={!reason.trim() || busy}>{busy ? "Đang lưu…" : "Lưu điều chỉnh"}</button></footer>
        </form>}
      </section>
    </div>}
  </section>;
}
