import { useCallback, useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
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
      <main className="main">
        <h1>Danh sách thành viên</h1>
        <p className="subtitle">Tra cứu tài khoản MEMBER và Member Profile.</p>
        {!allowed && <div className="banner error" role="alert">Chỉ ADMIN và RECEPTIONIST được truy cập trang này.</div>}
        {notice && <div className="banner success" role="status">{notice}</div>}
        {error && <div className="banner error" role="alert">{error}</div>}
        {forbidden && <p className="queue-state">Danh sách thành viên hiện không khả dụng.</p>}

        {allowed && <>
          <div className="member-list-toolbar">
            <label htmlFor="memberSearch">Tìm tên, email, số điện thoại, ID hoặc trạng thái</label>
            <input id="memberSearch" type="search" value={query}
              onChange={(event) => { setQuery(event.target.value); setPage(0); setSelected(null); }} />
            <button type="button" onClick={loadMembers} disabled={loading}>{loading ? "Đang tải…" : "Làm mới"}</button>
            {isAdmin && <button type="button" onClick={() => onNavigate("register")}>Đăng ký MEMBER</button>}
          </div>

          {loading ? <div className="queue-state" role="status">Đang tải danh sách thành viên…</div>
            : filtered.length === 0 ? <div className="queue-state">Không tìm thấy thành viên phù hợp.</div>
              : <>
                <div className="queue-table-wrap">
                  <table className="queue-table member-table">
                    <thead><tr><th>ID</th><th>Họ tên</th><th>Email</th><th>Điện thoại</th><th>Tài khoản</th><th>Hồ sơ</th><th>Thao tác</th></tr></thead>
                    <tbody>{pageMembers.map((member) => {
                      const memberProfile = profiles.find((item) => item.memberId === member.id);
                      return <tr key={member.id}>
                        <td>#{member.id}</td><td>{member.name}</td><td>{member.email}</td><td>{member.phone || "—"}</td>
                        <td><span className={"status-pill status-" + String(member.status).toLowerCase()}>{member.status}</span></td>
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
        </>}

        {detailLoading && <div className="queue-state" role="status">Đang tải chi tiết…</div>}
        {detailError && <div className="banner error" role="alert">{detailError}</div>}
        {selected && <section className="member-detail form-card" aria-labelledby="member-detail-title">
          <div className="member-detail-heading">
            <div><p className="subtitle">Tài khoản MEMBER #{selected.user.id}</p><h2 id="member-detail-title">{selected.user.name}</h2></div>
            <div>
              {isAdmin && <button type="button" onClick={() => setEditing((value) => !value)}>{editing ? "Đóng chỉnh sửa" : "Chỉnh sửa"}</button>}
              {isAdmin && selected.user.status === "ACTIVE" && <button type="button" onClick={deactivate} disabled={deactivating}>{deactivating ? "Đang xử lý…" : "Vô hiệu hóa"}</button>}
              <button type="button" onClick={() => setSelected(null)}>Đóng</button>
            </div>
          </div>

          {!editing ? <dl className="member-detail-grid">
            <div><dt>Email</dt><dd>{selected.user.email || "—"}</dd></div>
            <div><dt>Điện thoại</dt><dd>{selected.user.phone || "—"}</dd></div>
            <div><dt>Ngày sinh</dt><dd>{selected.user.dateOfBirth || "—"}</dd></div>
            <div><dt>Giới tính</dt><dd>{selected.user.gender || "—"}</dd></div>
            <div><dt>Trạng thái tài khoản</dt><dd>{selected.user.status}</dd></div>
            <div><dt>Trình độ thể chất</dt><dd>{selected.profile ? `${selected.profile.height} cm · ${selected.profile.weight} kg` : "Chưa có hồ sơ"}</dd></div>
            {selected.profile && <div><dt>Mục tiêu</dt><dd>{selected.profile.goal || "—"}</dd></div>}
          </dl> : <div className="member-edit-grid">
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
      </main>
    </div>
  );
}
