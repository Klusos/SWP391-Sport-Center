import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./MemberList.css";
import {
  createMemberProfileApi,
  deactivateMemberApi,
  getMemberProfilesApi,
  getUserApi,
  getUsersApi,
  updateMemberApi,
  updateMemberProfileApi,
} from "../api";

const PAGE_SIZE = 10;

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

function memberInitials(name) {
  return String(name || "?").trim().split(/\s+/).filter(Boolean).slice(-2).map((part) => part[0]).join("").toUpperCase() || "?";
}

function ProfileIcon({ type }) {
  const paths = {
    email: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3 7 9 6 9-6" /></>,
    phone: <path d="M7 3h3l2 5-2 2a15 15 0 0 0 4 4l2-2 5 2v3a2 2 0 0 1-2 2A16 16 0 0 1 5 5a2 2 0 0 1 2-2Z" />,
    birth: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 11h18" /></>,
    gender: <><circle cx="10" cy="8" r="5" /><path d="M10 13v8m-4-4h8" /></>,
    height: <><path d="M12 3v18M8 7l4-4 4 4M8 17l4 4 4-4" /></>,
    goal: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  };
  return <svg className="member-profile-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[type]}</svg>;
}

export default function MemberList({ token, role, onLogout, onNavigate }) {
  const [members, setMembers] = useState([]);
  const [profiles, setProfiles] = useState([]);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState("");
  const [detailError, setDetailError] = useState("");
  const [notice, setNotice] = useState("");
  const [forbidden, setForbidden] = useState(false);
  const [editing, setEditing] = useState(false);
  const [account, setAccount] = useState(null);
  const [profile, setProfile] = useState(null);
  const [accountSaving, setAccountSaving] = useState(false);
  const [profileSaving, setProfileSaving] = useState(false);
  const [deactivating, setDeactivating] = useState(false);
  const allowed = role === "ADMIN" || role === "RECEPTIONIST";
  const isAdmin = role === "ADMIN";

  const loadMembers = useCallback(async () => {
    setLoading(true);
    setError("");
    setForbidden(false);
    try {
      const [users, memberProfiles] = await Promise.all([
        getUsersApi(token),
        getMemberProfilesApi(token),
      ]);
      setMembers(Array.isArray(users) ? users.filter((user) => user.role === "MEMBER") : []);
      setProfiles(Array.isArray(memberProfiles) ? memberProfiles : []);
    } catch (requestError) {
      setForbidden(requestError.status === 401 || requestError.status === 403);
      setError(requestError.status === 401
        ? "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại."
        : requestError.status === 403
          ? "Tài khoản không được phép xem danh sách thành viên."
          : requestError.message || "Không tải được danh sách thành viên.");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    if (allowed) Promise.resolve().then(loadMembers);
  }, [allowed, loadMembers]);

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return members;
    return members.filter((member) => [member.id, member.name, member.email, member.phone, member.status]
      .some((value) => String(value ?? "").toLowerCase().includes(normalized)));
  }, [members, query]);
  const pageCount = Math.ceil(filtered.length / PAGE_SIZE);
  const pageMembers = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  async function openDetail(memberId) {
    setDetailLoading(true);
    setDetailError("");
    setNotice("");
    setEditing(false);
    try {
      const user = await getUserApi(token, memberId);
      if (user.role !== "MEMBER") throw new Error("Tài khoản này không còn là MEMBER.");
      const memberProfile = profiles.find((item) => item.memberId === user.id) || null;
      setSelected({ user, profile: memberProfile });
      setAccount(accountForm(user));
      setProfile(profileForm(memberProfile));
    } catch (requestError) {
      setDetailError(requestError.status === 403
        ? "Bạn không có quyền xem thành viên này."
        : requestError.message || "Không tải được thông tin thành viên.");
    } finally {
      setDetailLoading(false);
    }
  }

  async function saveAccount(event) {
    event.preventDefault();
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
      setSelected((current) => ({ ...current, user: updated }));
      setAccount(accountForm(updated));
      await loadMembers();
      setNotice("Đã cập nhật thông tin tài khoản MEMBER.");
    } catch (requestError) {
      setDetailError(requestError.status === 409
        ? "Email này đã được tài khoản khác sử dụng."
        : requestError.message || "Không thể cập nhật thông tin tài khoản.");
    } finally {
      setAccountSaving(false);
    }
  }

  async function saveProfile(event) {
    event.preventDefault();
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
      setSelected((current) => ({ ...current, profile: updated }));
      setProfile(profileForm(updated));
      await loadMembers();
      setNotice("Đã lưu Member Profile.");
    } catch (requestError) {
      setDetailError(requestError.status === 400
        ? requestError.message || "Kiểm tra chiều cao, cân nặng và mục tiêu."
        : requestError.message || "Không thể lưu Member Profile.");
    } finally {
      setProfileSaving(false);
    }
  }

  async function deactivate() {
    if (!selected || !isAdmin) return;
    if (!window.confirm(`Vô hiệu hóa tài khoản MEMBER #${selected.user.id}? Lịch sử nghiệp vụ sẽ được giữ lại.`)) return;
    setDeactivating(true);
    setDetailError("");
    setNotice("");
    try {
      const updated = await deactivateMemberApi(token, selected.user.id);
      setSelected((current) => ({ ...current, user: updated }));
      setAccount(accountForm(updated));
      await loadMembers();
      setNotice("Đã vô hiệu hóa tài khoản. Dữ liệu lịch sử được giữ nguyên.");
    } catch (requestError) {
      setDetailError(requestError.message || "Không thể vô hiệu hóa tài khoản.");
    } finally {
      setDeactivating(false);
    }
  }

  return (
    <div className="layout">
      <Sidebar role={role} activePage="members" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main member-list-main">
        <header className="member-list-heading"><div><p className="member-list-eyebrow">SPORTCENTER / THÀNH VIÊN</p><h1>Danh sách thành viên</h1>
          <p className="subtitle">{loading ? "Đang tải danh sách…" : `${filtered.length} thành viên phù hợp`}</p></div>
          {isAdmin && <button className="member-list-create" type="button" onClick={() => onNavigate("register")}>Đăng ký MEMBER</button>}
        </header>
        {!allowed && <div className="banner error" role="alert">Chỉ ADMIN và RECEPTIONIST được truy cập trang này.</div>}
        {notice && <div className="banner success" role="status">{notice}</div>}
        {error && <div className="banner error" role="alert">{error}</div>}
        {forbidden && <p className="queue-state">Danh sách thành viên hiện không khả dụng.</p>}

        {allowed && <div className={`member-list-workspace${selected ? " has-selection" : ""}`}>
          <section className="member-list-table-column">
          <div className="member-list-toolbar">
            <label className="member-search-label" htmlFor="memberSearch">Tìm tên, email, số điện thoại, ID hoặc trạng thái</label>
            <input id="memberSearch" type="search" placeholder="T\u00ecm t\u00ean, email, S\u0110T ho\u1eb7c ID" value={query}
              onChange={(event) => { setQuery(event.target.value); setPage(0); setSelected(null); }} />
            <button type="button" onClick={loadMembers} disabled={loading}>{loading ? "Đang tải…" : "Làm mới"}</button>
          </div>

          {loading ? <div className="queue-state" role="status">Đang tải danh sách thành viên…</div>
            : filtered.length === 0 ? <div className="queue-state">Không tìm thấy thành viên phù hợp.</div>
              : <>
                <div className="queue-table-wrap">
                  <table className="queue-table member-table">
                    <thead><tr><th>ID</th><th>Họ tên</th><th>Email</th><th>Điện thoại</th><th>Tài khoản</th><th>Hồ sơ</th><th>Thao tác</th></tr></thead>
                    <tbody>{pageMembers.map((member) => {
                      const memberProfile = profiles.find((item) => item.memberId === member.id);
                      return <tr key={member.id} className={selected?.user.id === member.id ? "member-row is-selected" : "member-row"} aria-selected={selected?.user.id === member.id}>
                        <td>#{member.id}</td><td><span className="member-name-cell"><i className={`member-initials tone-${Number(member.id || 0) % 4}`}>{memberInitials(member.name)}</i><b>{member.name || `Thành viên #${member.id}`}</b></span></td><td className="member-contact-cell" title={member.email || undefined}>{member.email || "—"}</td><td className="member-contact-cell" title={member.phone || undefined}>{member.phone || "—"}</td>
                        <td><span className={"status-pill status-" + String(member.status).toLowerCase()}>{member.status === "ACTIVE" ? "Ho\u1ea1t \u0111\u1ed9ng" : "Ng\u1eebng ho\u1ea1t \u0111\u1ed9ng"}</span></td>
                        <td>{memberProfile ? "Có hồ sơ" : "Chưa có hồ sơ"}</td>
                        <td><button type="button" onClick={() => openDetail(member.id)} disabled={detailLoading}>Chi tiết</button></td>
                      </tr>;
                    })}</tbody>
                  </table>
                </div>
                <div className="queue-pagination">
                  <span>Trang {page + 1}/{Math.max(pageCount, 1)} · {filtered.length} thành viên</span>
                  <div>
                    <button type="button" onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={page === 0}>Trước</button>
                    <button type="button" onClick={() => setPage((current) => Math.min(pageCount - 1, current + 1))} disabled={pageCount === 0 || page + 1 >= pageCount}>Sau</button>
                  </div>
                </div>
              </>}
          </section>

          <aside className="member-list-detail-column">
        {detailLoading && <div className="queue-state" role="status">Đang tải chi tiết…</div>}
        {detailError && <div className="banner error" role="alert">{detailError}</div>}
        {selected && <section className="member-detail form-card" aria-labelledby="member-detail-title">
          <div className="member-detail-heading">
            <div className="member-detail-identity">
              <i className={`member-initials tone-${Number(selected.user.id || 0) % 4}`}>{memberInitials(selected.user.name)}</i>
              <div className="member-detail-person">
                <p className="member-detail-id">MEMBER #{selected.user.id}</p>
                <h2 id="member-detail-title">{selected.user.name || `Th\u00e0nh vi\u00ean #${selected.user.id}`}</h2>
                <span className={"status-pill status-" + String(selected.user.status).toLowerCase()}>{selected.user.status === "ACTIVE" ? "Ho\u1ea1t \u0111\u1ed9ng" : "Ng\u1eebng ho\u1ea1t \u0111\u1ed9ng"}</span>
              </div>
            </div>
            <div className="member-detail-actions">
              {isAdmin && <button type="button" onClick={() => setEditing((value) => !value)}>{editing ? "\u0110\u00f3ng ch\u1ec9nh s\u1eeda" : "Ch\u1ec9nh s\u1eeda"}</button>}
              {isAdmin && selected.user.status === "ACTIVE" && <button type="button" onClick={deactivate} disabled={deactivating}>{deactivating ? "\u0110ang x\u1eed l\u00fd\u2026" : "V\u00f4 hi\u1ec7u h\u00f3a"}</button>}
              <button className="member-detail-close" type="button" aria-label="\u0110\u00f3ng chi ti\u1ebft th\u00e0nh vi\u00ean" onClick={() => setSelected(null)}>{"\u00d7"}</button>
            </div>
          </div>

          {!editing ? <div className="member-detail-grid member-detail-sections">
            <section className="member-detail-section" aria-labelledby="member-contact-title">
              <h3 id="member-contact-title">{"Th\u00f4ng tin li\u00ean h\u1ec7"}</h3>
              <div className="member-detail-field"><ProfileIcon type="email" /><div><span className="member-detail-label">Email</span><span className="member-detail-value" title={selected.user.email || undefined}>{selected.user.email || "Ch\u01b0a c\u1eadp nh\u1eadt"}</span></div></div>
              <div className="member-detail-field"><ProfileIcon type="phone" /><div><span className="member-detail-label">{"\u0110i\u1ec7n tho\u1ea1i"}</span><span className="member-detail-value">{selected.user.phone || "Ch\u01b0a c\u1eadp nh\u1eadt"}</span></div></div>
              <div className="member-detail-field"><ProfileIcon type="birth" /><div><span className="member-detail-label">{"Ng\u00e0y sinh"}</span><span className="member-detail-value">{selected.user.dateOfBirth || "Ch\u01b0a c\u1eadp nh\u1eadt"}</span></div></div>
              <div className="member-detail-field"><ProfileIcon type="gender" /><div><span className="member-detail-label">{"Gi\u1edbi t\u00ednh"}</span><span className="member-detail-value">{selected.user.gender === "M" ? "Nam" : selected.user.gender === "F" ? "N\u1eef" : selected.user.gender || "Ch\u01b0a c\u1eadp nh\u1eadt"}</span></div></div>
            </section>
            <section className="member-detail-section" aria-labelledby="member-physical-title">
              <h3 id="member-physical-title">{"H\u1ed3 s\u01a1 th\u1ec3 ch\u1ea5t"}</h3>
              {selected.profile ? <>
                <div className="member-detail-field"><ProfileIcon type="height" /><div><span className="member-detail-label">{"Chi\u1ec1u cao / C\u00e2n n\u1eb7ng"}</span><span className="member-detail-value">{selected.profile.height} cm \u00b7 {selected.profile.weight} kg</span></div></div>
                <div className="member-detail-field"><ProfileIcon type="goal" /><div><span className="member-detail-label">{"M\u1ee5c ti\u00eau"}</span><span className="member-detail-value">{selected.profile.goal || "Ch\u01b0a c\u1eadp nh\u1eadt"}</span></div></div>
              </> : <p className="member-detail-empty-note">Ch\u01b0a c\u00f3 h\u1ed3 s\u01a1 th\u1ec3 ch\u1ea5t.</p>}
            </section>
          </div> : <div className="member-edit-grid">
            <form className="form-card" onSubmit={saveAccount}>
              <h3>Thông tin tài khoản</h3>
              <label htmlFor="memberName">Họ tên</label>
              <input id="memberName" required maxLength={100} value={account.name} onChange={(event) => setAccount({ ...account, name: event.target.value })} />
              <label htmlFor="memberEmail">Email</label>
              <input id="memberEmail" type="email" required maxLength={150} value={account.email} onChange={(event) => setAccount({ ...account, email: event.target.value })} />
              <label htmlFor="memberDob">Ngày sinh</label>
              <input id="memberDob" type="date" max={new Date().toISOString().slice(0, 10)} value={account.dateOfBirth} onChange={(event) => setAccount({ ...account, dateOfBirth: event.target.value })} />
              <label htmlFor="memberGender">Giới tính (tối đa 1 ký tự)</label>
              <input id="memberGender" maxLength={1} value={account.gender} onChange={(event) => setAccount({ ...account, gender: event.target.value })} />
              <label htmlFor="memberPhone">Điện thoại</label>
              <input id="memberPhone" maxLength={20} value={account.phone} onChange={(event) => setAccount({ ...account, phone: event.target.value })} />
              <button type="submit" disabled={accountSaving}>{accountSaving ? "Đang lưu…" : "Lưu tài khoản"}</button>
            </form>
            <form className="form-card" onSubmit={saveProfile}>
              <h3>Member Profile</h3>
              <label htmlFor="profileHeight">Chiều cao (50–250 cm)</label>
              <input id="profileHeight" type="number" min="50" max="250" step="0.01" required value={profile.height} onChange={(event) => setProfile({ ...profile, height: event.target.value })} />
              <label htmlFor="profileWeight">Cân nặng (10–500 kg)</label>
              <input id="profileWeight" type="number" min="10" max="500" step="0.01" required value={profile.weight} onChange={(event) => setProfile({ ...profile, weight: event.target.value })} />
              <label htmlFor="profileGoal">Mục tiêu (tối đa 1000 ký tự)</label>
              <textarea id="profileGoal" maxLength={1000} value={profile.goal} onChange={(event) => setProfile({ ...profile, goal: event.target.value })} />
              <button type="submit" disabled={profileSaving}>{profileSaving ? "Đang lưu…" : selected.profile ? "Lưu hồ sơ" : "Tạo hồ sơ"}</button>
            </form>
          </div>}
        </section>}
          </aside>
        </div>}
      </main>
    </div>
  );
}
