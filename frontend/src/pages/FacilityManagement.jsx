import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import { createFacilityApi, getFacilitiesApi, getSportsApi, updateFacilityApi } from "../api";

const EMPTY_FORM = { sportId: "", name: "", type: "", status: "ACTIVE" };

export default function FacilityManagement({ token, role, onLogout, onNavigate }) {
  const [facilities, setFacilities] = useState([]);
  const [sports, setSports] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selectedFacility = useMemo(
    () => facilities.find((facility) => facility.id === selectedId) || null,
    [facilities, selectedId],
  );

  const load = useCallback(async (preferredId) => {
    setLoading(true);
    setError("");
    try {
      const [facilityRows, sportRows] = await Promise.all([getFacilitiesApi(token), getSportsApi(token)]);
      const facilityList = Array.isArray(facilityRows) ? facilityRows : [];
      setFacilities(facilityList);
      setSports((Array.isArray(sportRows) ? sportRows : []).filter((sport) => sport.status === "ACTIVE"));
      setSelectedId((currentId) => {
        const desiredId = preferredId ?? currentId;
        return facilityList.some((facility) => facility.id === desiredId) ? desiredId : null;
      });
    } catch (requestError) {
      setError(requestError.status === 401 || requestError.status === 403
        ? "B\u1ea1n kh\u00f4ng c\u00f3 quy\u1ec1n xem danh s\u00e1ch c\u01a1 s\u1edf."
        : requestError.message || "Kh\u00f4ng t\u1ea3i \u0111\u01b0\u1ee3c d\u1eef li\u1ec7u c\u01a1 s\u1edf.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(null), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  function beginCreate() {
    setSelectedId(null);
    setForm({ ...EMPTY_FORM, sportId: sports[0] ? String(sports[0].id) : "" });
    setError("");
    setNotice("");
  }

  function selectFacility(facility) {
    setSelectedId(facility.id);
    setForm({ name: facility.name || "", type: facility.type || "", status: facility.status || "ACTIVE", sportId: "" });
    setError("");
    setNotice("");
  }

  async function saveFacility(event) {
    event.preventDefault();
    if (saving) return;
    const name = form.name.trim();
    const type = form.type.trim();
    if (!name || name.length > 100) {
      setError("T\u00ean c\u01a1 s\u1edf l\u00e0 b\u1eaft bu\u1ed9c v\u00e0 kh\u00f4ng \u0111\u01b0\u1ee3c v\u01b0\u1ee3t qu\u00e1 100 k\u00fd t\u1ef1.");
      return;
    }
    if (!type || type.length > 50) {
      setError("Lo\u1ea1i c\u01a1 s\u1edf l\u00e0 b\u1eaft bu\u1ed9c v\u00e0 kh\u00f4ng \u0111\u01b0\u1ee3c v\u01b0\u1ee3t qu\u00e1 50 k\u00fd t\u1ef1.");
      return;
    }
    if (!selectedFacility && !form.sportId) {
      setError("Vui l\u00f2ng ch\u1ecdn b\u1ed9 m\u00f4n \u0111ang ho\u1ea1t \u0111\u1ed9ng.");
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");
    const editing = Boolean(selectedFacility);
    try {
      const saved = editing
        ? await updateFacilityApi(token, selectedFacility.id, { name, type, status: form.status })
        : await createFacilityApi(token, { sportId: Number(form.sportId), name, type });
      setSelectedId(saved.id);
      setNotice(editing ? "\u0110\u00e3 c\u1eadp nh\u1eadt c\u01a1 s\u1edf." : "\u0110\u00e3 t\u1ea1o c\u01a1 s\u1edf #" + saved.id + ".");
      await load(saved.id);
    } catch (saveError) {
      setError(saveError.status === 401 || saveError.status === 403
        ? "B\u1ea1n kh\u00f4ng c\u00f3 quy\u1ec1n thay \u0111\u1ed5i c\u01a1 s\u1edf."
        : saveError.message || "Kh\u00f4ng l\u01b0u \u0111\u01b0\u1ee3c c\u01a1 s\u1edf.");
    } finally {
      setSaving(false);
    }
  }

  if (role !== "ADMIN") {
    return <div className="layout"><Sidebar role={role} activePage="facilities" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main"><p className="banner error" role="alert">{"Ch\u1ec9 ADMIN \u0111\u01b0\u1ee3c ph\u00e9p qu\u1ea3n l\u00fd c\u01a1 s\u1edf."}</p></main>
    </div>;
  }

  return <div className="layout">
    <Sidebar role={role} activePage="facilities" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main">
      <header className="page-header">
        <p className="eyebrow">{"QU\u1ea2N TR\u1eca N\u1ed8I DUNG"}</p>
        <h1>{"Qu\u1ea3n l\u00fd c\u01a1 s\u1edf v\u1eadt ch\u1ea5t"}</h1>
        <p className="subtitle">{"T\u1ea1o c\u01a1 s\u1edf, ch\u1ecdn b\u1ed9 m\u00f4n v\u00e0 c\u1eadp nh\u1eadt tr\u1ea1ng th\u00e1i cho ho\u1ea1t \u0111\u1ed9ng n\u1ed9i b\u1ed9."}</p>
      </header>

      <div className="sport-admin-toolbar">
        <p className="muted">{facilities.length} {"c\u01a1 s\u1edf"} · {sports.length} {"b\u1ed9 m\u00f4n \u0111ang ho\u1ea1t \u0111\u1ed9ng"}</p>
        <div className="facility-toolbar-actions">
          <button type="button" className="btn-secondary" onClick={() => void load(selectedId)} disabled={loading || saving}>{"T\u1ea3i l\u1ea1i"}</button>
          <button type="button" className="btn-primary" onClick={beginCreate} disabled={saving}>{"Th\u00eam c\u01a1 s\u1edf"}</button>
        </div>
      </div>

      {error && <p className="banner error" role="alert">{error}</p>}
      {notice && <p className="banner success" role="status">{notice}</p>}

      <div className="sport-admin-grid facility-admin-grid">
        <section className="sport-admin-list" aria-label={"Danh s\u00e1ch c\u01a1 s\u1edf"}>
          {loading ? <div className="queue-state" role="status">{"\u0110ang t\u1ea3i danh s\u00e1ch\u2026"}</div>
            : facilities.length === 0 ? <div className="queue-state">{"Ch\u01b0a c\u00f3 c\u01a1 s\u1edf. B\u1ea1n c\u00f3 th\u1ec3 t\u1ea1o c\u01a1 s\u1edf m\u1edbi."}</div>
              : <div className="sport-admin-table-wrap"><table className="sport-admin-table facility-admin-table">
                <thead><tr><th>ID</th><th>{"T\u00ean c\u01a1 s\u1edf"}</th><th>{"Lo\u1ea1i"}</th><th>{"B\u1ed9 m\u00f4n"}</th><th>{"Tr\u1ea1ng th\u00e1i"}</th><th /></tr></thead>
                <tbody>{facilities.map((facility) => <tr key={facility.id} className={selectedId === facility.id ? "selected" : ""}>
                  <td>{facility.id}</td><td>{facility.name}</td><td>{facility.type}</td><td>{facility.sportName}</td>
                  <td><span className={"status-pill status-" + String(facility.status || "").toLowerCase()}>{facility.status}</span></td>
                  <td><button type="button" className="btn-secondary" onClick={() => selectFacility(facility)} disabled={saving}>{"Ch\u1ec9nh s\u1eeda"}</button></td>
                </tr>)}</tbody>
              </table></div>}
        </section>

        <section className="sport-admin-editor" aria-labelledby="facility-editor-title">
          <h2 id="facility-editor-title">{selectedFacility ? "Ch\u1ec9nh s\u1eeda c\u01a1 s\u1edf #" + selectedFacility.id : "C\u01a1 s\u1edf m\u1edbi"}</h2>
          {selectedFacility && <p className="facility-associated-sport">{"B\u1ed9 m\u00f4n"}: <strong>{selectedFacility.sportName}</strong> (ID {selectedFacility.sportId})</p>}
          <form onSubmit={saveFacility}>
            {!selectedFacility && <>
              <label htmlFor="facility-sport">{"B\u1ed9 m\u00f4n"} <span aria-hidden="true">*</span></label>
              <select id="facility-sport" required value={form.sportId} onChange={(event) => setForm({ ...form, sportId: event.target.value })} disabled={saving || sports.length === 0}>
                <option value="">{"Ch\u1ecdn b\u1ed9 m\u00f4n \u0111ang ho\u1ea1t \u0111\u1ed9ng"}</option>
                {sports.map((sport) => <option key={sport.id} value={sport.id}>{sport.name}</option>)}
              </select>
              {sports.length === 0 && <p className="muted small">{"C\u1ea7n c\u00f3 \u00edt nh\u1ea5t m\u1ed9t b\u1ed9 m\u00f4n ACTIVE \u0111\u1ec3 t\u1ea1o c\u01a1 s\u1edf."}</p>}
            </>}
            <label htmlFor="facility-name">{"T\u00ean c\u01a1 s\u1edf"} <span aria-hidden="true">*</span></label>
            <input id="facility-name" value={form.name} maxLength={100} required onChange={(event) => setForm({ ...form, name: event.target.value })} disabled={saving} />
            <label htmlFor="facility-type">{"Lo\u1ea1i c\u01a1 s\u1edf"} <span aria-hidden="true">*</span></label>
            <input id="facility-type" value={form.type} maxLength={50} required onChange={(event) => setForm({ ...form, type: event.target.value })} disabled={saving} />
            {selectedFacility && <>
              <label htmlFor="facility-status">{"Tr\u1ea1ng th\u00e1i"}</label>
              <select id="facility-status" value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })} disabled={saving}>
                <option value="ACTIVE">ACTIVE</option><option value="INACTIVE">INACTIVE</option>
              </select>
              <p className="muted small">{"Kh\u00f4ng th\u1ec3 chuy\u1ec3n sang INACTIVE khi c\u00f2n bu\u1ed5i h\u1ecdc SCHEDULED \u0111ang di\u1ec5n ra ho\u1eb7c s\u1eafp t\u1edbi."}</p>
            </>}
            <div className="sport-admin-actions">
              <button type="submit" className="btn-primary" disabled={saving || (!selectedFacility && sports.length === 0)}>{saving ? "\u0110ang l\u01b0u\u2026" : selectedFacility ? "L\u01b0u thay \u0111\u1ed5i" : "T\u1ea1o c\u01a1 s\u1edf"}</button>
            </div>
          </form>
        </section>
      </div>
    </main>
  </div>;
}
