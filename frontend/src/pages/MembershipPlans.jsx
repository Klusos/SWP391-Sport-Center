import { useCallback, useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  createMembershipPlanApi,
  getCurrentUserApi,
  getMembershipPlansApi,
  getPublicMembershipPlansApi,
  updateMembershipPlanApi,
  updateMembershipPlanStatusApi,
} from "../api";
import "./MembershipPlans.css";

/* ── SVG Icons ── */
const Ic = ({ d, size = 18, className = "" }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
    className={className}
    style={{ flexShrink: 0 }}
  >
    {d.split(" M").map((p, i) => (
      <path key={i} d={(i ? "M" : "") + p} />
    ))}
  </svg>
);

const ICONS = {
  bolt: "M13 2L3 14h9l-1 8 10-12h-9l1-8",
  bell: "M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9 M13.73 21a2 2 0 0 1-3.46 0",
  user: "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8",
  refresh: "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16 M8 16H3v5",
  pos: "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8 M12 17.5v-11",
  grid: "M3 3h7v7H3z M14 3h7v7h-7z M14 14h7v7h-7z M3 14h7v7H3z",
  folderOff: "M2 2l20 20 M8.5 8.5L4 8.5C2.9 8.5 2 9.4 2 10.5L2 18.5C2 19.6 2.9 20.5 4 20.5L18.5 20.5 M22 17.5L22 7.5C22 6.4 21.1 5.5 20 5.5L11 5.5L9 3.5L5.5 3.5",
  checkCircle: "M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4L12 14.01l-3-3",
  minusCircle: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M8 12h8",
  fitness: "M6.5 6.5l11 11 M21 7l-4-4 M3 17l4 4 M18 10l-4-4 M10 18l-4-4",
  star: "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z",
  award: "M12 15a6 6 0 1 0 0-12 6 6 0 0 0 0 12 M8.2 13.9L7 22l5-3 5 3-1.2-8.1",
  lock: "M19 11H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2z M7 11V7a5 5 0 0 1 10 0v4",
  verified: "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
  pause: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M10 15V9 M14 15V9",
  info: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M12 16v-4 M12 8h.01",
  plus: "M12 5v14 M5 12h14",
  close: "M18 6L6 18 M6 6l12 12",
  arrowBack: "M19 12H5 M12 19l-7-7 7-7",
  edit: "M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7 M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z",
};

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

function formatVnd(value) {
  return Number(value || 0).toLocaleString("vi-VN") + " đ";
}

function getPlanCode(plan) {
  const n = (plan.name || "").toUpperCase();
  const m = String(plan.durationMonths || 1).padStart(2, "0");
  if (n.includes("STANDARD")) return `STD-${m}M`;
  if (n.includes("PREMIUM")) return `PRM-${m}M`;
  if (n.includes("PLATINUM")) return `PLT-VIP`;
  return `PKG-${String(plan.id).padStart(2, "0")}`;
}

const initialsOf = (name) => {
  if (!name) return "LT";
  const parts = name.trim().split(/\s+/);
  return parts.slice(-2).map((w) => w[0]).join("").toUpperCase();
};

export default function MembershipPlans({ token, role, onLogout, onNavigate }) {
  const isAdmin = role === "ADMIN";
  const [plans, setPlans] = useState([]);
  const [currentUser, setCurrentUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [statusSaving, setStatusSaving] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [viewMode, setViewMode] = useState("cards"); // 'cards' | 'empty'
  const [formOpen, setFormOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);

  /* Load current user for topbar profile */
  useEffect(() => {
    if (token) {
      getCurrentUserApi(token)
        .then((u) => setCurrentUser(u))
        .catch(() => setCurrentUser(null));
    }
  }, [token]);

  /* Load plans */
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const rows = isAdmin ? await getMembershipPlansApi(token) : await getPublicMembershipPlansApi();
      setPlans(Array.isArray(rows) ? rows : []);
    } catch (requestError) {
      setError(requestError.message || "Không thể tải danh sách gói hội viên.");
    } finally {
      setLoading(false);
    }
  }, [isAdmin, token]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleRefresh() {
    setRefreshing(true);
    await load();
    setTimeout(() => setRefreshing(false), 500);
  }

  function startCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setError("");
    setNotice("");
    setFormOpen(true);
  }

  function startEdit(plan) {
    setEditingId(plan.id);
    setForm(formFromPlan(plan));
    setError("");
    setNotice("");
    setFormOpen(true);
  }

  function validateForm() {
    if (!form.name.trim()) return "Vui lòng nhập tên gói.";
    if (form.name.trim().length > 100) return "Tên gói không được vượt quá 100 ký tự.";
    if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(form.price) || Number(form.price) < 0)
      return "Giá phải từ 0 trở lên và có tối đa 2 chữ số thập phân.";
    if (!Number.isInteger(Number(form.durationMonths)) || Number(form.durationMonths) <= 0)
      return "Thời hạn phải là số tháng nguyên dương.";
    for (const field of ["courseDiscountPercent", "personalTrainingDiscountPercent"]) {
      const rawValue = form[field];
      const value = Number(rawValue);
      if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(rawValue) || !Number.isFinite(value) || value < 0 || value > 100)
        return "Mức ưu đãi phải từ 0 đến 100%, tối đa 2 chữ số thập phân.";
    }
    return "";
  }

  async function savePlan(e) {
    e.preventDefault();
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
        setNotice("Đã tạo gói hội viên mới thành công.");
      } else {
        const current = plans.find((p) => p.id === editingId);
        await updateMembershipPlanApi(token, editingId, {
          ...payload,
          status: current?.status || "ACTIVE",
        });
        setNotice("Đã cập nhật gói hội viên thành công.");
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
    if (!window.confirm(`Bạn có chắc muốn ${action} gói "${plan.name}"?`)) return;

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

  const displayedPlans = plans;
  const activeCount = plans.filter((p) => p.status === "ACTIVE").length;

  return (
    <div className="layout">
      <Sidebar role={role} activePage="membership-plans" onNavigate={onNavigate} onLogout={onLogout} />

      <main className="main mp-main">
        {/* ── Top Bar ── */}
        <header className="rc-topbar">
          <nav className="rc-crumb" aria-label="Breadcrumb">
            <span style={{ cursor: "pointer" }} onClick={() => onNavigate("dashboard")}>SPORTS CENTER</span>
            <i>›</i>
            <span style={{ cursor: "pointer" }} onClick={() => onNavigate("dashboard")}>{isAdmin ? "QUẢN TRỊ" : "LỄ TÂN"}</span>
            <i>›</i>
            <b>GÓI HỘI VIÊN</b>
          </nav>

          <div className="rc-user">
            <span className="rc-avatar">{initialsOf(currentUser?.name)}</span>
            <div>
              <b>{currentUser?.name || "Lê Minh Trang"}</b>
              <small>{isAdmin ? "Quản trị viên" : "Quầy lễ tân 01"}</small>
            </div>
          </div>
        </header>

        {/* ── Content Canvas ── */}
        <div className="mp-content">
          {/* Header & Switcher */}
          <div className="mp-header">
            <div className="mp-title-block">
              <h1>Gói hội viên</h1>
              <p>Danh sách gói hội viên đang áp dụng tại Sport Center.</p>
            </div>

            {/* View State Switcher */}
            <div className="mp-view-switcher">
              <button
                type="button"
                className={`mp-switch-btn ${viewMode === "cards" ? "active" : ""}`}
                onClick={() => setViewMode("cards")}
              >
                <Ic d={ICONS.grid} size={15} />
                <span>Đang áp dụng</span>
              </button>
              <button
                type="button"
                className={`mp-switch-btn ${viewMode === "empty" ? "active" : ""}`}
                onClick={() => setViewMode("empty")}
              >
                <Ic d={ICONS.folderOff} size={15} />
                <span>Xem trạng thái trống</span>
              </button>
            </div>
          </div>

          {/* Action Toolbar */}
          <div className="mp-toolbar">
            <div className="mp-count-group">
              <span className="mp-count-label">
                {viewMode === "empty" ? "0" : displayedPlans.length} gói hội viên
              </span>
              <span className="mp-badge-readonly">{isAdmin ? "Admin mode" : "Read-only view"}</span>
            </div>

            <div className="mp-actions-group">
              <button
                type="button"
                className="mp-btn-refresh"
                onClick={handleRefresh}
                disabled={loading}
              >
                <Ic
                  d={ICONS.refresh}
                  size={16}
                  className={refreshing ? "rotate-spin" : ""}
                />
                <span>Làm mới</span>
              </button>

              <button
                type="button"
                className="mp-btn-sell"
                onClick={() => onNavigate("membership-sales")}
              >
                <Ic d={ICONS.pos} size={18} />
                <span>Bán gói hội viên</span>
              </button>

              {isAdmin && (
                <button type="button" className="mp-btn-create" onClick={startCreate}>
                  <Ic d={ICONS.plus} size={16} />
                  <span>Tạo gói hội viên</span>
                </button>
              )}
            </div>
          </div>

          {/* Alerts */}
          {notice && (
            <div className="mp-alert mp-alert--success" role="status">
              <Ic d={ICONS.checkCircle} size={18} />
              <span>{notice}</span>
            </div>
          )}
          {error && (
            <div className="mp-alert mp-alert--error" role="alert">
              <Ic d={ICONS.minusCircle} size={18} />
              <span>{error}</span>
            </div>
          )}

          {/* Main View: Cards or Empty */}
          {loading ? (
            <div className="mp-empty-state">
              <div className="mp-empty-icon">
                <Ic d={ICONS.refresh} size={30} className="rotate-spin" />
              </div>
              <h2>Đang tải danh sách gói hội viên…</h2>
              <p>Vui lòng đợi giây lát trong khi hệ thống nạp dữ liệu gói dịch vụ.</p>
            </div>
          ) : viewMode === "empty" || displayedPlans.length === 0 ? (
            <div className="mp-empty-state">
              <div className="mp-empty-icon">
                <Ic d={ICONS.folderOff} size={32} />
              </div>
              <h2>Chưa có gói hội viên nào.</h2>
              <p>
                Hiện tại không có gói hội viên khả dụng cho quầy lễ tân hoặc hệ thống đang đồng bộ dữ
                liệu với ban quản trị.
              </p>
              <div className="mp-empty-actions">
                <button
                  type="button"
                  className="mp-btn-refresh"
                  onClick={() => setViewMode("cards")}
                >
                  <Ic d={ICONS.arrowBack} size={16} />
                  <span>Quay lại danh sách</span>
                </button>
                <button
                  type="button"
                  className="mp-btn-sell"
                  onClick={handleRefresh}
                >
                  <Ic d={ICONS.refresh} size={16} />
                  <span>Đồng bộ ngay</span>
                </button>
              </div>
            </div>
          ) : (
            <div className="mp-cards-grid">
              {displayedPlans.map((plan, idx) => {
                const isFeatured =
                  plan.name.toUpperCase().includes("PREMIUM") ||
                  (!displayedPlans.some((p) => p.name.toUpperCase().includes("PREMIUM")) && idx === 1);
                const isActive = plan.status === "ACTIVE";
                const isVip =
                  plan.name.toUpperCase().includes("VIP") ||
                  plan.name.toUpperCase().includes("PREMIUM") ||
                  plan.name.toUpperCase().includes("PLATINUM");

                let cardIcon = ICONS.fitness;
                if (plan.name.toUpperCase().includes("PREMIUM")) cardIcon = ICONS.star;
                else if (plan.name.toUpperCase().includes("PLATINUM")) cardIcon = ICONS.award;

                const code = getPlanCode(plan);

                return (
                  <div
                    key={plan.id}
                    className={`mp-card ${isFeatured ? "mp-card--featured" : ""} ${
                      !isActive ? "mp-card--inactive" : ""
                    }`}
                  >
                    {isFeatured && (
                      <div className="mp-popular-ribbon">Phổ biến nhất</div>
                    )}

                    <div>
                      {/* Top Row: Icon + Status Badge */}
                      <div className="mp-card-top">
                        <div className="mp-card-icon">
                          <Ic d={cardIcon} size={20} />
                        </div>
                        <span className={`mp-status-pill ${isActive ? "active" : "inactive"}`}>
                          <i />
                          {isActive ? "Đang áp dụng" : "Ngừng áp dụng"}
                        </span>
                      </div>

                      {/* Plan Title & VIP Tag */}
                      <div className="mp-card-title-row">
                        <h3 className="mp-plan-title">{plan.name}</h3>
                        {isVip && <span className="mp-tag-vip">VIP</span>}
                      </div>

                      {/* Description */}
                      <p className="mp-plan-desc">
                        {plan.description ||
                          (plan.name.toUpperCase().includes("STANDARD")
                            ? "Gói tập cơ bản dành cho cá nhân rèn luyện thể chất hàng ngày và trải nghiệm dịch vụ tiện ích tiêu chuẩn."
                            : plan.name.toUpperCase().includes("PREMIUM")
                            ? "Gói hội viên nâng cao bao gồm quyền sử dụng toàn bộ máy tập cao cấp, phòng xông hơi và các tiện ích mở rộng."
                            : plan.name.toUpperCase().includes("PLATINUM")
                            ? "Gói tập toàn diện không giới hạn thời gian tập luyện, hỗ trợ đầy đủ huấn luyện viên kèm sát và quyền lợi đối tác đặc quyền."
                            : "Gói tập luyện thể chất tiêu chuẩn tại Sports Center.")}
                      </p>

                      {/* Price Box */}
                      <div className="mp-price-box">
                        <div className="mp-price-row">
                          <span className="mp-price-amount">{formatVnd(plan.price)}</span>
                          <span className="mp-price-duration">/ {plan.durationMonths} tháng</span>
                        </div>
                        {isFeatured && (
                          <span className="mp-price-subnote">
                            Ưu đãi chọn chu kỳ 3 tháng: {formatVnd(Number(plan.price) * 0.8)}/tháng
                          </span>
                        )}
                      </div>

                      {/* Perks Section */}
                      <div className="mp-perks-section">
                        <p className="mp-perks-header">Quyền lợi thành viên</p>
                        <ul className="mp-perks-list">
                          <li className="mp-perk-item">
                            <Ic
                              d={ICONS.checkCircle}
                              size={18}
                              className="mp-perk-icon checked"
                            />
                            <span>
                              Ưu đãi khóa học <strong>{plan.courseDiscountPercent}%</strong>
                            </span>
                          </li>

                          <li
                            className={`mp-perk-item ${
                              Number(plan.personalTrainingDiscountPercent) === 0 ? "disabled-text" : ""
                            }`}
                          >
                            <Ic
                              d={
                                Number(plan.personalTrainingDiscountPercent) > 0
                                  ? ICONS.checkCircle
                                  : ICONS.minusCircle
                              }
                              size={18}
                              className={`mp-perk-icon ${
                                Number(plan.personalTrainingDiscountPercent) > 0
                                  ? "checked"
                                  : "disabled"
                              }`}
                            />
                            <span>
                              Ưu đãi huấn luyện cá nhân{" "}
                              <strong>{plan.personalTrainingDiscountPercent}%</strong>
                            </span>
                          </li>

                          <li className="mp-perk-item">
                            <Ic
                              d={ICONS.checkCircle}
                              size={18}
                              className="mp-perk-icon checked"
                            />
                            <span>
                              {plan.name.toUpperCase().includes("PLATINUM")
                                ? "Nước uống điện giải không giới hạn & khăn tập cao cấp mỗi buổi"
                                : plan.name.toUpperCase().includes("PREMIUM")
                                ? "Miễn phí xông hơi đá muối & tủ đồ cá nhân riêng"
                                : plan.allowPersonalTrainingPlan
                                ? "Bao gồm gói huấn luyện cá nhân có huấn luyện viên"
                                : "Tự do sử dụng trang thiết bị cơ bản & tủ đồ thông thường"}
                            </span>
                          </li>

                          {plan.allowOneOnOneCoaching && (
                            <li className="mp-perk-item">
                              <Ic
                                d={ICONS.checkCircle}
                                size={18}
                                className="mp-perk-icon checked"
                              />
                              <span>Hỗ trợ huấn luyện 1 kèm 1 theo lộ trình thể chất</span>
                            </li>
                          )}
                        </ul>
                      </div>
                    </div>

                    {/* Card Footer Stamp */}
                    <div className="mp-card-footer">
                      <span className="mp-plan-code">Mã gói: {code}</span>

                      {isAdmin ? (
                        <div className="mp-admin-card-actions">
                          <button
                            type="button"
                            className="mp-btn-action-sm"
                            onClick={() => startEdit(plan)}
                          >
                            <Ic d={ICONS.edit} size={12} />
                            <span>Sửa</span>
                          </button>
                          <button
                            type="button"
                            className="mp-btn-action-sm"
                            disabled={statusSaving === plan.id}
                            onClick={() => changeStatus(plan)}
                          >
                            {statusSaving === plan.id
                              ? "…"
                              : isActive
                              ? "Ngừng"
                              : "Kích hoạt"}
                          </button>
                        </div>
                      ) : isFeatured ? (
                        <span className="mp-plan-stamp recommended">
                          <Ic d={ICONS.verified} size={14} />
                          <span>Khuyến dùng</span>
                        </span>
                      ) : !isActive ? (
                        <span className="mp-plan-stamp">
                          <Ic d={ICONS.pause} size={14} />
                          <span>Tạm dừng đăng ký</span>
                        </span>
                      ) : (
                        <span className="mp-plan-stamp">
                          <Ic d={ICONS.lock} size={14} />
                          <span>Chỉ xem</span>
                        </span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Informational Footnote */}
          <div className="mp-footnote">
            <Ic d={ICONS.info} size={20} className="mp-footnote-icon" />
            <div>
              <strong>Quyền hạn của Lễ tân:</strong> Bạn đang ở chế độ xem thông tin biểu phí và đặc
              quyền hội viên để tư vấn khách hàng. Việc bổ sung, điều chỉnh giá hoặc tắt kích hoạt
              gói được quản lý tập trung bởi Quản lý phòng Gym tại phân hệ{" "}
              <strong>Cấu hình Vận hành</strong>.
            </div>
          </div>
        </div>

        {/* ── Admin Create / Edit Modal ── */}
        {isAdmin && formOpen && (
          <div className="mp-modal-backdrop" onClick={() => setFormOpen(false)}>
            <div className="mp-modal-panel" onClick={(e) => e.stopPropagation()}>
              <div className="mp-modal-head">
                <h2>{editingId === null ? "Tạo gói hội viên mới" : "Chỉnh sửa gói hội viên"}</h2>
                <button
                  type="button"
                  className="mp-modal-close"
                  onClick={() => setFormOpen(false)}
                >
                  <Ic d={ICONS.close} size={18} />
                </button>
              </div>

              <form onSubmit={savePlan}>
                <div className="mp-form-grid">
                  <div className="mp-form-label">
                    <span>Tên gói *</span>
                    <input
                      className="mp-form-input"
                      required
                      maxLength={100}
                      value={form.name}
                      onChange={(e) => setForm({ ...form, name: e.target.value })}
                      placeholder="VD: PREMIUM, STANDARD..."
                    />
                  </div>

                  <div className="mp-form-label">
                    <span>Giá (VNĐ) *</span>
                    <input
                      className="mp-form-input"
                      type="number"
                      min="0"
                      step="0.01"
                      required
                      value={form.price}
                      onChange={(e) => setForm({ ...form, price: e.target.value })}
                      placeholder="1500000"
                    />
                  </div>

                  <div className="mp-form-label">
                    <span>Thời hạn (tháng) *</span>
                    <input
                      className="mp-form-input"
                      type="number"
                      min="1"
                      step="1"
                      required
                      value={form.durationMonths}
                      onChange={(e) => setForm({ ...form, durationMonths: e.target.value })}
                    />
                  </div>

                  <div className="mp-form-label">
                    <span>Ưu đãi khóa học (%)</span>
                    <input
                      className="mp-form-input"
                      type="number"
                      min="0"
                      max="100"
                      step="0.01"
                      value={form.courseDiscountPercent}
                      onChange={(e) => setForm({ ...form, courseDiscountPercent: e.target.value })}
                    />
                  </div>

                  <div className="mp-form-label">
                    <span>Ưu đãi PT cá nhân (%)</span>
                    <input
                      className="mp-form-input"
                      type="number"
                      min="0"
                      max="100"
                      step="0.01"
                      value={form.personalTrainingDiscountPercent}
                      onChange={(e) =>
                        setForm({ ...form, personalTrainingDiscountPercent: e.target.value })
                      }
                    />
                  </div>

                  <div className="mp-col-full mp-form-label">
                    <span>Mô tả gói</span>
                    <textarea
                      className="mp-form-textarea"
                      rows={3}
                      value={form.description}
                      onChange={(e) => setForm({ ...form, description: e.target.value })}
                      placeholder="Mô tả các tiện ích và đặc quyền của gói hội viên..."
                    />
                  </div>

                  <label className="mp-col-full mp-form-checkbox">
                    <input
                      type="checkbox"
                      checked={form.allowPersonalTrainingPlan}
                      onChange={(e) =>
                        setForm({ ...form, allowPersonalTrainingPlan: e.target.checked })
                      }
                    />
                    <span>Cho phép gói huấn luyện cá nhân</span>
                  </label>

                  <label className="mp-col-full mp-form-checkbox">
                    <input
                      type="checkbox"
                      checked={form.allowOneOnOneCoaching}
                      onChange={(e) =>
                        setForm({ ...form, allowOneOnOneCoaching: e.target.checked })
                      }
                    />
                    <span>Cho phép huấn luyện 1 kèm 1</span>
                  </label>
                </div>

                <div className="mp-modal-foot">
                  <button
                    type="button"
                    className="mp-btn-refresh"
                    onClick={() => setFormOpen(false)}
                    disabled={saving}
                  >
                    Hủy
                  </button>
                  <button type="submit" className="mp-btn-sell" disabled={saving}>
                    {saving ? "Đang lưu…" : editingId === null ? "Tạo gói" : "Lưu thay đổi"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
