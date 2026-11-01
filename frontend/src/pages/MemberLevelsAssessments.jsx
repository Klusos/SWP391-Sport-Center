import { useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./MemberLevels.css";
import {
  getMyConsultationRequestsApi, getMyMemberSportLevelsApi, getMySkillAssessmentsApi, getPublicSportsApi,
  getCoachesApi, getCoachSportsApi, getCurrentUserApi, createMemberConsultationRequestApi, resolveImageUrl,
} from "../api";

const LEVELS = ["BEGINNER", "INTERMEDIATE", "ADVANCED"];
const LEVEL_VN = { BEGINNER: "Khởi đầu", INTERMEDIATE: "Trung cấp", ADVANCED: "Nâng cao" };
const ASSESSMENT = { PENDING: ["Đang chờ Coach", "warn"], COMPLETED: ["Đã hoàn thành", "ok"], CANCELLED: ["Đã hủy", "gray"] };
const CONSULT = { PENDING: ["Chờ tiếp nhận", "warn"], IN_PROGRESS: ["Đang tư vấn", "blue"], COMPLETED: ["Hoàn tất", "ok"], CANCELLED: ["Đã hủy", "gray"] };

const ICON = {
  user: "M12 8a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M4 21a8 8 0 0 1 16 0",
  cal: "M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
  check: "M20 6 9 17l-5-5",
  medal: "M12 15a6 6 0 1 0 0-12 6 6 0 0 0 0 12 M8.2 13.9 7 22l5-3 5 3-1.2-8.1",
  up: "M12 19V5 M5 12l7-7 7 7",
  file: "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M8 13h8 M8 17h8",
  eye: "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6",
  help: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3 M12 17h.01",
};
const Ic = ({ d, size = 18 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {d.split(" M").map((p, i) => <path key={i} d={(i ? "M" : "") + p} />)}
  </svg>
);

const fmt = (v) => { if (!v) return "—"; const d = new Date(v); return Number.isNaN(d.getTime()) ? v : d.toLocaleDateString("vi-VN"); };
const fmtFull = (v) => { if (!v) return "—"; const d = new Date(v); return Number.isNaN(d.getTime()) ? v : d.toLocaleString("vi-VN"); };
const quarter = (v) => { const d = new Date(v); return Number.isNaN(d.getTime()) ? null : { q: Math.ceil((d.getMonth() + 1) / 3), y: d.getFullYear() }; };
const errText = (e, fallback) => (e?.status === 401 ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại." : e?.status === 403 ? "Tài khoản không có quyền xem dữ liệu này." : e?.message || fallback);
const chip = ([label, tone]) => <span className={"lv-chip " + tone}>{label}</span>;

export default function MemberLevelsAssessments({ token, role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]);
  const [levels, setLevels] = useState([]);
  const [assessments, setAssessments] = useState([]);
  const [consultations, setConsultations] = useState([]);
  const [coaches, setCoaches] = useState([]);
  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errors, setErrors] = useState({});
  const [pickedSportId, setPickedSportId] = useState("");
  const [year, setYear] = useState("");
  const [detail, setDetail] = useState(null);
  const [cert, setCert] = useState("");
  const [msg, setMsg] = useState(null);
  const [ask, setAsk] = useState(null);
  const [askErr, setAskErr] = useState("");
  const [sending, setSending] = useState(false);

  useEffect(() => {
    let active = true;
    Promise.allSettled([
      getPublicSportsApi(), getMyMemberSportLevelsApi(token), getMySkillAssessmentsApi(token),
      getMyConsultationRequestsApi(token), getCoachesApi(token), getCurrentUserApi(token),
    ]).then((rs) => {
      if (!active) return;
      const arr = (r) => (r.status === "fulfilled" && Array.isArray(r.value) ? r.value : []);
      const msgs = ["Không tải được danh sách bộ môn.", "Không tải được trình độ của bạn.", "Không tải được lịch sử đánh giá.", "Không tải được lịch sử yêu cầu tư vấn."];
      const keys = ["sports", "levels", "assessments", "consultations"];
      const next = {};
      keys.forEach((k, i) => { if (rs[i].status === "rejected") next[k] = errText(rs[i].reason, msgs[i]); });
      setSports(arr(rs[0])); setLevels(arr(rs[1])); setAssessments(arr(rs[2])); setConsultations(arr(rs[3])); setCoaches(arr(rs[4]));
      if (rs[5].status === "fulfilled") setMe(rs[5].value);
      setErrors(next); setLoading(false);
    });
    return () => { active = false; };
  }, [token]);

  const levelsBySport = useMemo(() => new Map(levels.map((l) => [Number(l.sportId), l])), [levels]);
  const selectedId = pickedSportId || (sports[0] ? String(sports[0].id) : "");
  const sport = sports.find((s) => String(s.id) === selectedId);
  const level = levelsBySport.get(Number(selectedId));
  const verified = level?.verifiedByCoachId != null && Boolean(level?.verifiedAt);
  const key = verified ? String(level.level).toUpperCase() : "BEGINNER";
  const idx = Math.max(0, LEVELS.indexOf(key));
  const next = LEVELS[idx + 1];

  const sportAssessments = assessments.filter((a) => String(a.sportId) === selectedId)
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  const years = [...new Set(sportAssessments.map((a) => quarter(a.createdAt)?.y).filter(Boolean))];
  const shown = year ? sportAssessments.filter((a) => String(quarter(a.createdAt)?.y) === year) : sportAssessments;
  const latestDone = sportAssessments.find((a) => a.status === "COMPLETED");

  const coachOf = (id) => coaches.find((c) => Number(c.id) === Number(id));
  const coachName = (id) => (id == null ? "" : coachOf(id)?.userName || coachOf(id)?.name || "Coach #" + id);
  const verifiedCoach = coachOf(level?.verifiedByCoachId);

  useEffect(() => {
    setCert("");
    if (!verified || !selectedId) return;
    let on = true;
    getCoachSportsApi(token, level.verifiedByCoachId)
      .then((d) => { const m = (Array.isArray(d) ? d : []).find((x) => String(x.sportId) === selectedId); if (on) setCert(m?.certification || ""); })
      .catch(() => {});
    return () => { on = false; };
  }, [token, verified, selectedId, level?.verifiedByCoachId]);

  const openAsk = () => { setAskErr(""); setAsk({ phone: me?.phone || "", note: "Đăng ký đánh giá trình độ môn " + (sport?.name || "") }); };
  async function sendAsk(e) {
    e.preventDefault();
    if (!ask.phone.trim()) return setAskErr("Vui lòng nhập số điện thoại để lễ tân liên hệ.");
    setSending(true); setAskErr("");
    try {
      await createMemberConsultationRequestApi(token, {
        name: me?.name || "Thành viên", phone: ask.phone.trim(), email: me?.email || undefined,
        sportId: Number(selectedId), note: ask.note.trim() || undefined,
      });
      setAsk(null);
      setMsg({ type: "success", text: "Đã gửi yêu cầu. Lễ tân sẽ liên hệ để sắp xếp buổi đánh giá." });
      getMyConsultationRequestsApi(token).then((d) => setConsultations(Array.isArray(d) ? d : [])).catch(() => {});
    } catch (err) { setAskErr(errText(err, "Không gửi được yêu cầu.")); } finally { setSending(false); }
  }

  const avatar = resolveImageUrl(verifiedCoach?.imageUrl);

  return (
    <div className="layout">
      <Sidebar role={role} activePage="member-levels" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main lv-main">
        <header className="lv-topbar">
          <nav className="lv-crumb" aria-label="Breadcrumb">
            <button type="button" onClick={() => onNavigate("dashboard")}>Trang chủ</button><span>›</span><b>Trình độ &amp; đánh giá</b>
          </nav>
          <button type="button" className="lv-cta" onClick={openAsk} disabled={!sport}><Ic d={ICON.cal} /> Đặt lịch kiểm tra trình độ</button>
        </header>

        <div className="lv-content">
          <div className="lv-title">
            <div>
              <p className="lv-eyebrow"><span>Hệ thống thẩm định kỹ năng</span> • Cập nhật {new Date().getFullYear()}</p>
              <h1>Trình độ &amp; lịch sử đánh giá</h1>
              <p className="lv-sub">Kết quả đánh giá và lộ trình nâng hạng</p>
            </div>
            {sport && <div className="lv-status"><i className={verified ? "ok" : ""} /> Trạng thái: <b>{verified ? "Đã xác minh bởi Coach" : "Chưa xác minh"}</b></div>}
          </div>

          {msg && <div className={"banner " + msg.type} role="status">{msg.text}</div>}
          {loading && <div className="queue-state" role="status">Đang tải thông tin của bạn…</div>}
          {errors.sports && <p className="banner error" role="alert">{errors.sports}</p>}
          {errors.levels && <p className="banner error" role="alert">{errors.levels}</p>}
          {!loading && !errors.sports && sports.length === 0 && <p className="queue-state">Hiện chưa có bộ môn đang hoạt động.</p>}

          {!loading && sport && (<>
            <div className="lv-picker">
              <span className="lv-picker-icon"><Ic d={ICON.user} size={22} /></span>
              <select aria-label="Chọn bộ môn" value={selectedId} onChange={(e) => { setPickedSportId(e.target.value); setYear(""); }}>
                {sports.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
              <span className="lv-chip ok">{key[0] + key.slice(1).toLowerCase()} (Mức {idx + 1}/3)</span>
            </div>

            <section className="lv-hero">
              <div className="lv-hero-left">
                <div className="lv-hero-tags">
                  <span className="lv-glass">Trình độ hiện tại • {sport.name}</span>
                  {verified && <span className="lv-verified"><Ic d={ICON.check} size={14} /> Đã xác minh</span>}
                </div>
                <div className="lv-level"><i><Ic d={ICON.medal} size={26} /></i><h2>{key} (MỨC {idx + 1}/3)</h2></div>
                <div className="lv-steps" aria-label="Lộ trình trình độ">
                  {LEVELS.map((l, i) => (
                    <span key={l} className={i < idx ? "done" : i === idx ? "now" : ""}>
                      {i < idx ? <Ic d={ICON.check} size={14} /> : <b>{i + 1}</b>} {LEVEL_VN[l]}
                    </span>
                  ))}
                </div>
                <div className="lv-bar"><i style={{ width: ((idx + 1) / 3) * 100 + "%" }} /></div>
                {!verified && <p className="lv-hint">Bạn đang ở mức khởi đầu mặc định. Đặt buổi đánh giá để Coach xác nhận trình độ thực tế của bạn.</p>}
              </div>

              <aside className="lv-glasscard">
                {verified ? (<>
                  <span className="lv-coachtag"><Ic d={ICON.check} size={13} /> Coach xác minh</span>
                  <p className="lv-date">Xác minh: {fmt(level.verifiedAt)}</p>
                  <p className="lv-lvl">Cấp bậc: {key[0] + key.slice(1).toLowerCase()}</p>
                  <div className="lv-coach">
                    {avatar ? <img src={avatar} alt="" /> : <span className="lv-avatar">{(coachName(level.verifiedByCoachId) || "C")[0]}</span>}
                    <div><b>{coachName(level.verifiedByCoachId)}</b>{cert && <small>{cert}</small>}</div>
                  </div>
                </>) : (<>
                  <span className="lv-coachtag off">Chưa xác minh</span>
                  <p className="lv-lvl">Chưa có Coach xác nhận trình độ cho bộ môn này.</p>
                </>)}
                <button type="button" className="lv-white" disabled={!latestDone} onClick={() => setDetail(latestDone)}>
                  <Ic d={ICON.file} size={16} /> {latestDone ? "Xem phiếu đánh giá" : "Chưa có phiếu đánh giá"}
                </button>
              </aside>
            </section>

            <div className="lv-cols">
              <section className="lv-card">
                <div className="lv-card-head">
                  <div><h2>Lịch sử đánh giá kỹ năng</h2><p>Hồ sơ thẩm định qua các đợt kiểm tra tại trung tâm.</p></div>
                  {years.length > 0 && (
                    <select aria-label="Lọc theo năm" value={year} onChange={(e) => setYear(e.target.value)}>
                      <option value="">Tất cả các năm</option>
                      {years.map((y) => <option key={y} value={y}>Năm {y}</option>)}
                    </select>
                  )}
                </div>
                {errors.assessments && <p className="banner error" role="alert">{errors.assessments}</p>}
                {!errors.assessments && shown.length === 0 && (
                  <div className="lv-empty"><Ic d={ICON.file} size={28} /><p>Bạn chưa có đánh giá nào cho bộ môn này.</p>
                    <button type="button" className="lv-cta" onClick={openAsk}>Đặt lịch kiểm tra trình độ</button></div>
                )}
                <div className="lv-list">
                  {shown.map((a) => {
                    const q = quarter(a.assessedAt || a.createdAt);
                    return (
                      <article key={a.id} className={"lv-item " + (a.status || "").toLowerCase()}>
                        <span className="lv-q">{q ? "Q" + q.q : "#"}</span>
                        <div className="lv-item-main">
                          <h3>{q ? `Đánh giá Q${q.q}/${q.y}` : "Đánh giá #" + a.id} {chip(ASSESSMENT[a.status] || [a.status, "gray"])}</h3>
                          <p>
                            <span><Ic d={ICON.cal} size={13} /> {fmt(a.assessedAt || a.createdAt)}</span>
                            {a.assignedCoachId != null && <span><Ic d={ICON.user} size={13} /> {coachName(a.assignedCoachId)}</span>}
                            {a.status === "COMPLETED" && a.assessedLevel && <b>Cấp bậc: {a.assessedLevel}</b>}
                          </p>
                        </div>
                        <button type="button" className="lv-small" onClick={() => setDetail(a)}><Ic d={ICON.eye} size={14} /> Xem chi tiết</button>
                      </article>
                    );
                  })}
                </div>
              </section>

              <div className="lv-side">
                <section className="lv-upgrade">
                  <i><Ic d={ICON.up} size={22} /></i>
                  <h3>Đăng ký nâng hạng</h3>
                  {next ? (<>
                    <p>Sẵn sàng lên cấp {LEVEL_VN[next]}? Gửi yêu cầu để lễ tân sắp xếp buổi đánh giá với Coach.</p>
                    <button type="button" className="lv-cta wide" onClick={openAsk}><Ic d={ICON.cal} size={16} /> Đăng ký ngay</button>
                  </>) : <p>Bạn đang ở cấp cao nhất của bộ môn này. Chúc mừng!</p>}
                </section>
                <section className="lv-help">
                  <div><i><Ic d={ICON.help} size={20} /></i><span><b>Yêu cầu tư vấn</b><small>Theo dõi các yêu cầu bạn đã gửi</small></span></div>
                  <a href="#lv-consult">Xem lịch sử tư vấn →</a>
                </section>
              </div>
            </div>
          </>)}

          <section className="lv-card" id="lv-consult">
            <div className="lv-card-head"><div><h2>Lịch sử yêu cầu tư vấn</h2><p>Các yêu cầu bạn đã gửi tới trung tâm.</p></div></div>
            {errors.consultations && <p className="banner error" role="alert">{errors.consultations}</p>}
            {!loading && !errors.consultations && consultations.length === 0 && <p className="lv-none">Bạn chưa gửi yêu cầu tư vấn nào.</p>}
            <div className="lv-list">
              {consultations.map((c) => (
                <article key={c.id} className="lv-item">
                  <span className="lv-q alt">#{c.id}</span>
                  <div className="lv-item-main">
                    <h3>{c.sportName || "Bộ môn #" + c.sportId} {chip(CONSULT[c.status] || [c.status, "gray"])}</h3>
                    <p><span><Ic d={ICON.cal} size={13} /> {fmt(c.createdAt)}</span>{c.completedAt && <span>Hoàn tất: {fmt(c.completedAt)}</span>}</p>
                    {(c.resolutionNote || c.note) && <p className="lv-note">{c.resolutionNote || c.note}</p>}
                  </div>
                </article>
              ))}
            </div>
          </section>
        </div>
      </main>

      {detail && (
        <div className="lv-overlay" role="dialog" aria-modal="true" aria-labelledby="lv-dt">
          <div className="lv-modal">
            <h2 id="lv-dt">Phiếu đánh giá #{detail.id}</h2>
            <p>{chip(ASSESSMENT[detail.status] || [detail.status, "gray"])}</p>
            <dl>
              <div><dt>Bộ môn</dt><dd>{sports.find((s) => Number(s.id) === Number(detail.sportId))?.name || "#" + detail.sportId}</dd></div>
              <div><dt>Ngày giao</dt><dd>{fmtFull(detail.createdAt)}</dd></div>
              {detail.assignedCoachId != null && <div><dt>Coach phụ trách</dt><dd>{coachName(detail.assignedCoachId)}</dd></div>}
              {detail.status === "COMPLETED" && <div><dt>Trình độ đánh giá</dt><dd>{detail.assessedLevel || "—"}</dd></div>}
              {detail.assessedAt && <div><dt>Ngày hoàn tất</dt><dd>{fmtFull(detail.assessedAt)}</dd></div>}
              {detail.cancelledAt && <div><dt>Ngày hủy</dt><dd>{fmtFull(detail.cancelledAt)}</dd></div>}
            </dl>
            {detail.status === "PENDING" && <p className="lv-hint dark">Đánh giá đang chờ Coach thực hiện.</p>}
            {detail.notes && <blockquote>{detail.notes}</blockquote>}
            <div className="lv-actions"><button type="button" onClick={() => setDetail(null)}>Đóng</button></div>
          </div>
        </div>
      )}

      {ask && (
        <div className="lv-overlay" role="dialog" aria-modal="true" aria-labelledby="lv-ask">
          <form className="lv-modal" onSubmit={sendAsk}>
            <h2 id="lv-ask">Đặt lịch kiểm tra trình độ</h2>
            <p className="lv-sub">Môn: <b>{sport?.name}</b>. Lễ tân sẽ liên hệ để sắp xếp buổi đánh giá với Coach.</p>
            <label htmlFor="lv-phone">Số điện thoại liên hệ</label>
            <input id="lv-phone" value={ask.phone} maxLength={20} onChange={(e) => setAsk({ ...ask, phone: e.target.value })} />
            <label htmlFor="lv-note">Ghi chú</label>
            <textarea id="lv-note" maxLength={500} value={ask.note} onChange={(e) => setAsk({ ...ask, note: e.target.value })} />
            {askErr && <div className="banner error" role="alert">{askErr}</div>}
            <div className="lv-actions">
              <button type="button" onClick={() => setAsk(null)}>Hủy</button>
              <button type="submit" className="lv-cta" disabled={sending}>{sending ? "Đang gửi…" : "Gửi yêu cầu"}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}