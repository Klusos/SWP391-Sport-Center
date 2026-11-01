import { useCallback, useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./MembershipPlans.css";
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
  price: "",
  durationMonths: "1",
  courseDiscountPercent: "0",
  personalTrainingDiscountPercent: "0",
  allowPersonalTrainingPlan: false,
  allowOneOnOneCoaching: false,
};

function formFromPlan(plan) {
  return {
    name: plan.name || "",
    description: plan.description || "",
    price: String(plan.price ?? ""),
    durationMonths: String(plan.durationMonths ?? "1"),
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

function PlanStatus({ status }) {
  const active = status === "ACTIVE";
  return <span className={"status-pill status-" + String(status).toLowerCase()}>
    {active ? "Đang áp dụng" : "Ngừng áp dụng"}
  </span>;
}

function PlanActions({ plan, statusSaving, onEdit, onChangeStatus }) {
  return <div className="membership-plan-actions">
    <button type="button" onClick={() => onEdit(plan)}>Sửa</button>
    <button type="button" disabled={statusSaving === plan.id} onClick={() => void onChangeStatus(plan)}>
      {statusSaving === plan.id ? "Đang lưu…" : plan.status === "ACTIVE" ? "Ngừng áp dụng" : "Kích hoạt"}
    </button>
  </div>;
}

export default function MembershipPlans({ token, role, onLogout, onNavigate }) {
  const admin = role === "ADMIN";
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [statusSaving, setStatusSaving] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [viewMode, setViewMode] = useState("cards");
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
    if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(form.price) || Number(form.price) < 0) return "Giá phải từ 0 trở lên và có tối đa 2 chữ số thập phân.";
    if (!Number.isInteger(Number(form.durationMonths)) || Number(form.durationMonths) <= 0) return "Thời hạn phải là số tháng nguyên dương.";
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
      price: Number(form.price),
      durationMonths: Number(form.durationMonths),
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
    <div className="layout membership-layout">
      <Sidebar role={role} activePage="membership-plans" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main membership-main">
        <h1>Gói hội viên</h1>
        <p className="subtitle">{admin
          ? "Tạo, chỉnh sửa, kích hoạt và tắt các gói hội viên."
          : "Danh sách gói hội viên đang áp dụng tại Sport Center."}</p>
        {notice && <div className="banner success" role="status">{notice}</div>}
        {error && <div className="banner error" role="alert">{error}</div>}

        <div className="member-list-toolbar">
          <span>{plans.length} gói hội viên</span>
          <div className="membership-view-switcher" role="group" aria-label="Kiểu hiển thị gói hội viên">
            <button type="button" aria-pressed={viewMode === "cards"} className={viewMode === "cards" ? "is-selected" : ""}
              onClick={() => setViewMode("cards")}>Thẻ</button>
            <button type="button" aria-pressed={viewMode === "list"} className={viewMode === "list" ? "is-selected" : ""}
              onClick={() => setViewMode("list")}>Danh sách</button>
          </div>
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
                <label className="form-label" htmlFor="planPrice">Giá</label>
                <input className="form-control" id="planPrice" type="number" min="0" step="0.01" required value={form.price}
                  onChange={(event) => setForm({ ...form, price: event.target.value })} />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="planDuration">Thời hạn (tháng)</label>
                <input className="form-control" id="planDuration" type="number" min="1" step="1" required value={form.durationMonths}
                  onChange={(event) => setForm({ ...form, durationMonths: event.target.value })} />
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
                <label className="form-check-label" htmlFor="allowPersonal">Có giáo án tập luyện cá nhân (không gồm buổi PT với huấn luyện viên)</label>
              </div>
              <div className="col-md-6 form-check ms-2">
                <input className="form-check-input" id="allowOneOnOne" type="checkbox" checked={form.allowOneOnOneCoaching}
                  onChange={(event) => setForm({ ...form, allowOneOnOneCoaching: event.target.checked })} />
                <label className="form-check-label" htmlFor="allowOneOnOne">Được đặt buổi PT 1-1 với huấn luyện viên</label>
              </div>
              {!form.allowOneOnOneCoaching && Number(form.personalTrainingDiscountPercent) > 0 && <p className="col-12 text-warning mb-0" role="note">
                Gói không được đặt buổi PT 1-1 nên ưu đãi huấn luyện cá nhân {form.personalTrainingDiscountPercent}% sẽ không được dùng.</p>}
            </div>
            <div className="d-flex gap-2 mt-3">
              <button type="submit" disabled={saving}>{saving ? "Đang lưu…" : "Lưu gói"}</button>
              <button type="button" onClick={() => setFormOpen(false)} disabled={saving}>Hủy</button>
            </div>
          </form>
        )}

        {loading ? <p role="status">Đang tải danh sách gói hội viên…</p>
          : plans.length === 0 ? <p className="membership-empty-state">Chưa có gói hội viên nào.</p>
            : viewMode === "cards" ? <div className="membership-plan-grid">
              {plans.map((plan) => (
                <article className="membership-plan-card" key={plan.id}>
                  <div className="membership-plan-card-heading">
                    <div><h2>{plan.name}</h2><PlanStatus status={plan.status} /></div>
                    <strong className="membership-plan-price">{formatPrice(plan.price)}</strong>
                  </div>
                  <p className="membership-plan-description">{plan.description || "Chưa có mô tả."}</p>
                  <p className="membership-plan-duration">Thời hạn <strong>{plan.durationMonths} tháng</strong></p>
                  <dl className="membership-plan-details">
                    <div><dt>Ưu đãi khóa học</dt><dd>{plan.courseDiscountPercent}%</dd></div>
                    <div><dt>Ưu đãi huấn luyện cá nhân</dt><dd>{plan.personalTrainingDiscountPercent}%</dd></div>
                    <div><dt>Giáo án cá nhân</dt><dd>{plan.allowPersonalTrainingPlan ? "Được sử dụng" : "Không bao gồm"}</dd></div>
                    <div><dt>Huấn luyện 1-1</dt><dd>{plan.allowOneOnOneCoaching ? "Được đăng ký" : "Không bao gồm"}</dd></div>
                  </dl>
                  {admin && <PlanActions plan={plan} statusSaving={statusSaving} onEdit={startEdit} onChangeStatus={changeStatus} />}
                </article>
              ))}
            </div> : <div className="queue-table-wrap">
              <table className="queue-table">
                <thead><tr>
                  <th>Tên gói</th><th>Giá</th><th>Thời hạn</th><th>Ưu đãi khóa học</th><th>Ưu đãi huấn luyện cá nhân</th><th>Giáo án cá nhân</th><th>Huấn luyện 1-1</th><th>Trạng thái</th>{admin && <th>Thao tác</th>}
                </tr></thead>
                <tbody>
                  {plans.map((plan) => (
                    <tr key={plan.id}>
                      <td><strong>{plan.name}</strong><br /><small>{plan.description || "Chưa có mô tả."}</small></td>
                      <td>{formatPrice(plan.price)}</td>
                      <td>{plan.durationMonths} tháng</td>
                      <td>{plan.courseDiscountPercent}%</td>
                      <td>{plan.personalTrainingDiscountPercent}%</td>
                      <td>{plan.allowPersonalTrainingPlan ? "Có" : "Không"}</td>
                      <td>{plan.allowOneOnOneCoaching ? "Có" : "Không"}</td>
                      <td><PlanStatus status={plan.status} /></td>
                      {admin && <td><PlanActions plan={plan} statusSaving={statusSaving} onEdit={startEdit} onChangeStatus={changeStatus} /></td>}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>}
      </main>
    </div>
  );
}
