import { useEffect, useMemo, useState } from "react";
import { Link, Navigate, useLocation, useNavigate, useParams } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import AdminTopBar from "../components/AdminTopBar";
import ClassSessionGenerationPanel from "./ClassSessionGenerationPanel";
import {
  getClassApi, getClassesApi, getCoachesApi, getCoachSportsApi, getCourseApi, getFacilitiesApi, getOfferingApi,
  getOfferingsApi, getSessionsByClassApi, saveClassApi, saveOfferingApi, saveSessionApi,
  setSessionStatusApi,
} from "../api";
import {
  filterClassSessions, filterCourseClasses, filterCourseOfferings, formatClassSessionDate,
  formatClassSessionTime, offeringTimeStatus, qualifiedClassCoaches, paginateItems, vietnamBusinessDate,
} from "../courseManagementModel";
import { addMinutesToDateTimeLocal, getSessionActionAvailability, validateClassSessionSchedule, validateCourseOffering } from "../classSessionValidation";
import { isOfferingCommercialEditLocked } from "../courseOfferingPolicy.js";
import "./Portal.css";
import "./Admin.css";
import "./CourseManagement.css";

const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];
const toDateTimeInput = (value) => value ? String(value).slice(0, 16) : "";
const toDateTimePayload = (value) => !value ? null : value.length === 16 ? `${value}:00` : value;
const statusPill = (value) => <span className={`status-pill status-${String(value || "").toLowerCase()}`}>{value || "—"}</span>;
const courseClassStatusPill = (value) => <span className={`status-pill course-class-status status-${String(value || "").toLowerCase()}`}>{value || "—"}</span>;
const SESSION_STATUS_LABELS = { SCHEDULED: "Đã lên lịch", COMPLETED: "Đã hoàn thành", CANCELLED: "Đã hủy" };
const sessionStatusPill = (value) => <span className={`status-pill session-status status-${String(value || "").toLowerCase()}`}>{SESSION_STATUS_LABELS[String(value || "").toUpperCase()] || "—"}</span>;
const TIME_STATUS_LABEL = { UPCOMING: "Sắp diễn ra", ONGOING: "Đang diễn ra", PAST: "Đã kết thúc" };

const statusField = (values, label = "Trạng thái") => ({ key: "status", label, type: "select", required: true, editOnly: true, options: values.map((value) => [value, value]) });

const COURSE_ICON_PATHS = {
  back: <path d="M19 12H5m7 7-7-7 7-7" />,
  refresh: <><path d="M20 7v5h-5" /><path d="M20 12a8 8 0 1 0 1.2 4" /></>,
  add: <path d="M12 5v14m-7-7h14" />,
  view: <><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></>,
  edit: <><path d="m15 5 4 4" /><path d="M4 20l4.5-1 10.8-10.8a2.1 2.1 0 0 0-3-3L5.5 16 4 20Z" /></>,
  previous: <path d="m15 18-6-6 6-6" />,
  next: <path d="m9 18 6-6-6-6" />,
};

function CourseIcon({ name }) {
  return <svg className="course-management-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{COURSE_ICON_PATHS[name]}</svg>;
}

function fieldsToPayload(fields, form, isNew) {
  const payload = {};
  fields.forEach((field) => {
    if (isNew && field.editOnly) return;
    let value = form[field.key];
    if (field.type === "number" || field.num) value = value === "" || value == null ? null : Number(value);
    else if (field.type === "datetime-local") value = toDateTimePayload(value);
    payload[field.key] = value;
  });
  return payload;
}

function EntityCrud({ title, entity, details, fields, onSave, validate, formHint, onFieldChange, panelClassName = "", editLabel = "Sửa thông tin", editAccessibleName, showPencil = false }) {
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  function open() {
    const next = {};
    fields.forEach((field) => {
      const value = entity?.[field.key];
      next[field.key] = field.type === "datetime-local" ? toDateTimeInput(value) : value ?? field.default ?? "";
    });
    setForm(next); setEditing(true); setError("");
  }
  async function submit(event) {
    event.preventDefault();
    const payload = fieldsToPayload(fields, form, false);
    const message = validate?.(form, payload, entity);
    if (message) { setError(message); return; }
    setBusy(true); setError("");
    try { await onSave(payload); setEditing(false); }
    catch (requestError) { setError(requestError.message || "Không thể lưu thông tin."); }
    finally { setBusy(false); }
  }
  return <section className={`adm-block course-entity-panel ${panelClassName}`}>
    <div className="adm-head"><h2>{title}</h2><button type="button" className={showPencil ? "cm-icon-button" : undefined} aria-label={editAccessibleName || editLabel} title={editAccessibleName || editLabel} onClick={open} disabled={!entity}>{showPencil && <CourseIcon name="edit" />}{editLabel && <span>{editLabel}</span>}</button></div>
    <dl className="course-entity-facts">{details.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? "—"}</dd></div>)}</dl>
    {editing && <form className="adm-form" onSubmit={submit} noValidate={Boolean(validate)}>
      <div className="grid-2">{fields.filter((field) => entity || !field.editOnly).map((field) => <Field key={field.key} field={{ ...field, disabled: field.disabled || (field.commercial && isOfferingCommercialEditLocked(entity)) }} value={form[field.key]} onChange={(value) => setForm((current) => onFieldChange ? onFieldChange(field.key, value, { ...current, [field.key]: value }) : ({ ...current, [field.key]: value }))} />)}</div>
      {formHint && <p className="subtitle">{formHint}</p>}
      {error && <div className="banner error" role="alert">{error}</div>}
      <div className="form-actions"><button type="button" onClick={() => setEditing(false)}>Đóng</button><button className="adm-primary" type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu"}</button></div>
    </form>}
  </section>;
}

function Field({ field, value, onChange }) {
  return <div className="field">
    <label htmlFor={`course-management-${field.key}`}>{field.label}</label>
    {field.type === "select" ? <select id={`course-management-${field.key}`} value={value ?? ""} required={field.required} disabled={field.disabled} onChange={(event) => onChange(event.target.value)}>
      {!field.required && <option value="">— Không chọn —</option>}{field.required && <option value="" disabled>Chọn…</option>}
      {(field.options || []).map(([optionValue, label]) => <option key={optionValue} value={optionValue}>{label}</option>)}
    </select> : field.type === "textarea" ? <textarea id={`course-management-${field.key}`} value={value ?? ""} onChange={(event) => onChange(event.target.value)} />
      : <input id={`course-management-${field.key}`} type={field.type || "text"} value={value ?? ""} min={field.min} max={field.max} step={field.type === "number" ? "any" : field.type === "datetime-local" ? 60 : undefined} required={field.required} disabled={field.disabled} onChange={(event) => onChange(event.target.value)} />}
  </div>;
}

function Crud({ title, items, fields, columns, onSave, onView, onRefresh, rowActions, disabled, hint, validate, onFieldChange, formHint, canEdit, pageResetKey, className = "" }) {
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [pagination, setPagination] = useState({ key: pageResetKey, page: 0 });
  const page = pagination.key === pageResetKey ? pagination.page : 0;
  const setPage = (nextPage) => setPagination({ key: pageResetKey, page: nextPage });
  const pageData = paginateItems(items, page, 10);
  function open(item) {
    const next = {};
    fields.forEach((field) => {
      const value = item?.[field.key];
      next[field.key] = field.type === "datetime-local" ? toDateTimeInput(value) : value ?? field.default ?? "";
    });
    setForm(next); setEditing(item || "new"); setError("");
  }
  async function submit(event) {
    event.preventDefault();
    const isNew = editing === "new";
    const payload = fieldsToPayload(fields, form, isNew);
    const message = validate?.(form, payload, isNew ? "new" : editing);
    if (message) { setError(message); return; }
    setBusy(true); setError("");
    try { await onSave(isNew ? null : editing.id, payload); setEditing(null); }
    catch (requestError) { setError(requestError.message || "Không thể lưu dữ liệu."); }
    finally { setBusy(false); }
  }
  return <section className={`adm-block ${className}`.trim()}>
    <div className="adm-head cm-list-toolbar"><div><h2>{title}</h2><span className="course-list-total">{items.length} mục</span></div><div className="adm-actions cm-toolbar-actions">{onRefresh && <button type="button" className="cm-refresh-button" aria-label={`Làm mới ${title}`} title="Làm mới" onClick={onRefresh}><CourseIcon name="refresh" /><span>Làm mới</span></button>}<button type="button" className="adm-primary" disabled={disabled} onClick={() => open(null)}><CourseIcon name="add" /> Thêm mới</button></div></div>
    {disabled ? <p className="dash-empty">{hint}</p> : items.length === 0 ? <p className="dash-empty">{hint || "Chưa có dữ liệu."}</p> : <>
      <div className="queue-table-wrap"><table className="queue-table"><thead><tr>{columns.map((column) => <th key={column.label}>{column.label}</th>)}<th aria-label="Thao tác"></th></tr></thead>
        <tbody>{pageData.items.map((item) => <tr key={item.id}>{columns.map((column) => <td key={column.label}>{column.render ? column.render(item) : item[column.key] ?? "—"}</td>)}<td><div className="adm-actions">
          {onView && <button type="button" className="cm-icon-button" aria-label={`Xem ${title}: ${item.name || `#${item.id}`}`} title="Xem chi tiết" onClick={() => onView(item)}><CourseIcon name="view" /></button>}
          {canEdit?.(item) !== false && <button type="button" className="cm-icon-button" aria-label={`Sửa ${title}: ${item.name || `#${item.id}`}`} title="Sửa" onClick={() => open(item)}><CourseIcon name="edit" /></button>}
          {rowActions?.(item)}
        </div></td></tr>)}</tbody>
      </table></div>
      <nav className="course-management-pagination" aria-label={`Trang ${title}`}><span>Hiển thị {pageData.items.length} / {pageData.total} · Trang {pageData.page + 1}/{pageData.pageCount}</span><div>
        <button type="button" disabled={pageData.page === 0} aria-label="Trang trước" onClick={() => setPage(pageData.page - 1)}><CourseIcon name="previous" /> Trang trước</button><button type="button" disabled={pageData.page + 1 >= pageData.pageCount} aria-label="Trang sau" onClick={() => setPage(pageData.page + 1)}>Trang sau <CourseIcon name="next" /></button>
      </div></nav>
    </>}
    {editing && <form className="adm-form" onSubmit={submit} noValidate={Boolean(validate)}><div className="grid-2">{fields.filter((field) => !(editing === "new" && field.editOnly)).map((field) => <Field key={field.key} field={{ ...field, disabled: field.disabled || (field.commercial && editing !== "new" && isOfferingCommercialEditLocked(editing)) }} value={form[field.key]} onChange={(value) => setForm((current) => onFieldChange ? onFieldChange(field.key, value, { ...current, [field.key]: value }, editing) : ({ ...current, [field.key]: value }))} />)}</div>
      {formHint && <p className="subtitle">{formHint}</p>}{error && <div className="banner error" role="alert">{error}</div>}
      <div className="form-actions"><button type="button" onClick={() => setEditing(null)}>Đóng</button><button className="adm-primary" type="submit" disabled={busy}>{busy ? "Đang lưu…" : "Lưu"}</button></div>
    </form>}
  </section>;
}

export default function CourseManagementPages({ token, role, currentUserName, onLogout, onNavigate }) {
  const { courseId, offeringId, classId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const basePath = location.pathname.startsWith("/admin/courses") ? "/admin/courses" : "/staff/courses";
  const coursePath = `${basePath}/${courseId}`;
  const offeringPath = `${coursePath}/offerings/${offeringId}`;
  const classPath = `${offeringPath}/classes/${classId}`;
  const pageKind = classId ? "class" : offeringId ? "offering" : "course";
  const parentPath = pageKind === "class" ? offeringPath : pageKind === "offering" ? coursePath : basePath;
  const parentLabel = pageKind === "class" ? "Quay lại đợt học" : pageKind === "offering" ? "Quay lại khóa học" : "Danh sách khóa học";
  const [course, setCourse] = useState(null);
  const [offering, setOffering] = useState(null);
  const [courseClass, setCourseClass] = useState(null);
  const [offerings, setOfferings] = useState([]);
  const [classes, setClasses] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [coaches, setCoaches] = useState([]);
  const [coachSports, setCoachSports] = useState([]);
  const [coachQualificationFailures, setCoachQualificationFailures] = useState(0);
  const [facilities, setFacilities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [reload, setReload] = useState(0);
  const [clock, setClock] = useState(() => new Date());
  const [offeringSearch, setOfferingSearch] = useState("");
  const [timeFilter, setTimeFilter] = useState("");
  const [offeringStatusFilter, setOfferingStatusFilter] = useState("");
  const [offeringSort, setOfferingSort] = useState("NEWEST");
  const [classSearch, setClassSearch] = useState("");
  const [classStatusFilter, setClassStatusFilter] = useState("");
  const [sessionStatusFilter, setSessionStatusFilter] = useState("");
  const [sessionStartDateFilter, setSessionStartDateFilter] = useState("");
  const [sessionEndDateFilter, setSessionEndDateFilter] = useState("");
  const [sessionSort, setSessionSort] = useState("ASC");
  const [sessionFilterRevision, setSessionFilterRevision] = useState(0);

  useEffect(() => {
    const timer = window.setInterval(() => setClock(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true); setError(""); setNotice("");
      setCoachSports([]); setCoachQualificationFailures(0);
      if (!/^\d+$/.test(String(courseId || "")) || (offeringId && !/^\d+$/.test(offeringId)) || (classId && !/^\d+$/.test(classId))) {
        setError("Đường dẫn khóa học, đợt học hoặc lớp học không hợp lệ."); setLoading(false); return;
      }
      try {
        const loadedCourse = await getCourseApi(token, courseId);
        if (!active) return;
        setCourse(loadedCourse);
        if (pageKind === "course") {
          const loadedOfferings = await getOfferingsApi(token, courseId);
          if (active) setOfferings(rows(loadedOfferings));
        } else {
          const [loadedOffering, coachData] = await Promise.all([
            getOfferingApi(token, offeringId), getCoachesApi(token),
          ]);
          if (!active) return;
          if (Number(loadedOffering.courseId) !== Number(courseId)) throw new Error("Đợt học không thuộc khóa học đã chọn.");
          setOffering(loadedOffering); setCoaches(rows(coachData));
          const activeCoaches = rows(coachData).filter((coach) => String(coach.status || "").toUpperCase() === "ACTIVE");
          const qualificationResults = await Promise.allSettled(activeCoaches.map((coach) => getCoachSportsApi(token, coach.id)));
          if (!active) return;
          const coachSportRows = [];
          let qualificationFailures = 0;
          qualificationResults.forEach((result) => {
            if (result.status === "fulfilled") coachSportRows.push(...rows(result.value));
            else qualificationFailures += 1;
          });
          setCoachSports(coachSportRows); setCoachQualificationFailures(qualificationFailures);
          if (pageKind === "offering") {
            const loadedClasses = await getClassesApi(token, offeringId);
            if (active) setClasses(rows(loadedClasses));
          } else {
            const [loadedClass, facilityData] = await Promise.all([getClassApi(token, classId), getFacilitiesApi(token)]);
            if (!active) return;
            if (Number(loadedClass.courseOfferingId) !== Number(offeringId)) throw new Error("Lớp không thuộc đợt học đã chọn.");
            setCourseClass(loadedClass); setFacilities(rows(facilityData));
            const loadedSessions = await getSessionsByClassApi(token, classId);
            if (active) setSessions(rows(loadedSessions));
          }
        }
      } catch (requestError) {
        if (active) { setCourse(null); setError(requestError.message || "Không tải được dữ liệu quản lý khóa học."); }
      } finally { if (active) setLoading(false); }
    }
    void load();
    return () => { active = false; };
  }, [token, courseId, offeringId, classId, pageKind, reload]);

  const today = vietnamBusinessDate(clock);
  const filteredOfferings = useMemo(() => filterCourseOfferings(offerings, {
    search: offeringSearch, timeStatus: timeFilter, status: offeringStatusFilter, sort: offeringSort, businessDate: today,
  }), [offerings, offeringSearch, timeFilter, offeringStatusFilter, offeringSort, today]);
  const filteredClasses = useMemo(() => filterCourseClasses(classes, { search: classSearch, status: classStatusFilter }), [classes, classSearch, classStatusFilter]);
  const filteredSessions = useMemo(() => filterClassSessions(sessions, {
    status: sessionStatusFilter, startDate: sessionStartDateFilter, endDate: sessionEndDateFilter, sort: sessionSort,
  }), [sessions, sessionStatusFilter, sessionStartDateFilter, sessionEndDateFilter, sessionSort]);

  const isCurrentCourse = String(course?.id ?? "") === String(courseId ?? "");
  const isCurrentOffering = String(offering?.id ?? "") === String(offeringId ?? "")
    && String(offering?.courseId ?? "") === String(courseId ?? "");
  const isCurrentClass = String(courseClass?.id ?? "") === String(classId ?? "")
    && String(courseClass?.courseOfferingId ?? "") === String(offeringId ?? "");
  const hasCourseName = isCurrentCourse && typeof course?.name === "string" && Boolean(course.name.trim());
  const hasOfferingName = isCurrentOffering && typeof offering?.name === "string" && Boolean(offering.name.trim());
  const hasClassName = isCurrentClass && typeof courseClass?.name === "string" && Boolean(courseClass.name.trim());
  const breadcrumbItems = hasCourseName
    && (pageKind === "course" || hasOfferingName)
    && (pageKind !== "class" || hasClassName)
    ? [
      { label: "Khóa học", to: basePath },
      { label: course.name.trim(), ...(pageKind === "course" ? { current: true } : { to: coursePath }) },
      ...(pageKind === "offering" || pageKind === "class"
        ? [{ label: offering.name.trim(), ...(pageKind === "offering" ? { current: true } : { to: offeringPath }) }]
        : []),
      ...(pageKind === "class" ? [{ label: courseClass.name.trim(), current: true }] : []),
    ]
    : undefined;

  const offeringPathFor = (id) => `${coursePath}/offerings/${id}`;
  const classPathFor = (id) => `${offeringPath}/classes/${id}`;
  const refresh = () => setReload((value) => value + 1);
  const saveSuccess = (message) => { setNotice(message); refresh(); };

  const offeringFields = [
    { key: "name", label: "Tên đợt học", required: true },
    { key: "price", label: "Học phí", type: "number", required: true, commercial: true },
    { key: "plannedSessions", label: "Số buổi dự kiến", type: "number", required: true, min: 1, max: 60, commercial: true },
    { key: "sessionDuration", label: "Thời lượng buổi (phút)", type: "number", required: true, min: 30, max: 180, commercial: true },
    { key: "startDate", label: "Ngày bắt đầu", type: "date", required: true, commercial: true },
    { key: "endDate", label: "Ngày kết thúc", type: "date", required: true, commercial: true },
    { key: "registrationDeadline", label: "Hạn đăng ký", type: "datetime-local", required: true, commercial: true },
    { key: "cancellationDeadline", label: "Hạn hủy", type: "datetime-local", required: true, commercial: true },
    statusField(["OPEN", "CLOSED"], "Trạng thái đợt học"),
  ];
  const suitableFacilities = facilities.filter((facility) => (facility.sports || (facility.sportId ? [{ id: facility.sportId }] : []))
    .some((sport) => Number(sport.id) === Number(course?.sportId)));
  const qualifiedCoaches = qualifiedClassCoaches(coaches, coachSports, course?.sportId);
  const coachQualificationHint = coachQualificationFailures
    ? `Không tải được thông tin chuyên môn của ${coachQualificationFailures} HLV. HLV chưa xác minh đã được ẩn; hãy làm mới để thử lại.`
    : qualifiedCoaches.length === 0
      ? "Không có HLV ACTIVE đã được xác nhận chuyên môn cho môn thể thao này. Hãy cập nhật CoachSport trước khi tạo hoặc đổi phân công."
      : "Chỉ hiển thị HLV ACTIVE có chuyên môn khớp môn thể thao của khóa học. Máy chủ vẫn kiểm tra điều kiện và xung đột lịch.";
  const classFields = [
    { key: "name", label: "Tên lớp", required: true },
    { key: "coachId", label: "Huấn luyện viên", type: "select", num: true, required: true, options: qualifiedCoaches.map((coach) => [coach.id, coach.userName || coach.name || `HLV #${coach.id}`]) },
    { key: "capacity", label: "Sức chứa", type: "number", required: true },
    statusField(["ACTIVE", "INACTIVE"]),
  ];
  const sessionFields = [
    { key: "startTime", label: "Bắt đầu", type: "datetime-local", required: true, min: offering ? `${offering.startDate}T00:00` : undefined, max: offering ? `${offering.endDate}T23:59` : undefined },
    { key: "endTime", label: "Kết thúc", type: "datetime-local", required: true, min: offering ? `${offering.startDate}T00:00` : undefined, max: offering ? `${offering.endDate}T23:59` : undefined },
    { key: "facilityId", label: "Cơ sở vật chất", type: "select", num: true, required: true, options: suitableFacilities.filter((facility) => String(facility.status || "").toUpperCase() === "ACTIVE").map((facility) => [facility.id, facility.name]) },
  ];

  if (!token) return <Navigate to="/login" replace />;
  if (role !== "ADMIN") return <Navigate to="/" replace />;

  return <div className="layout">
    <Sidebar role={role} activePage="courses" onLogout={onLogout} onNavigate={onNavigate} />
    <main className="main course-management-pages">
      <AdminTopBar
        pageLabel="QUẢN LÝ KHÓA HỌC"
        displayName={currentUserName}
        avatarName={currentUserName}
        breadcrumbItems={breadcrumbItems}
      />
      <div className="course-management-content">
      <button type="button" className="adm-back" onClick={() => navigate(parentPath)}><CourseIcon name="back" /> {parentLabel}</button>
      {loading && <p className="course-management-state" role="status">Đang tải dữ liệu khóa học…</p>}
      {error && <div className="banner error" role="alert">{error} <button type="button" onClick={refresh}>Thử lại</button></div>}
      {notice && <div className="banner success" role="status">{notice}</div>}
      {!loading && !error && course && <>
        {pageKind === "course" && <CourseDetailPage {...{ course, offerings: filteredOfferings, today, offeringSearch, setOfferingSearch, timeFilter, setTimeFilter, offeringStatusFilter, setOfferingStatusFilter, offeringSort, setOfferingSort, offeringFields, refresh, saveSuccess, token, navigate, offeringPathFor }} />}
        {pageKind === "offering" && offering && <OfferingDetailPage {...{ course, offering, classes: filteredClasses, classSearch, setClassSearch, classStatusFilter, setClassStatusFilter, classFields, coachQualificationHint, refresh, saveSuccess, token, navigate, classPathFor }} />}
        {pageKind === "class" && offering && courseClass && <ClassDetailPage {...{ course, offering, courseClass, sessions: filteredSessions, allSessions: sessions, sessionStatusFilter, setSessionStatusFilter, sessionStartDateFilter, setSessionStartDateFilter, sessionEndDateFilter, setSessionEndDateFilter, sessionSort, setSessionSort, sessionFilterRevision, setSessionFilterRevision, sessionFields, suitableFacilities, coaches, coachSports, coachQualificationHint, refresh, saveSuccess, token, coursePath, offeringPath, classPath, onActionError: setError }} />}
      </>}
      </div>
    </main>
  </div>;
}

function CourseDetailPage({ course, offerings, today, offeringSearch, setOfferingSearch, timeFilter, setTimeFilter, offeringStatusFilter, setOfferingStatusFilter, offeringSort, setOfferingSort, offeringFields, refresh, saveSuccess, token, navigate, offeringPathFor }) {
  return <>
    <header className="course-detail-heading"><div><h1>{course.name}</h1><p>Quản lý thông tin khóa học, các đợt học và lớp trực thuộc.</p></div></header>
    <section className="adm-block course-entity-panel course-info-card"><div className="adm-head"><h2>Thông tin khóa học</h2><button type="button" aria-label="Sửa thông tin khóa học" title="Sửa thông tin khóa học" onClick={() => navigate("/staff/courses/" + course.id + "/edit")}><CourseIcon name="edit" /><span>Sửa</span></button></div><dl className="course-entity-facts">
      <Fact label="Bộ môn" value={course.sportName} /><Fact label="Trình độ" value={course.level} /><Fact label="Độ tuổi" value={`${course.targetAgeMin ?? "—"} – ${course.targetAgeMax ?? "—"}`} /><Fact label="Chính sách độ tuổi" value={course.agePolicy || "—"} /><Fact label="Trạng thái" value={statusPill(course.status)} />
      {course.description && <Fact label="Mô tả" value={course.description} />}{course.learningOutcome && <Fact label="Kết quả đầu ra" value={course.learningOutcome} />}
    </dl></section>
    <section className="adm-block"><div className="adm-head"><div><h2>Đợt học</h2><span className="course-list-total">{offerings.length} kết quả · ngày nghiệp vụ {today}</span></div></div>
      <div className="course-hierarchy-filters">
        <label>Tìm tên đợt<input type="search" value={offeringSearch} onChange={(event) => setOfferingSearch(event.target.value)} /></label>
        <label>Thời gian<select value={timeFilter} onChange={(event) => setTimeFilter(event.target.value)}><option value="">Tất cả</option><option value="UPCOMING">Sắp diễn ra</option><option value="ONGOING">Đang diễn ra</option><option value="PAST">Đã kết thúc</option></select></label>
        <label>Trạng thái đợt học<select value={offeringStatusFilter} onChange={(event) => setOfferingStatusFilter(event.target.value)}><option value="">Tất cả</option><option value="OPEN">OPEN</option><option value="CLOSED">CLOSED</option></select></label>
        <label>Sắp xếp<select value={offeringSort} onChange={(event) => setOfferingSort(event.target.value)}><option value="NEWEST">Ngày bắt đầu mới nhất</option><option value="OLDEST">Ngày bắt đầu cũ nhất</option></select></label>
      </div>
      <Crud title="Danh sách đợt học" items={offerings} fields={offeringFields} onRefresh={refresh}
        columns={[
          { key: "name", label: "Đợt học" }, { label: "Học phí", render: (item) => Number(item.price || 0).toLocaleString("vi-VN") + " ₫" },
          { key: "startDate", label: "Bắt đầu" }, { key: "endDate", label: "Kết thúc" },
          { label: "Thời gian", render: (item) => { const state = offeringTimeStatus(item, today); return state ? <span className={`course-time-status time-${state.toLowerCase()}`}>{TIME_STATUS_LABEL[state]}</span> : "—"; } },
          { label: "Trạng thái đợt học", render: (item) => statusPill(item.status) },
        ]}
        onView={(item) => navigate(offeringPathFor(item.id))}
        onSave={async (id, body) => { await saveOfferingApi(token, id, id ? body : { ...body, courseId: course.id }); saveSuccess("Đã lưu đợt học."); }}
        formHint="Chỉ có thể đăng ký khi đợt học đang mở, còn trong thời gian nhận đăng ký và có lớp đủ điều kiện. Giá và lịch học không thể chỉnh sửa từ 14 ngày trước hạn đăng ký."
        validate={(form, _payload, original) => validateCourseOffering(form, original === "new" ? null : original)} />
    </section>
  </>;
}

function OfferingDetailPage({ course, offering, classes, classSearch, setClassSearch, classStatusFilter, setClassStatusFilter, classFields, coachQualificationHint, refresh, saveSuccess, token, navigate, classPathFor }) {
  const fields = ["name", "price", "plannedSessions", "sessionDuration", "startDate", "endDate", "registrationDeadline", "cancellationDeadline", "status"];
  const businessDate = vietnamBusinessDate();
  const timeStatus = offeringTimeStatus(offering, businessDate);
  return <>
    <header className="course-detail-heading"><div><div className="course-title-group"><h1>{offering.name}</h1><span className={`course-time-status time-${String(timeStatus || "unknown").toLowerCase()}`}>{TIME_STATUS_LABEL[timeStatus] || "Chưa xác định"}</span></div><p>Đợt học thuộc khóa {course.name}; quản lý điều kiện và lịch các lớp bên dưới.</p></div></header>
    <EntityCrud title="Thông tin đợt học" entity={offering} fields={offeringFieldsForEntity(fields, offering)} formHint="Chỉ có thể đăng ký khi đợt học đang mở, còn trong thời gian nhận đăng ký và có lớp đủ điều kiện. Giá và lịch học không thể chỉnh sửa từ 14 ngày trước hạn đăng ký; trạng thái và tên đợt vẫn có thể cập nhật nếu hợp lệ." panelClassName="course-offering-info" editLabel="" editAccessibleName="Sửa thông tin" showPencil details={[
      ["Khóa học", course.name], ["Học phí", `${Number(offering.price || 0).toLocaleString("vi-VN")} ₫`], ["Ngày bắt đầu", offering.startDate], ["Ngày kết thúc", offering.endDate],
      ["Hạn đăng ký", offering.registrationDeadline || "—"], ["Hạn hủy", offering.cancellationDeadline || "—"], ["Số buổi dự kiến", offering.plannedSessions], ["Thời lượng mỗi buổi", offering.sessionDuration ? `${offering.sessionDuration} phút` : "—"],
      ["Trạng thái đợt học", statusPill(offering.status)], ["Thời gian", TIME_STATUS_LABEL[timeStatus] || "Chưa xác định"],
    ]} validate={(form, _body, original) => validateCourseOffering(form, original)} onSave={async (body) => { await saveOfferingApi(token, offering.id, body); saveSuccess("Đã cập nhật đợt học."); }} />
    <section className="adm-block"><div className="adm-head"><div><h2>Lớp trong đợt học</h2><span className="course-list-total">{classes.length} lớp</span></div></div>
      <div className="course-hierarchy-filters course-two-filters"><label>Tìm tên lớp<input type="search" value={classSearch} onChange={(event) => setClassSearch(event.target.value)} /></label><label>Trạng thái<select value={classStatusFilter} onChange={(event) => setClassStatusFilter(event.target.value)}><option value="">Tất cả</option><option value="ACTIVE">ACTIVE</option><option value="INACTIVE">INACTIVE</option></select></label></div>
      <p className="subtitle" role="status">{coachQualificationHint}</p>
      <Crud title="Danh sách lớp" items={classes} fields={classFields} formHint={coachQualificationHint} onRefresh={refresh}
        columns={[{ key: "name", label: "Lớp" }, { key: "coachName", label: "Huấn luyện viên" }, { key: "capacity", label: "Sức chứa" }, { label: "Trạng thái", render: (item) => courseClassStatusPill(item.status) }]}
        onView={(item) => navigate(classPathFor(item.id))}
        onSave={async (id, body) => { await saveClassApi(token, id, id ? body : { ...body, courseOfferingId: offering.id }); saveSuccess("Đã lưu lớp."); }} />
    </section>
  </>;
}

function ClassDetailPage({ course, offering, courseClass, sessions, allSessions, sessionStatusFilter, setSessionStatusFilter, sessionStartDateFilter, setSessionStartDateFilter, sessionEndDateFilter, setSessionEndDateFilter, sessionSort, setSessionSort, sessionFilterRevision, setSessionFilterRevision, sessionFields, suitableFacilities, coaches, coachSports, coachQualificationHint, refresh, saveSuccess, token, coursePath, offeringPath, classPath, onActionError }) {
  const [actionClock, setActionClock] = useState(() => new Date());
  useEffect(() => { const timer = window.setInterval(() => setActionClock(new Date()), 30_000); return () => window.clearInterval(timer); }, []);
  const classFields = [
    { key: "name", label: "Tên lớp", required: true },
    { key: "coachId", label: "Huấn luyện viên", type: "select", num: true, required: true, options: qualifiedClassCoaches(coaches, coachSports, course.sportId).map((coach) => [coach.id, coach.userName || coach.name || `HLV #${coach.id}`]) },
    { key: "capacity", label: "Sức chứa", type: "number", required: true }, statusField(["ACTIVE", "INACTIVE"]),
  ];
  if (courseClass.coachId != null && !classFields[1].options.some(([id]) => Number(id) === Number(courseClass.coachId))) {
    classFields[1].options = [[courseClass.coachId, courseClass.coachName || `HLV #${courseClass.coachId}`], ...classFields[1].options];
  }
  const currentCoachEligible = qualifiedClassCoaches(coaches, coachSports, course.sportId).some((coach) => Number(coach.id) === Number(courseClass.coachId));
  const classCoachHint = [coachQualificationHint, courseClass.coachId != null && !currentCoachEligible
    ? "HLV hiện được phân công không còn ACTIVE hoặc chưa được xác minh đủ chuyên môn. Phân công hiện tại được giữ nguyên; máy chủ sẽ kiểm tra mọi thay đổi."
    : ""].filter(Boolean).join(" ");
  return <>
    <header className="course-detail-heading"><div><div className="course-title-group"><h1>{courseClass.name}</h1>{courseClassStatusPill(courseClass.status)}</div><p>{course.name} · {offering.name} · Lịch học và buổi học</p></div></header>
    <EntityCrud title="Thông tin lớp" entity={courseClass} fields={classFields} formHint={classCoachHint} panelClassName="course-class-info" editLabel="" editAccessibleName="Sửa thông tin" showPencil details={[
      ["Khóa học", course.name], ["Đợt học", offering.name], ["Huấn luyện viên", courseClass.coachName || "—"], ["Sức chứa", courseClass.capacity], ["Trạng thái", courseClassStatusPill(courseClass.status)],
    ]} onSave={async (body) => { await saveClassApi(token, courseClass.id, body); saveSuccess("Đã cập nhật lớp."); }} />
    <section className="adm-block course-session-section"><div className="adm-head"><div><h2>Buổi học</h2><span className="course-list-total">{allSessions.length} buổi · sắp xếp thời gian tăng dần</span></div></div>
      <div className="course-session-filters" aria-label="Bộ lọc buổi học">
        <label>Trạng thái<select value={sessionStatusFilter} onChange={(event) => { setSessionStatusFilter(event.target.value); setSessionFilterRevision((value) => value + 1); }}><option value="">Tất cả</option><option value="SCHEDULED">Đã lên lịch</option><option value="COMPLETED">Đã hoàn thành</option><option value="CANCELLED">Đã hủy</option></select></label>
        <label>Từ ngày<input type="date" value={sessionStartDateFilter} onChange={(event) => { setSessionStartDateFilter(event.target.value); setSessionFilterRevision((value) => value + 1); }} /></label>
        <label>Đến ngày<input type="date" value={sessionEndDateFilter} onChange={(event) => { setSessionEndDateFilter(event.target.value); setSessionFilterRevision((value) => value + 1); }} /></label>
        <label>Sắp xếp<select value={sessionSort} onChange={(event) => { setSessionSort(event.target.value); setSessionFilterRevision((value) => value + 1); }}><option value="ASC">Thời gian tăng dần</option><option value="DESC">Thời gian giảm dần</option></select></label>
      </div>
      <Crud className="course-session-list" title="Danh sách buổi học" items={sessions} fields={sessionFields} onRefresh={refresh}
        pageResetKey={sessionFilterRevision}
        hint={allSessions.length ? "Không có buổi học nào khớp bộ lọc." : "Chưa có buổi học."}
        columns={[{ label: "Ngày", render: (session) => formatClassSessionDate(session.startTime) }, { label: "Thời gian", render: (session) => `${formatClassSessionTime(session.startTime)}–${formatClassSessionTime(session.endTime)}` }, { label: "Huấn luyện viên", render: () => courseClass.coachName || "—" }, { key: "facilityName", label: "Cơ sở vật chất" }, { label: "Trạng thái", render: (session) => sessionStatusPill(session.status) }]}
        canEdit={(session) => String(session.status).toUpperCase() === "SCHEDULED"}
        rowActions={(session) => {
          if (String(session.status).toUpperCase() !== "SCHEDULED") return null;
          const availability = getSessionActionAvailability(session, actionClock);
          return <><button type="button" disabled={!availability.canComplete} title={availability.completeReason || undefined} onClick={() => void changeSessionStatus(session, "COMPLETED")}>Hoàn thành</button><button type="button" disabled={!availability.canCancel} title={availability.cancelReason || undefined} onClick={() => void changeSessionStatus(session, "CANCELLED")}>Hủy buổi</button></>;
        }}
        onSave={async (id, body) => { await saveSessionApi(token, id, { ...body, classId: courseClass.id }); saveSuccess("Đã lưu buổi học."); }}
        validate={(form) => validateClassSessionSchedule(form, offering)}
        formHint={offering.sessionDuration ? `Thời lượng bắt buộc: ${offering.sessionDuration} phút. Máy chủ vẫn kiểm tra thời lượng và xung đột lịch.` : "Máy chủ kiểm tra thời lượng và xung đột lịch."}
        onFieldChange={(key, value, next) => key === "startTime" && offering.sessionDuration ? { ...next, endTime: addMinutesToDateTimeLocal(value, offering.sessionDuration) } : next} />
    </section>
    <ClassSessionGenerationPanel key={courseClass.id} token={token} courseClass={courseClass} offering={offering} facilities={suitableFacilities} sessions={allSessions} onGenerated={refresh} />
    <div className="course-detail-return-links"><Link to={offeringPath}>Quay lại đợt học</Link><Link to={coursePath}>Quay lại khóa học</Link><span className="sr-only">Lớp hiện tại: {classPath}</span></div>
  </>;

  async function changeSessionStatus(session, status) {
    try { await setSessionStatusApi(token, session.id, status); saveSuccess(`Đã cập nhật buổi học: ${status}.`); }
    catch (requestError) { onActionError(requestError.message || "Không thể cập nhật buổi học."); }
  }
}

function offeringFieldsForEntity(keys, offering) {
  return [
    { key: "name", label: "Tên đợt học", required: true }, { key: "price", label: "Học phí", type: "number", required: true, commercial: true },
    { key: "plannedSessions", label: "Số buổi dự kiến", type: "number", required: true, min: 1, max: 60, commercial: true }, { key: "sessionDuration", label: "Thời lượng buổi (phút)", type: "number", required: true, min: 30, max: 180, commercial: true },
    { key: "startDate", label: "Ngày bắt đầu", type: "date", required: true, commercial: true }, { key: "endDate", label: "Ngày kết thúc", type: "date", required: true, commercial: true },
    { key: "registrationDeadline", label: "Hạn đăng ký", type: "datetime-local", required: true, commercial: true }, { key: "cancellationDeadline", label: "Hạn hủy", type: "datetime-local", required: true, commercial: true }, statusField(["OPEN", "CLOSED"], "Trạng thái đợt học"),
  ].filter((field) => keys.includes(field.key) && (field.key !== "courseId" || offering.courseId));
}

function Fact({ label, value }) { return <div><dt>{label}</dt><dd>{value ?? "—"}</dd></div>; }
