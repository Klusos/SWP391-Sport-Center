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
  return new Intl.NumberFormat("vi-VN", { minimumFractionDigits: 0, maximumFractionDigits: 2 })
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
      setError(requestError.message || "Could not load membership plans.");
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
    if (!form.name.trim()) return "Plan name is required.";
    if (form.name.trim().length > 100) return "Plan name must be at most 100 characters.";
    if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(form.price) || Number(form.price) < 0) return "Price must be zero or greater and have at most two decimal places.";
    if (!Number.isInteger(Number(form.durationMonths)) || Number(form.durationMonths) <= 0) return "Duration must be a positive whole number of months.";
    for (const field of ["courseDiscountPercent", "personalTrainingDiscountPercent"]) {
      const rawValue = form[field];
      const value = Number(rawValue);
      if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(rawValue) || !Number.isFinite(value) || value < 0 || value > 100) return "Discounts must be between 0 and 100 with at most two decimal places.";
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
        setNotice("Membership plan created.");
      } else {
        const current = plans.find((plan) => plan.id === editingId);
        await updateMembershipPlanApi(token, editingId, { ...payload, status: current?.status || "ACTIVE" });
        setNotice("Membership plan updated.");
      }
      setFormOpen(false);
      setEditingId(null);
      setForm(EMPTY_FORM);
      await load();
    } catch (requestError) {
      setError(requestError.message || "Could not save the membership plan.");
    } finally {
      setSaving(false);
    }
  }

  async function changeStatus(plan) {
    const nextStatus = plan.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    const action = nextStatus === "INACTIVE" ? "deactivate" : "activate";
    if (!window.confirm("Are you sure you want to " + action + " " + plan.name + "?")) return;

    setStatusSaving(plan.id);
    setError("");
    setNotice("");
    try {
      await updateMembershipPlanStatusApi(token, plan.id, nextStatus);
      setNotice("Membership plan " + action + "d.");
      await load();
    } catch (requestError) {
      setError(requestError.message || "Could not change the plan status.");
    } finally {
      setStatusSaving(null);
    }
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="membership-plans" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main">
        <h1>Membership plans</h1>
        <p className="subtitle">{admin
          ? "Create, edit, activate, and deactivate the public membership catalog."
          : "Active membership plans available at Sport Center."}</p>
        {notice && <div className="banner success" role="status">{notice}</div>}
        {error && <div className="banner error" role="alert">{error}</div>}

        <div className="member-list-toolbar">
          <span>{plans.length} plan{plans.length === 1 ? "" : "s"}</span>
          <button type="button" onClick={() => void load()} disabled={loading}>
            {loading ? "Loading?" : "Refresh"}
          </button>
          {admin && <button type="button" onClick={startCreate}>Create plan</button>}
        </div>

        {admin && formOpen && (
          <form className="form-card" onSubmit={savePlan}>
            <h2>{editingId === null ? "Create membership plan" : "Edit membership plan"}</h2>
            <div className="row g-3">
              <div className="col-md-6">
                <label className="form-label" htmlFor="planName">Name</label>
                <input className="form-control" id="planName" maxLength="100" required value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })} />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="planPrice">Price</label>
                <input className="form-control" id="planPrice" type="number" min="0" step="0.01" required value={form.price}
                  onChange={(event) => setForm({ ...form, price: event.target.value })} />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="planDuration">Duration (months)</label>
                <input className="form-control" id="planDuration" type="number" min="1" step="1" required value={form.durationMonths}
                  onChange={(event) => setForm({ ...form, durationMonths: event.target.value })} />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="planCourseDiscount">Course discount (%)</label>
                <input className="form-control" id="planCourseDiscount" type="number" min="0" max="100" step="0.01" required value={form.courseDiscountPercent}
                  onChange={(event) => setForm({ ...form, courseDiscountPercent: event.target.value })} />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="planTrainingDiscount">Personal training discount (%)</label>
                <input className="form-control" id="planTrainingDiscount" type="number" min="0" max="100" step="0.01" required value={form.personalTrainingDiscountPercent}
                  onChange={(event) => setForm({ ...form, personalTrainingDiscountPercent: event.target.value })} />
              </div>
              <div className="col-12">
                <label className="form-label" htmlFor="planDescription">Description</label>
                <textarea className="form-control" id="planDescription" rows="3" value={form.description}
                  onChange={(event) => setForm({ ...form, description: event.target.value })} />
              </div>
              <div className="col-md-6 form-check ms-2">
                <input className="form-check-input" id="allowPersonal" type="checkbox" checked={form.allowPersonalTrainingPlan}
                  onChange={(event) => setForm({ ...form, allowPersonalTrainingPlan: event.target.checked })} />
                <label className="form-check-label" htmlFor="allowPersonal">Allows personal training plans</label>
              </div>
              <div className="col-md-6 form-check ms-2">
                <input className="form-check-input" id="allowOneOnOne" type="checkbox" checked={form.allowOneOnOneCoaching}
                  onChange={(event) => setForm({ ...form, allowOneOnOneCoaching: event.target.checked })} />
                <label className="form-check-label" htmlFor="allowOneOnOne">Allows one-on-one coaching</label>
              </div>
            </div>
            <div className="d-flex gap-2 mt-3">
              <button type="submit" disabled={saving}>{saving ? "Saving?" : "Save plan"}</button>
              <button type="button" onClick={() => setFormOpen(false)} disabled={saving}>Cancel</button>
            </div>
          </form>
        )}

        {loading ? <p role="status">Loading membership plans?</p>
          : plans.length === 0 ? <p>No membership plans are available.</p>
            : <div className="queue-table-wrap">
              <table className="queue-table">
                <thead><tr>
                  <th>Name</th><th>Price</th><th>Duration</th><th>Discounts</th><th>Status</th>{admin && <th>Actions</th>}
                </tr></thead>
                <tbody>
                  {plans.map((plan) => (
                    <tr key={plan.id}>
                      <td><strong>{plan.name}</strong><br /><small>{plan.description || "?"}</small></td>
                      <td>{formatPrice(plan.price)}</td>
                      <td>{plan.durationMonths} months</td>
                      <td>Course {plan.courseDiscountPercent}%<br />Training {plan.personalTrainingDiscountPercent}%</td>
                      <td><span className={"badge " + (plan.status === "ACTIVE" ? "text-bg-success" : "text-bg-secondary")}>{plan.status}</span></td>
                      {admin && <td>
                        <div className="d-flex flex-wrap gap-2">
                          <button type="button" onClick={() => startEdit(plan)}>Edit</button>
                          <button type="button" disabled={statusSaving === plan.id} onClick={() => void changeStatus(plan)}>
                            {statusSaving === plan.id ? "Saving?" : plan.status === "ACTIVE" ? "Deactivate" : "Activate"}
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
