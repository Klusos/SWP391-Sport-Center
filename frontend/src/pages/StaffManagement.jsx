import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import Img from "../public-site/components/Img";
import "./StaffManagement.css";
import {
  assignCoachSportApi,
  createStaffCoachApi,
  createStaffReceptionistApi,
  getCoachesApi,
  getCoachSportsApi,
  getSportsApi,
  getUsersApi,
  updateCoachSportApi,
  updateCoachProfileApi,
  uploadCoachImageApi,
  updateStaffApi,
  updateStaffStatusApi,
} from "../api";

const EMPTY_FORM = { name: "", email: "", phone: "", gender: "", dateOfBirth: "", password: "", bio: "" };
const EMPTY_SPECIALIZATION = { sportId: "", experienceYears: 0, certification: "" };

export default function StaffManagement({ token, role, onLogout, onNavigate }) {
  const [staff, setStaff] = useState([]);
  const [sports, setSports] = useState([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selected, setSelected] = useState(null);
  const [specializations, setSpecializations] = useState([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [editing, setEditing] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createRole, setCreateRole] = useState("RECEPTIONIST");
  const [form, setForm] = useState(EMPTY_FORM);
  const [createSpecializations, setCreateSpecializations] = useState([{ ...EMPTY_SPECIALIZATION }]);
  const [saving, setSaving] = useState(false);
  const [specializationSaving, setSpecializationSaving] = useState(false);
  const [statusSaving, setStatusSaving] = useState(false);
  const [createImage, setCreateImage] = useState(null);
  const [createImagePreview, setCreateImagePreview] = useState("");
  const [detailImage, setDetailImage] = useState(null);
  const [detailImagePreview, setDetailImagePreview] = useState("");
  const [imageSaving, setImageSaving] = useState(false);
  const [bioSaving, setBioSaving] = useState(false);
  const admin = role === "ADMIN";

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [users, coaches, sportRows] = await Promise.all([
        getUsersApi(token), getCoachesApi(token), getSportsApi(token),
      ]);
      const coachByUserId = new Map((Array.isArray(coaches) ? coaches : []).map((coach) => [coach.userId, coach]));
      setStaff((Array.isArray(users) ? users : [])
        .filter((user) => user.role === "COACH" || user.role === "RECEPTIONIST")
        .map((user) => ({ ...user, coach: coachByUserId.get(user.id) || null })));
      setSports(Array.isArray(sportRows) ? sportRows : []);
    } catch (requestError) {
      setError(requestError.status === 401 || requestError.status === 403
        ? "Chỉ ADMIN được phép quản lý nhân sự."
        : requestError.message || "Không tải được danh sách nhân sự.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    if (admin) Promise.resolve().then(load);
  }, [admin, load]);

  useEffect(() => () => {
    if (createImagePreview) URL.revokeObjectURL(createImagePreview);
  }, [createImagePreview]);

  useEffect(() => () => {
    if (detailImagePreview) URL.revokeObjectURL(detailImagePreview);
  }, [detailImagePreview]);

  const filtered = useMemo(() => {
    const value = query.trim().toLowerCase();
    if (!value) return staff;
    return staff.filter((item) => [item.id, item.name, item.email, item.role, item.status]
      .some((field) => String(field ?? "").toLowerCase().includes(value)));
  }, [staff, query]);

  async function openDetail(user) {
    setSelected(user);
    setError("");
    setNotice("");
    setEditing(false);
    setDetailLoading(true);
    if (user.role === "COACH" && user.coach) {
      try {
        const rows = await getCoachSportsApi(token, user.coach.id);
        setSpecializations((Array.isArray(rows) ? rows : []).map((row) => ({ ...row, existing: true })));
      } catch (requestError) {
        setSpecializations([]);
        setError(requestError.message || "Không tải được môn chuyên môn của Coach.");
      }
    } else {
      setSpecializations([]);
    }
    setForm({ ...EMPTY_FORM, name: user.name || "", email: user.email || "", phone: user.phone || "", gender: user.gender || "", dateOfBirth: user.dateOfBirth || "", bio: user.coach?.bio || "" });
    setDetailImage(null);
    setDetailImagePreview("");
    setDetailLoading(false);
  }

  function chooseCoachImage(event, setFile, setPreview) {
    const file = event.target.files?.[0] || null;
    setError("");
    setNotice("");
    if (file && !["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      event.target.value = "";
      setFile(null);
      setPreview("");
      setError("Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP.");
      return;
    }
    if (file && file.size > 5 * 1024 * 1024) {
      event.target.value = "";
      setFile(null);
      setPreview("");
      setError("Ảnh không được vượt quá 5 MB.");
      return;
    }
    setPreview(file ? URL.createObjectURL(file) : "");
    setFile(file);
    event.target.value = "";
  }

  async function saveCoachBio(event) {
    event.preventDefault();
    if (!selected?.coach || !admin || bioSaving) return;
    setBioSaving(true);
    setError("");
    setNotice("");
    try {
      const updated = await updateCoachProfileApi(token, selected.coach.id, { bio: form.bio.trim() || null });
      setSelected((current) => ({ ...current, coach: { ...current.coach, ...updated } }));
      await load();
      setNotice("Đã cập nhật tiểu sử Coach.");
    } catch (requestError) {
      setError(requestError.message || "Không thể cập nhật tiểu sử Coach.");
    } finally {
      setBioSaving(false);
    }
  }

  async function uploadDetailImage() {
    if (!selected?.coach || !detailImage || imageSaving || !admin) return;
    setImageSaving(true);
    setError("");
    setNotice("");
    try {
      const updated = await uploadCoachImageApi(token, selected.coach.id, detailImage);
      setSelected((current) => ({ ...current, coach: { ...current.coach, ...updated } }));
      setDetailImage(null);
      setDetailImagePreview("");
      await load();
      setNotice("Đã cập nhật ảnh Coach.");
    } catch (requestError) {
      setError(requestError.message || "Không thể tải ảnh Coach lên.");
    } finally {
      setImageSaving(false);
    }
  }

  function updateForm(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function updateSpecialization(index, field, value) {
    setSpecializations((rows) => rows.map((row, rowIndex) => rowIndex === index ? { ...row, [field]: value } : row));
  }

  async function saveStaff(event) {
    event.preventDefault();
    if (!selected || !admin) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const updated = await updateStaffApi(token, selected.id, {
        name: form.name,
        email: form.email,
        phone: form.phone || null,
        gender: form.gender || null,
        dateOfBirth: form.dateOfBirth || null,
      });
      setSelected((current) => ({ ...current, ...updated }));
      await load();
      setNotice("Đã cập nhật thông tin nhân sự.");
    } catch (requestError) {
      setError(requestError.status === 409 ? "Email đã được tài khoản khác sử dụng." : requestError.message || "Không thể cập nhật nhân sự.");
    } finally {
      setSaving(false);
    }
  }

  async function changeStatus() {
    if (!selected || !admin) return;
    const nextStatus = selected.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    const action = nextStatus === "INACTIVE" ? "vô hiệu hóa" : "kích hoạt lại";
    if (!window.confirm(`Bạn muốn ${action} tài khoản ${selected.role} #${selected.id}?`)) return;
    setStatusSaving(true);
    setError("");
    setNotice("");
    try {
      const updated = await updateStaffStatusApi(token, selected.id, nextStatus);
      setSelected((current) => ({ ...current, ...updated, coach: current.coach ? { ...current.coach, status: nextStatus } : null }));
      await load();
      setNotice(nextStatus === "INACTIVE"
        ? "Đã vô hiệu hóa tài khoản. Các phân công lớp và lịch sử CoachSport vẫn được giữ nguyên."
        : "Đã kích hoạt lại tài khoản.");
    } catch (requestError) {
      setError(requestError.message || "Không cập nhật được trạng thái nhân sự.");
    } finally {
      setStatusSaving(false);
    }
  }

  async function saveSpecializations(event) {
    event.preventDefault();
    if (!selected?.coach || !admin) return;
    setSpecializationSaving(true);
    setError("");
    setNotice("");
    try {
      for (const row of specializations) {
        const payload = {
          sportId: Number(row.sportId),
          experienceYears: Number(row.experienceYears),
          certification: row.certification || null,
        };
        if (row.existing) {
          await updateCoachSportApi(token, selected.coach.id, payload.sportId, {
            experienceYears: payload.experienceYears,
            certification: payload.certification,
          });
        } else {
          await assignCoachSportApi(token, selected.coach.id, payload);
        }
      }
      await openDetail(selected);
      setNotice("Đã lưu các chuyên môn Coach.");
    } catch (requestError) {
      setError(requestError.message || "Không thể lưu chuyên môn. Một số thay đổi trước đó có thể đã được lưu.");
      await load();
    } finally {
      setSpecializationSaving(false);
    }
  }

  async function createStaff(event) {
    event.preventDefault();
    if (saving) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      let imageUploadError = "";
      if (createRole === "COACH") {
        const createdCoach = await createStaffCoachApi(token, {
          name: form.name,
          email: form.email,
          password: form.password,
          phone: form.phone || null,
          gender: form.gender || null,
          bio: form.bio.trim() || null,
          specializations: createSpecializations.map((row) => ({
            sportId: Number(row.sportId),
            experienceYears: Number(row.experienceYears),
            certification: row.certification || null,
          })),
        });
        if (createImage) {
          try {
            await uploadCoachImageApi(token, createdCoach.id, createImage);
          } catch (uploadError) {
            imageUploadError = `Coach #${createdCoach.id} đã được tạo, nhưng tải ảnh thất bại: ${uploadError.message || "Lỗi máy chủ"}. Không gửi lại biểu mẫu tạo; có thể tải ảnh tại Chi tiết Coach.`;
          }
        }
      } else {
        await createStaffReceptionistApi(token, {
          ...form,
          dateOfBirth: form.dateOfBirth || null,
          gender: form.gender || null,
          phone: form.phone || null,
        });
      }
      setCreating(false);
      setForm(EMPTY_FORM);
      setCreateImage(null);
      setCreateImagePreview("");
      setCreateSpecializations([{ ...EMPTY_SPECIALIZATION }]);
      await load();
      setNotice(imageUploadError || `Đã tạo tài khoản ${createRole}. Mật khẩu chỉ được nhập khi tạo và không thể xem lại.`);
    } catch (requestError) {
      setError(requestError.status === 409 ? "Email đã được sử dụng." : requestError.message || "Không thể tạo tài khoản nhân sự.");
    } finally {
      setSaving(false);
    }
  }

  if (!admin) {
    return <div className="layout"><Sidebar role={role} onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main"><h1>Staff Management</h1><div className="banner error" role="alert">Chỉ ADMIN được truy cập chức năng này.</div></main>
    </div>;
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="staff" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main staff-page">
        <h1>Quản lý nhân sự</h1>
        <p className="subtitle">Quản lý tài khoản COACH, RECEPTIONIST và chuyên môn Coach.</p>
        {notice && <div className="banner success" role="status">{notice}</div>}
        {error && <div className="banner error" role="alert">{error}</div>}
        <div className="staff-toolbar">
          <div className="staff-search">
            <label htmlFor="staffSearch">Tìm theo tên, email, ID, vai trò hoặc trạng thái</label>
            <input id="staffSearch" type="search" placeholder="Nhập từ khóa để tìm kiếm…" value={query} onChange={(event) => setQuery(event.target.value)} />
          </div>
          <div className="staff-toolbar-actions">
            <button type="button" onClick={load} disabled={loading}>{loading ? "Đang tải…" : "Làm mới"}</button>
            <button type="button" className="btn-primary" onClick={() => { setCreating((value) => !value); setSelected(null); setForm(EMPTY_FORM); setCreateImage(null); setCreateImagePreview(""); }}>{creating ? "Đóng form" : "+ Tạo nhân sự"}</button>
          </div>
        </div>

        {creating && <form className="form-card staff-create-form" onSubmit={createStaff}>
          <h2>Tạo tài khoản nhân sự</h2>
          <div className="field field-narrow">
            <label htmlFor="staffType">Loại tài khoản</label>
            <select id="staffType" value={createRole} onChange={(event) => setCreateRole(event.target.value)}>
              <option value="RECEPTIONIST">RECEPTIONIST</option><option value="COACH">COACH</option>
            </select>
          </div>
          <div className="grid-2">
            <label>Họ tên<input required maxLength={100} value={form.name} onChange={(event) => updateForm("name", event.target.value)} /></label>
            <label>Email<input required type="email" maxLength={150} value={form.email} onChange={(event) => updateForm("email", event.target.value)} /></label>
            <label>Điện thoại<input maxLength={20} value={form.phone} onChange={(event) => updateForm("phone", event.target.value)} /></label>
            <label>Giới tính (tối đa 1 ký tự)<input maxLength={1} value={form.gender} onChange={(event) => updateForm("gender", event.target.value)} /></label>
            <label>Mật khẩu khởi tạo<input required type="password" minLength={8} autoComplete="new-password" value={form.password} onChange={(event) => updateForm("password", event.target.value)} /></label>
          </div>
          {createRole === "COACH" && <fieldset>
            <legend>Chuyên môn bắt buộc</legend>
            <label>Tiểu sử công khai (không bắt buộc, tối đa 1000 ký tự)<textarea maxLength={1000} rows={4} value={form.bio} onChange={(event) => updateForm("bio", event.target.value)} /></label>
            <div className="coach-image-editor">
              <Img src={createImagePreview} alt="Xem trước ảnh Coach" label={form.name || "Chưa có ảnh"} className="coach-image-preview" />
              <label htmlFor="newCoachImage">Ảnh Coach (JPEG, PNG hoặc WebP; tối đa 5 MB)</label>
              <input id="newCoachImage" type="file" accept="image/jpeg,image/png,image/webp" disabled={saving} onChange={(event) => chooseCoachImage(event, setCreateImage, setCreateImagePreview)} />
            </div>
            {createSpecializations.map((row, index) => <div className="grid-2" key={index}>
              <label>Môn thể thao<select required value={row.sportId} onChange={(event) => setCreateSpecializations((rows) => rows.map((item, i) => i === index ? { ...item, sportId: event.target.value } : item))}>
                <option value="">Chọn môn</option>{sports.filter((sport) => sport.status === "ACTIVE").map((sport) => <option key={sport.id} value={sport.id}>{sport.name}</option>)}
              </select></label>
              <label>Số năm kinh nghiệm<input required type="number" min="0" value={row.experienceYears} onChange={(event) => setCreateSpecializations((rows) => rows.map((item, i) => i === index ? { ...item, experienceYears: event.target.value } : item))} /></label>
              <label>Chứng chỉ (không bắt buộc)<input maxLength={255} value={row.certification} onChange={(event) => setCreateSpecializations((rows) => rows.map((item, i) => i === index ? { ...item, certification: event.target.value } : item))} /></label>
              {createSpecializations.length > 1 && <button type="button" onClick={() => setCreateSpecializations((rows) => rows.filter((_, i) => i !== index))}>Bỏ chuyên môn này</button>}
            </div>)}
            <button type="button" onClick={() => setCreateSpecializations((rows) => [...rows, { ...EMPTY_SPECIALIZATION }])}>Thêm môn chuyên môn</button>
          </fieldset>}
          <div className="form-actions"><button type="submit" disabled={saving}>{saving ? "Đang tạo…" : "Tạo tài khoản"}</button><button type="button" onClick={() => { setCreating(false); setCreateImage(null); setCreateImagePreview(""); }}>Hủy</button></div>
        </form>}

        {loading ? <div className="queue-state" role="status">Đang tải danh sách nhân sự…</div>
          : filtered.length === 0 ? <div className="queue-state">Chưa có nhân sự phù hợp.</div>
            : <div className="queue-table-wrap"><table className="queue-table">
              <thead><tr><th>ID</th><th>Họ tên</th><th>Email</th><th>Vai trò</th><th>Tài khoản</th><th>Trạng thái Coach</th><th></th></tr></thead>
              <tbody>{filtered.map((item) => <tr key={item.id}>
                <td>#{item.id}</td><td>{item.name}</td><td>{item.email}</td><td>{item.role}</td>
                <td><span className={"status-pill status-" + String(item.status).toLowerCase()}>{item.status === "ACTIVE" ? "Ho\u1ea1t \u0111\u1ed9ng" : "Ng\u1eebng ho\u1ea1t \u0111\u1ed9ng"}</span></td><td>{item.coach?.status ? <span className={"status-pill status-" + String(item.coach.status).toLowerCase()}>{item.coach.status === "ACTIVE" ? "Ho\u1ea1t \u0111\u1ed9ng" : "Ng\u1eebng ho\u1ea1t \u0111\u1ed9ng"}</span> : "\u2014"}</td>
                <td><button type="button" onClick={() => openDetail(item)}>Chi tiết</button></td>
              </tr>)}</tbody>
            </table></div>}

        {detailLoading && <div className="queue-state" role="status">Đang tải chi tiết nhân sự…</div>}
        {selected && <section className="member-detail form-card">
          <div className="member-detail-heading"><div><p className="subtitle">{selected.role} #{selected.id}</p><h2>{selected.name}</h2></div>
            <div><button type="button" onClick={() => setEditing((value) => !value)}>{editing ? "Đóng chỉnh sửa" : "Chỉnh sửa"}</button>
              <button type="button" onClick={changeStatus} disabled={statusSaving}>{statusSaving ? "Đang lưu…" : selected.status === "ACTIVE" ? "Vô hiệu hóa" : "Kích hoạt lại"}</button>
              <button type="button" onClick={() => setSelected(null)}>Đóng</button></div>
          </div>
          {!editing ? <dl className="member-detail-grid">
            <div><dt>Email</dt><dd>{selected.email}</dd></div><div><dt>Điện thoại</dt><dd>{selected.phone || "—"}</dd></div>
            <div><dt>Ngày sinh</dt><dd>{selected.dateOfBirth || "—"}</dd></div><div><dt>Trạng thái tài khoản</dt><dd><span className={"status-pill status-" + String(selected.status).toLowerCase()}>{selected.status === "ACTIVE" ? "Ho\u1ea1t động" : "Ng\u1eebng ho\u1ea1t động"}</span></dd></div>
            {selected.coach && <div><dt>Trạng thái Coach</dt><dd><span className={"status-pill status-" + String(selected.coach.status).toLowerCase()}>{selected.coach.status === "ACTIVE" ? "Ho\u1ea1t động" : "Ng\u1eebng ho\u1ea1t động"}</span></dd></div>}
          </dl> : <form className="form-card" onSubmit={saveStaff}>
            <label>Họ tên<input required maxLength={100} value={form.name} onChange={(event) => updateForm("name", event.target.value)} /></label>
            <label>Email<input required type="email" maxLength={150} value={form.email} onChange={(event) => updateForm("email", event.target.value)} /></label>
            <label>Ngày sinh<input type="date" max={new Date().toISOString().slice(0, 10)} value={form.dateOfBirth} onChange={(event) => updateForm("dateOfBirth", event.target.value)} /></label>
            <label>Điện thoại<input maxLength={20} value={form.phone} onChange={(event) => updateForm("phone", event.target.value)} /></label>
            <label>Giới tính<input maxLength={1} value={form.gender} onChange={(event) => updateForm("gender", event.target.value)} /></label>
            <button type="submit" disabled={saving}>{saving ? "Đang lưu…" : "Lưu thông tin"}</button>
          </form>}

          {selected.role === "COACH" && selected.coach && <section className="form-card coach-profile-editor">
            <h3>Hồ sơ công khai Coach</h3>
            <Img src={detailImagePreview || selected.coach.imageUrl} alt={selected.name} label={selected.name} className="coach-image-preview" />
            <label htmlFor="editCoachImage">Thay ảnh (JPEG, PNG hoặc WebP; tối đa 5 MB)</label>
            <input id="editCoachImage" type="file" accept="image/jpeg,image/png,image/webp" disabled={imageSaving} onChange={(event) => chooseCoachImage(event, setDetailImage, setDetailImagePreview)} />
            <button type="button" onClick={uploadDetailImage} disabled={!detailImage || imageSaving}>{imageSaving ? "Đang tải ảnh…" : "Lưu ảnh Coach"}</button>
            <form onSubmit={saveCoachBio}>
              <label>Tiểu sử công khai<textarea maxLength={1000} rows={4} value={form.bio} onChange={(event) => updateForm("bio", event.target.value)} /></label>
              <button type="submit" disabled={bioSaving}>{bioSaving ? "Đang lưu…" : "Lưu tiểu sử"}</button>
            </form>
          </section>}

          {selected.role === "COACH" && selected.coach && <form className="form-card" onSubmit={saveSpecializations}>
            <h3>Chuyên môn Coach</h3>
            {specializations.map((row, index) => <div className="grid-2" key={row.id || `new-${index}`}>
              <label>Môn<select required disabled={row.existing} value={row.sportId} onChange={(event) => updateSpecialization(index, "sportId", event.target.value)}>
                <option value="">Chọn môn</option>{sports.map((sport) => <option key={sport.id} value={sport.id}>{sport.name}{sport.status !== "ACTIVE" ? " (INACTIVE)" : ""}</option>)}
              </select></label>
              <label>Kinh nghiệm (năm)<input required type="number" min="0" value={row.experienceYears} onChange={(event) => updateSpecialization(index, "experienceYears", event.target.value)} /></label>
              <label>Chứng chỉ<input maxLength={255} value={row.certification || ""} onChange={(event) => updateSpecialization(index, "certification", event.target.value)} /></label>
              {row.existing && <span>Đã có chuyên môn này</span>}
            </div>)}
            <button type="button" onClick={() => setSpecializations((rows) => [...rows, { ...EMPTY_SPECIALIZATION, existing: false }])}>Thêm chuyên môn</button>
            <button type="submit" disabled={specializationSaving}>{specializationSaving ? "Đang lưu…" : "Lưu chuyên môn"}</button>
            <p>Không có API xóa chuyên môn; bản ghi hiện có được giữ lại.</p>
          </form>}
        </section>}
      </main>
    </div>
  );
}