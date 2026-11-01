import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  createMemberProfileApi,
  deactivateMemberApi,
  getMemberProfilesApi,
  getCurrentUserApi,
  getUserApi,
  getUsersApi,
  updateMemberApi,
  updateMemberProfileApi,
} from "../api";
import "./MemberList.css";

const PAGE_SIZE = 8;

/* ── helpers ── */
const initialsOf = (name) =>
  (name || "?")
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((w) => w[0])
    .join("")
    .toUpperCase();

const avatarColor = (id) => {
  const COLORS = [
    ["#dae8ff", "#1565c0"],
    ["#d6f5e3", "#1b7a45"],
    ["#fde8c8", "#b45309"],
    ["#f0d9ff", "#7e22ce"],
    ["#ffe0e0", "#c62828"],
    ["#d9f0ff", "#0277bd"],
  ];
  return COLORS[Number(id ?? 0) % COLORS.length];
};

function accountForm(user) {
  return {
    name: user.name || "",
    email: user.email || "",
    dateOfBirth: user.dateOfBirth || "",
    gender: user.gender || "",
    phone: user.phone || "",
  };
}

function profileForm(profile) {
  return {
    height: profile?.height ?? "",
    weight: profile?.weight ?? "",
    goal: profile?.goal ?? "",
  };
}

/* ── small icon component ── */
const Ic = ({ d, size = 16 }) => (
  <svg
    width={size} height={size} viewBox="0 0 24 24"
    fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
    aria-hidden="true"
  >
    {d.split(" M").map((p, i) => <path key={i} d={(i ? "M" : "") + p} />)}
  </svg>
);

const ICONS = {
  search: "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16 M21 21l-4.35-4.35",
  refresh: "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16 M8 16H3v5",
  plus: "M12 5v14 M5 12h14",
  close: "M18 6 6 18 M6 6l12 12",
  edit: "M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7 M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4z",
  ban: "M10 14a4 4 0 0 0 5.477-.96l3.588 3.588A9 9 0 1 1 5.372 5.372l3.588 3.588A4 4 0 0 0 10 14z M3 3l18 18",
  mail: "M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z M22 6l-10 7L2 6",
  phone: "M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.11 10.5 19.79 19.79 0 0 1 1.07 4.18 2 2 0 0 1 3.03 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L7.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z",
  cake: "M20 21v-8a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8 M4 16s.5-1 2-1 2.5 2 4 2 2.5-2 4-2 2.5 2 4 2 2-1 2-1 M2 21h20 M7 8v2 M12 8v2 M17 8v2 M7 4h.01 M12 2v2 M17 4h.01",
  gender: "M12 2a5 5 0 1 0 0 10 5 5 0 0 0 0-10 M12 12v10 M9 19h6",
  goal: "M3 12a9 9 0 1 0 18 0 9 9 0 0 0-18 0 M12 8v4l3 3",
  user: "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8",
  chevLeft: "M15 18l-6-6 6-6",
  chevRight: "M9 18l6-6-6-6",
};

/* ── Avatar ── */
function Avatar({ name, id, size = 38 }) {
  const [bg, color] = avatarColor(id);
  return (
    <span
      style={{
        display: "inline-grid",
        placeItems: "center",
        width: size,
        height: size,
        borderRadius: "50%",
        background: bg,
        color,
        fontSize: size * 0.35,
        fontWeight: 700,
        flexShrink: 0,
        letterSpacing: ".02em",
      }}
    >
      {initialsOf(name)}
    </span>
  );
}

/* ── Status badge ── */
function StatusBadge({ status }) {
  const active = status === "ACTIVE";
  return (
    <span className={"ml-badge " + (active ? "ml-badge--active" : "ml-badge--inactive")}>
      <span className="ml-badge-dot" />
      {active ? "Hoạt động" : "Ngừng HĐ"}
    </span>
  );
}

/* ── Detail field row ── */
function Field({ icon, label, value }) {
  return (
    <div className="ml-field">
      <span className="ml-field-icon"><Ic d={icon} size={14} /></span>
      <div className="ml-field-body">
        <span className="ml-field-label">{label}</span>
        <span className="ml-field-value">{value || "—"}</span>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════════════════ */
export default function MemberList({ token, role, onLogout, onNavigate }) {
  const [members, setMembers]             = useState([]);
  const [profiles, setProfiles]           = useState([]);
  const [query, setQuery]                 = useState("");
  const [page, setPage]                   = useState(0);
  const [selected, setSelected]           = useState(null);
  const [currentUser, setCurrentUser]     = useState(null);

  useEffect(() => {
    if (token) {
      getCurrentUserApi(token).then(setCurrentUser).catch(() => setCurrentUser(null));
    }
  }, [token]);
  const [loading, setLoading]             = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError]                 = useState("");
  const [detailError, setDetailError]     = useState("");
  const [notice, setNotice]               = useState("");
  const [editing, setEditing]             = useState(false);
  const [account, setAccount]             = useState(null);
  const [profile, setProfile]             = useState(null);
  const [accountSaving, setAccountSaving] = useState(false);
  const [profileSaving, setProfileSaving] = useState(false);
  const [deactivating, setDeactivating]   = useState(false);

  const allowed = role === "ADMIN" || role === "RECEPTIONIST";
  const isAdmin = role === "ADMIN";

  /* ── load ── */
  const loadMembers = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [users, memberProfiles] = await Promise.all([
        getUsersApi(token),
        getMemberProfilesApi(token),
      ]);
      setMembers(Array.isArray(users) ? users.filter((u) => u.role === "MEMBER") : []);
      setProfiles(Array.isArray(memberProfiles) ? memberProfiles : []);
    } catch (err) {
      setError(
        err.status === 401
          ? "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
          : err.status === 403
          ? "Tài khoản không được phép xem danh sách thành viên."
          : err.message || "Không tải được danh sách thành viên."
      );
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    if (allowed) loadMembers();
  }, [allowed, loadMembers]);

  /* ── filter / paging ── */
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return members;
    return members.filter((m) =>
      [m.id, m.name, m.email, m.phone, m.status].some((v) =>
        String(v ?? "").toLowerCase().includes(q)
      )
    );
  }, [members, query]);

  const pageCount   = Math.ceil(filtered.length / PAGE_SIZE);
  const pageMembers = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  /* ── open detail ── */
  async function openDetail(memberId) {
    setDetailLoading(true);
    setDetailError("");
    setNotice("");
    setEditing(false);
    try {
      const user = await getUserApi(token, memberId);
      if (user.role !== "MEMBER") throw new Error("Tài khoản này không còn là MEMBER.");
      const memberProfile = profiles.find((p) => p.memberId === user.id) || null;
      setSelected({ user, profile: memberProfile });
      setAccount(accountForm(user));
      setProfile(profileForm(memberProfile));
    } catch (err) {
      setDetailError(
        err.status === 403
          ? "Bạn không có quyền xem thành viên này."
          : err.message || "Không tải được thông tin thành viên."
      );
    } finally {
      setDetailLoading(false);
    }
  }

  /* ── save account ── */
  async function saveAccount(e) {
    e.preventDefault();
    if (!selected || !isAdmin) return;
    setAccountSaving(true);
    setDetailError("");
    setNotice("");
    try {
      const updated = await updateMemberApi(token, selected.user.id, {
        ...account,
        dateOfBirth: account.dateOfBirth || null,
        gender: account.gender || null,
        phone: account.phone || null,
      });
      setSelected((s) => ({ ...s, user: updated }));
      setAccount(accountForm(updated));
      await loadMembers();
      setNotice("Đã cập nhật thông tin tài khoản MEMBER.");
    } catch (err) {
      setDetailError(
        err.status === 409
          ? "Email này đã được tài khoản khác sử dụng."
          : err.message || "Không thể cập nhật thông tin tài khoản."
      );
    } finally {
      setAccountSaving(false);
    }
  }

  /* ── save profile ── */
  async function saveProfile(e) {
    e.preventDefault();
    if (!selected || !isAdmin) return;
    setProfileSaving(true);
    setDetailError("");
    setNotice("");
    try {
      const payload = {
        height: Number(profile.height),
        weight: Number(profile.weight),
        goal: profile.goal,
      };
      const updated = selected.profile
        ? await updateMemberProfileApi(token, selected.profile.id, payload)
        : await createMemberProfileApi(token, { memberId: selected.user.id, ...payload });
      setSelected((s) => ({ ...s, profile: updated }));
      setProfile(profileForm(updated));
      await loadMembers();
      setNotice("Đã lưu Member Profile.");
    } catch (err) {
      setDetailError(
        err.status === 400
          ? err.message || "Kiểm tra chiều cao, cân nặng và mục tiêu."
          : err.message || "Không thể lưu Member Profile."
      );
    } finally {
      setProfileSaving(false);
    }
  }

  /* ── deactivate ── */
  async function deactivate() {
    if (!selected || !isAdmin) return;
    if (
      !window.confirm(
        `Vô hiệu hóa tài khoản MEMBER #${selected.user.id}? Lịch sử nghiệp vụ sẽ được giữ lại.`
      )
    )
      return;
    setDeactivating(true);
    setDetailError("");
    setNotice("");
    try {
      const updated = await deactivateMemberApi(token, selected.user.id);
      setSelected((s) => ({ ...s, user: updated }));
      setAccount(accountForm(updated));
      await loadMembers();
      setNotice("Đã vô hiệu hóa tài khoản. Dữ liệu lịch sử được giữ nguyên.");
    } catch (err) {
      setDetailError(err.message || "Không thể vô hiệu hóa tài khoản.");
    } finally {
      setDeactivating(false);
    }
  }

  /* ═══════════════════════════════════════════ render ═══════════════════ */
  return (
    <div className="layout">
      <Sidebar role={role} activePage="members" onNavigate={onNavigate} onLogout={onLogout} />

      <main className="main ml-main">
        <header className="rc-topbar">
          <nav className="rc-crumb" aria-label="Breadcrumb">
            <span style={{ cursor: "pointer" }} onClick={() => onNavigate("dashboard")}>SPORTS CENTER</span>
            <i>›</i>
            <span style={{ cursor: "pointer" }} onClick={() => onNavigate("dashboard")}>{isAdmin ? "QUẢN TRỊ" : "LỄ TÂN"}</span>
            <i>›</i>
            <b>THÀNH VIÊN</b>
          </nav>

          <div className="rc-user">
            <span className="rc-avatar">{initialsOf(currentUser?.name)}</span>
            <div>
              <b>{currentUser?.name || "Lê Minh Trang"}</b>
              <small>{isAdmin ? "Quản trị viên" : "Quầy lễ tân 01"}</small>
            </div>
          </div>
        </header>
        {/* ── page header ── */}
        <div className="ml-header">
          <div>
            <h1 className="ml-title">Danh sách thành viên</h1>
            <p className="ml-sub">
              {loading ? "Đang tải…" : `${filtered.length} thành viên`}
            </p>
          </div>
          <div className="ml-header-actions">
            {isAdmin && (
              <button className="ml-btn-primary" onClick={() => onNavigate("register")}>
                <Ic d={ICONS.plus} size={15} />
                Thêm thành viên
              </button>
            )}
          </div>
        </div>

        {/* ── alerts ── */}
        {!allowed && (
          <div className="ml-alert ml-alert--error">
            Chỉ ADMIN và RECEPTIONIST được truy cập trang này.
          </div>
        )}
        {error && <div className="ml-alert ml-alert--error">{error}</div>}

        {allowed && (
          <div className={"ml-body" + (selected ? " ml-body--split" : "")}>

            {/* ════════════ LEFT: table ════════════ */}
            <div className="ml-table-col">
              {/* toolbar */}
              <div className="ml-toolbar">
                <label className="ml-search-wrap">
                  <Ic d={ICONS.search} size={15} />
                  <input
                    className="ml-search"
                    type="search"
                    placeholder="Tìm theo tên, email, SĐT, ID hoặc trạng thái…"
                    value={query}
                    onChange={(e) => {
                      setQuery(e.target.value);
                      setPage(0);
                      setSelected(null);
                    }}
                  />
                </label>
                <button
                  className="ml-btn-ghost"
                  onClick={loadMembers}
                  disabled={loading}
                >
                  <Ic d={ICONS.refresh} size={15} />
                  Làm mới
                </button>
              </div>

              {/* table */}
              {loading ? (
                <div className="ml-state">Đang tải danh sách thành viên…</div>
              ) : filtered.length === 0 ? (
                <div className="ml-state">Không tìm thấy thành viên phù hợp.</div>
              ) : (
                <>
                  <div className="ml-table-wrap">
                    <table className="ml-table">
                      <thead>
                        <tr>
                          <th>#</th>
                          <th>Thành viên</th>
                          <th>Email</th>
                          <th>Điện thoại</th>
                          <th>Trạng thái</th>
                          <th />
                        </tr>
                      </thead>
                      <tbody>
                        {pageMembers.map((m, idx) => {
                          const isActive = selected?.user?.id === m.id;
                          return (
                            <tr
                              key={m.id}
                              className={"ml-row" + (isActive ? " ml-row--active" : "")}
                              onClick={() => openDetail(m.id)}
                            >
                              <td className="ml-td-num">{page * PAGE_SIZE + idx + 1}</td>
                              <td>
                                <div className="ml-member-cell">
                                  <Avatar name={m.name} id={m.id} />
                                  <div>
                                    <div className="ml-member-name">{m.name}</div>
                                    <div className="ml-member-id">#{m.id}</div>
                                  </div>
                                </div>
                              </td>
                              <td className="ml-td-email">{m.email}</td>
                              <td className="ml-td-phone">{m.phone || "—"}</td>
                              <td><StatusBadge status={m.status} /></td>
                              <td className="ml-td-chevron">
                                <Ic d={ICONS.chevRight} size={16} />
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>

                  {/* pagination */}
                  <div className="ml-pagination">
                    <span className="ml-page-info">
                      Trang {page + 1}/{Math.max(pageCount, 1)} · {filtered.length} thành viên
                    </span>
                    <div className="ml-page-btns">
                      <button
                        className="ml-page-btn"
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                        disabled={page === 0}
                      >
                        <Ic d={ICONS.chevLeft} size={15} /> Trước
                      </button>
                      <button
                        className="ml-page-btn"
                        onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
                        disabled={pageCount === 0 || page + 1 >= pageCount}
                      >
                        Sau <Ic d={ICONS.chevRight} size={15} />
                      </button>
                    </div>
                  </div>
                </>
              )}
            </div>

            {/* ════════════ RIGHT: detail panel ════════════ */}
            {selected && (
              <aside className="ml-panel">
                {/* panel header */}
                <div className="ml-panel-head">
                  <div className="ml-panel-identity">
                    <Avatar name={selected.user.name} id={selected.user.id} size={48} />
                    <div>
                      <div className="ml-panel-name">{selected.user.name}</div>
                      <StatusBadge status={selected.user.status} />
                    </div>
                  </div>
                  <button
                    className="ml-close-btn"
                    onClick={() => { setSelected(null); setEditing(false); }}
                    title="Đóng"
                  >
                    <Ic d={ICONS.close} size={16} />
                  </button>
                </div>

                {detailLoading && <div className="ml-state">Đang tải chi tiết…</div>}
                {detailError && (
                  <div className="ml-alert ml-alert--error ml-alert--inset">{detailError}</div>
                )}
                {notice && (
                  <div className="ml-alert ml-alert--success ml-alert--inset">{notice}</div>
                )}

                {!detailLoading && (
                  <div className="ml-panel-body">
                    {!editing ? (
                      <>
                        <div className="ml-section">
                          <div className="ml-section-title">Thông tin liên hệ</div>
                          <Field icon={ICONS.mail}   label="Email"      value={selected.user.email} />
                          <Field icon={ICONS.phone}  label="Điện thoại" value={selected.user.phone} />
                          <Field icon={ICONS.cake}   label="Ngày sinh"  value={selected.user.dateOfBirth} />
                          <Field
                            icon={ICONS.gender}
                            label="Giới tính"
                            value={
                              selected.user.gender === "M" ? "Nam"
                              : selected.user.gender === "F" ? "Nữ"
                              : selected.user.gender
                            }
                          />
                        </div>

                        <div className="ml-section">
                          <div className="ml-section-title">Hồ sơ thể chất</div>
                          {selected.profile ? (
                            <>
                              <Field
                                icon={ICONS.user}
                                label="Chiều cao / Cân nặng"
                                value={`${selected.profile.height} cm · ${selected.profile.weight} kg`}
                              />
                              <Field icon={ICONS.goal} label="Mục tiêu" value={selected.profile.goal} />
                            </>
                          ) : (
                            <p className="ml-empty-note">Chưa có hồ sơ thể chất.</p>
                          )}
                        </div>

                        {isAdmin && (
                          <div className="ml-panel-actions">
                            <button className="ml-btn-outline" onClick={() => setEditing(true)}>
                              <Ic d={ICONS.edit} size={14} /> Chỉnh sửa
                            </button>
                            {selected.user.status === "ACTIVE" && (
                              <button
                                className="ml-btn-danger"
                                onClick={deactivate}
                                disabled={deactivating}
                              >
                                <Ic d={ICONS.ban} size={14} />
                                {deactivating ? "Đang xử lý…" : "Vô hiệu hóa"}
                              </button>
                            )}
                          </div>
                        )}
                      </>
                    ) : (
                      /* ── edit mode ── */
                      <>
                        <form className="ml-form" onSubmit={saveAccount}>
                          <div className="ml-form-title">Thông tin tài khoản</div>
                          <label>Họ tên
                            <input required maxLength={100} value={account.name}
                              onChange={(e) => setAccount({ ...account, name: e.target.value })} />
                          </label>
                          <label>Email
                            <input type="email" required maxLength={150} value={account.email}
                              onChange={(e) => setAccount({ ...account, email: e.target.value })} />
                          </label>
                          <label>Điện thoại
                            <input maxLength={20} value={account.phone}
                              onChange={(e) => setAccount({ ...account, phone: e.target.value })} />
                          </label>
                          <label>Ngày sinh
                            <input type="date" max={new Date().toISOString().slice(0, 10)}
                              value={account.dateOfBirth}
                              onChange={(e) => setAccount({ ...account, dateOfBirth: e.target.value })} />
                          </label>
                          <label>Giới tính
                            <select value={account.gender}
                              onChange={(e) => setAccount({ ...account, gender: e.target.value })}>
                              <option value="">— Chọn —</option>
                              <option value="M">Nam</option>
                              <option value="F">Nữ</option>
                            </select>
                          </label>
                          <button className="ml-btn-primary ml-btn--full" type="submit" disabled={accountSaving}>
                            {accountSaving ? "Đang lưu…" : "Lưu tài khoản"}
                          </button>
                        </form>

                        <form className="ml-form" onSubmit={saveProfile}>
                          <div className="ml-form-title">Hồ sơ thể chất</div>
                          <label>Chiều cao (cm)
                            <input type="number" min="50" max="250" step="0.01" required
                              value={profile.height}
                              onChange={(e) => setProfile({ ...profile, height: e.target.value })} />
                          </label>
                          <label>Cân nặng (kg)
                            <input type="number" min="10" max="500" step="0.01" required
                              value={profile.weight}
                              onChange={(e) => setProfile({ ...profile, weight: e.target.value })} />
                          </label>
                          <label>Mục tiêu
                            <textarea maxLength={1000} rows={3} value={profile.goal}
                              onChange={(e) => setProfile({ ...profile, goal: e.target.value })} />
                          </label>
                          <button className="ml-btn-primary ml-btn--full" type="submit" disabled={profileSaving}>
                            {profileSaving ? "Đang lưu…" : selected.profile ? "Lưu hồ sơ" : "Tạo hồ sơ"}
                          </button>
                        </form>

                        <div className="ml-form-cancel">
                          <button className="ml-btn-ghost ml-btn--full"
                            onClick={() => { setEditing(false); setDetailError(""); setNotice(""); }}>
                            Hủy chỉnh sửa
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                )}
              </aside>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
