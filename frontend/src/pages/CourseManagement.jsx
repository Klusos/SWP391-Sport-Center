import { useCallback, useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import "./Admin.css";
import { getSportsApi, getCoachesApi, getFacilitiesApi, getCoursesBySportApi, saveCourseApi, getOfferingsApi, saveOfferingApi, getClassesApi, saveClassApi, getSessionsByClassApi, saveSessionApi, setSessionStatusApi } from "../api";
import { addMinutesToDateTimeLocal, getSessionActionAvailability, validateClassSessionSchedule, validateCourseOffering } from "../classSessionValidation";

const list = (d) => (Array.isArray(d) ? d : d?.content ?? []);
const dt = (s) => (s ? String(s).slice(0, 16) : "");
const toDt = (s) => (!s ? null : s.length === 16 ? s + ":00" : s);
const when = (s) => (s ? new Date(s).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" }) : "—");
const pill = (v) => <span className={"status-pill status-" + String(v || "").toLowerCase()}>{v}</span>;

/* Khối CRUD dùng chung: bảng + form thêm/sửa theo cấu hình fields */
function Crud({ title, items, fields, columns, onSave, onPick, picked, rowActions, disabled, hint, validate, onFieldChange, formHint }) {
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({});
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  function changeField(key, value) {
    setForm((current) => {
      const next = { ...current, [key]: value };
      return onFieldChange ? onFieldChange(key, value, next, editing) : next;
    });
  }

  function open(item) {
    const f = {};
    fields.forEach((fl) => {
      const v = item?.[fl.key];
      f[fl.key] = fl.type === "datetime-local" ? dt(v) : v ?? fl.default ?? "";
    });
    setForm(f); setEditing(item || "new"); setErr("");
  }

  async function submit(e) {
    e.preventDefault();
    const isNew = editing === "new";
    const body = {};
    fields.forEach((fl) => {
      if (isNew && fl.editOnly) return;
      let v = form[fl.key];
      if (fl.type === "number" || fl.num) v = v === "" || v == null ? null : Number(v);
      else if (fl.type === "datetime-local") v = toDt(v);
      body[fl.key] = v;
    });
    const validationMessage = validate?.(form, body, editing);
    if (validationMessage) { setErr(validationMessage); return; }
    setBusy(true); setErr("");
    try { await onSave(isNew ? null : editing.id, body); setEditing(null); }
    catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  return (
    <section className="adm-block">
      <div className="adm-head">
        <h2>{title}</h2>
        <button type="button" className="adm-primary" disabled={disabled} onClick={() => open(null)}>Thêm mới</button>
      </div>
      {disabled ? <p className="dash-empty">{hint}</p> : items.length === 0 ? <p className="dash-empty">Chưa có dữ liệu.</p> : (
        <div className="queue-table-wrap">
          <table className="queue-table">
            <thead><tr>{columns.map((c) => <th key={c.label}>{c.label}</th>)}<th /></tr></thead>
            <tbody>
              {items.map((it) => (
                <tr key={it.id} className={picked?.id === it.id ? "adm-sel" : ""}>
                  {columns.map((c) => <td key={c.label}>{c.render ? c.render(it) : it[c.key] ?? "—"}</td>)}
                  <td><div className="adm-actions">
                    {onPick && <button type="button" onClick={() => onPick(it)}>Chọn</button>}
                    <button type="button" onClick={() => open(it)}>Sửa</button>
                    {rowActions?.(it)}
                  </div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && (
        <form className="adm-form" onSubmit={submit} noValidate={Boolean(validate)}>
          <div className="grid-2">
            {fields.filter((fl) => !(editing === "new" && fl.editOnly)).map((fl) => (
              <div className="field" key={fl.key}>
                <label htmlFor={title + fl.key}>{fl.label}</label>
                {fl.type === "select" ? (
                  <select id={title + fl.key} value={form[fl.key]} onChange={(e) => changeField(fl.key, e.target.value)} required={fl.required}>
                    {!fl.required && <option value="">— Không chọn —</option>}
                    {fl.required && <option value="" disabled>Chọn…</option>}
                    {fl.options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                  </select>
                ) : fl.type === "textarea" ? (
                  <textarea id={title + fl.key} value={form[fl.key]} onChange={(e) => changeField(fl.key, e.target.value)} />
                ) : (
                  <input id={title + fl.key} type={fl.type || "text"} value={form[fl.key]} step={fl.type === "number" ? "any" : fl.type === "datetime-local" ? 60 : undefined} min={fl.min} max={fl.max}
                    onChange={(e) => changeField(fl.key, e.target.value)} required={fl.required} />
                )}
              </div>
            ))}
          </div>
          {formHint && <p className="subtitle">{formHint}</p>}
          {err && <div className="banner error" role="alert" style={{ marginTop: 14 }}>{err}</div>}
          <div className="form-actions">
            <button type="button" onClick={() => setEditing(null)}>Hủy</button>
            <button className="adm-primary" type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu"}</button>
          </div>
        </form>
      )}
    </section>
  );
}

const LEVELS = ["BEGINNER", "INTERMEDIATE", "ADVANCED"].map((v) => [v, v]);
const st = (...vals) => ({ key: "status", label: "Trạng thái", type: "select", required: true, editOnly: true, options: vals.map((v) => [v, v]) });

export default function CourseManagement({ token, role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]);
  const [coaches, setCoaches] = useState([]);
  const [facilities, setFacilities] = useState([]);
  const [sportId, setSportId] = useState(null);
  const [courses, setCourses] = useState([]); const [course, setCourse] = useState(null);
  const [offerings, setOfferings] = useState([]); const [offering, setOffering] = useState(null);
  const [classes, setClasses] = useState([]); const [cls, setCls] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [msg, setMsg] = useState(null);
  const [actionClock, setActionClock] = useState(() => new Date());
  const fail = useCallback((e) => setMsg({ type: "error", text: e.message }), []);

  const loadCourses = useCallback(() => sportId != null && getCoursesBySportApi(token, sportId).then((d) => setCourses(list(d))).catch(fail), [fail, sportId, token]);
  const loadOfferings = useCallback(() => course && getOfferingsApi(token, course.id).then((d) => setOfferings(list(d))).catch(fail), [course, fail, token]);
  const loadClasses = useCallback(() => offering && getClassesApi(token, offering.id).then((d) => setClasses(list(d))).catch(fail), [fail, offering, token]);
  const loadSessions = useCallback(() => cls && getSessionsByClassApi(token, cls.id).then((d) => setSessions(list(d))).catch(fail), [cls, fail, token]);

  useEffect(() => {
    getSportsApi(token).then((d) => { const s = list(d); setSports(s); if (s[0]) setSportId(s[0].id); }).catch(fail);
    getCoachesApi(token).then((d) => setCoaches(list(d))).catch(() => {});
    getFacilitiesApi(token).then((d) => setFacilities(list(d))).catch(() => {});
  }, [fail, token]);
  useEffect(() => {
    const timer = window.setInterval(() => setActionClock(new Date()), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { setCourse(null); setCourses([]); loadCourses(); }, [loadCourses]);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { setOffering(null); setOfferings([]); loadOfferings(); }, [loadOfferings]);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { setCls(null); setClasses([]); loadClasses(); }, [loadClasses]);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { setSessions([]); loadSessions(); }, [loadSessions]);

  const save = (fn, reload) => async (id, body) => {
    await fn(id, body);
    setMsg({ type: "success", text: "Đã lưu." });
    reload();
  };

  const courseFields = [
    { key: "name", label: "Tên khóa học", required: true },
    { key: "level", label: "Trình độ", type: "select", required: true, options: LEVELS },
    { key: "targetAgeMin", label: "Tuổi tối thiểu", type: "number" },
    { key: "targetAgeMax", label: "Tuổi tối đa", type: "number" },
    { key: "description", label: "Mô tả", type: "textarea" },
    { key: "learningOutcome", label: "Kết quả đạt được", type: "textarea" },
    st("ACTIVE", "INACTIVE"),
  ];
  const offeringFields = [
    { key: "name", label: "Tên đợt học", required: true },
    { key: "price", label: "Học phí", type: "number", required: true },
    { key: "plannedSessions", label: "Số buổi dự kiến", type: "number", required: true, min: 1, max: 60, step: 1 },
    { key: "sessionDuration", label: "Thời lượng buổi (phút)", type: "number", required: true, min: 30, max: 180, step: 1 },
    { key: "startDate", label: "Ngày bắt đầu", type: "date", required: true },
    { key: "endDate", label: "Ngày kết thúc", type: "date", required: true },
    { key: "registrationDeadline", label: "Hạn đăng ký", type: "datetime-local", required: true },
    { key: "cancellationDeadline", label: "Hạn hủy", type: "datetime-local", required: true },
    st("OPEN", "CLOSED"),
  ];
  const classFields = [
    { key: "name", label: "Tên lớp", required: true },
    { key: "coachId", label: "Huấn luyện viên", type: "select", num: true, required: true, options: coaches.map((c) => [c.id, c.userName || c.name || "HLV #" + c.id]) },
    { key: "capacity", label: "Sĩ số tối đa", type: "number", required: true },
    st("ACTIVE", "INACTIVE"),
  ];
  const sessionFields = [
    { key: "startTime", label: "Bắt đầu", type: "datetime-local", required: true, min: offering ? offering.startDate + "T00:00" : undefined, max: offering ? offering.endDate + "T23:59" : undefined },
    { key: "endTime", label: "Kết thúc", type: "datetime-local", required: true, min: offering ? offering.startDate + "T00:00" : undefined, max: offering ? offering.endDate + "T23:59" : undefined },
    { key: "facilityId", label: "Cơ sở vật chất", type: "select", num: true, options: facilities.map((f) => [f.id, f.name]) },
  ];

  return (
    <div className="layout">
      <Sidebar role={role} activePage="courses" onLogout={onLogout} onNavigate={onNavigate} />
      <main className="main">
        <h1>Quản lý khóa học &amp; lớp học</h1>
        <p className="subtitle">Chọn bộ môn, rồi lần lượt chọn khóa học → đợt học → lớp để quản lý buổi học.</p>
        {msg && <div className={"banner " + msg.type} role="status">{msg.text}</div>}

        <div className="pf-chips" role="tablist" aria-label="Bộ môn">
          {sports.map((s) => (
            <button key={s.id} type="button" role="tab" aria-selected={s.id === sportId}
              className={"pf-chip" + (s.id === sportId ? " active" : "")} onClick={() => setSportId(s.id)}>{s.name}</button>
          ))}
        </div>

        <Crud title="Khóa học" items={courses} fields={courseFields} picked={course} onPick={setCourse}
          disabled={sportId == null} hint="Chọn một bộ môn."
          columns={[{ key: "name", label: "Tên" }, { key: "level", label: "Trình độ" },
            { label: "Độ tuổi", render: (c) => (c.targetAgeMin ?? "—") + " – " + (c.targetAgeMax ?? "—") },
            { label: "Trạng thái", render: (c) => pill(c.status) }]}
          onSave={save((id, b) => saveCourseApi(token, id, id ? b : { ...b, sportId }), loadCourses)} />

        <Crud title={"Đợt học" + (course ? " – " + course.name : "")} items={offerings} fields={offeringFields} picked={offering} onPick={setOffering}
          disabled={!course} hint="Chọn một khóa học ở trên."
          columns={[{ key: "name", label: "Tên" },
            { label: "Học phí", render: (o) => Number(o.price).toLocaleString("vi-VN") + "đ" },
            { label: "Thời gian", render: (o) => o.startDate + " → " + o.endDate },
            { label: "Trạng thái", render: (o) => pill(o.status) }]}
          onSave={save((id, b) => saveOfferingApi(token, id, id ? b : { ...b, courseId: course.id }), loadOfferings)}
          validate={(form, _body, original) => validateCourseOffering(form, original === "new" ? null : original)} />

        <Crud title={"Lớp học" + (offering ? " – " + offering.name : "")} items={classes} fields={classFields} picked={cls} onPick={setCls}
          disabled={!offering} hint="Chọn một đợt học ở trên."
          columns={[{ key: "name", label: "Tên lớp" }, { key: "coachName", label: "HLV" }, { key: "capacity", label: "Sĩ số" },
            { label: "Trạng thái", render: (c) => pill(c.status) }]}
          onSave={save((id, b) => saveClassApi(token, id, id ? b : { ...b, courseOfferingId: offering.id }), loadClasses)} />

        <Crud title={"Buổi học" + (cls ? " – " + cls.name : "")} items={sessions} fields={sessionFields}
          disabled={!cls} hint="Chọn một lớp ở trên."
          columns={[{ label: "Bắt đầu", render: (s) => when(s.startTime) }, { label: "Kết thúc", render: (s) => when(s.endTime) },
            { key: "facilityName", label: "Cơ sở" }, { label: "Trạng thái", render: (s) => pill(s.status) }]}
          onSave={save((id, b) => saveSessionApi(token, id, { ...b, classId: cls.id }), loadSessions)}
          validate={(form) => validateClassSessionSchedule(form, offering)}
          formHint={offering?.sessionDuration ? ("Thời lượng bắt buộc: " + offering.sessionDuration + " phút. Khi đổi giờ bắt đầu, giờ kết thúc được tính lại tự động; máy chủ vẫn kiểm tra quy tắc này.") : "Chọn đợt học để xem thời lượng buổi học."}
          onFieldChange={(key, value, next) => key === "startTime" && offering?.sessionDuration
            ? { ...next, endTime: addMinutesToDateTimeLocal(value, offering.sessionDuration) } : next}
          rowActions={(s) => {
            if (String(s.status).toUpperCase() !== "SCHEDULED") return null;
            const availability = getSessionActionAvailability(s, actionClock);
            return <>
              <button type="button" disabled={!availability.canComplete} title={availability.completeReason || undefined}
                onClick={() => setSessionStatusApi(token, s.id, "COMPLETED").then(loadSessions).catch(fail)}>Hoàn thành</button>
              <button type="button" disabled={!availability.canCancel} title={availability.cancelReason || undefined}
                onClick={() => setSessionStatusApi(token, s.id, "CANCELLED").then(loadSessions).catch(fail)}>Hủy buổi</button>
              {availability.completeReason && <small>{availability.completeReason}</small>}
              {availability.cancelReason && <small>{availability.cancelReason}</small>}
            </>;
          }} />
      </main>
    </div>
  );
}
