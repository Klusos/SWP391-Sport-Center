import { useEffect, useState } from "react";
import { createPublicConsultationRequestApi, getPublicSportsApi } from "../../api";

const EMPTY_FORM = {
  name: "",
  phone: "",
  email: "",
  sportId: "",
  age: "",
  experience: "",
  note: "",
};

export default function ConsultForm() {
  const [form, setForm] = useState(EMPTY_FORM);
  const [sports, setSports] = useState([]);
  const [loadingSports, setLoadingSports] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [sent, setSent] = useState(false);

  useEffect(() => {
    let active = true;
    getPublicSportsApi()
      .then((data) => { if (active) setSports(Array.isArray(data) ? data : []); })
      .catch(() => { if (active) setError("Không tải được danh sách môn học. Vui lòng thử tải lại trang."); })
      .finally(() => { if (active) setLoadingSports(false); });
    return () => { active = false; };
  }, []);

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((previous) => ({ ...previous, [name]: value }));
    setError("");
    setSent(false);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    setSent(false);
    try {
      await createPublicConsultationRequestApi({
        name: form.name.trim(),
        phone: form.phone.trim(),
        email: form.email.trim() || null,
        sportId: Number(form.sportId),
        age: form.age ? Number(form.age) : null,
        experience: form.experience.trim() || null,
        note: form.note.trim() || null,
      });
      setForm(EMPTY_FORM);
      setSent(true);
    } catch (requestError) {
      setError(requestError.message || "Chưa gửi được yêu cầu. Vui lòng thử lại.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="consult-form" onSubmit={handleSubmit}>
      <h3>TƯ VẤN GHI DANH</h3>
      <input className="form-control" name="name" type="text" placeholder="Họ và tên" aria-label="Họ và tên" maxLength={100} value={form.name} onChange={handleChange} required />
      <input className="form-control" name="phone" type="tel" placeholder="Số điện thoại" aria-label="Số điện thoại" maxLength={20} value={form.phone} onChange={handleChange} required />
      <input className="form-control" name="email" type="email" placeholder="Email (không bắt buộc)" aria-label="Email (không bắt buộc)" maxLength={150} value={form.email} onChange={handleChange} />
      <select className="form-control" name="sportId" aria-label="Môn học" value={form.sportId} onChange={handleChange} required disabled={loadingSports || sports.length === 0}>
        <option value="">{loadingSports ? "Đang tải môn học…" : "Chọn môn học"}</option>
        {sports.map((sport) => <option key={sport.id} value={sport.id}>{sport.name}</option>)}
      </select>
      <input className="form-control" name="age" type="number" min="1" max="120" placeholder="Tuổi (không bắt buộc)" aria-label="Tuổi (không bắt buộc)" value={form.age} onChange={handleChange} />
      <textarea className="form-control" name="experience" placeholder="Kinh nghiệm (không bắt buộc)" aria-label="Kinh nghiệm (không bắt buộc)" value={form.experience} onChange={handleChange} rows="2" />
      <textarea className="form-control" name="note" placeholder="Ghi chú (không bắt buộc)" aria-label="Ghi chú (không bắt buộc)" value={form.note} onChange={handleChange} rows="2" />
      <button type="submit" className="btn btn-brand-dark" disabled={submitting || loadingSports || sports.length === 0}>
        {submitting ? "ĐANG GỬI…" : "ĐĂNG KÝ TƯ VẤN"}
      </button>
      {error && <p className="consult-error" role="alert">{error}</p>}
      {sent && <p className="consult-ok" role="status">Đã gửi yêu cầu! Trung tâm sẽ phản hồi trong thời gian sớm nhất.</p>}
    </form>
  );
}
