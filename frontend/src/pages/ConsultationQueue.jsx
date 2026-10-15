import { useCallback, useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  completeConsultationRequestApi,
  createConsultationMemberApi,
  getConsultationRequestApi,
  getConsultationRequestsApi,
  getCurrentUserApi,
  getUsersApi,
  linkConsultationMemberApi,
  takeConsultationRequestApi,
} from "../api";

const PAGE_SIZE = 20;
const STATUSES = ["", "PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"];
const STATUS_LABELS = { PENDING: "Ch\u1edd ti\u1ebfp nh\u1eadn", IN_PROGRESS: "\u0110ang x\u1eed l\u00fd", COMPLETED: "Ho\u00e0n t\u1ea5t", CANCELLED: "\u0110\u00e3 h\u1ee7y" };

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export default function ConsultationQueue({ token, role, onLogout, onNavigate }) {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [takingId, setTakingId] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [actorId, setActorId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState("");
  const [resolutionNote, setResolutionNote] = useState("");
  const [completingId, setCompletingId] = useState(null);
  const [memberCandidates, setMemberCandidates] = useState([]);
  const [candidateSearch, setCandidateSearch] = useState("");
  const [selectedMemberId, setSelectedMemberId] = useState("");
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  const [linkingMember, setLinkingMember] = useState(false);
  const [memberDateOfBirth, setMemberDateOfBirth] = useState("");
  const [memberPassword, setMemberPassword] = useState("");
  const [creatingMember, setCreatingMember] = useState(false);
  const allowedRole = role === "RECEPTIONIST" || role === "ADMIN";

  const loadQueue = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getConsultationRequestsApi(token, { status, page, size: PAGE_SIZE });
      setResult(data);
    } catch (requestError) {
      setResult(null);
      if (requestError.status === 401) {
        setError("Your session is missing or expired. Sign in again to view the queue.");
      } else if (requestError.status === 403) {
        setError("Your account is not authorized to view the consultation queue.");
      } else {
        setError(requestError.message || "Could not load consultation requests.");
      }
    } finally {
      setLoading(false);
    }
  }, [token, status, page]);

  useEffect(() => { Promise.resolve().then(loadQueue); }, [loadQueue]);

  useEffect(() => {
    if (!token || !allowedRole) return;
    getCurrentUserApi(token).then((user) => setActorId(user.id)).catch(() => setActorId(null));
  }, [token, allowedRole]);

  async function handleOpenDetail(requestId) {
    setDetail(null);
    setDetailError("");
    setDetailLoading(true);
    try {
      const request = await getConsultationRequestApi(token, requestId);
      setDetail(request);
      setResolutionNote(request.resolutionNote || "");
    } catch (requestError) {
      setDetailError(requestError.status === 403
        ? "Chỉ người được phân công hoặc ADMIN mới xem được yêu cầu này."
        : requestError.message || "Không tải được chi tiết yêu cầu.");
    } finally {
      setDetailLoading(false);
    }
  }

  async function loadMemberCandidates() {
    setLoadingCandidates(true);
    setDetailError("");
    try {
      const users = await getUsersApi(token);
      const members = Array.isArray(users)
        ? users.filter((user) => user.role === "MEMBER" && user.status === "ACTIVE")
        : [];
      setMemberCandidates(members);
      setSelectedMemberId("");
    } catch (requestError) {
      setDetailError(requestError.status === 403
        ? "Tài khoản không có quyền tra cứu thành viên."
        : requestError.message || "Không tải được danh sách thành viên.");
    } finally {
      setLoadingCandidates(false);
    }
  }

  async function handleLinkMember() {
    if (!detail || !selectedMemberId) return;
    setLinkingMember(true);
    setDetailError("");
    setNotice("");
    try {
      await linkConsultationMemberApi(token, detail.id, Number(selectedMemberId));
      await handleOpenDetail(detail.id);
      await loadQueue();
      setNotice("Đã liên kết yêu cầu với tài khoản MEMBER đã chọn.");
    } catch (requestError) {
      setDetailError(requestError.status === 409
        ? "Yêu cầu đã được liên kết hoặc tài khoản không còn đủ điều kiện. Hãy tải lại chi tiết."
        : requestError.status === 403
          ? "Chỉ người tiếp nhận hoặc ADMIN mới được liên kết yêu cầu này."
          : requestError.message || "Không thể liên kết thành viên.");
    } finally {
      setLinkingMember(false);
    }
  }

  async function handleCreateMember() {
    if (!detail || !memberDateOfBirth || memberPassword.length < 8) return;
    setCreatingMember(true);
    setDetailError("");
    setNotice("");
    try {
      await createConsultationMemberApi(token, detail.id, {
        dateOfBirth: memberDateOfBirth,
        password: memberPassword,
      });
      setMemberPassword("");
      setMemberDateOfBirth("");
      await handleOpenDetail(detail.id);
      await loadQueue();
      setNotice("Đã tạo và liên kết tài khoản MEMBER. Hãy chuyển thông tin đăng nhập cho khách qua kênh an toàn.");
    } catch (requestError) {
      setDetailError(requestError.status === 409
        ? "Email của yêu cầu đã có tài khoản. Hãy chọn liên kết tài khoản MEMBER hiện có."
        : requestError.status === 400
          ? requestError.message || "Yêu cầu email và ngày sinh hợp lệ để tạo tài khoản."
          : requestError.status === 403
            ? "Chỉ người tiếp nhận hoặc ADMIN mới được tạo thành viên từ yêu cầu này."
            : requestError.message || "Không thể tạo tài khoản thành viên.");
    } finally {
      setCreatingMember(false);
    }
  }

  async function handleComplete() {
    if (!detail || detail.status !== "IN_PROGRESS") return;
    setCompletingId(detail.id);
    setDetailError("");
    setNotice("");
    try {
      await completeConsultationRequestApi(token, detail.id, resolutionNote.trim());
      const refreshed = await getConsultationRequestApi(token, detail.id);
      setDetail(refreshed);
      setNotice("Đã hoàn tất cuộc tư vấn. Việc đăng ký khóa học hoặc thanh toán là quy trình riêng.");
      await loadQueue();
    } catch (requestError) {
      setDetailError(requestError.status === 409
        ? "Yêu cầu đã thay đổi trạng thái. Hãy tải lại chi tiết trước khi tiếp tục."
        : requestError.status === 403
          ? "Chỉ người được phân công hoặc ADMIN mới có thể hoàn tất yêu cầu này."
          : requestError.message || "Không thể hoàn tất yêu cầu.");
    } finally {
      setCompletingId(null);
    }
  }

  async function handleTake(requestId) {
    setTakingId(requestId);
    setError("");
    setNotice("");
    try {
      await takeConsultationRequestApi(token, requestId);
      setNotice("Request #" + requestId + " was taken.");
      await loadQueue();
    } catch (requestError) {
      if (requestError.status === 409) {
        await loadQueue();
        setError("This request was already taken or changed by another staff member. The queue has been refreshed.");
      } else if (requestError.status === 401) {
        setError("Your session is missing or expired. Sign in again to continue.");
      } else if (requestError.status === 403) {
        setError("Your account is not authorized to take this request.");
      } else {
        setError(requestError.message || "Could not take this request.");
      }
    } finally {
      setTakingId(null);
    }
  }

  const requests = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;

  return (
    <div className="layout">
      <Sidebar role={role} activePage="consultations" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main">
        <h1>{"Y\u00eau c\u1ea7u t\u01b0 v\u1ea5n"}</h1>
        <p className="subtitle">{"Ti\u1ebfp nh\u1eadn v\u00e0 theo d\u00f5i c\u00e1c y\u00eau c\u1ea7u t\u01b0 v\u1ea5n c\u1ee7a kh\u00e1ch h\u00e0ng."}</p>
        {!allowedRole && <div className="banner error" role="alert">Only Receptionist and Admin accounts can access this queue.</div>}
        {notice && <div className="banner success" role="status">{notice}</div>}
        {error && <div className="banner error" role="alert">{error}</div>}

        <div className="queue-toolbar">
          <div className="field queue-filter">
            <label htmlFor="consultationStatus">{"Tr\u1ea1ng th\u00e1i"}</label>
            <select id="consultationStatus" value={status}
              onChange={(event) => { setStatus(event.target.value); setPage(0); setNotice(""); }}
              disabled={!allowedRole}>
              {STATUSES.map((value) => <option key={value || "ALL"} value={value}>{value ? STATUS_LABELS[value] : "T\u1ea5t c\u1ea3 tr\u1ea1ng th\u00e1i"}</option>)}
            </select>
          </div>
          <button className="queue-refresh" type="button" onClick={loadQueue} disabled={loading || !allowedRole}>
            {loading ? "\u0110ang t\u1ea3i\u2026" : "L\u00e0m m\u1edbi"}
          </button>
        </div>

        {loading ? (
          <div className="queue-state" role="status">{"\u0110ang t\u1ea3i danh s\u00e1ch y\u00eau c\u1ea7u t\u01b0 v\u1ea5n\u2026"}</div>
        ) : error && !result ? (
          <div className="queue-state">{"Kh\u00f4ng th\u1ec3 t\u1ea3i danh s\u00e1ch. H\u00e3y ki\u1ec3m tra quy\u1ec1n truy c\u1eadp ho\u1eb7c th\u1eed l\u00e0m m\u1edbi."}</div>
        ) : requests.length === 0 ? (
          <div className="queue-state">{"Kh\u00f4ng t\u00ecm th\u1ea5y y\u00eau c\u1ea7u t\u01b0 v\u1ea5n n\u00e0o."}</div>
        ) : (
          <>
            <div className="queue-table-wrap">
              <table className="queue-table">
                <thead><tr>
                  <th>{"M\u00e3 y\u00eau c\u1ea7u"}</th><th>{"Kh\u00e1ch h\u00e0ng"}</th><th>{"\u0110i\u1ec7n tho\u1ea1i"}</th><th>{"B\u1ed9 m\u00f4n"}</th>
                  <th>{"Tr\u1ea1ng th\u00e1i"}</th><th>{"Ng\u00e0y t\u1ea1o"}</th><th>{"Ng\u01b0\u1eddi ti\u1ebfp nh\u1eadn"}</th><th>{"Thao t\u00e1c"}</th>
                </tr></thead>
                <tbody>
                  {requests.map((request) => (
                    <tr key={request.id}>
                      <td>#{request.id}</td>
                      <td>{request.name}</td>
                      <td>{request.phone}</td>
                      <td>{request.sportName || "—"}</td>
                      <td><span className={"status-pill status-" + String(request.status).toLowerCase()}>{STATUS_LABELS[request.status] || request.status}</span></td>
                      <td>{formatDate(request.createdAt)}</td>
                      <td>{request.handledBy == null ? "—" : "#" + request.handledBy}</td>
                      <td className="queue-actions">
                        {(role === "ADMIN" || (actorId != null && request.handledBy === actorId)) && (
                          <button type="button" onClick={() => handleOpenDetail(request.id)}>Chi tiết</button>
                        )}
                        {request.status === "PENDING" && allowedRole && (
                          <button className="queue-take" type="button" onClick={() => handleTake(request.id)} disabled={takingId !== null}>
                            {takingId === request.id ? "Đang tiếp nhận…" : "Tiếp nhận"}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="queue-pagination">
              <span>{"Trang "}{page + 1}/{Math.max(totalPages, 1)} | {result?.totalElements ?? requests.length}{" y\u00eau c\u1ea7u"}</span>
              <div>
                <button type="button" onClick={() => setPage((current) => Math.max(0, current - 1))} disabled={page <= 0 || loading}>{"Tr\u01b0\u1edbc"}</button>
                <button type="button" onClick={() => setPage((current) => current + 1)} disabled={loading || totalPages === 0 || page + 1 >= totalPages}>Sau</button>
              </div>
            </div>
          </>
        )}

        {detailLoading && <div className="queue-state" role="status">Đang tải chi tiết yêu cầu…</div>}
        {detailError && <div className="banner error" role="alert">{detailError}</div>}
        {detail && (
          <section className="consultation-detail" aria-labelledby="consultation-detail-title">
            <div className="consultation-detail-heading">
              <div><p className="subtitle">Yêu cầu #{detail.id}</p><h2 id="consultation-detail-title">{detail.name}</h2></div>
              <button type="button" onClick={() => { setDetail(null); setDetailError(""); }}>Đóng</button>
            </div>
            <dl className="consultation-detail-grid">
              <div><dt>Điện thoại</dt><dd>{detail.phone}</dd></div>
              <div><dt>Email</dt><dd>{detail.email || "—"}</dd></div>
              <div><dt>Bộ môn</dt><dd>{detail.sportName}</dd></div>
              <div><dt>Trạng thái</dt><dd><span className={"status-pill status-" + String(detail.status).toLowerCase()}>{STATUS_LABELS[detail.status] || detail.status}</span></dd></div>
              <div><dt>Thành viên liên kết</dt><dd>{detail.memberId ? `#${detail.memberId}` : "Chưa liên kết"}</dd></div>
              <div><dt>Tiếp nhận bởi</dt><dd>{detail.handledBy ? `#${detail.handledBy}` : "—"}</dd></div>
              <div><dt>Kinh nghiệm</dt><dd>{detail.experience || "—"}</dd></div>
              <div><dt>Ghi chú khách hàng</dt><dd>{detail.note || "—"}</dd></div>
            </dl>
            {detail.status === "IN_PROGRESS" && (role === "ADMIN" || (role === "RECEPTIONIST" && detail.handledBy === actorId)) && !detail.memberId && (
              <div className="consultation-member-link-form">
                <h3>Liên kết tài khoản thành viên</h3>
                <p>Chọn và xác minh trực tiếp tài khoản MEMBER; hệ thống không tự ghép theo email hoặc số điện thoại.</p>
                <button type="button" onClick={loadMemberCandidates} disabled={loadingCandidates}>
                  {loadingCandidates ? "Đang tải…" : "Tìm thành viên hiện có"}
                </button>
                {memberCandidates.length > 0 && (
                  <>
                    <label htmlFor="memberSearch">Tìm theo tên, email hoặc ID</label>
                    <input id="memberSearch" value={candidateSearch} onChange={(event) => setCandidateSearch(event.target.value)} />
                    <label htmlFor="memberCandidate">Tài khoản ACTIVE MEMBER</label>
                    <select id="memberCandidate" value={selectedMemberId} onChange={(event) => setSelectedMemberId(event.target.value)}>
                      <option value="">Chọn tài khoản đã xác minh</option>
                      {memberCandidates.filter((user) => (user.name + " " + user.email + " " + user.id).toLowerCase().includes(candidateSearch.toLowerCase()))
                        .map((user) => <option key={user.id} value={user.id}>#{user.id} · {user.name} · {user.email}</option>)}
                    </select>
                    <button type="button" onClick={handleLinkMember} disabled={linkingMember || !selectedMemberId}>
                      {linkingMember ? "Đang liên kết…" : "Liên kết tài khoản đã chọn"}
                    </button>
                  </>
                )}
                {memberCandidates.length === 0 && !loadingCandidates && <p>Chưa tải danh sách, hoặc không có ACTIVE MEMBER phù hợp.</p>}
                {detail.email ? (
                  <form onSubmit={(event) => { event.preventDefault(); handleCreateMember(); }}>
                    <h4>Hoặc đăng ký tài khoản MEMBER mới</h4>
                    <p>Email dùng để đăng nhập: <strong>{detail.email}</strong></p>
                    <label htmlFor="newMemberDob">Ngày sinh</label>
                    <input id="newMemberDob" type="date" required max={new Date().toISOString().slice(0, 10)} value={memberDateOfBirth} onChange={(event) => setMemberDateOfBirth(event.target.value)} />
                    <label htmlFor="newMemberPassword">Mật khẩu khởi tạo (tối thiểu 8 ký tự)</label>
                    <input id="newMemberPassword" type="password" autoComplete="new-password" minLength={8} required value={memberPassword} onChange={(event) => setMemberPassword(event.target.value)} />
                    <button type="submit" disabled={creatingMember || memberPassword.length < 8 || !memberDateOfBirth}>
                      {creatingMember ? "Đang tạo tài khoản…" : "Tạo và liên kết thành viên"}
                    </button>
                  </form>
                ) : <p>Yêu cầu chưa có email. Hãy liên kết một tài khoản MEMBER hiện có.</p>}
              </div>
            )}
            {detail.status === "IN_PROGRESS" && (role === "ADMIN" || detail.handledBy === actorId) && (
              <div className="consultation-complete-form">
                <label htmlFor="resolutionNote">Ghi chú kết quả tư vấn (không bắt buộc)</label>
                <textarea id="resolutionNote" maxLength={1000} value={resolutionNote} onChange={(event) => setResolutionNote(event.target.value)} />
                <button type="button" onClick={handleComplete} disabled={completingId === detail.id}>
                  {completingId === detail.id ? "Đang hoàn tất…" : "Hoàn tất tư vấn"}
                </button>
              </div>
            )}
          </section>
        )}
      </main>
    </div>
  );
}

