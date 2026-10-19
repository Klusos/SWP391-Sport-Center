import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import "./Dashboard.css";
import { getRevenueReportApi, getMemberReportApi, getClassRegReportApi } from "../api";

const list = (d) => (Array.isArray(d) ? d : d?.content ?? []);
const money = (v) => Number(v ?? 0).toLocaleString("vi-VN") + "đ";
const human = (k) => k.replace(/([A-Z])/g, " $1").replace(/^./, (c) => c.toUpperCase());
const isMoneyKey = (k) => /amount|revenue|total|sum/i.test(k) && !/count/i.test(k);
const iso = (d) => d.toLocaleDateString("sv-SE");

function Table({ rows, nameLabel }) {
  if (!rows.length) return <p className="dash-empty">Không có dữ liệu trong khoảng này.</p>;
  return (
    <div className="queue-table-wrap">
      <table className="queue-table">
        <thead><tr><th>{nameLabel}</th><th>Số giao dịch</th><th>Tổng tiền</th></tr></thead>
        <tbody>{rows.map((r) => <tr key={r.id}><td>{r.name}</td><td>{r.paymentCount}</td><td>{money(r.totalAmount)}</td></tr>)}</tbody>
      </table>
    </div>
  );
}

/* RevenueReportResponse chưa rõ cấu trúc: hiển thị các số thành thẻ và mảng thành bảng */
function Revenue({ data }) {
  const entries = Object.entries(data || {});
  const nums = entries.filter(([, v]) => v !== null && v !== "" && typeof v !== "object" && !isNaN(Number(v)));
  const arrays = entries.filter(([, v]) => Array.isArray(v) && v.length && typeof v[0] === "object");
  return (<>
    {nums.length > 0 && (
      <div className="dash-stats">
        {nums.map(([k, v]) => (
          <div className="dash-stat" key={k}><div><span>{human(k)}</span><strong>{isMoneyKey(k) ? money(v) : Number(v).toLocaleString("vi-VN")}</strong></div><i className="dash-dot" /></div>
        ))}
      </div>
    )}
    {arrays.map(([k, rows]) => {
      const cols = Object.keys(rows[0]).filter((c) => typeof rows[0][c] !== "object");
      return (
        <div className="queue-table-wrap" key={k} style={{ marginBottom: 18 }}>
          <table className="queue-table">
            <thead><tr>{cols.map((c) => <th key={c}>{human(c)}</th>)}</tr></thead>
            <tbody>{rows.map((r, i) => <tr key={i}>{cols.map((c) => <td key={c}>{isMoneyKey(c) ? money(r[c]) : String(r[c] ?? "—")}</td>)}</tr>)}</tbody>
          </table>
        </div>
      );
    })}
    {nums.length === 0 && arrays.length === 0 && <p className="dash-empty">Không có dữ liệu doanh thu trong khoảng này.</p>}
  </>);
}

export default function Reports({ token, role, onLogout, onNavigate }) {
  const now = new Date();
  const [from, setFrom] = useState(iso(new Date(now.getFullYear(), now.getMonth(), 1)));
  const [to, setTo] = useState(iso(now));
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function run() {
    if (from > to) return setError("Ngày bắt đầu phải trước ngày kết thúc.");
    setLoading(true); setError("");
    const rs = await Promise.allSettled([getRevenueReportApi(token, from, to), getMemberReportApi(token, from, to), getClassRegReportApi(token, from, to)]);
    const bad = rs.find((r) => r.status === "rejected");
    if (bad) setError(bad.reason.message);
    setData({ revenue: rs[0].value ?? null, members: list(rs[1].value), classes: list(rs[2].value) });
    setLoading(false);
  }
  useEffect(() => { run(); }, []);

  return (
    <div className="layout">
      <Sidebar role={role} activePage="reports" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main">
        <h1>Báo cáo</h1>
        <p className="subtitle">Doanh thu và giao dịch theo khoảng thời gian.</p>
        <div className="queue-toolbar">
          <div className="field queue-filter"><label htmlFor="rpFrom">Từ ngày</label><input id="rpFrom" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></div>
          <div className="field queue-filter"><label htmlFor="rpTo">Đến ngày</label><input id="rpTo" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></div>
          <button type="button" className="queue-refresh" onClick={run} disabled={loading}>{loading ? "Đang tải…" : "Xem báo cáo"}</button>
        </div>
        {error && <div className="banner error" role="alert">{error}</div>}
        {data && (<>
          <h2 style={{ fontSize: 18 }}>Doanh thu</h2>
          <Revenue data={data.revenue} />
          <h2 style={{ fontSize: 18, margin: "24px 0 12px" }}>Theo thành viên</h2>
          <Table rows={data.members} nameLabel="Thành viên" />
          <h2 style={{ fontSize: 18, margin: "24px 0 12px" }}>Theo lớp học</h2>
          <Table rows={data.classes} nameLabel="Lớp học" />
        </>)}
      </main>
    </div>
  );
}