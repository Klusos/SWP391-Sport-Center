import { useCallback, useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import { availableUpgradeTargets, upgradePathRequestError } from "../membershipPlanUpgrade";
import "./MembershipPlans.css";
import {
  createMembershipPlanApi,
  getMembershipPlansApi,
  getMembershipPlanUpgradePathsApi,
  addMembershipPlanUpgradePathApi,
  removeMembershipPlanUpgradePathApi,
  getPublicMembershipPlansApi,
  updateMembershipPlanApi,
  updateMembershipPlanStatusApi,
} from "../api";

const EMPTY_FORM = {
  name: "",
  description: "",
  monthlyPrice: "",
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
  const [upgradePaths, setUpgradePaths] = useState([]);
  const [upgradeTargetByPlan, setUpgradeTargetByPlan] = useState({});
  const [upgradeBusy, setUpgradeBusy] = useState(false);
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
      if (admin) {
        const [planRows, pathRows] = await Promise.all([
          getMembershipPlansApi(token), getMembershipPlanUpgradePathsApi(token),
        ]);
        setPlans(Array.isArray(planRows) ? planRows : []);
        setUpgradePaths(Array.isArray(pathRows) ? pathRows : []);
      } else {
        const rows = await getPublicMembershipPlansApi();
        setPlans(Array.isArray(rows) ? rows : []);
        setUpgradePaths([]);
      }
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

  async function addUpgradeTarget(source, targetId) {
    if (!targetId || upgradeBusy) return;
    setUpgradeBusy(true); setError(""); setNotice("");
    try {
      await addMembershipPlanUpgradePathApi(token, source.id, Number(targetId));
      setUpgradeTargetByPlan((current) => ({ ...current, [source.id]: "" }));
      setNotice("Đã thêm hướng nâng cấp.");
      await load();
    } catch (requestError) {
      const message = upgradePathRequestError(requestError.status);
      setError(message || requestError.message || "Không thể thêm hướng nâng cấp.");
    } finally { setUpgradeBusy(false); }
  }

  async function removeUpgradeTarget(path) {
    if (upgradeBusy || !window.confirm(`Xóa hướng nâng cấp ${path.fromPlanName} → ${path.toPlanName}?`)) return;
    setUpgradeBusy(true); setError(""); setNotice("");
    try {
      await removeMembershipPlanUpgradePathApi(token, path.id);
      setNotice("Đã xóa hướng nâng cấp. Membership và lịch sử thanh toán không bị ảnh hưởng.");
      await load();
    } catch (requestError) {
      const message = upgradePathRequestError(requestError.status);
      setError(message || requestError.message || "Không thể xóa hướng nâng cấp.");
      if (requestError.status === 404) await load();
    } finally { setUpgradeBusy(false); }
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
                  <th>Tên gói</th><th>Giá / tháng</th><th>Các gói nâng cấp</th><th>Ưu đãi</th><th>Trạng thái</th>{admin && <th>Thao tác</th>}
                </tr></thead>
                <tbody>
                  {plans.map((plan) => (
                    <tr key={plan.id}>
                      <td><strong>{plan.name}</strong><br /><small>{plan.description || "Chưa có mô tả."}</small></td>
                      <td>{plan.monthlyPrice == null ? "Cần cấu hình giá tháng" : formatPrice(plan.monthlyPrice)}</td>
                      <td>{admin ? <div className="membership-upgrade-paths">
                        {upgradePaths.filter((path) => Number(path.fromPlanId) === Number(plan.id)).map((path) => <div key={path.id} className="membership-upgrade-path">
                          <span>{path.toPlanName} · {formatPrice(path.toMonthlyPrice)}</span>
                          <button type="button" disabled={upgradeBusy} onClick={() => void removeUpgradeTarget(path)}>Xóa</button>
                        </div>)}
                        {plan.status === "ACTIVE" && <div className="membership-upgrade-add">
                          <label className="visually-hidden" htmlFor={`upgrade-target-${plan.id}`}>Gói đích cho {plan.name}</label>
                          <select id={`upgrade-target-${plan.id}`} value={upgradeTargetByPlan[plan.id] || ""} disabled={upgradeBusy}
                            onChange={(event) => setUpgradeTargetByPlan((current) => ({ ...current, [plan.id]: event.target.value }))}>
                            <option value="">Chọn gói đích</option>
                            {availableUpgradeTargets(plan, plans, upgradePaths)
                              .map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.name} · {formatPrice(candidate.monthlyPrice)}</option>)}
                          </select>
                          <button type="button" disabled={upgradeBusy || !upgradeTargetByPlan[plan.id]}
                            onClick={() => void addUpgradeTarget(plan, upgradeTargetByPlan[plan.id])}>Thêm hướng</button>
                        </div>}
                        {!upgradePaths.some((path) => Number(path.fromPlanId) === Number(plan.id)) && <small>Chưa có hướng nâng cấp.</small>}
                      </div> : "—"}</td>
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
