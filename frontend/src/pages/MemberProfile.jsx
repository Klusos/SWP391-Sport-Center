import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import { getCurrentUserApi, getMyMemberProfileApi, saveMyMemberProfileApi } from "../api";

const GENDER = { M: "Nam", F: "Nữ" };

export default function MemberProfile({ token, role, onLogout, onNavigate }) {
  const [me, setMe] = useState(null);
  const [exists, setExists] = useState(false);
  const [form, setForm] = useState({ height: "", weight: "", goal: "" });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState(null);

  useEffect(() => {
    let off = false;
    (async () => {
      try {
        const user = await getCurrentUserApi(token);
        if (!off) setMe(user);
        try {
          const p = await getMyMemberProfileApi(token);
          if (!off) {
            setExists(true);
            setForm({ height: p.height ?? "", weight: p.weight ?? "", goal: p.goal ?? "" });
          }
        } catch (e) {
          if (e.status !== 404 && !off) setMsg({ type: "error", text: e.message });
        }
      } catch (e) {
        if (!off) setMsg({ type: "error", text: e.message });
      } finally { if (!off) setLoading(false); }
    })();
    return () => { off = true; };
  }, [token]);

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function save(e) {
    e.preventDefault();
    setMsg(null);
    const height = Number(form.height), weight = Number(form.weight);
    if (!(height >= 50 && height <= 250)) return setMsg({ type: "error", text: "Chiều cao phải từ 50 đến 250 cm." });
    if (!(weight >= 10 && weight <= 500)) return setMsg({ type: "error", text: "Cân nặng phải từ 10 đến 500 kg." });
    setSaving(true);
    try {
      await saveMyMemberProfileApi(token, { height, weight, goal: form.goal }, exists);
      setExists(true);
      setMsg({ type: "success", text: "Đã lưu hồ sơ." });
    } catch (err) {
      setMsg({ type: "error", text: err.status === 409 ? "Hồ sơ đã tồn tại. Hãy tải lại trang." : err.message });
    } finally { setSaving(false); }
  }

  const account = me ? [
    ["Họ và tên", me.name], ["Email", me.email], ["Số điện thoại", me.phone],
    ["Ngày sinh", me.dateOfBirth], ["Giới tính", GENDER[me.gender] || me.gender],
  ].filter(([, v]) => v) : [];

  return (
    <div className="layout">
      <Sidebar role={role} activePage="member-profile" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main">
        <h1>Hồ sơ cá nhân</h1>
        <p className="subtitle">Thông tin tài khoản và chỉ số cơ thể của bạn.</p>
        {msg && <div className={"banner " + msg.type} role="status">{msg.text}</div>}
        {loading ? <div className="queue-state">Đang tải hồ sơ…</div> : (<>
          {account.length > 0 && (
            <section className="dash-card narrow">
              <h2>Tài khoản</h2>
              <dl className="member-detail-grid">
                {account.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
              </dl>
            </section>
          )}
          <form className="form-card" onSubmit={save} style={{ maxWidth: 640 }}>
            {!exists && <p className="subtitle">Bạn chưa có hồ sơ thể chất. Điền thông tin bên dưới để tạo.</p>}
            <div className="grid-2">
              <div className="field"><label htmlFor="pf-height">Chiều cao (cm)</label>
                <input id="pf-height" type="number" step="0.01" min="50" max="250" value={form.height} onChange={set("height")} required /></div>
              <div className="field"><label htmlFor="pf-weight">Cân nặng (kg)</label>
                <input id="pf-weight" type="number" step="0.01" min="10" max="500" value={form.weight} onChange={set("weight")} required /></div>
            </div>
            <div className="field" style={{ marginTop: 16 }}><label htmlFor="pf-goal">Mục tiêu tập luyện</label>
              <textarea id="pf-goal" maxLength={1000} value={form.goal} onChange={set("goal")} style={{ width: "100%" }} /></div>
            <div className="form-actions">
              <button className="btn-primary" type="submit" disabled={saving}>{saving ? "Đang lưu…" : exists ? "Lưu thay đổi" : "Tạo hồ sơ"}</button>
            </div>
          </form>
        </>)}
      </main>
    </div>
  );
}
