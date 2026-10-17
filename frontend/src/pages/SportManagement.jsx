import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import Img from "../public-site/components/Img";
import { createSportApi, getSportsApi, updateSportApi, uploadSportImageApi } from "../api";

const EMPTY_FORM = { name: "", description: "", status: "ACTIVE" };
const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

function validateImage(file) {
  if (!file) return "";
  if (!IMAGE_TYPES.includes(file.type)) return "Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP.";
  if (file.size > MAX_IMAGE_BYTES) return "Ảnh không được vượt quá 5 MB.";
  return "";
}

export default function SportManagement({ token, role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [file, setFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selectedSport = useMemo(
    () => sports.find((sport) => sport.id === selectedId) || null,
    [sports, selectedId],
  );

  const load = useCallback(async (preferredId) => {
    setLoading(true);
    setError("");
    try {
      const rows = await getSportsApi(token);
      const list = Array.isArray(rows) ? rows : [];
      setSports(list);
      setSelectedId((currentId) => {
        const desiredId = preferredId ?? currentId;
        return list.some((sport) => sport.id === desiredId) ? desiredId : null;
      });
    } catch (requestError) {
      setError(requestError.status === 401 || requestError.status === 403
        ? "Bạn không có quyền xem danh sách bộ môn."
        : requestError.message || "Không tải được danh sách bộ môn.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(null), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  function clearImageSelection() {
    setFile(null);
    setPreviewUrl("");
  }

  function beginCreate() {
    setSelectedId(null);
    setForm(EMPTY_FORM);
    clearImageSelection();
    setError("");
    setNotice("");
  }

  function selectSport(sport) {
    setSelectedId(sport.id);
    setForm({ name: sport.name || "", description: sport.description || "", status: sport.status || "ACTIVE" });
    clearImageSelection();
    setError("");
    setNotice("");
  }

  function chooseImage(event) {
    const nextFile = event.target.files?.[0] || null;
    event.target.value = "";
    const imageError = validateImage(nextFile);
    setError(imageError);
    setNotice("");
    clearImageSelection();
    if (!imageError && nextFile) {
      setFile(nextFile);
      setPreviewUrl(URL.createObjectURL(nextFile));
    }
  }

  async function saveSport(event) {
    event.preventDefault();
    if (saving || uploading) return;
    const name = form.name.trim();
    if (!name) {
      setError("Vui lòng nhập tên bộ môn.");
      return;
    }
    if (name.length > 100) {
      setError("Tên bộ môn không được vượt quá 100 ký tự.");
      return;
    }
    const imageError = validateImage(file);
    if (imageError) {
      setError(imageError);
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");
    const editing = Boolean(selectedSport);
    try {
      const savedSport = editing
        ? await updateSportApi(token, selectedSport.id, {
          name,
          description: form.description.trim() || null,
          status: form.status,
        })
        : await createSportApi(token, { name, description: form.description.trim() || null });

      if (file) {
        try {
          await uploadSportImageApi(token, savedSport.id, file);
        } catch (uploadError) {
          setSelectedId(savedSport.id);
          clearImageSelection();
          await load(savedSport.id);
          setNotice(editing
            ? `Đã lưu thông tin bộ môn ${savedSport.id}, nhưng tải ảnh thất bại. Hãy chọn ảnh và thử tải lại.`
            : `Đã tạo bộ môn ID ${savedSport.id}, nhưng tải ảnh thất bại. Bộ môn đã được lưu; hãy chọn bộ môn này để thử tải ảnh lại.`);
          setError(uploadError.message || "Không tải được ảnh lên.");
          return;
        }
      }

      setSelectedId(savedSport.id);
      clearImageSelection();
      setNotice(editing ? "Đã cập nhật bộ môn." : `Đã tạo bộ môn ID ${savedSport.id}.`);
      await load(savedSport.id);
    } catch (saveError) {
      setError(saveError.status === 401 || saveError.status === 403
        ? "Bạn không có quyền thay đổi bộ môn."
        : saveError.message || "Không lưu được bộ môn.");
    } finally {
      setSaving(false);
    }
  }

  async function retryImageUpload() {
    if (!selectedSport || !file || uploading || saving) return;
    const imageError = validateImage(file);
    if (imageError) {
      setError(imageError);
      return;
    }
    setUploading(true);
    setError("");
    setNotice("");
    try {
      await uploadSportImageApi(token, selectedSport.id, file);
      clearImageSelection();
      setNotice("Đã cập nhật ảnh bộ môn.");
      await load(selectedSport.id);
    } catch (uploadError) {
      setError(uploadError.status === 401 || uploadError.status === 403
        ? "Bạn không có quyền tải ảnh bộ môn lên."
        : uploadError.message || "Không tải được ảnh lên.");
    } finally {
      setUploading(false);
    }
  }

  if (role !== "ADMIN") {
    return <div className="layout"><Sidebar role={role} activePage="sports" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main"><p className="banner error" role="alert">Chỉ ADMIN được phép quản lý bộ môn.</p></main>
    </div>;
  }

  return <div className="layout">
    <Sidebar role={role} activePage="sports" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main">
      <header className="page-header">
        <p className="eyebrow">QUẢN TRỊ NỘI DUNG</p>
        <h1>Quản lý bộ môn</h1>
        <p className="subtitle">Tạo, cập nhật trạng thái và hình ảnh bộ môn. Bộ môn ACTIVE được hiển thị trên trang công khai.</p>
      </header>

      <div className="sport-admin-toolbar">
        <p className="muted">{sports.length} bộ môn</p>
        <button type="button" className="btn-primary" onClick={beginCreate} disabled={saving || uploading}>Thêm bộ môn</button>
      </div>

      {error && <p className="banner error" role="alert">{error}</p>}
      {notice && <p className="banner success" role="status">{notice}</p>}

      <div className="sport-admin-grid">
        <section className="sport-admin-list" aria-label="Danh sách bộ môn">
          {loading ? <div className="queue-state" role="status">Đang tải danh sách…</div>
            : sports.length === 0 ? <div className="queue-state">Chưa có bộ môn nào. Bạn có thể tạo bộ môn mới.</div>
              : <div className="sport-admin-table-wrap"><table className="sport-admin-table">
                <thead><tr><th>Ảnh</th><th>ID</th><th>Tên bộ môn</th><th>Trạng thái</th><th /></tr></thead>
                <tbody>{sports.map((sport) => <tr key={sport.id} className={selectedId === sport.id ? "selected" : ""}>
                  <td><Img src={sport.imageUrl} alt={sport.name} label={sport.name} className="sport-admin-thumb" /></td>
                  <td>{sport.id}</td><td>{sport.name}</td><td><span className={`sport-status ${sport.status === "ACTIVE" ? "active" : "inactive"}`}>{sport.status}</span></td>
                  <td><button type="button" className="btn-secondary" onClick={() => selectSport(sport)}>Chỉnh sửa</button></td>
                </tr>)}</tbody>
              </table></div>}
        </section>

        <section className="sport-admin-editor" aria-labelledby="sport-editor-title">
          <h2 id="sport-editor-title">{selectedSport ? `Chỉnh sửa bộ môn #${selectedSport.id}` : "Bộ môn mới"}</h2>
          <Img src={previewUrl || selectedSport?.imageUrl} alt={form.name || "Ảnh bộ môn"} label={form.name || "Chưa có ảnh"} className="sport-admin-preview" />
          <form onSubmit={saveSport}>
            <label htmlFor="sport-name">Tên bộ môn</label>
            <input id="sport-name" value={form.name} maxLength={100} required onChange={(event) => setForm({ ...form, name: event.target.value })} disabled={saving || uploading} />
            <label htmlFor="sport-description">Mô tả</label>
            <textarea id="sport-description" rows={4} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} disabled={saving || uploading} />
            {selectedSport && <>
              <label htmlFor="sport-status">Trạng thái</label>
              <select id="sport-status" value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value })} disabled={saving || uploading}>
                <option value="ACTIVE">ACTIVE</option><option value="INACTIVE">INACTIVE</option>
              </select>
            </>}
            <label htmlFor="sport-image">Ảnh (JPEG, PNG hoặc WebP; tối đa 5 MB)</label>
            <input id="sport-image" type="file" accept="image/jpeg,image/png,image/webp" onChange={chooseImage} disabled={saving || uploading} />
            {selectedSport && <p className="muted small">Ảnh mới sẽ thay thế ảnh hiện tại sau khi tải lên thành công.</p>}
            <div className="sport-admin-actions">
              <button type="submit" className="btn-primary" disabled={saving || uploading}>{saving ? "Đang lưu…" : selectedSport ? "Lưu thay đổi" : "Tạo bộ môn"}</button>
              {selectedSport && file && <button type="button" className="btn-secondary" onClick={retryImageUpload} disabled={saving || uploading}>{uploading ? "Đang tải ảnh…" : "Chỉ tải ảnh lên"}</button>}
            </div>
          </form>
        </section>
      </div>
    </main>
  </div>;
}
