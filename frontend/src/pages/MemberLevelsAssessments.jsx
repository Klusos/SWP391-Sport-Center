import { useEffect, useMemo, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  getMyConsultationRequestsApi,
  getMyMemberSportLevelsApi,
  getMySkillAssessmentsApi,
  getPublicSportsApi,
} from "../api";

const ASSESSMENT_STATUS = {
  PENDING: "Đang chờ Coach đánh giá",
  COMPLETED: "Đã hoàn tất",
  CANCELLED: "Đã hủy",
};
const CONSULTATION_STATUS = {
  PENDING: "Đang chờ tiếp nhận",
  IN_PROGRESS: "Đang được tư vấn",
  COMPLETED: "Đã hoàn tất tư vấn",
  CANCELLED: "Đã hủy",
};

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function requestMessage(error, fallback) {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Tài khoản không có quyền xem dữ liệu này.";
  return error?.message || fallback;
}

export default function MemberLevelsAssessments({ token, role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]);
  const [levels, setLevels] = useState([]);
  const [assessments, setAssessments] = useState([]);
  const [consultations, setConsultations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    let active = true;
    Promise.allSettled([
      getPublicSportsApi(),
      getMyMemberSportLevelsApi(token),
      getMySkillAssessmentsApi(token),
      getMyConsultationRequestsApi(token),
    ]).then((results) => {
      if (!active) return;
      const setters = [setSports, setLevels, setAssessments, setConsultations];
      const keys = ["sports", "levels", "assessments", "consultations"];
      const messages = [
        "Không tải được danh sách bộ môn đang hoạt động.",
        "Không tải được trình độ của bạn.",
        "Không tải được lịch sử đánh giá.",
        "Không tải được lịch sử yêu cầu tư vấn.",
      ];
      const nextErrors = {};
      results.forEach((result, index) => {
        if (result.status === "fulfilled") {
          setters[index](Array.isArray(result.value) ? result.value : []);
        } else {
          nextErrors[keys[index]] = requestMessage(result.reason, messages[index]);
          setters[index]([]);
        }
      });
      setErrors(nextErrors);
      setLoading(false);
    });
    return () => { active = false; };
  }, [token]);

  const sportNames = useMemo(() => new Map(sports.map((sport) => [Number(sport.id), sport.name])), [sports]);
  const levelsBySport = useMemo(() => new Map(levels.map((item) => [Number(item.sportId), item])), [levels]);

  return <div className="layout">
    <Sidebar role={role} activePage="member-levels" onNavigate={onNavigate} onLogout={onLogout} />
    <main className="main member-levels-page">
      <header className="page-header">
        <p className="eyebrow">SPORTCENTER / MEMBER</p>
        <h1>Trình độ &amp; lịch sử đánh giá</h1>
        <p className="subtitle">Theo dõi trình độ riêng theo từng bộ môn và kết quả đánh giá của Coach.</p>
      </header>

      {loading && <div className="queue-state" role="status">Đang tải thông tin của bạn…</div>}

      <section className="member-levels-section">
        <h2>Trình độ theo bộ môn</h2>
        {errors.sports && <p className="banner error" role="alert">{errors.sports}</p>}
        {errors.levels && <p className="banner error" role="alert">{errors.levels}</p>}
        {!loading && !errors.sports && sports.length === 0 && <p className="queue-state">Hiện chưa có bộ môn đang hoạt động.</p>}
        {!loading && sports.length > 0 && <div className="member-level-grid">
          {sports.map((sport) => {
            const level = levelsBySport.get(Number(sport.id));
            const verified = level?.verifiedByCoachId != null && Boolean(level?.verifiedAt);
            const levelsAvailable = !errors.levels;
            return <article className="member-level-card" key={sport.id}>
              <div><p className="eyebrow">BỘ MÔN #{sport.id}</p><h3>{sport.name}</h3></div>
              <strong className="member-level-value">{!levelsAvailable ? "—" : verified ? level.level : "BEGINNER"}</strong>
              {!levelsAvailable ? <p>Không thể xác định trình độ khi chưa tải được dữ liệu.</p>
                : verified ? <p>Coach #{level.verifiedByCoachId} xác minh · {formatDate(level.verifiedAt)}</p>
                  : <p>Beginner (mặc định; chưa có bản ghi trình độ đã xác minh cho bộ môn này).</p>}
            </article>;
          })}
        </div>}
      </section>

      <section className="member-levels-section">
        <h2>Lịch sử Skill Assessment</h2>
        {errors.assessments && <p className="banner error" role="alert">{errors.assessments}</p>}
        {!loading && !errors.assessments && assessments.length === 0 && <p className="queue-state">Bạn chưa có assessment nào.</p>}
        {!loading && assessments.length > 0 && <div className="member-history-list">
          {assessments.map((item) => <article className="member-history-card" key={item.id}>
            <div className="member-history-heading">
              <div><strong>Assessment #{item.id}</strong><span>{sportNames.get(Number(item.sportId)) || `Bộ môn #${item.sportId}`}</span></div>
              <span className={`coach-assessment-status status-${String(item.status).toLowerCase()}`}>
                {ASSESSMENT_STATUS[item.status] || item.status}
              </span>
            </div>
            <dl className="member-history-facts">
              <div><dt>Ngày giao</dt><dd>{formatDate(item.createdAt)}</dd></div>
              {item.assignedCoachId != null && <div><dt>Coach phụ trách</dt><dd>#{item.assignedCoachId}</dd></div>}
              {item.status === "COMPLETED" && <div><dt>Trình độ đánh giá</dt><dd>{item.assessedLevel || "—"}</dd></div>}
              {item.assessedAt && <div><dt>Ngày hoàn tất</dt><dd>{formatDate(item.assessedAt)}</dd></div>}
              {item.status === "CANCELLED" && item.cancelledAt && <div><dt>Ngày hủy</dt><dd>{formatDate(item.cancelledAt)}</dd></div>}
            </dl>
            {item.status === "PENDING" && <p>Assessment đang chờ Coach đánh giá.</p>}
            {item.notes && <p className="member-history-notes"><strong>Ghi chú:</strong> {item.notes}</p>}
          </article>)}
        </div>}
      </section>

      <section className="member-levels-section">
        <h2>Lịch sử yêu cầu tư vấn</h2>
        {errors.consultations && <p className="banner error" role="alert">{errors.consultations}</p>}
        {!loading && !errors.consultations && consultations.length === 0 && <p className="queue-state">Bạn chưa gửi yêu cầu tư vấn nào.</p>}
        {!loading && consultations.length > 0 && <div className="member-history-list">
          {consultations.map((item) => <article className="member-history-card" key={item.id}>
            <div className="member-history-heading">
              <div><strong>Yêu cầu tư vấn #{item.id}</strong><span>{item.sportName || `Bộ môn #${item.sportId}`}</span></div>
              <span className={`coach-assessment-status status-${String(item.status).toLowerCase()}`}>
                {CONSULTATION_STATUS[item.status] || item.status}
              </span>
            </div>
            <dl className="member-history-facts">
              <div><dt>Ngày gửi</dt><dd>{formatDate(item.createdAt)}</dd></div>
              {item.handledBy != null && <div><dt>Nhân viên tiếp nhận</dt><dd>#{item.handledBy}</dd></div>}
              {item.completedAt && <div><dt>Ngày hoàn tất</dt><dd>{formatDate(item.completedAt)}</dd></div>}
            </dl>
            {(item.note || item.resolutionNote) && <p className="member-history-notes">{item.resolutionNote || item.note}</p>}
          </article>)}
        </div>}
      </section>
    </main>
  </div>;
}
