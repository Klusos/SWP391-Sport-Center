import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import { getAssignedPtBookingsApi } from "../api";
import "./CoachPt.css";

const DAY_LONG = ["Chủ nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"];
const PILL = ["CHỦ NHẬT", "THỨ 2", "THỨ 3", "THỨ 4", "THỨ 5", "THỨ 6", "THỨ 7"];
const STATUS = {
  CONFIRMED: ["Đã xác nhận", "ok"], PENDING_PAYMENT: ["Chờ thanh toán", "warn"], COMPLETED: ["Đã hoàn thành", "ok"],
  EXPIRED: ["Đã hết hạn", "gray"], CANCELLED: ["Đã hủy", "gray"],
};
const TONES = ["blue", "purple", "green", "red", "orange"];
const TABS = [["upcoming", "Sắp tới"], ["done", "Đã hoàn thành"], ["all", "Tất cả"]];

const ICON = {
  cal: "M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
  bolt: "M13 2 3 14h9l-1 8 10-12h-9l1-8z",
  hourglass: "M5 22h14 M5 2h14 M17 22v-4.172a2 2 0 0 0-.586-1.414L12 12l-4.414 4.414A2 2 0 0 0 7 17.828V22 M7 2v4.172a2 2 0 0 0 .586 1.414L12 12l4.414-4.414A2 2 0 0 0 17 6.172V2",
  refresh: "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16 M8 16H3v5",
  left: "M15 18l-6-6 6-6", right: "M9 18l6-6-6-6",
  clock: "M12 6v6l4 2 M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20",
  alert: "M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z M12 9v4 M12 17h.01",
  tag: "M12 8a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M4 21a8 8 0 0 1 16 0",
};
const Ic = ({ d, size = 18 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {d.split(" M").map((p, i) => <path key={i} d={(i ? "M" : "") + p} />)}
  </svg>
);

const dayKey = (v) => new Date(v).toLocaleDateString("sv-SE");
const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
const mondayOf = (d) => { const x = new Date(d); x.setHours(0, 0, 0, 0); x.setDate(x.getDate() - ((x.getDay() + 6) % 7)); return x; };
const isoWeek = (d) => {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  t.setUTCDate(t.getUTCDate() + 4 - (t.getUTCDay() || 7));
  return Math.ceil(((t - new Date(Date.UTC(t.getUTCFullYear(), 0, 1))) / 86400000 + 1) / 7);
};
const hm = (v) => (v ? new Date(v).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) : "");
const dmy = (v) => new Date(v).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
const money = (v) => Number(v || 0).toLocaleString("vi-VN") + "đ";
const initials = (name) => (name || "?").trim().split(/\s+/).slice(-2).map((w) => w[0]).join("").toUpperCase();
const tone = (name) => TONES[[...String(name || "")].reduce((a, c) => a + c.charCodeAt(0), 0) % TONES.length];

function phase(b, now) {
  const end = new Date(b.endTime || b.startTime);
  const dead = /CANCEL|EXPIRED/i.test(b.status || "");
  if (dead) return "dead";
  if (end >= now) return "upcoming";
  return /CONFIRMED|COMPLETED/i.test(b.status || "") ? "done" : "past";
}

export default function CoachPtSessions({ token, role, onLogout, onNavigate }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [tab, setTab] = useState("upcoming");
  const [offset, setOffset] = useState(0);
  const [pickedDay, setPickedDay] = useState(null);
  const [detail, setDetail] = useState(null);

  const load = useCallback(() => {
    setLoading(true); setError("");
    getAssignedPtBookingsApi(token)
      .then((d) => setRows(Array.isArray(d) ? d : []))
      .catch((e) => { setRows([]); setError(e.status === 403 ? "Tài khoản không có quyền xem buổi PT." : e.message || "Không tải được buổi PT."); })
      .finally(() => setLoading(false));
  }, [token]);
  useEffect(() => { load(); }, [load]);

  const now = new Date();
  const todayKey = dayKey(now);
  const items = useMemo(() => rows.map((b) => ({ ...b, _phase: phase(b, new Date()) })), [rows]);
  const inTab = (b) => tab === "all" || (tab === "upcoming" ? b._phase === "upcoming" : b._phase === "done");

  const weekStart = addDays(mondayOf(now), offset * 7);
  const days = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i));
  const weekKeys = new Set(days.map(dayKey));
  const forDay = (d) => items.filter((b) => b.startTime && dayKey(b.startTime) === dayKey(d) && inTab(b));

  const shown = items
    .filter((b) => b.startTime && weekKeys.has(dayKey(b.startTime)) && inTab(b) && (!pickedDay || dayKey(b.startTime) === pickedDay))
    .sort((a, b) => new Date(a.startTime) - new Date(b.startTime));
  const groups = [];
  shown.forEach((b) => {
    const k = dayKey(b.startTime);
    const g = groups.find((x) => x.key === k);
    g ? g.list.push(b) : groups.push({ key: k, date: new Date(b.startTime), list: [b] });
  });

  const upcoming = items.filter((b) => b._phase === "upcoming");
  const in7 = upcoming.filter((b) => new Date(b.startTime) - now < 7 * 86400000);
  const today = items.filter((b) => b.startTime && dayKey(b.startTime) === todayKey && b._phase !== "dead");
  const nextToday = today.filter((b) => new Date(b.startTime) > now).sort((a, b) => new Date(a.startTime) - new Date(b.startTime))[0];
  const unpaid = upcoming.filter((b) => b.status === "PENDING_PAYMENT");

  const heading = (g) => {
    const k = g.key;
    const full = DAY_LONG[g.date.getDay()] + ", " + dmy(g.date);
    if (k === todayKey) return "Hôm nay - " + full;
    if (k === dayKey(addDays(now, 1))) return "Ngày mai - " + full;
    return full;
  };
  const st = (b) => (b._phase === "done" && b.status === "CONFIRMED" ? ["Đã hoàn thành", "ok"] : STATUS[b.status] || [b.status || "—", "warn"]);

  return (
    <div className="layout">
      <Sidebar role={role} activePage="coach-pt-sessions" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main cp-main">
        <header className="cp-topbar">
          <nav className="cp-crumb" aria-label="Breadcrumb"><span>Sports Center</span><i>›</i><span>Huấn luyện viên</span><i>›</i><b>Buổi huấn luyện cá nhân</b></nav>
        </header>

        <div className="cp-content">
          <div className="cp-head">
            <div><h1>Buổi huấn luyện cá nhân</h1><p>Các buổi huấn luyện 1-1 được phân bổ cho bạn.</p></div>
            <div className="cp-tools">
              <div className="cp-seg" role="tablist" aria-label="Bộ lọc">
                {TABS.map(([k, label]) => (
                  <button key={k} type="button" role="tab" aria-selected={tab === k} className={tab === k ? "on" : ""} onClick={() => { setTab(k); setPickedDay(null); }}>{label}</button>
                ))}
              </div>
              <button type="button" className="cp-icon" onClick={load} disabled={loading} aria-label="Làm mới"><Ic d={ICON.refresh} /></button>
            </div>
          </div>

          {error && <p className="banner error" role="alert">{error}</p>}

          <div className="cp-kpis">
            <article><header><span>Buổi tập sắp tới</span><i className="blue"><Ic d={ICON.cal} size={22} /></i></header>
              <div><strong>{upcoming.length}</strong><em className="blue">Trong 7 ngày: {in7.length}</em></div></article>
            <article><header><span>Lịch hôm nay</span><i className="green"><Ic d={ICON.bolt} size={22} /></i></header>
              <div><strong>{today.length}</strong><em className="green">{nextToday ? hm(nextToday.startTime) + " bắt đầu" : today.length ? "Hết buổi hôm nay" : "Không có buổi"}</em></div></article>
            <article><header><span>Chờ học viên thanh toán</span><i className="orange"><Ic d={ICON.hourglass} size={22} /></i></header>
              <div><strong>{unpaid.length}</strong><em className="orange">Giữ chỗ tạm thời</em></div></article>
          </div>

          <section className="cp-week">
            <div className="cp-week-head">
              <h2><Ic d={ICON.cal} size={20} /> Tuần {String(isoWeek(weekStart)).padStart(2, "0")} ({dmy(weekStart)} - {dmy(days[6])}) {offset === 0 && <span className="cp-now">Hiện tại</span>}</h2>
              <div className="cp-nav">
                <button type="button" className="cp-today" onClick={() => { setOffset(0); setPickedDay(null); }}>Tuần này</button>
                <div><button type="button" aria-label="Tuần trước" onClick={() => { setOffset(offset - 1); setPickedDay(null); }}><Ic d={ICON.left} /></button>
                  <button type="button" aria-label="Tuần sau" onClick={() => { setOffset(offset + 1); setPickedDay(null); }}><Ic d={ICON.right} /></button></div>
              </div>
            </div>
            <div className="cp-days">
              {days.map((d) => {
                const list = forDay(d);
                const k = dayKey(d);
                const cls = ["cp-day", k === todayKey ? "today" : "", pickedDay === k ? "picked" : "", list.length === 0 ? "empty" : ""].join(" ");
                return (
                  <button key={k} type="button" className={cls} onClick={() => setPickedDay(pickedDay === k ? null : k)} aria-pressed={pickedDay === k}>
                    <small>{PILL[d.getDay()]}</small><b>{d.getDate()}</b>
                    <span>{k === todayKey ? "Hôm nay" : list.length ? list.length + " buổi" : "Trống"}</span>
                    <div className="cp-dots">{list.slice(0, 3).map((x) => <i key={x.id} className={x.status === "PENDING_PAYMENT" ? "warn" : ""} />)}</div>
                  </button>
                );
              })}
            </div>
          </section>

          {loading ? <div className="queue-state" role="status">Đang tải buổi PT…</div> : groups.length === 0 ? (
            <div className="cp-empty"><i><Ic d={ICON.cal} size={30} /></i>
              <h3>{rows.length === 0 ? "Bạn chưa được giao buổi PT nào" : "Không có buổi tập nào trong khoảng này"}</h3>
              <p>{rows.length === 0 ? "Khi học viên đặt lịch với bạn, các buổi tập sẽ xuất hiện tại đây." : "Thử đổi bộ lọc, chọn tuần khác hoặc quay về tuần hiện tại."}</p>
              {(offset !== 0 || pickedDay) && <button type="button" className="cp-today" onClick={() => { setOffset(0); setPickedDay(null); }}>Về tuần này</button>}
            </div>
          ) : groups.map((g) => (
            <section key={g.key} className="cp-group">
              <h2 className={g.key === todayKey ? "now" : ""}>{g.key === todayKey && <i />}{heading(g)} <span>{g.list.length} buổi tập</span><small>Giờ địa phương</small></h2>
              {g.list.map((b) => {
                const [label, kind] = st(b);
                const t = tone(b.sportName);
                return (
                  <article key={b.id} className={"cp-card " + (b.status === "PENDING_PAYMENT" ? "warn" : kind === "ok" ? "ok" : "gray")}>
                    <div className="cp-card-row">
                      <div className="cp-time"><b><Ic d={ICON.clock} size={18} /> {hm(b.startTime)} - {hm(b.endTime)}</b><small>Thời lượng: {b.durationMinutes || 90} phút</small></div>
                      <div className="cp-who">
                        <span className={"cp-avatar " + t}>{initials(b.memberName)}</span>
                        <div><h3>{b.memberName || "Học viên"} {b.memberId != null && <em>#HV{b.memberId}</em>}</h3>
                          <span className={"cp-sport " + t}><Ic d={ICON.tag} size={13} /> {b.sportName || "Bộ môn"}</span></div>
                      </div>
                      <span className={"cp-chip " + kind}>{label}</span>
                      <button type="button" className="cp-detail" onClick={() => setDetail(b)}>Chi tiết</button>
                    </div>
                    {b.status === "PENDING_PAYMENT" && (
                      <p className="cp-note"><Ic d={ICON.alert} size={16} /> <b>Lưu ý:</b> Học viên chưa thanh toán, chỗ đang được giữ tạm thời{b.paymentExpiresAt ? " (tự động giải phóng lúc " + hm(b.paymentExpiresAt) + " " + dmy(b.paymentExpiresAt) + " nếu chưa hoàn tất phí)" : ""}.</p>
                    )}
                  </article>
                );
              })}
            </section>
          ))}
        </div>
      </main>

      {detail && (
        <div className="cp-overlay" role="dialog" aria-modal="true" aria-labelledby="cp-dt">
          <div className="cp-modal">
            <h2 id="cp-dt">Chi tiết buổi PT #{detail.id}</h2>
            <p><span className={"cp-chip " + st(detail)[1]}>{st(detail)[0]}</span></p>
            <dl>
              <div><dt>Học viên</dt><dd>{detail.memberName || "—"}{detail.memberId != null ? " (#HV" + detail.memberId + ")" : ""}</dd></div>
              <div><dt>Bộ môn</dt><dd>{detail.sportName || "—"}</dd></div>
              <div><dt>Ngày</dt><dd>{detail.startTime ? DAY_LONG[new Date(detail.startTime).getDay()] + ", " + dmy(detail.startTime) : "—"}</dd></div>
              <div><dt>Giờ</dt><dd>{hm(detail.startTime)} - {hm(detail.endTime)} ({detail.durationMinutes || 90} phút)</dd></div>
              {detail.originalPrice != null && <div><dt>Giá gốc</dt><dd>{money(detail.originalPrice)}</dd></div>}
              {detail.discount != null && Number(detail.discount) > 0 && <div><dt>Giảm giá</dt><dd>-{money(detail.discount)}</dd></div>}
              {detail.finalPrice != null && <div><dt>Học viên thanh toán</dt><dd>{money(detail.finalPrice)}</dd></div>}
              {detail.paymentExpiresAt && detail.status === "PENDING_PAYMENT" && <div><dt>Giữ chỗ đến</dt><dd>{hm(detail.paymentExpiresAt)} {dmy(detail.paymentExpiresAt)}</dd></div>}
            </dl>
            <div className="cp-actions"><button type="button" onClick={() => setDetail(null)}>Đóng</button></div>
          </div>
        </div>
      )}
    </div>
  );
}