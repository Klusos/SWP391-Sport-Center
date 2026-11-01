import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  createPaymentApi,
  getCurrentUserApi,
  getMembershipsByMemberApi,
  getMemberPaymentsApi,
  getPendingMembershipPaymentsApi,
  getPublicMembershipPlansApi,
  getUsersApi,
  processCounterPaymentApi,
} from "../api";
import "./ReceptionistMembershipSales.css";

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
  menu: "M3 12h18 M3 6h18 M3 18h18",
  bell: "M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9 M13.73 21a2 2 0 0 1-3.46 0",
  user: "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8",
  support: "M19 11a7 7 0 0 1-7 7m0 0a7 7 0 0 1-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 1 0 0-6 3 3 0 0 0 0 6",
  refresh: "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16 M8 16H3v5",
  help: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3 M12 17h.01",
  search: "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16 M21 21l-4.35-4.35",
  cancel: "M18 6L6 18 M6 6l12 12",
  check: "M20 6L9 17l-5-5",
  checkCircle: "M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4L12 14.01l-3-3",
  minus: "M5 12h14",
  mail: "M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z M22 6l-10 7L2 6",
  phone: "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.11 10.5 19.79 19.79 0 0 1 1.07 4.18 2 2 0 0 1 3.03 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L7.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z",
  verified: "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
  receipt: "M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z M16 8h-6a2 2 0 1 0 0 4h4a2 2 0 1 1 0 4H8 M12 17.5v-11",
  cart: "M9 20a1 1 0 1 0 0 2 1 1 0 0 0 0-2 M20 20a1 1 0 1 0 0 2 1 1 0 0 0 0-2 M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6",
  info: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M12 16v-4 M12 8h.01",
  payments: "M2 6h20v12H2z M6 10h.01 M10 10h.01 M14 10h.01 M2 14h20",
  visibility: "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6",
  contactless: "M19.07 4.93a10 10 0 0 1 0 14.14 M15.54 8.46a5 5 0 0 1 0 7.08 M12 12h.01",
  print: "M6 9V2h12v7 M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2 M6 14h12v8H6z",
  plusCircle: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M12 8v8 M8 12h8",
};

const rows = (value) => (Array.isArray(value) ? value : value?.content ?? []);
const money = (value) =>
  value == null ? "0 đ" : Number(value).toLocaleString("vi-VN") + " đ";

function initialsOf(name) {
  return (name || "?")
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((w) => w[0])
    .join("")
    .toUpperCase();
}

function formatDate(val) {
  if (!val) return "—";
  const d = new Date(val);
  return Number.isNaN(d.getTime()) ? val : d.toLocaleDateString("vi-VN");
}

export default function ReceptionistMembershipSales({ token, role, onLogout, onNavigate }) {
  const [members, setMembers] = useState([]);
  const [plans, setPlans] = useState([]);
  const [currentUser, setCurrentUser] = useState(null);
  const [memberId, setMemberId] = useState("");
  const [planId, setPlanId] = useState("");
  const [query, setQuery] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("CASH"); // CASH | POS
  const [membershipData, setMembershipData] = useState(null);
  const [paymentData, setPaymentData] = useState(null);
  const [pendingPayment, setPendingPayment] = useState(null);
  const [queue, setQueue] = useState(null);
  const [queuePage, setQueuePage] = useState(0);
  const [queueLoading, setQueueLoading] = useState(false);
  const queueRequest = useRef(0);
  const [historyVersion, setHistoryVersion] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [receiptModalOpen, setReceiptModalOpen] = useState(false);
  const [latestSuccessPayment, setLatestSuccessPayment] = useState(null);

  /* Load current user for topbar profile */
  useEffect(() => {
    if (token) {
      getCurrentUserApi(token)
        .then((u) => setCurrentUser(u))
        .catch(() => setCurrentUser(null));
    }
  }, [token]);

  /* Load pending queue */
  const loadQueue = useCallback(async () => {
    const requestId = ++queueRequest.current;
    setQueueLoading(true);
    try {
      const result = await getPendingMembershipPaymentsApi(token, queuePage);
      if (requestId !== queueRequest.current) return;
      if (queuePage > 0 && result.content.length === 0) {
        setQueuePage(queuePage - 1);
      } else {
        setQueue(result);
      }
    } catch (requestError) {
      if (requestId !== queueRequest.current) return;
      setQueue(null);
      setError(
        requestError.status === 401
          ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
          : requestError.status === 403
          ? "Bạn không có quyền xem hàng chờ thanh toán."
          : requestError.message
      );
    } finally {
      if (requestId === queueRequest.current) setQueueLoading(false);
    }
  }, [token, queuePage]);

  useEffect(() => {
    let current = true;
    Promise.resolve().then(() => current && loadQueue());
    return () => {
      current = false;
      queueRequest.current += 1;
    };
  }, [loadQueue]);

  function selectPayment(payment) {
    setQuery("");
    setMemberId(String(payment.memberId));
    setPlanId(String(payment.referenceId));
    setPendingPayment(payment);
    setNotice("");
    setError("");
  }

  /* Load members and active plans */
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [usersResult, plansResult] = await Promise.all([
        getUsersApi(token),
        getPublicMembershipPlansApi(),
      ]);
      const activeMembers = rows(usersResult).filter(
        (user) => user?.role === "MEMBER" && user?.status === "ACTIVE"
      );
      setMembers(activeMembers);

      const activePlans = rows(plansResult).filter((plan) => plan?.status === "ACTIVE");
      setPlans(activePlans);

      // Default select first member and first plan if available
      if (activeMembers.length > 0 && !memberId) {
        setMemberId(String(activeMembers[0].id));
      }
      if (activePlans.length > 0 && !planId) {
        // default select PREMIUM if exists, or second plan, or first
        const prm = activePlans.find((p) => p.name.toUpperCase().includes("PREMIUM"));
        setPlanId(String(prm ? prm.id : activePlans[0].id));
      }
    } catch (requestError) {
      setError(
        requestError.status === 401
          ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
          : requestError.status === 403
          ? "Tài khoản không có quyền hỗ trợ mua gói hội viên."
          : requestError.message || "Không tải được danh sách thành viên hoặc gói hội viên."
      );
    } finally {
      setLoading(false);
    }
  }, [token, memberId, planId]);

  useEffect(() => {
    let current = true;
    Promise.resolve().then(() => current && load());
    return () => {
      current = false;
    };
  }, [load]);

  /* Load member's active memberships and payment history */
  useEffect(() => {
    if (!memberId) return undefined;
    let current = true;
    Promise.all([
      getMembershipsByMemberApi(token, memberId),
      getMemberPaymentsApi(token, memberId),
    ])
      .then(([membershipResult, paymentResult]) => {
        if (!current) return;
        const memberPayments = rows(paymentResult).filter(
          (payment) => payment.purpose === "MEMBERSHIP"
        );
        setMembershipData({ memberId, rows: rows(membershipResult) });
        setPaymentData({ memberId, rows: memberPayments });
      })
      .catch((requestError) => {
        if (current) setError(requestError.message || "Không tải được thông tin gói hiện tại.");
      });
    return () => {
      current = false;
    };
  }, [memberId, token, historyVersion]);

  const visibleMembers = useMemo(() => {
    const term = query.trim().toLocaleLowerCase("vi");
    return members.filter(
      (m) =>
        !term ||
        [m.id, m.name, m.phone, m.email].some((val) =>
          String(val ?? "").toLocaleLowerCase("vi").includes(term)
        )
    );
  }, [members, query]);

  const selectedMember = members.find((m) => String(m.id) === memberId) || null;
  const selectedPlan =
    plans.find((p) => String(p.id) === (pendingPayment ? String(pendingPayment.referenceId) : planId)) ||
    null;

  /* Complete counter payment directly or confirm pending payment */
  async function handleConfirmPayment() {
    if (saving || !selectedMember || !selectedPlan) return;
    setSaving(true);
    setError("");
    setNotice("");

    try {
      let targetPayment = pendingPayment;

      // 1. If not yet created, create counter payment first
      if (!targetPayment) {
        targetPayment = await createPaymentApi(token, {
          memberId: Number(selectedMember.id),
          method: "COUNTER",
          purpose: "MEMBERSHIP",
          referenceId: Number(selectedPlan.id),
          autoRenew: false,
        });
      }

      // 2. Immediately process counter payment confirmation
      const result = await processCounterPaymentApi(
        token,
        targetPayment.id,
        true,
        `Front desk payment confirmed via ${paymentMethod === "CASH" ? "Cash" : "POS/Card"}`
      );

      setLatestSuccessPayment({
        id: result.id,
        member: selectedMember,
        plan: selectedPlan,
        amount: result.amount || selectedPlan.price,
        time: new Date().toLocaleString("vi-VN"),
        staff: currentUser?.name || "Lê Minh Trang (Quầy 01)",
      });

      setPendingPayment(null);
      setNotice(`Thanh toán #${result.id} thành công! Gói hội viên đã được kích hoạt.`);
      setHistoryVersion((v) => v + 1);
      await loadQueue();
      setReceiptModalOpen(true);
    } catch (requestError) {
      setError(
        requestError.status === 401
          ? "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
          : requestError.status === 403
          ? "Chỉ nhân viên được phép xử lý thanh toán tại quầy."
          : requestError.message || "Không thể xử lý thanh toán."
      );
    } finally {
      setSaving(false);
    }
  }

  /* Cancel a pending payment */
  async function handleCancelPayment(paymentId) {
    if (!window.confirm(`Bạn có chắc muốn hủy hóa đơn #${paymentId}?`)) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await processCounterPaymentApi(
        token,
        paymentId,
        false,
        "Front desk payment cancelled by receptionist"
      );
      if (pendingPayment?.id === paymentId) {
        setPendingPayment(null);
      }
      setNotice(`Đã hủy hóa đơn #${paymentId}.`);
      setHistoryVersion((v) => v + 1);
      await loadQueue();
    } catch (requestError) {
      setError(requestError.message || "Không thể hủy thanh toán.");
    } finally {
      setSaving(false);
    }
  }

  function startAnother() {
    setReceiptModalOpen(false);
    setPendingPayment(null);
    setNotice("");
    setError("");
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="membership-sales" onNavigate={onNavigate} onLogout={onLogout} />

      <main className="main rms-main">
        {/* ── Top Bar ── */}
        <header className="rc-topbar">
          <nav className="rc-crumb" aria-label="Breadcrumb">
            <span style={{ cursor: "pointer" }} onClick={() => onNavigate("dashboard")}>SPORTS CENTER</span>
            <i>›</i>
            <span style={{ cursor: "pointer" }} onClick={() => onNavigate("dashboard")}>LỄ TÂN</span>
            <i>›</i>
            <b>BÁN GÓI HỘI VIÊN</b>
          </nav>

          <div className="rc-user">
            <span className="rc-avatar">{initialsOf(currentUser?.name)}</span>
            <div>
              <b>{currentUser?.name || "Lê Minh Trang"}</b>
              <small>Quầy lễ tân 01</small>
            </div>
          </div>
        </header>

        {/* ── Main Workspace Content ── */}
        <div className="rms-content">
          {/* Header Title & Secondary Actions */}
          <div className="rms-header">
            <div className="rms-title-block">
              <h1>Bán gói hội viên</h1>
              <p>Thanh toán tại quầy cho thành viên.</p>
            </div>

            <div className="rms-header-actions">
              <button
                type="button"
                className="rms-btn-ghost"
                onClick={() => {
                  void load();
                  void loadQueue();
                  setHistoryVersion((v) => v + 1);
                }}
                disabled={loading || saving || queueLoading}
              >
                <Ic d={ICONS.refresh} size={15} />
                <span>Làm mới</span>
              </button>
              <button type="button" className="rms-btn-ghost">
                <Ic d={ICONS.help} size={15} />
                <span>Hướng dẫn thu ngân</span>
              </button>
            </div>
          </div>

          {/* Alerts */}
          {error && (
            <div className="rms-alert error" role="alert">
              <Ic d={ICONS.cancel} size={16} />
              <span>{error}</span>
            </div>
          )}
          {notice && (
            <div className="rms-alert success" role="status">
              <Ic d={ICONS.checkCircle} size={16} />
              <span>{notice}</span>
            </div>
          )}

          {/* 2-Column Main Workspace */}
          <div className="rms-grid">
            {/* ════ LEFT COLUMN: 3 Execution Steps ════ */}
            <div className="rms-flow-col">
              {/* ── BƯỚC 1: Chọn thành viên ── */}
              <section className="rms-card">
                <div className="rms-card-head">
                  <div className="rms-card-title-group">
                    <span className="rms-step-num">1</span>
                    <h2>1. Chọn thành viên</h2>
                  </div>
                  <span className="rms-badge-required">Bước bắt buộc</span>
                </div>

                {/* Search Bar */}
                <div className="rms-search-wrap">
                  <Ic d={ICONS.search} size={18} className="search-ic" />
                  <input
                    className="rms-search-input"
                    type="text"
                    placeholder="Tìm theo tên, SĐT hoặc mã thành viên..."
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                  />
                  {query && (
                    <button
                      type="button"
                      className="rms-search-clear"
                      onClick={() => setQuery("")}
                      title="Xóa tìm kiếm"
                    >
                      <Ic d={ICONS.cancel} size={16} />
                    </button>
                  )}
                </div>

                {/* Member selection list */}
                <div className="rms-member-list">
                  {visibleMembers.length === 0 ? (
                    <p style={{ padding: "16px 0", color: "#64748b", textAlign: "center" }}>
                      Không tìm thấy thành viên phù hợp.
                    </p>
                  ) : (
                    visibleMembers.slice(0, 6).map((m) => {
                      const isSelected = String(m.id) === memberId;
                      return (
                        <div
                          key={m.id}
                          className={`rms-member-item ${isSelected ? "selected" : ""}`}
                          onClick={() => {
                            setMemberId(String(m.id));
                            setPendingPayment(null);
                          }}
                        >
                          <div className="rms-member-left">
                            <div className="rms-radio-indicator">
                              {isSelected && <Ic d={ICONS.check} size={14} />}
                            </div>

                            <div className="rms-member-avatar">{initialsOf(m.name)}</div>

                            <div className="rms-member-details">
                              <div className="rms-member-name-row">
                                <span className="rms-member-name">{m.name}</span>
                                <span className="rms-member-id">#{m.id}</span>
                                <span className="rms-badge-active">HOẠT ĐỘNG</span>
                              </div>
                              <div className="rms-member-contact">
                                {m.email && (
                                  <span className="rms-contact-item">
                                    <Ic d={ICONS.mail} size={12} />
                                    {m.email}
                                  </span>
                                )}
                                {m.email && m.phone && <span>•</span>}
                                {m.phone && (
                                  <span className="rms-contact-item">
                                    <Ic d={ICONS.phone} size={12} />
                                    {m.phone}
                                  </span>
                                )}
                              </div>
                            </div>
                          </div>

                          <div className="rms-member-right">
                            {isSelected ? (
                              <span className="rms-badge-selected">Đã chọn</span>
                            ) : (
                              <span className="rms-text-choose">Chọn thành viên</span>
                            )}
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </section>

              {/* ── BƯỚC 2: Chọn gói hội viên ── */}
              <section className="rms-card">
                <div className="rms-card-head">
                  <div className="rms-card-title-group">
                    <span className="rms-step-num">2</span>
                    <h2>2. Chọn gói hội viên</h2>
                  </div>
                  <small>Bấm chọn 1 gói để áp dụng</small>
                </div>

                <div className="rms-plans-grid">
                  {plans.map((p, idx) => {
                    const isSelected = String(p.id) === planId;
                    const isPopular =
                      p.name.toUpperCase().includes("PREMIUM") ||
                      (!plans.some((x) => x.name.toUpperCase().includes("PREMIUM")) && idx === 1);

                    return (
                      <div
                        key={p.id}
                        className={`rms-plan-card ${isSelected ? "selected" : ""}`}
                        onClick={() => setPlanId(String(p.id))}
                      >
                        {isPopular && <div className="rms-ribbon-popular">Phổ biến nhất</div>}

                        <div>
                          <div className="rms-plan-head">
                            <span className="rms-plan-name">GÓI {p.name}</span>
                            <div className="rms-plan-check">
                              {isSelected && <Ic d={ICONS.check} size={13} />}
                            </div>
                          </div>

                          <div className="rms-plan-price-row">
                            <span className="rms-plan-price">{money(p.price)}</span>
                            <span className="rms-plan-dur">/ {p.durationMonths} tháng</span>
                          </div>

                          {isPopular && (
                            <div className="rms-plan-recommendation">
                              Khuyến nghị 3 tháng: {money(Number(p.price) * 0.8)}/tháng
                            </div>
                          )}

                          <ul className="rms-plan-perks">
                            <li>
                              <Ic d={ICONS.checkCircle} size={15} className="rms-perk-ic" />
                              <span>
                                Ưu đãi khóa học <strong>{p.courseDiscountPercent}%</strong>
                              </span>
                            </li>
                            <li
                              className={
                                Number(p.personalTrainingDiscountPercent) === 0 ? "disabled" : ""
                              }
                            >
                              <Ic
                                d={
                                  Number(p.personalTrainingDiscountPercent) > 0
                                    ? ICONS.checkCircle
                                    : ICONS.minus
                                }
                                size={15}
                                className={`rms-perk-ic ${
                                  Number(p.personalTrainingDiscountPercent) === 0 ? "disabled" : ""
                                }`}
                              />
                              <span>
                                Ưu đãi HLV cá nhân{" "}
                                <strong>{p.personalTrainingDiscountPercent}%</strong>
                              </span>
                            </li>
                          </ul>
                        </div>

                        <div className="rms-plan-select-status">
                          <span>{isSelected ? "Đang chọn gói này" : `Chọn gói ${p.name}`}</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </section>

              {/* ── BƯỚC 3: Thanh toán tại quầy ── */}
              <section className="rms-card">
                <div className="rms-card-head">
                  <div className="rms-card-title-group">
                    <span className="rms-step-num">3</span>
                    <h2>3. Thanh toán tại quầy</h2>
                  </div>
                  <span className="rms-badge-ready">
                    <Ic d={ICONS.verified} size={16} />
                    Sẵn sàng ghi nhận
                  </span>
                </div>

                <div className="rms-payment-summary-grid">
                  <div>
                    <div className="rms-summary-block-label">Thành viên nhận gói:</div>
                    <div className="rms-summary-block-val">
                      {selectedMember?.name || "Chưa chọn"} (#{selectedMember?.id || "—"})
                    </div>
                    <div className="rms-summary-block-sub">{selectedMember?.phone || "—"}</div>
                  </div>

                  <div>
                    <div className="rms-summary-block-label">Gói đã chọn & Thời hạn:</div>
                    <div className="rms-summary-block-val blue">
                      {selectedPlan?.name || "Chưa chọn"} ({selectedPlan?.durationMonths || 1} tháng)
                    </div>
                    <div className="rms-summary-block-sub">
                      {(selectedPlan?.durationMonths || 1) * 30} ngày kể từ ngày thanh toán
                    </div>
                  </div>

                  <div>
                    <div className="rms-summary-block-label">Hình thức giao dịch:</div>
                    <div className="rms-payment-method-radios">
                      <label className="rms-radio-label">
                        <input
                          type="radio"
                          name="pay_method"
                          checked={paymentMethod === "CASH"}
                          onChange={() => setPaymentMethod("CASH")}
                        />
                        <span>Tiền mặt tại quầy</span>
                      </label>
                      <label className="rms-radio-label">
                        <input
                          type="radio"
                          name="pay_method"
                          checked={paymentMethod === "POS"}
                          onChange={() => setPaymentMethod("POS")}
                        />
                        <span>Chuyển khoản / Quẹt thẻ POS</span>
                      </label>
                    </div>
                  </div>

                  <div>
                    <div className="rms-summary-block-label">Số tiền cần thu:</div>
                    <div className="rms-summary-amount-big">
                      {money(selectedPlan?.price || 0)}
                    </div>
                  </div>
                </div>

                <div className="rms-step3-actions">
                  <button
                    type="button"
                    className="rms-btn-cancel"
                    onClick={() => {
                      setPendingPayment(null);
                      setNotice("");
                      setError("");
                    }}
                  >
                    Hủy
                  </button>

                  <button
                    type="button"
                    className="rms-btn-confirm"
                    onClick={handleConfirmPayment}
                    disabled={saving || !selectedMember || !selectedPlan}
                  >
                    <Ic d={ICONS.checkCircle} size={18} />
                    <span>{saving ? "Đang xử lý…" : "Xác nhận thanh toán tại quầy"}</span>
                  </button>
                </div>
              </section>

              {/* ── Lịch sử / Thanh toán gần đây ── */}
              <section className="rms-history-card">
                <div className="rms-history-head">
                  <div className="rms-history-title">
                    <Ic d={ICONS.receipt} size={18} />
                    <span>Thanh toán gần đây của thành viên</span>
                  </div>
                  <span className="rms-history-sub">
                    {paymentData?.rows?.length || 0} giao dịch gần nhất
                  </span>
                </div>

                <div className="rms-table-wrap">
                  <table className="rms-table">
                    <thead>
                      <tr>
                        <th>MÃ HÓA ĐƠN</th>
                        <th>GÓI DỊCH VỤ</th>
                        <th>SỐ TIỀN</th>
                        <th>NGÀY TẠO</th>
                        <th>TRẠNG THÁI</th>
                        <th className="rms-th-right">THAO TÁC</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(!paymentData?.rows || paymentData.rows.length === 0) && (
                        <tr>
                          <td colSpan={6} style={{ textAlign: "center", color: "#64748b", padding: "16px" }}>
                            Chưa có lịch sử giao dịch gói nào cho thành viên này.
                          </td>
                        </tr>
                      )}
                      {(paymentData?.rows || []).slice(0, 4).map((item) => {
                        const isPending = item.status === "PENDING";
                        const isSuccess = item.status === "SUCCESS";
                        return (
                          <tr key={item.id}>
                            <td style={{ fontWeight: 700 }}>#PAY-{item.id}</td>
                            <td>Gói {plans.find((p) => p.id === item.referenceId)?.name || `#${item.referenceId}`}</td>
                            <td style={{ fontWeight: 700 }}>{money(item.amount)}</td>
                            <td>{formatDate(item.createdAt || item.updatedAt)}</td>
                            <td>
                              <span
                                className={`rms-status-badge ${
                                  isSuccess ? "paid" : isPending ? "pending" : "cancelled"
                                }`}
                              >
                                {isSuccess ? "Đã thanh toán" : isPending ? "Chờ thanh toán" : "Đã hủy"}
                              </span>
                            </td>
                            <td className="rms-th-right">
                              {isPending ? (
                                <button
                                  type="button"
                                  className="rms-action-btn-link danger"
                                  onClick={() => handleCancelPayment(item.id)}
                                  disabled={saving}
                                >
                                  Hủy hóa đơn
                                </button>
                              ) : isSuccess ? (
                                <button
                                  type="button"
                                  className="rms-action-btn-link"
                                  onClick={() => {
                                    setLatestSuccessPayment({
                                      id: item.id,
                                      member: selectedMember,
                                      plan: plans.find((p) => p.id === item.referenceId) || selectedPlan,
                                      amount: item.amount,
                                      time: new Date(item.createdAt || Date.now()).toLocaleString("vi-VN"),
                                      staff: currentUser?.name || "Lê Minh Trang (Quầy 01)",
                                    });
                                    setReceiptModalOpen(true);
                                  }}
                                >
                                  Xem lại
                                </button>
                              ) : (
                                <span style={{ color: "#94a3b8" }}>—</span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </section>
            </div>

            {/* ════ RIGHT COLUMN: Sticky Summary & POS ════ */}
            <div className="rms-sticky-col">
              {/* Order Summary Card */}
              <div className="rms-summary-card">
                <div className="rms-summary-head">
                  <h3>Tóm tắt đơn hàng</h3>
                  <Ic d={ICONS.cart} size={20} style={{ color: "#1e60d5" }} />
                </div>

                {/* Customer Info Box */}
                <div className="rms-summary-customer-box">
                  <div className="rms-summary-cust-label">Khách hàng hưởng gói</div>
                  <div className="rms-summary-cust-name">
                    {selectedMember?.name || "Chưa chọn thành viên"}{" "}
                    {selectedMember && <span style={{ color: "#525f75", fontWeight: 400 }}>(#{selectedMember.id})</span>}
                  </div>
                  {selectedMember?.phone && (
                    <div className="rms-summary-cust-phone">
                      <Ic d={ICONS.phone} size={13} />
                      <span>{selectedMember.phone}</span>
                    </div>
                  )}
                </div>

                {/* Line Items */}
                <div className="rms-summary-line-items">
                  <div className="rms-summary-row">
                    <span>Gói dịch vụ:</span>
                    <span>
                      {selectedPlan ? `Gói ${selectedPlan.name} ${selectedPlan.durationMonths} Tháng` : "Chưa chọn"}
                    </span>
                  </div>
                  <div className="rms-summary-row">
                    <span>Giá gốc:</span>
                    <span>{money(selectedPlan?.price || 0)}</span>
                  </div>
                  <div className="rms-summary-row">
                    <span>Giảm giá / Khuyến mãi:</span>
                    <span className="discount">- 0 đ</span>
                  </div>
                  <div className="rms-summary-row">
                    <span>Thuế VAT (đã gồm):</span>
                    <span>0 đ</span>
                  </div>
                </div>

                {/* Total Box */}
                <div className="rms-summary-total-box">
                  <div className="rms-total-row">
                    <span className="rms-total-label">TỔNG TIỀN PHẢI THU</span>
                    <span className="rms-total-amount">{money(selectedPlan?.price || 0)}</span>
                  </div>
                  <div className="rms-total-sub">
                    <span>Hình thức dự kiến</span>
                    <span style={{ fontWeight: 600, color: "#0b1c30" }}>
                      {paymentMethod === "CASH" ? "Tiền mặt tại quầy" : "Thẻ POS / Chuyển khoản"}
                    </span>
                  </div>
                </div>

                {/* Operational Notice */}
                <div className="rms-summary-notice">
                  <Ic d={ICONS.info} size={18} style={{ color: "#1e60d5", flexShrink: 0 }} />
                  <span>
                    Gói chỉ được kích hoạt ngay khi quầy lễ tân xác nhận nhận đủ tiền hoặc quẹt thẻ thành công.
                  </span>
                </div>

                {/* Submit button */}
                <button
                  type="button"
                  className="rms-btn-submit-order"
                  onClick={handleConfirmPayment}
                  disabled={saving || !selectedMember || !selectedPlan}
                >
                  <Ic d={ICONS.payments} size={18} />
                  <span>{saving ? "Đang xử lý…" : "Xác nhận & Thu tiền"}</span>
                </button>

                <button
                  type="button"
                  className="rms-btn-preview-receipt"
                  onClick={() => {
                    setLatestSuccessPayment({
                      id: pendingPayment?.id || "PREVIEW",
                      member: selectedMember,
                      plan: selectedPlan,
                      amount: selectedPlan?.price || 0,
                      time: new Date().toLocaleString("vi-VN"),
                      staff: currentUser?.name || "Lê Minh Trang (Quầy 01)",
                    });
                    setReceiptModalOpen(true);
                  }}
                  disabled={!selectedMember || !selectedPlan}
                >
                  <Ic d={ICONS.visibility} size={16} />
                  <span>Xem trước biên lai quầy</span>
                </button>
              </div>

              {/* POS Integration Badge Card */}
              <div className="rms-pos-card">
                <div className="rms-pos-icon">
                  <Ic d={ICONS.contactless} size={22} />
                </div>
                <div className="rms-pos-info">
                  <span className="rms-pos-title">Tích hợp POS cảm ứng</span>
                  <span className="rms-pos-sub">Đã kết nối thiết bị quét mã QR VietQR & máy POS 01</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* ── Receipt Modal ── */}
        {receiptModalOpen && latestSuccessPayment && (
          <div className="rms-modal-backdrop" onClick={() => setReceiptModalOpen(false)}>
            <div className="rms-receipt-modal" onClick={(e) => e.stopPropagation()}>
              <button
                type="button"
                className="rms-modal-close-ic"
                onClick={() => setReceiptModalOpen(false)}
              >
                <Ic d={ICONS.cancel} size={20} />
              </button>

              <div className="rms-receipt-header">
                <div className="rms-receipt-icon-circle">
                  <Ic d={ICONS.checkCircle} size={32} />
                </div>
                <h3>Giao dịch thành công</h3>
                <p>Biên lai thu phí thành viên tại quầy</p>
              </div>

              <div className="rms-receipt-paper">
                <div className="rms-receipt-row divider">
                  <span style={{ color: "#525f75" }}>Mã giao dịch:</span>
                  <span style={{ fontWeight: 700 }}>#REC-2026-{latestSuccessPayment.id}</span>
                </div>
                <div className="rms-receipt-row">
                  <span style={{ color: "#525f75" }}>Thành viên:</span>
                  <span style={{ fontWeight: 600 }}>
                    {latestSuccessPayment.member?.name} (#{latestSuccessPayment.member?.id})
                  </span>
                </div>
                <div className="rms-receipt-row">
                  <span style={{ color: "#525f75" }}>Gói hội viên:</span>
                  <span style={{ fontWeight: 600, color: "#1e60d5" }}>
                    {latestSuccessPayment.plan?.name} ({latestSuccessPayment.plan?.durationMonths || 1} tháng)
                  </span>
                </div>
                <div className="rms-receipt-row">
                  <span style={{ color: "#525f75" }}>Thời gian:</span>
                  <span>{latestSuccessPayment.time}</span>
                </div>
                <div className="rms-receipt-row">
                  <span style={{ color: "#525f75" }}>Thu ngân:</span>
                  <span>{latestSuccessPayment.staff}</span>
                </div>
                <div className="rms-receipt-row total">
                  <span style={{ fontWeight: 700 }}>SỐ TIỀN ĐÃ THU:</span>
                  <span className="rms-receipt-total-val">{money(latestSuccessPayment.amount)}</span>
                </div>
              </div>

              <div className="rms-receipt-actions">
                <button
                  type="button"
                  className="rms-btn-ghost"
                  style={{ height: "44px", justifyContent: "center" }}
                  onClick={() => window.print()}
                >
                  <Ic d={ICONS.print} size={18} />
                  <span>In hóa đơn / biên lai</span>
                </button>
                <button
                  type="button"
                  className="rms-btn-confirm"
                  style={{ height: "44px", justifyContent: "center" }}
                  onClick={startAnother}
                >
                  <Ic d={ICONS.plusCircle} size={18} />
                  <span>Bán gói khác</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
