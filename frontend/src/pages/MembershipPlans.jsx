import { useCallback, useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  createMembershipPlanApi,
  getMembershipPlansApi,
  getPublicMembershipPlansApi,
  updateMembershipPlanApi,
  updateMembershipPlanStatusApi,
} from "../api";

const EMPTY_FORM = {
  name: "",
  description: "",
  monthlyPrice: "",
  upgradeFromPlanIds: [],
  courseDiscountPercent: "0",
  personalTrainingDiscountPercent: "0",
  allowPersonalTrainingPlan: false,
  allowOneOnOneCoaching: false,
};

function formFromPlan(plan) {
  return {
    name: plan.name || "",
    description: plan.description || "",
    monthlyPrice: String(plan.monthlyPrice ?? ""),
    upgradeFromPlanIds: Array.isArray(plan.upgradeFromPlanIds) ? plan.upgradeFromPlanIds.map(Number) : [],
    courseDiscountPercent: String(plan.courseDiscountPercent ?? "0"),
    personalTrainingDiscountPercent: String(plan.personalTrainingDiscountPercent ?? "0"),
    allowPersonalTrainingPlan: Boolean(plan.allowPersonalTrainingPlan),
    allowOneOnOneCoaching: Boolean(plan.allowOneOnOneCoaching),
  };
}

function formatPrice(value) {
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 })
    .format(Number(value || 0));
}

export default function MembershipPlans({ token, role, onLogout, onNavigate }) {
  const admin = role === "ADMIN";
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [statusSaving, setStatusSaving] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const rows = admin ? await getMembershipPlansApi(token) : await getPublicMembershipPlansApi();
      setPlans(Array.isArray(rows) ? rows : []);
    } catch (requestError) {
      setError(requestError.message || "Không thể tải danh sách gói hội viên.");
    } finally {
      setLoading(false);
    }
  }, [admin, token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  function startCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setError("");
    setFormOpen(true);
  }

  function startEdit(plan) {
    setEditingId(plan.id);
    setForm(formFromPlan(plan));
    setError("");
    setFormOpen(true);
  }

  function validateForm() {
    if (!form.name.trim()) return "Vui lòng nhập tên gói.";
    if (form.name.trim().length > 100) return "Tên gói không được vượt quá 100 ký tự.";
    if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(form.monthlyPrice) || Number(form.monthlyPrice) <= 0) return "Giá tháng phải lớn hơn 0 và có tối đa 2 chữ số thập phân.";
    for (const field of ["courseDiscountPercent", "personalTrainingDiscountPercent"]) {
      const rawValue = form[field];
      const value = Number(rawValue);
      if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(rawValue) || !Number.isFinite(value) || value < 0 || value > 100) return "Mức ưu đãi phải từ 0 đến 100%, tối đa 2 chữ số thập phân.";
    }
    return "";
  }

  async function savePlan(event) {
    event.preventDefault();
    const validation = validateForm();
    if (validation) {
      setError(validation);
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      monthlyPrice: Number(form.monthlyPrice),
      upgradeFromPlanIds: form.upgradeFromPlanIds,
      courseDiscountPercent: Number(form.courseDiscountPercent),
      personalTrainingDiscountPercent: Number(form.personalTrainingDiscountPercent),
      allowPersonalTrainingPlan: form.allowPersonalTrainingPlan,
      allowOneOnOneCoaching: form.allowOneOnOneCoaching,
    };
    try {
      if (editingId === null) {
        await createMembershipPlanApi(token, payload);
        setNotice("Đã tạo gói hội viên.");
      } else {
        const current = plans.find((plan) => plan.id === editingId);
        await updateMembershipPlanApi(token, editingId, { ...payload, status: current?.status || "ACTIVE" });
        setNotice("Đã cập nhật gói hội viên.");
      }
      setFormOpen(false);
      setEditingId(null);
      setForm(EMPTY_FORM);
      await load();
    } catch (requestError) {
      setError(requestError.message || "Không thể lưu gói hội viên.");
    } finally {
      setSaving(false);
    }
  }

  async function changeStatus(plan) {
    const nextStatus = plan.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    const action = nextStatus === "INACTIVE" ? "ngừng áp dụng" : "kích hoạt";
    if (!window.confirm("Bạn có chắc muốn " + action + " " + plan.name + "?")) return;

    setStatusSaving(plan.id);
    setError("");
    setNotice("");
    try {
      await updateMembershipPlanStatusApi(token, plan.id, nextStatus);
      setNotice(nextStatus === "INACTIVE" ? "Đã ngừng áp dụng gói hội viên." : "Đã kích hoạt gói hội viên.");
      await load();
    } catch (requestError) {
      setError(requestError.message || "Không thể thay đổi trạng thái gói.");
    } finally {
      setStatusSaving(null);
    }
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="membership-plans" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main">
        <h1>Gói hội viên</h1>
        <p className="subtitle">{admin
          ? "Tạo, chỉnh sửa, kích hoạt và tắt các gói hội viên."
          : "Danh sách gói hội viên đang áp dụng tại Sport Center."}</p>
        {notice && <div className="banner success" role="status">{notice}</div>}
        {error && <div className="banner error" role="alert">{error}</div>}

        <div className="member-list-toolbar">
          <span>{plans.length} gói hội viên</span>
          <button type="button" onClick={() => void load()} disabled={loading}>
            {loading ? "Đang tải…" : "Làm mới"}
          </button>
          {admin && <button type="button" onClick={startCreate}>Tạo gói hội viên</button>}
        </div>

        {admin && formOpen && (
          <form className="form-card" onSubmit={savePlan}>
            <h2>{editingId === null ? "Tạo gói hội viên" : "Chỉnh sửa gói hội viên"}</h2>
            <div className="row g-3">
              <div className="col-md-6">
                <label className="form-label" htmlFor="planName">Tên gói</label>
                <input className="form-control" id="planName" maxLength="100" required value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })} />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="planPrice">Giá mỗi tháng (VND)</label>
                <input className="form-control" id="planPrice" type="number" min="0.01" step="0.01" required value={form.monthlyPrice}
                  onChange={(event) => setForm({ ...form, monthlyPrice: event.target.value })} />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="planCourseDiscount">Ưu đãi khóa học (%)</label>
                <input className="form-control" id="planCourseDiscount" type="number" min="0" max="100" step="0.01" required value={form.courseDiscountPercent}
                  onChange={(event) => setForm({ ...form, courseDiscountPercent: event.target.value })} />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="planTrainingDiscount">Ưu đãi huấn luyện cá nhân (%)</label>
                <input className="form-control" id="planTrainingDiscount" type="number" min="0" max="100" step="0.01" required value={form.personalTrainingDiscountPercent}
                  onChange={(event) => setForm({ ...form, personalTrainingDiscountPercent: event.target.value })} />
              </div>
              <div className="col-12">
                <label className="form-label" htmlFor="planDescription">Mô tả</label>
                <textarea className="form-control" id="planDescription" rows="3" value={form.description}
                  onChange={(event) => setForm({ ...form, description: event.target.value })} />
              </div>
              <div className="col-md-6 form-check ms-2">
                <input className="form-check-input" id="allowPersonal" type="checkbox" checked={form.allowPersonalTrainingPlan}
                  onChange={(event) => setForm({ ...form, allowPersonalTrainingPlan: event.target.checked })} />
                <label className="form-check-label" htmlFor="allowPersonal">Cho phép gói huấn luyện cá nhân</label>
              </div>
              <div className="col-12">
                <fieldset><legend className="form-label">Các gói được phép nâng cấp trực tiếp lên gói này</legend>
                  {plans.filter((plan) => plan.id !== editingId).map((plan) => <label className="d-block" key={plan.id}>
                    <input type="checkbox" checked={form.upgradeFromPlanIds.includes(Number(plan.id))}
                      onChange={(event) => setForm((current) => ({ ...current, upgradeFromPlanIds: event.target.checked
                        ? [...current.upgradeFromPlanIds, Number(plan.id)]
                        : current.upgradeFromPlanIds.filter((id) => id !== Number(plan.id)) }))} /> {plan.name}
                  </label>)}
                  <small>Chỉ lưu các hướng được chọn; máy chủ từ chối tự nâng cấp và mọi chu trình.</small>
                </fieldset>
              </div>
              <div className="col-md-6 form-check ms-2">
                <input className="form-check-input" id="allowOneOnOne" type="checkbox" checked={form.allowOneOnOneCoaching}
                  onChange={(event) => setForm({ ...form, allowOneOnOneCoaching: event.target.checked })} />
                <label className="form-check-label" htmlFor="allowOneOnOne">Cho phép huấn luyện 1 kèm 1</label>
              </div>
            </div>
            <div className="d-flex gap-2 mt-3">
              <button type="submit" disabled={saving}>{saving ? "Đang lưu…" : "Lưu gói"}</button>
              <button type="button" onClick={() => setFormOpen(false)} disabled={saving}>Hủy</button>
            </div>
          </form>
        )}

        {loading ? <p role="status">Đang tải danh sách gói hội viên…</p>
          : plans.length === 0 ? <p>Chưa có gói hội viên nào.</p>
            : <div className="queue-table-wrap">
              <table className="queue-table">
                <thead><tr>
                  <th>Tên gói</th><th>Giá / tháng</th><th>Nâng cấp trực tiếp</th><th>Ưu đãi</th><th>Trạng thái</th>{admin && <th>Thao tác</th>}
                </tr></thead>
                <tbody>
                  {plans.map((plan) => (
                    <tr key={plan.id}>
                      <td><strong>{plan.name}</strong><br /><small>{plan.description || "Chưa có mô tả."}</small></td>
                      <td>{plan.monthlyPrice == null ? "Cần cấu hình giá tháng" : formatPrice(plan.monthlyPrice)}</td>
                      <td>{(plan.upgradeFromPlanIds || []).map((id) => plans.find((item) => Number(item.id) === Number(id))?.name || `#${id}`).join(", ") || "—"}</td>
                      <td>Khóa học {plan.courseDiscountPercent}%<br />Huấn luyện cá nhân {plan.personalTrainingDiscountPercent}%</td>
                      <td><span className={"status-pill status-" + String(plan.status).toLowerCase()}>{plan.status === "ACTIVE" ? "\u0110ang \u00e1p d\u1ee5ng" : "Ng\u1eebng \u00e1p d\u1ee5ng"}</span></td>
                      {admin && <td>
                        <div className="d-flex flex-wrap gap-2">
                          <button type="button" onClick={() => startEdit(plan)}>Sửa</button>
                          <button type="button" disabled={statusSaving === plan.id} onClick={() => void changeStatus(plan)}>
                            {statusSaving === plan.id ? "\u0110ang l\u01b0u\u2026" : plan.status === "ACTIVE" ? "Ng\u1eebng \u00e1p d\u1ee5ng" : "K\u00edch ho\u1ea1t"}
                          </button>
                        </div>
                      </td>}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>}
      </main>
    </div>
  );
}
