import { useEffect, useMemo, useRef, useState } from "react";
import Sidebar from "../components/Sidebar";
import {
  createMemberConsultationRequestApi,
  getCurrentUserApi,
  getMyConsultationRequestsApi,

  getMyMemberSportLevelsApi,
  getMyRegistrationsApi,
  getMySkillAssessmentsApi,
  getPublicSportsApi,
} from "../api";
import { getMemberRelevantSports } from "../memberSportEligibility";
import { CONSULTATION_EMAIL_ERROR, normalizeConsultationEmail } from "../consultationEmail.js";
import "./Membertheme.css";
import "./MemberLevelsAssessments.css";

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
  const local = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value);
  const date = new Date(local ? `${value}+07:00` : value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
}

function formatShortDate(value) {
  if (!value) return "—";
  const local = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value);
  const date = new Date(local ? `${value}+07:00` : value);
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
      });
}

function requestMessage(error, fallback) {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Tài khoản không có quyền xem dữ liệu này.";
  return error?.message || fallback;
}

function normalizeLevel(rawLevel) {
  const upper = String(rawLevel || "BEGINNER").trim().toUpperCase();
  if (upper.includes("ADV") || upper.includes("NÂNG CAO")) return "ADVANCED";
  if (upper.includes("INT") || upper.includes("TRUNG CẤP")) return "INTERMEDIATE";
  return "BEGINNER";
}

function levelRank(rawLevel) {
  const norm = normalizeLevel(rawLevel);
  if (norm === "ADVANCED") return 3;
  if (norm === "INTERMEDIATE") return 2;
  return 1;
}

function getSportTheme(sportName = "", index = 0) {
  const lower = sportName.toLowerCase();
  if (lower.includes("cầu lông") || lower.includes("badminton")) {
    return {
      theme: "blue",
      subtitle: "Badminton Club & Training",
      stage1: "Cơ bản: Phát cầu & Giao lưu",
      stage2: "Trung cấp: Đập cầu, Phông sâu, Di chuyển",
      stage3: "Nâng cao: Chiến thuật thi đấu",
    };
  }
  if (lower.includes("bơi") || lower.includes("swim")) {
    return {
      theme: "cyan",
      subtitle: "Olympic Pool & Aqua Fitness",
      stage1: "Cơ bản: Làm quen nước & Bơi ếch",
      stage2: "Trung cấp: Bơi sải & Kỹ thuật thở",
      stage3: "Nâng cao: Bơi bướm & Sức bền",
    };
  }
  if (lower.includes("tennis") || lower.includes("quần vợt") || lower.includes("pickleball")) {
    return {
      theme: "lime",
      subtitle: "Sân tiêu chuẩn & Kỹ thuật",
      stage1: "Cơ bản: Cầm vợt & Forehand",
      stage2: "Trung cấp: Giao bóng & Volley",
      stage3: "Nâng cao: Đối kháng & Chiến thuật",
    };
  }
  if (lower.includes("gym") || lower.includes("fitness") || lower.includes("thể hình")) {
    return {
      theme: "amber",
      subtitle: "Thể hình & Thể lực chuyên sâu",
      stage1: "Cơ bản: Kỹ thuật form & Cơ nền",
      stage2: "Trung cấp: Tăng cơ & Kiểm soát tạ",
      stage3: "Nâng cao: Sức mạnh & Conditioning",
    };
  }
  if (lower.includes("yoga") || lower.includes("pilates")) {
    return {
      theme: "rose",
      subtitle: "Dẻo dai & Phục hồi cơ thể",
      stage1: "Cơ bản: Hít thở & Thăng bằng",
      stage2: "Trung cấp: Chuỗi Asana linh hoạt",
      stage3: "Nâng cao: Kiểm soát cơ lõi chuyên sâu",
    };
  }
  if (lower.includes("bóng rổ") || lower.includes("basketball") || lower.includes("bóng")) {
    return {
      theme: "orange",
      subtitle: "Indoor Basketball Arena",
      stage1: "Cơ bản: Nhồi bóng & Lên rổ",
      stage2: "Trung cấp: Ném rổ & Phối hợp",
      stage3: "Nâng cao: Chiến thuật 5v5",
    };
  }
  const themes = ["blue", "cyan", "lime", "amber", "rose", "orange"];
  return {
    theme: themes[index % themes.length],
    subtitle: "Đào tạo & Huấn luyện thể thao",
    stage1: "Cơ bản: Nền tảng & Làm quen",
    stage2: "Trung cấp: Kỹ thuật & Phối hợp",
    stage3: "Nâng cao: Chiến thuật thi đấu",
  };
}

export default function MemberLevelsAssessments({ token, role, onLogout, onNavigate }) {
  const [sports, setSports] = useState([]);
  const [levels, setLevels] = useState([]);
  const [assessments, setAssessments] = useState([]);
  const [consultations, setConsultations] = useState([]);
  const [registrations, setRegistrations] = useState([]);


  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errors, setErrors] = useState({});

  // Bước 1 (pickedSportId === "") -> Danh sách bộ môn; Bước 2 (pickedSportId !== "") -> Chi tiết bộ môn
  const [pickedSportId, setPickedSportId] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL"); // ALL | VERIFIED | UNVERIFIED
  const [searchQuery, setSearchQuery] = useState("");
  const [consultationScope, setConsultationScope] = useState("SPORT"); // SPORT | ALL

  // Modal gửi yêu cầu tư vấn / đăng ký đánh giá mới
  const [modalOpen, setModalOpen] = useState(false);
  const [modalSubmitting, setModalSubmitting] = useState(false);
  const [modalError, setModalError] = useState("");
  const [modalSuccess, setModalSuccess] = useState("");
  const submitLockRef = useRef(false);
  const [consultForm, setConsultForm] = useState({
    sportId: "",
    name: "",
    phone: "",
    email: "",
    experience: "",
    note: "",
  });

  useEffect(() => {
    let active = true;
    Promise.allSettled([
      getPublicSportsApi(),
      getMyMemberSportLevelsApi(token),
      getMySkillAssessmentsApi(token),
      getMyConsultationRequestsApi(token),
      token ? getCurrentUserApi(token) : Promise.resolve(null),
      getMyRegistrationsApi(token),

    ]).then((results) => {
      if (!active) return;
      const setters = [setSports, setLevels, setAssessments, setConsultations, null, setRegistrations];
      const keys = ["sports", "levels", "assessments", "consultations", null, "registrations"];
      const messages = [
        "Không tải được danh sách bộ môn đang hoạt động.",
        "Không tải được trình độ của bạn.",
        "Không tải được lịch sử đánh giá.",
        "Không tải được lịch sử yêu cầu tư vấn.",
      ];
      const nextErrors = {};
      messages.push(null, "Could not verify your confirmed course registrations.");
      for (let index = 0; index < setters.length; index += 1) {
        if (!setters[index]) continue;
        const result = results[index];
        if (result.status === "fulfilled" && Array.isArray(result.value)) {
          setters[index](result.value);
        } else {
          nextErrors[keys[index]] = requestMessage(result.status === "rejected" ? result.reason : null, messages[index]);
          setters[index]([]);
        }
      }
      if (results[4]?.status === "fulfilled" && results[4].value) {
        setProfile(results[4].value);
      }
      setErrors(nextErrors);

      setLoading(false);
    });
    return () => {
      active = false;
    };
  }, [token]);

  const sportNames = useMemo(
    () => new Map(sports.map((sport) => [Number(sport.id), sport.name])),
    [sports]
  );
  const levelsBySport = useMemo(
    () => new Map(levels.map((item) => [Number(item.sportId), item])),
    [levels]
  );

  const levelsAvailable = !errors.levels;
  const eligibilityAvailable = !errors.sports && !errors.registrations && !errors.assessments;
  const relevantSports = useMemo(
    () => eligibilityAvailable
      ? getMemberRelevantSports({ sports, registrations, assessments, memberId: profile?.id })
      : [],
    [eligibilityAvailable, sports, registrations, assessments, profile?.id]
  );

  // Tổng hợp dữ liệu cho từng bộ môn ở Bước 1
  const enrichedSports = useMemo(() => {
    return relevantSports.map((sport, index) => {
      const sportIdNum = Number(sport.id);
      const levelRecord = levelsBySport.get(sportIdNum);
      const isVerified = levelRecord?.verifiedByCoachId != null && Boolean(levelRecord?.verifiedAt);
      const currentLevel = !levelsAvailable
        ? "—"
        : isVerified
        ? normalizeLevel(levelRecord.level)
        : "BEGINNER";
      const rank = !levelsAvailable ? 1 : levelRank(currentLevel);

      const sportAssessments = assessments.filter((item) => Number(item.sportId) === sportIdNum);
      const sportConsultations = consultations.filter(
        (item) =>
          Number(item.sportId) === sportIdNum ||
          (item.sportName && sport.name && item.sportName.toLowerCase() === sport.name.toLowerCase())
      );

      const pendingCount =
        sportAssessments.filter((a) => String(a.status).toUpperCase() === "PENDING").length +
        sportConsultations.filter((c) =>
          ["PENDING", "IN_PROGRESS"].includes(String(c.status).toUpperCase())
        ).length;

      const latestTimestamp =
        levelRecord?.verifiedAt ||
        sportAssessments[0]?.assessedAt ||
        sportAssessments[0]?.createdAt ||
        sportConsultations[0]?.createdAt ||
        null;

      return {
        ...sport,
        index,
        levelRecord,
        isVerified,
        currentLevel,
        rank,
        sportAssessments,
        sportConsultations,
        pendingCount,
        latestTimestamp,
        meta: getSportTheme(sport.name, index),
      };
    });
  }, [relevantSports, levelsBySport, levelsAvailable, assessments, consultations]);

  const verifiedSportsCount = useMemo(
    () => enrichedSports.filter((s) => s.isVerified).length,
    [enrichedSports]
  );
  const unverifiedSportsCount = Math.max(0, enrichedSports.length - verifiedSportsCount);

  const pendingRequestsCount = useMemo(() => {
    return enrichedSports.reduce((total, sport) => total + sport.pendingCount, 0);
  }, [enrichedSports]);

  const filteredSports = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return enrichedSports.filter((sport) => {
      if (statusFilter === "VERIFIED" && !sport.isVerified) return false;
      if (statusFilter === "UNVERIFIED" && sport.isVerified) return false;
      if (!q) return true;
      const matchName = String(sport.name || "").toLowerCase().includes(q);
      const matchLevel = String(sport.currentLevel || "").toLowerCase().includes(q);
      const matchCoach =
        sport.levelRecord?.verifiedByCoachId != null &&
        `coach #${sport.levelRecord.verifiedByCoachId}`.includes(q);
      return matchName || matchLevel || matchCoach;
    });
  }, [enrichedSports, statusFilter, searchQuery]);

  // Bộ môn đang chọn ở Bước 2
  const selectedSport = useMemo(
    () => enrichedSports.find((sport) => String(sport.id) === String(pickedSportId)) || null,
    [enrichedSports, pickedSportId]
  );

  const sportAssessments = useMemo(() => {
    if (!selectedSport) return [];
    const q = searchQuery.trim().toLowerCase();
    return selectedSport.sportAssessments.filter((item) => {
      if (!q) return true;
      return (
        String(item.id).includes(q) ||
        String(item.assignedCoachId || "").includes(q) ||
        String(item.assessedLevel || "").toLowerCase().includes(q) ||
        String(item.notes || "").toLowerCase().includes(q)
      );
    });
  }, [selectedSport, searchQuery]);

  const displayedConsultations = useMemo(() => {
    const relevantIds = new Set(relevantSports.map((sport) => Number(sport.id)));
    const baseList =
      selectedSport && consultationScope === "SPORT"
        ? selectedSport.sportConsultations
        : consultations.filter((item) => relevantIds.has(Number(item.sportId)));
    const q = searchQuery.trim().toLowerCase();
    if (!q) return baseList;
    return baseList.filter(
      (item) =>
        String(item.id).includes(q) ||
        String(item.sportName || "").toLowerCase().includes(q) ||
        String(item.note || "").toLowerCase().includes(q) ||
        String(item.resolutionNote || "").toLowerCase().includes(q)
    );
  }, [selectedSport, consultationScope, consultations, relevantSports, searchQuery]);

  function handleSelectSport(sportId) {
    setPickedSportId(String(sportId));
    setConsultationScope("SPORT");
    setModalSuccess("");
    window.scrollTo?.({ top: 0, behavior: "smooth" });
  }

  function handleBackToDirectory() {
    setPickedSportId("");
    setModalSuccess("");
  }

  function openRequestModal(defaultSportId) {
    const targetSportId =
      defaultSportId || pickedSportId || (sports[0] ? String(sports[0].id) : "");
    setModalError("");
    setConsultForm({
      sportId: String(targetSportId),
      name: profile?.name || profile?.fullName || "",
      phone: profile?.phone || "",
      email: profile?.email || "",
      experience: "",
      note: "",
    });
    setModalOpen(true);
  }

  async function handleSubmitConsultation(event) {
    event.preventDefault();
    if (modalSubmitting || submitLockRef.current) return;
    if (!consultForm.name.trim() || !consultForm.phone.trim() || !consultForm.sportId) {
      setModalError("Vui lòng điền đầy đủ Họ tên, Số điện thoại và chọn Bộ môn.");
      return;
    }
    const email = normalizeConsultationEmail(consultForm.email);
    if (!email) {
      setModalError(CONSULTATION_EMAIL_ERROR);
      return;
    }
    submitLockRef.current = true;
    setModalSubmitting(true);
    setModalError("");
    try {
      await createMemberConsultationRequestApi(token, {
        name: consultForm.name.trim(),
        phone: consultForm.phone.trim(),
        email,
        sportId: Number(consultForm.sportId),
        age: null,
        experience: consultForm.experience.trim() || null,
        note:
          consultForm.note.trim() ||
          "Yêu cầu đăng ký kiểm tra đánh giá trình độ và tư vấn lộ trình tập luyện.",
      });
      const updatedConsultations = await getMyConsultationRequestsApi(token);
      setConsultations(Array.isArray(updatedConsultations) ? updatedConsultations : []);
      setModalOpen(false);
      setModalSuccess("Đã gửi yêu cầu tư vấn & đăng ký đánh giá trình độ thành công!");
    } catch (err) {
      setModalError(
        requestMessage(err, "Không thể gửi yêu cầu tư vấn. Vui lòng kiểm tra thông tin và thử lại.")
      );
    } finally {
      submitLockRef.current = false;
      setModalSubmitting(false);
    }
  }

  const latestCompletedAssessment = useMemo(() => {
    if (!selectedSport) return null;
    return (
      selectedSport.sportAssessments.find(
        (item) => String(item.status).toUpperCase() === "COMPLETED"
      ) ||
      selectedSport.sportAssessments[0] ||
      null
    );
  }, [selectedSport]);

  return (
    <div className="layout">
      <Sidebar
        role={role}
        activePage="member-levels"
        onNavigate={onNavigate}
        onLogout={onLogout}
      />

      <main className="main member-levels-page mla-workspace">
        {/* TOP UTILITY BAR */}
        <div className="mla-topbar">
          <div className="mla-topbar-left">
            <span className="mla-eyebrow-pill">SPORTCENTER / HỘI VIÊN</span>
            <span className="mla-crumb-sep">/</span>
            <button
              type="button"
              className={`mla-crumb-link ${!selectedSport ? "is-current" : ""}`}
              onClick={handleBackToDirectory}
            >
              Trình độ &amp; đánh giá năng lực thể thao
            </button>
            {selectedSport && (
              <>
                <span className="mla-crumb-sep">/</span>
                <span className="mla-crumb-active">
                  <span className="mla-crumb-dot" aria-hidden="true" />
                  {selectedSport.name}
                </span>
              </>
            )}
          </div>

          <div className="mla-topbar-right">
            <div className="mla-search-box">
              <svg
                className="mla-search-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                aria-hidden="true"
              >
                <circle cx="11" cy="11" r="8" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35" />
              </svg>
              <input
                type="search"
                className="mla-search-input"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Tìm bộ môn hoặc Coach..."
                aria-label="Tìm bộ môn hoặc Coach"
              />
            </div>

            <button
              type="button"
              className="mla-primary-pill-btn"
              onClick={() => openRequestModal(selectedSport?.id)}
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.3"
                aria-hidden="true"
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
              </svg>
              <span>Đăng ký đánh giá mới</span>
            </button>
          </div>
        </div>

        <div className="mla-container">
          {modalSuccess && (
            <div className="banner success mla-banner-success" role="status">
              <span>{modalSuccess}</span>
              <button
                type="button"
                className="mla-banner-dismiss"
                onClick={() => setModalSuccess("")}
                aria-label="Đóng thông báo"
              >
                ×
              </button>
            </div>
          )}

          {errors.sports && (
            <p className="banner error" role="alert">
              {errors.sports}
            </p>
          )}
          {(errors.registrations || errors.assessments) && (
            <p className="banner error" role="alert">{"Kh\u00f4ng th\u1ec3 x\u00e1c minh \u0111\u1ea7y \u0111\u1ee7 c\u00e1c m\u00f4n \u0111ang h\u1ecdc ho\u1eb7c \u0111\u00e1nh gi\u00e1 c\u1ee7a Coach. H\u00e3y t\u1ea3i l\u1ea1i trang \u0111\u1ec3 th\u1eed l\u1ea1i."}</p>
          )}
          {errors.levels && (
            <p className="banner error" role="alert">
              {errors.levels}
            </p>
          )}
          {errors.consultations && (
            <p className="banner error" role="alert">
              {errors.consultations}
            </p>
          )}

          {loading && (
            <div className="queue-state mla-loading-state" role="status">
              <span className="mla-spinner" aria-hidden="true" />
              <span>Đang tải thông tin trình độ và lịch sử đánh giá của bạn…</span>
            </div>
          )}

          {!loading && eligibilityAvailable && relevantSports.length === 0 && (
            <p className="queue-state">{"B\u1ed9 m\u00f4n s\u1ebd hi\u1ec3n sau khi b\u1ea1n tham gia m\u1ed9t l\u1edbp h\u1ecdc ho\u1eb7c \u0111\u01b0\u1ee3c Coach \u0111\u00e1nh gi\u00e1."}</p>
          )}

          {/* =====================================================================
              BƯỚC 1: DANH SÁCH CÁC BỘ MÔN (Khi chưa chọn bộ môn cụ thể)
              ===================================================================== */}
          {!loading && eligibilityAvailable && relevantSports.length > 0 && !selectedSport && (
            <div className="mla-step1-view">
              {/* 1. PAGE HEADER */}
              <header className="page-header mla-step1-header">
                <div className="mla-step1-heading">
                  <div className="mla-step-tag-row">
                    <span className="eyebrow mla-step-badge">BƯỚC 1: CHỌN BỘ MÔN</span>
                    <span className="mla-step-sublabel">Quy trình 2 bước</span>
                  </div>
                  <h1>Trình độ &amp; lịch sử đánh giá</h1>
                  <p className="subtitle">
                    Chọn một bộ môn bên dưới để xem chi tiết trình độ hiện tại, lịch sử đánh giá
                    của Coach và lịch sử tư vấn.
                  </p>
                </div>

                <div className="mla-stepper-pill" aria-label="Tiến trình xem hồ sơ">
                  <div className="mla-stepper-item is-active">
                    <span className="mla-stepper-num">1</span>
                    <span>Chọn môn</span>
                  </div>
                  <svg
                    className="mla-stepper-arrow"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    aria-hidden="true"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                  </svg>
                  <div className="mla-stepper-item is-muted">
                    <span className="mla-stepper-num">2</span>
                    <span>Xem hồ sơ &amp; Lịch sử</span>
                  </div>
                </div>
              </header>

              {/* 2. SUMMARY STRIP (3 KPI CARDS) */}
              <section className="mla-kpi-grid" aria-label="Thống kê nhanh trình độ">
                <div className="mla-kpi-card">
                  <div className="mla-kpi-left">
                    <div className="mla-kpi-icon blue">
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        aria-hidden="true"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"
                        />
                      </svg>
                    </div>
                    <div>
                      <p className="mla-kpi-label">Tổng số bộ môn</p>
                      <strong className="mla-kpi-value">
                        {relevantSports.length} bộ môn đang hoạt động
                      </strong>
                    </div>
                  </div>
                  <span className="mla-kpi-tag neutral">Đầy đủ</span>
                </div>

                <div className="mla-kpi-card">
                  <div className="mla-kpi-left">
                    <div className="mla-kpi-icon green">
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2.2"
                        aria-hidden="true"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
                        />
                      </svg>
                    </div>
                    <div>
                      <p className="mla-kpi-label">Đã xác minh trình độ</p>
                      <strong className="mla-kpi-value green">{verifiedSportsCount} bộ môn</strong>
                    </div>
                  </div>
                  <span className="mla-kpi-tag green">
                    <span className="mla-dot" />
                    Coach xác nhận
                  </span>
                </div>

                <div className="mla-kpi-card">
                  <div className="mla-kpi-left">
                    <div className="mla-kpi-icon amber">
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2.2"
                        aria-hidden="true"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                        />
                      </svg>
                    </div>
                    <div>
                      <p className="mla-kpi-label">Đang chờ đánh giá / tư vấn</p>
                      <strong className="mla-kpi-value amber">
                        {pendingRequestsCount} yêu cầu
                      </strong>
                    </div>
                  </div>
                  <span className="mla-kpi-tag amber">
                    <span className="mla-dot pulse" />
                    {pendingRequestsCount > 0 ? "Đang tiếp nhận" : "Sẵn sàng"}
                  </span>
                </div>
              </section>

              {/* 3. CATALOG FILTER BAR & SPORTS GRID */}
              <section className="member-levels-section mla-catalog-section">
                <div className="mla-catalog-bar">
                  <div className="mla-catalog-title">
                    <h2>Danh mục bộ môn theo dõi</h2>
                    <span className="mla-count-pill">{filteredSports.length}</span>
                  </div>

                  <div className="mla-filter-tabs" role="tablist" aria-label="Lọc bộ môn theo xác minh">
                    <button
                      type="button"
                      role="tab"
                      aria-selected={statusFilter === "ALL"}
                      className={`mla-filter-tab ${statusFilter === "ALL" ? "is-active" : ""}`}
                      onClick={() => setStatusFilter("ALL")}
                    >
                      Tất cả ({relevantSports.length})
                    </button>
                    <button
                      type="button"
                      role="tab"
                      aria-selected={statusFilter === "VERIFIED"}
                      className={`mla-filter-tab ${statusFilter === "VERIFIED" ? "is-active" : ""}`}
                      onClick={() => setStatusFilter("VERIFIED")}
                    >
                      Đã xác minh ({verifiedSportsCount})
                    </button>
                    <button
                      type="button"
                      role="tab"
                      aria-selected={statusFilter === "UNVERIFIED"}
                      className={`mla-filter-tab ${statusFilter === "UNVERIFIED" ? "is-active" : ""}`}
                      onClick={() => setStatusFilter("UNVERIFIED")}
                    >
                      Chưa xác minh ({unverifiedSportsCount})
                    </button>
                  </div>
                </div>

                {filteredSports.length === 0 ? (
                  <div className="queue-state">
                    Không tìm thấy bộ môn nào khớp với bộ lọc hiện tại.
                  </div>
                ) : (
                  <div className="mla-sports-grid">
                    {filteredSports.map((sport) => {
                      const levelClass =
                        sport.currentLevel === "ADVANCED"
                          ? "advanced"
                          : sport.currentLevel === "INTERMEDIATE"
                          ? "intermediate"
                          : "beginner";

                      return (
                        <article
                          key={sport.id}
                          className="mla-sport-card"
                          onClick={() => handleSelectSport(sport.id)}
                        >
                          <div className="mla-sport-card-body">
                            <div className="mla-sport-card-top">

                              {sport.isVerified ? (
                                <span className="mla-verify-chip verified">
                                  <svg
                                    viewBox="0 0 24 24"
                                    fill="none"
                                    stroke="currentColor"
                                    strokeWidth="2.5"
                                    aria-hidden="true"
                                  >
                                    <path
                                      strokeLinecap="round"
                                      strokeLinejoin="round"
                                      d="M5 13l4 4L19 7"
                                    />
                                  </svg>
                                  Coach đã xác minh
                                </span>
                              ) : (
                                <span className="mla-verify-chip unverified">
                                  <span className="mla-chip-dot" aria-hidden="true" />
                                  Mặc định · Chưa xác minh
                                </span>
                              )}
                            </div>

                            <div className="mla-sport-title-group">
                              <h3>{sport.name}</h3>
                              <p>{sport.description || sport.meta.subtitle}</p>
                            </div>

                            <div className={`mla-sport-level-box ${levelClass}`}>
                              <div className="mla-sport-level-head">
                                <span>TRÌNH ĐỘ HIỆN TẠI</span>
                                <strong className={`mla-level-pill ${levelClass}`}>
                                  {sport.currentLevel}
                                </strong>
                              </div>

                              <div className="mla-seg-bar" aria-hidden="true">
                                <span className={sport.rank >= 1 ? "is-filled" : ""} />
                                <span className={sport.rank >= 2 ? "is-filled" : ""} />
                                <span className={sport.rank >= 3 ? "is-filled" : ""} />
                              </div>

                              <div className="mla-seg-labels">
                                <span className={sport.rank === 1 ? "is-current" : ""}>
                                  Beginner
                                </span>
                                <span className={sport.rank === 2 ? "is-current" : ""}>
                                  Intermediate
                                </span>
                                <span className={sport.rank === 3 ? "is-current" : ""}>
                                  Advanced
                                </span>
                              </div>
                            </div>

                            <div className="mla-sport-facts">
                              <div className="mla-sport-fact-line">
                                {sport.sportAssessments.length > 0 ? (
                                  <>
                                    <svg
                                      className="icon-green"
                                      viewBox="0 0 24 24"
                                      fill="none"
                                      stroke="currentColor"
                                      strokeWidth="2"
                                      aria-hidden="true"
                                    >
                                      <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                                      />
                                    </svg>
                                    <span className="strong">
                                      {sport.sportAssessments.length} lần đánh giá ·{" "}
                                      {sport.sportConsultations.length} yêu cầu tư vấn
                                    </span>
                                  </>
                                ) : (
                                  <>
                                    <svg
                                      viewBox="0 0 24 24"
                                      fill="none"
                                      stroke="currentColor"
                                      strokeWidth="2"
                                      aria-hidden="true"
                                    >
                                      <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
                                      />
                                    </svg>
                                    <span>Chưa có bản ghi đánh giá của Coach</span>
                                  </>
                                )}
                              </div>

                              <div className="mla-sport-fact-line">
                                {sport.pendingCount > 0 ? (
                                  <>
                                    <svg
                                      className="icon-amber"
                                      viewBox="0 0 24 24"
                                      fill="none"
                                      stroke="currentColor"
                                      strokeWidth="2"
                                      aria-hidden="true"
                                    >
                                      <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
                                      />
                                    </svg>
                                    <span className="amber-text">
                                      {sport.pendingCount} yêu cầu đang chờ xử lý
                                    </span>
                                  </>
                                ) : sport.latestTimestamp ? (
                                  <>
                                    <svg
                                      viewBox="0 0 24 24"
                                      fill="none"
                                      stroke="currentColor"
                                      strokeWidth="2"
                                      aria-hidden="true"
                                    >
                                      <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
                                      />
                                    </svg>
                                    <span>
                                      Cập nhật gần nhất: {formatShortDate(sport.latestTimestamp)}
                                    </span>
                                  </>
                                ) : (
                                  <>
                                    <svg
                                      viewBox="0 0 24 24"
                                      fill="none"
                                      stroke="currentColor"
                                      strokeWidth="2"
                                      aria-hidden="true"
                                    >
                                      <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
                                      />
                                    </svg>
                                    <span>Sẵn sàng đặt lịch hẹn Coach</span>
                                  </>
                                )}
                              </div>
                            </div>
                          </div>

                          <button
                            type="button"
                            className="mla-sport-card-cta"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleSelectSport(sport.id);
                            }}
                          >
                            <span>Xem chi tiết &amp; lịch sử</span>
                            <svg
                              viewBox="0 0 24 24"
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="2.4"
                              aria-hidden="true"
                            >
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                d="M14 5l7 7m0 0l-7 7m7-7H3"
                              />
                            </svg>
                          </button>
                        </article>
                      );
                    })}
                  </div>
                )}
              </section>

              {/* 4. HELPER INFO BANNER */}
              <div className="mla-info-banner" role="note">
                <svg
                  className="mla-info-banner-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
                <p>
                  <strong>Lưu ý về quy chuẩn xác minh trình độ:</strong> Trình độ mặc định ban đầu
                  của hội viên là <strong>BEGINNER</strong>. Chỉ khi Huấn luyện viên (Coach) hoàn
                  thành buổi kiểm tra năng lực và gửi đánh giá chính thức, trình độ mới được cập
                  nhật sang <strong className="green-highlight">Coach đã xác minh</strong>. Chọn
                  bất kỳ bộ môn nào để xem chi tiết biểu đồ kỹ năng và lịch sử yêu cầu tư vấn tại
                  Bước 2.
                </p>
              </div>
            </div>
          )}

          {/* =====================================================================
              BƯỚC 2: CHI TIẾT TRÌNH ĐỘ & LỊCH SỬ CỦA BỘ MÔN ĐÃ CHỌN
              ===================================================================== */}
          {!loading && eligibilityAvailable && relevantSports.length > 0 && selectedSport && (
            <div className="mla-step2-view">
              {/* 1. STEP 2 NAVIGATION & QUICK SWITCHER */}
              <div className="mla-step2-nav">
                <div className="mla-step2-nav-left">
                  <button
                    type="button"
                    className="mla-back-btn"
                    onClick={handleBackToDirectory}
                  >
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2.2"
                      aria-hidden="true"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        d="M10 19l-7-7m0 0l7-7m-7 7h18"
                      />
                    </svg>
                    <span>Quay lại danh sách bộ môn</span>
                  </button>

                  <div className="mla-step2-breadcrumb">
                    <span className="mla-step2-badge">Bước 2: Chi tiết hồ sơ</span>
                    <span>Trình độ &amp; lịch sử đánh giá</span>
                    <span>/</span>
                    <strong>{selectedSport.name}</strong>
                  </div>
                </div>

                <div className="mla-quick-switcher">
                  <span className="mla-switcher-dot" aria-hidden="true" />
                  <label htmlFor="levelSportPicker">Đang xem:</label>
                  <select
                    id="levelSportPicker"
                    value={String(selectedSport.id)}
                    onChange={(event) => handleSelectSport(event.target.value)}
                  >
                    {relevantSports.map((sport) => (
                      <option key={sport.id} value={String(sport.id)}>
                        {sport.name} · Đổi bộ môn nhanh
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* 2. HERO CARD: TRÌNH ĐỘ HIỆN TẠI */}
              <article className="member-level-card mla-hero-card">

                <div className="mla-hero-grid">
                  {/* Cột trái: Trình độ & Lộ trình 3 bước */}
                  <div className="mla-hero-main">
                    <div className="mla-hero-badges">
                      <span className="eyebrow mla-hero-eyebrow">
                        <svg
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2.2"
                          aria-hidden="true"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            d="M13 10V3L4 14h7v7l9-11h-7z"
                          />
                        </svg>
                        TRÌNH ĐỘ HIỆN TẠI · BỘ MÔN {selectedSport.name.toUpperCase()}
                      </span>

                      {selectedSport.isVerified ? (
                        <span className="mla-hero-verify-pill verified">
                          <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path
                              fillRule="evenodd"
                              d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                              clipRule="evenodd"
                            />
                          </svg>
                          Coach đã xác minh
                        </span>
                      ) : (
                        <span className="mla-hero-verify-pill unverified">
                          Mặc định · Chưa xác minh
                        </span>
                      )}
                    </div>

                    <div className="mla-hero-title-block">
                      <div className="mla-hero-level-row">
                        <strong className="member-level-value mla-hero-level-text">
                          {selectedSport.currentLevel}
                        </strong>
                        <span className="mla-hero-tier-badge">
                          Bậc {selectedSport.rank} / 3
                        </span>
                      </div>
                      <h3 className="mla-sr-only">{selectedSport.name}</h3>

                      {!levelsAvailable ? (
                        <p className="mla-hero-desc">
                          Không thể xác định trình độ khi chưa tải được dữ liệu.
                        </p>
                      ) : selectedSport.isVerified ? (
                        <p className="mla-hero-desc">
                          Trình độ của bạn đã được huấn luyện viên xác minh thực tế qua bài kiểm
                          tra đánh giá kỹ năng chuẩn. Bạn đủ điều kiện đăng ký các lớp đào tạo
                          tương ứng của bộ môn {selectedSport.name}.
                        </p>
                      ) : (
                        <p className="mla-hero-desc">
                          Beginner (mặc định; chưa có bản ghi trình độ đã xác minh cho bộ môn
                          này). Bạn có thể đăng ký kiểm tra đánh giá đầu vào cùng Coach để cập nhật
                          hồ sơ năng lực.
                        </p>
                      )}
                    </div>

                    <div className="mla-hero-roadmap">
                      <div className="mla-roadmap-steps">
                        <span
                          className={`mla-roadmap-step ${
                            selectedSport.rank > 1
                              ? "is-done"
                              : selectedSport.rank === 1
                              ? "is-current"
                              : ""
                          }`}
                        >
                          {selectedSport.rank > 1 ? "✓ " : "● "}
                          1. BEGINNER ({selectedSport.rank > 1 ? "Đạt" : "Hiện tại"})
                        </span>

                        <span
                          className={`mla-roadmap-step ${
                            selectedSport.rank > 2
                              ? "is-done"
                              : selectedSport.rank === 2
                              ? "is-current"
                              : ""
                          }`}
                        >
                          {selectedSport.rank > 2
                            ? "✓ 2. INTERMEDIATE (Đạt)"
                            : selectedSport.rank === 2
                            ? "● 2. INTERMEDIATE (Hiện tại)"
                            : "2. INTERMEDIATE (Mục tiêu)"}
                        </span>

                        <span
                          className={`mla-roadmap-step ${
                            selectedSport.rank === 3 ? "is-current" : ""
                          }`}
                        >
                          {selectedSport.rank === 3
                            ? "● 3. ADVANCED (Hiện tại)"
                            : "3. ADVANCED (Mục tiêu)"}
                        </span>
                      </div>

                      <div className="mla-roadmap-bar" aria-hidden="true">
                        <span
                          className={
                            selectedSport.rank > 1
                              ? "seg-done"
                              : selectedSport.rank === 1
                              ? "seg-active"
                              : ""
                          }
                        />
                        <span
                          className={
                            selectedSport.rank > 2
                              ? "seg-done"
                              : selectedSport.rank === 2
                              ? "seg-active"
                              : ""
                          }
                        />
                        <span className={selectedSport.rank === 3 ? "seg-active" : ""} />
                      </div>

                      <div className="mla-roadmap-captions">
                        <span className={selectedSport.rank === 1 ? "is-bright" : ""}>
                          {selectedSport.meta.stage1}
                        </span>
                        <span className={selectedSport.rank === 2 ? "is-bright" : ""}>
                          {selectedSport.meta.stage2}
                        </span>
                        <span className={selectedSport.rank === 3 ? "is-bright" : ""}>
                          {selectedSport.meta.stage3}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Cột phải: Khung kính mờ Chứng nhận xác minh */}
                  <div className="mla-glass-card">
                    <div className="mla-glass-header">
                      <div className="mla-glass-header-left">
                        <div className="mla-glass-icon">
                          <svg
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                            aria-hidden="true"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
                            />
                          </svg>
                        </div>
                        <div>
                          <h4>CHỨNG NHẬN XÁC MINH</h4>
                          <p>Sports Center {selectedSport.name} Academy</p>
                        </div>
                      </div>
                      <span
                        className={`mla-glass-status ${
                          selectedSport.isVerified ? "verified" : "default"
                        }`}
                      >
                        {selectedSport.isVerified ? "HIỆU LỰC" : "MẶC ĐỊNH"}
                      </span>
                    </div>

                    <dl className="mla-glass-meta">
                      <div>
                        <dt>Coach xác minh:</dt>
                        <dd>
                          {selectedSport.isVerified ? (
                            <span className="mla-coach-pill">
                              <span>#{selectedSport.levelRecord.verifiedByCoachId}</span>
                              Coach #{selectedSport.levelRecord.verifiedByCoachId}
                            </span>
                          ) : (
                            "Chưa có Coach xác minh"
                          )}
                        </dd>
                      </div>
                      <div>
                        <dt>Thời gian xác minh:</dt>
                        <dd>
                          {selectedSport.isVerified
                            ? formatDate(selectedSport.levelRecord.verifiedAt)
                            : "Chưa xác minh"}
                        </dd>
                      </div>
                      <div>
                        <dt>Mã biên bản đánh giá:</dt>
                        <dd className="mono">
                          {latestCompletedAssessment
                            ? `#EVAL-${selectedSport.id}-${latestCompletedAssessment.id}`
                            : "—"}
                        </dd>
                      </div>
                      <div>
                        <dt>Tổng đợt đánh giá:</dt>
                        <dd className="amber">
                          {selectedSport.sportAssessments.length} đợt sát hạch
                        </dd>
                      </div>
                    </dl>

                    <button
                      type="button"
                      className="mla-glass-cta"
                      onClick={() => onNavigate?.("member-courses")}
                    >
                      <span>Xem khóa học {selectedSport.name} phù hợp</span>
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2.4"
                        aria-hidden="true"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M14 5l7 7m0 0l-7 7m7-7H3"
                        />
                      </svg>
                    </button>
                  </div>
                </div>
              </article>

              {/* 3. KHU VỰC LỊCH SỬ 2 CỘT (ĐÁNH GIÁ & TƯ VẤN) */}
              <div className="mla-history-grid">
                {/* CỘT TRÁI: LỊCH SỬ ĐÁNH GIÁ */}
                <section className="member-levels-section mla-history-panel">
                  <div className="mla-panel-header">
                    <div className="mla-panel-title-wrap">
                      <div className="mla-panel-icon blue">
                        <svg
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2"
                          aria-hidden="true"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"
                          />
                        </svg>
                      </div>
                      <div>
                        <h2>Lịch sử đánh giá – {selectedSport.name}</h2>
                        <p>
                          Các đợt sát hạch và biên bản nhận xét chuyên môn từ Huấn luyện viên
                        </p>
                      </div>
                    </div>
                    <span className="mla-badge-count blue">
                      {sportAssessments.length} bản ghi
                    </span>
                  </div>

                  {errors.assessments && (
                    <p className="banner error" role="alert">
                      {errors.assessments}
                    </p>
                  )}

                  {!errors.assessments && sportAssessments.length === 0 && (
                    <div className="queue-state mla-empty-box">
                      <p>Bạn chưa có đánh giá nào cho bộ môn này.</p>
                    </div>
                  )}

                  {sportAssessments.length > 0 && (
                    <div className="member-history-list mla-card-stack">
                      {sportAssessments.map((item) => (
                        <article
                          className="member-history-card mla-record-card"
                          key={item.id}
                        >
                          <div className="member-history-heading mla-record-head">
                            <div className="mla-record-title-group">
                              <span className="mla-record-id-box blue">#{item.id}</span>
                              <div>
                                <strong>Đánh giá #{item.id}</strong>
                                <span>
                                  {sportNames.get(Number(item.sportId)) ||
                                    `Bộ môn #${item.sportId}`}{" "}
                                  · Mã đợt: #EVAL-{item.id}
                                </span>
                              </div>
                            </div>
                            <span
                              className={`coach-assessment-status status-${String(
                                item.status
                              ).toLowerCase()}`}
                            >
                              {ASSESSMENT_STATUS[item.status] || item.status}
                            </span>
                          </div>

                          <dl className="member-history-facts mla-record-facts">
                            <div>
                              <dt>Ngày giao</dt>
                              <dd>{formatDate(item.createdAt)}</dd>
                            </div>
                            <div>
                              <dt>Coach phụ trách</dt>
                              <dd>
                                {item.assignedCoachId != null
                                  ? `Coach #${item.assignedCoachId}`
                                  : "Đang phân công"}
                              </dd>
                            </div>
                            <div>
                              <dt>Trình độ đánh giá</dt>
                              <dd className="highlight-blue">
                                {item.status === "COMPLETED"
                                  ? item.assessedLevel || "—"
                                  : "—"}
                              </dd>
                            </div>
                            <div>
                              <dt>
                                {item.status === "CANCELLED" ? "Ngày hủy" : "Ngày hoàn tất"}
                              </dt>
                              <dd>
                                {item.status === "CANCELLED" && item.cancelledAt
                                  ? formatDate(item.cancelledAt)
                                  : formatDate(item.assessedAt)}
                              </dd>
                            </div>
                          </dl>

                          {item.status === "PENDING" && (
                            <p className="mla-pending-hint">
                              Đánh giá đang chờ Coach thực hiện.
                            </p>
                          )}

                          {item.notes && (
                            <div className="member-history-notes mla-note-callout blue">
                              <div className="mla-note-header">
                                <strong>
                                  Ghi chú chuyên môn của Coach
                                  {item.assignedCoachId != null
                                    ? ` #${item.assignedCoachId}`
                                    : ""}
                                  :
                                </strong>
                                <span>Biên bản kiểm tra</span>
                              </div>
                              <p>{item.notes}</p>
                            </div>
                          )}
                        </article>
                      ))}
                    </div>
                  )}

                  <div className="mla-panel-helper">
                    <span>
                      Đánh giá tiếp theo có thể thực hiện khi bạn đăng ký kiểm tra nâng bậc cùng
                      Huấn luyện viên.
                    </span>
                    <button
                      type="button"
                      className="mla-inline-link"
                      onClick={() => openRequestModal(selectedSport.id)}
                    >
                      Đăng ký đánh giá →
                    </button>
                  </div>
                </section>

                {/* CỘT PHẢI: LỊCH SỬ YÊU CẦU TƯ VẤN */}
                <section className="member-levels-section mla-history-panel">
                  <div className="mla-panel-header">
                    <div className="mla-panel-title-wrap">
                      <div className="mla-panel-icon amber">
                        <svg
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2"
                          aria-hidden="true"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
                          />
                        </svg>
                      </div>
                      <div>
                        <h2>Lịch sử yêu cầu tư vấn</h2>
                        <p>Tư vấn lộ trình tập luyện, chọn khóa học và xếp lớp</p>
                      </div>
                    </div>

                    <div className="mla-consult-header-right">
                      {consultations.length > 0 && (
                        <div className="mla-mini-toggle" role="group" aria-label="Phạm vi tư vấn">
                          <button
                            type="button"
                            className={consultationScope === "SPORT" ? "is-active" : ""}
                            onClick={() => setConsultationScope("SPORT")}
                          >
                            {selectedSport.name} ({selectedSport.sportConsultations.length})
                          </button>
                          <button
                            type="button"
                            className={consultationScope === "ALL" ? "is-active" : ""}
                            onClick={() => setConsultationScope("ALL")}
                          >
                            Tất cả ({consultations.length})
                          </button>
                        </div>
                      )}
                      <span className="mla-badge-count amber">
                        {displayedConsultations.length} bản ghi
                      </span>
                    </div>
                  </div>

                  {errors.consultations && (
                    <p className="banner error" role="alert">
                      {errors.consultations}
                    </p>
                  )}

                  {!errors.consultations && displayedConsultations.length === 0 && (
                    <div className="queue-state mla-empty-box">
                      <p>
                        {consultationScope === "SPORT" && consultations.length > 0
                          ? `Bạn chưa gửi yêu cầu tư vấn nào cho bộ môn ${selectedSport.name}.`
                          : "Bạn chưa gửi yêu cầu tư vấn nào."}
                      </p>
                      {consultationScope === "SPORT" && consultations.length > 0 && (
                        <button
                          type="button"
                          className="mla-inline-link"
                          onClick={() => setConsultationScope("ALL")}
                        >
                          Xem tất cả {consultations.length} yêu cầu tư vấn →
                        </button>
                      )}
                    </div>
                  )}

                  {displayedConsultations.length > 0 && (
                    <div className="member-history-list mla-card-stack">
                      {displayedConsultations.map((item) => (
                        <article
                          className="member-history-card mla-record-card"
                          key={item.id}
                        >
                          <div className="member-history-heading mla-record-head">
                            <div className="mla-record-title-group">
                              <span className="mla-record-id-box amber">#{item.id}</span>
                              <div>
                                <strong>Yêu cầu tư vấn #{item.id}</strong>
                                <span>
                                  {item.sportName ||
                                    sportNames.get(Number(item.sportId)) ||
                                    `Bộ môn #${item.sportId}`}
                                </span>
                              </div>
                            </div>
                            <span
                              className={`coach-assessment-status status-${String(
                                item.status
                              ).toLowerCase()}`}
                            >
                              {CONSULTATION_STATUS[item.status] || item.status}
                            </span>
                          </div>

                          <dl className="member-history-facts mla-record-facts cols-2">
                            <div>
                              <dt>Ngày gửi yêu cầu</dt>
                              <dd>{formatDate(item.createdAt)}</dd>
                            </div>
                            <div>
                              <dt>Nhân viên tiếp nhận</dt>
                              <dd>
                                {item.handledBy != null
                                  ? `Lễ tân #${item.handledBy}`
                                  : "Đang chờ tiếp nhận"}
                              </dd>
                            </div>
                            {item.completedAt && (
                              <div className="span-2">
                                <dt>Ngày hoàn tất tư vấn</dt>
                                <dd className="highlight-green">
                                  {formatDate(item.completedAt)}
                                </dd>
                              </div>
                            )}
                          </dl>

                          {(item.note || item.resolutionNote) && (
                            <div className="member-history-notes mla-note-callout amber">
                              <div className="mla-note-header">
                                <strong>Nội dung tư vấn &amp; Giải pháp:</strong>
                              </div>
                              <p>{item.resolutionNote || item.note}</p>
                            </div>
                          )}
                        </article>
                      ))}
                    </div>
                  )}

                  {/* CTA GỬI YÊU CẦU TƯ VẤN MỚI */}
                  <div className="mla-consult-cta-box">
                    <div className="mla-consult-cta-icon" aria-hidden="true">
                      <svg
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M12 4v16m8-8H4"
                        />
                      </svg>
                    </div>
                    <h4>Cần hỗ trợ tư vấn về môn {selectedSport.name}?</h4>
                    <p>
                      Gửi yêu cầu để nhận phản hồi từ bộ phận chuyên môn và sắp xếp lịch kiểm tra
                      trình độ cùng Coach.
                    </p>
                    <button
                      type="button"
                      className="mla-consult-cta-btn"
                      onClick={() => openRequestModal(selectedSport.id)}
                    >
                      <span>Gửi yêu cầu tư vấn mới</span>
                    </button>
                  </div>
                </section>
              </div>

              {/* 4. FOOTER NOTE */}
              <footer className="mla-step2-footer">
                <div className="mla-footer-left">
                  <span className="mla-footer-dot" aria-hidden="true" />
                  <span>
                    Khu tập luyện {selectedSport.name} Sports Center mở cửa hàng ngày từ 06:00 đến
                    22:00
                  </span>
                </div>
                <div className="mla-footer-links">
                  <button type="button" onClick={() => onNavigate?.("member-courses")}>
                    Khóa học {selectedSport.name}
                  </button>
                  <span>•</span>
                  <button type="button" onClick={() => onNavigate?.("member-pt-booking")}>
                    Đặt lịch Coach PT
                  </button>
                  <span>•</span>
                  <button type="button" onClick={() => onNavigate?.("support-tickets")}>
                    Hỗ trợ &amp; Góp ý
                  </button>
                </div>
              </footer>
            </div>
          )}
        </div>

        {/* =====================================================================
            MODAL ĐĂNG KÝ ĐÁNH GIÁ / GỬI YÊU CẦU TƯ VẤN MỚI
            ===================================================================== */}
        {modalOpen && (
          <div
            className="mla-modal-backdrop"
            onMouseDown={(e) => {
              if (e.target === e.currentTarget && !modalSubmitting) setModalOpen(false);
            }}
          >
            <section
              className="mla-modal"
              role="dialog"
              aria-modal="true"
              aria-labelledby="mlaModalTitle"
            >
              <header className="mla-modal-header">
                <div>
                  <span className="mla-eyebrow-pill">SPORTCENTER / TƯ VẤN &amp; ĐÁNH GIÁ</span>
                  <h2 id="mlaModalTitle">Đăng ký đánh giá &amp; Tư vấn trình độ</h2>
                </div>
                <button
                  type="button"
                  className="mla-modal-close"
                  onClick={() => setModalOpen(false)}
                  disabled={modalSubmitting}
                  aria-label="Đóng hộp thoại"
                >
                  ×
                </button>
              </header>

              <form className="mla-modal-form" onSubmit={handleSubmitConsultation}>
                {modalError && (
                  <p className="banner error" role="alert">
                    {modalError}
                  </p>
                )}

                <div className="mla-modal-grid">
                  <label className="wide">
                    <span>Bộ môn cần đánh giá / tư vấn *</span>
                    <select
                      required
                      value={consultForm.sportId}
                      onChange={(e) =>
                        setConsultForm((prev) => ({ ...prev, sportId: e.target.value }))
                      }
                    >
                      {sports.map((sport) => (
                        <option key={sport.id} value={String(sport.id)}>
                          {sport.name}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label>
                    <span>Họ và tên *</span>
                    <input
                      type="text"
                      required
                      maxLength={100}
                      value={consultForm.name}
                      onChange={(e) =>
                        setConsultForm((prev) => ({ ...prev, name: e.target.value }))
                      }
                      placeholder="Nhập họ và tên"
                    />
                  </label>

                  <label>
                    <span>Số điện thoại *</span>
                    <input
                      type="tel"
                      required
                      maxLength={20}
                      value={consultForm.phone}
                      onChange={(e) =>
                        setConsultForm((prev) => ({ ...prev, phone: e.target.value }))
                      }
                      placeholder="Nhập số điện thoại"
                    />
                  </label>

                  <label className="wide">
                    <span>Email liên hệ</span>
                    <input
                      type="email"
                      required
                      maxLength={150}
                      value={consultForm.email}
                      onInvalid={(event) => { event.preventDefault(); setModalError(CONSULTATION_EMAIL_ERROR); }}
                      onChange={(e) =>
                        setConsultForm((prev) => ({ ...prev, email: e.target.value }))
                      }
                      placeholder="Email bắt buộc"
                    />
                  </label>

                  <label className="wide">
                    <span>Kinh nghiệm hiện tại</span>
                    <textarea
                      rows="2"
                      value={consultForm.experience}
                      onChange={(e) =>
                        setConsultForm((prev) => ({ ...prev, experience: e.target.value }))
                      }
                      placeholder="Ví dụ: Đã tập cơ bản 3 tháng, muốn kiểm tra lên lớp Trung cấp..."
                    />
                  </label>

                  <label className="wide">
                    <span>Ghi chú / Khung giờ mong muốn</span>
                    <textarea
                      rows="3"
                      maxLength={1000}
                      value={consultForm.note}
                      onChange={(e) =>
                        setConsultForm((prev) => ({ ...prev, note: e.target.value }))
                      }
                      placeholder="Ghi chú khung giờ rảnh hoặc yêu cầu tư vấn cụ thể..."
                    />
                  </label>
                </div>

                <div className="mla-modal-actions">
                  <button
                    type="button"
                    className="mla-btn-secondary"
                    onClick={() => setModalOpen(false)}
                    disabled={modalSubmitting}
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    className="mla-primary-pill-btn"
                    disabled={modalSubmitting}
                  >
                    {modalSubmitting ? "Đang gửi yêu cầu…" : "Gửi yêu cầu ngay"}
                  </button>
                </div>
              </form>
            </section>
          </div>
        )}
      </main>
    </div>
  );
}
