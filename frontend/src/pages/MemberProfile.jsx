
import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import "./MemberProfile.css";

import {
  getCurrentUserApi,
  getMyMemberProfileApi,
  saveMyMemberProfileApi,
} from "../api";

const GENDER = {
  M: "Nam",
  F: "Nữ",
};

// Giới hạn dùng để trực quan hóa thanh tiến độ.
// Đây không phải giới hạn sức khỏe hay mục tiêu cá nhân.
const HEIGHT_SCALE_MAX = 200;
const WEIGHT_SCALE_MAX = 100;
const BMI_SCALE_MAX = 40;

function getProgress(value, max) {
  const number = Number(value);

  if (!Number.isFinite(number) || number <= 0) {
    return 0;
  }

  return Math.min(100, Math.max(0, (number / max) * 100));
}

function getBmiLabel(bmi) {
  if (bmi == null) return "Chưa có dữ liệu";
  if (bmi < 18.5) return "Thiếu cân";
  if (bmi < 25) return "Bình thường";
  if (bmi < 30) return "Thừa cân";
  return "Béo phì";
}

function formatNumber(value) {
  if (value === "" || value == null) return "--";

  const number = Number(value);

  if (!Number.isFinite(number)) return "--";

  return Number.isInteger(number)
    ? String(number)
    : String(number);
}

function ProfileIcon({ type }) {
  const common = {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.7,
    strokeLinecap: "round",
    strokeLinejoin: "round",
    "aria-hidden": true,
  };

  switch (type) {
    case "user":
      return (
        <svg {...common}>
          <circle cx="12" cy="8" r="4" />
          <path d="M5 21v-2a7 7 0 0 1 14 0v2" />
        </svg>
      );

    case "email":
      return (
        <svg {...common}>
          <rect x="3" y="5" width="18" height="14" rx="2" />
          <path d="m3 7 9 6 9-6" />
        </svg>
      );

    case "phone":
      return (
        <svg {...common}>
          <path d="M5 3h4l2 5-3 2a15 15 0 0 0 6 6l2-3 5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 5a2 2 0 0 1 2-2Z" />
        </svg>
      );

    case "calendar":
      return (
        <svg {...common}>
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M16 3v4M8 3v4M3 10h18" />
        </svg>
      );

    case "gender":
      return (
        <svg {...common}>
          <circle cx="9" cy="8" r="4" />
          <path d="M2 21v-2a7 7 0 0 1 14 0v2M19 8v6M16 11h6" />
        </svg>
      );

    case "save":
      return (
        <svg {...common}>
          <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" />
          <path d="M17 21v-8H7v8M7 3v5h8" />
        </svg>
      );

    default:
      return null;
  }
}

function MetricCard({
  type,
  label,
  value,
  unit,
  description,
  progress,
  progressLabel,
}) {
  return (
    <article className={`mp-metric-card mp-metric-${type}`}>
      <div className="mp-metric-top">
        <span className="mp-metric-label">{label}</span>

        <span className="mp-metric-icon" aria-hidden="true">
          {type === "height" && (
            <svg viewBox="0 0 24 24" fill="none">
              <path
                d="M12 3v18M8 7l4-4 4 4M8 17l4 4 4-4"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          )}

          {type === "weight" && (
            <svg viewBox="0 0 24 24" fill="none">
              <path
                d="m12 3 9 9-9 9-9-9 9-9Z"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinejoin="round"
              />
            </svg>
          )}

          {type === "bmi" && (
            <svg viewBox="0 0 24 24" fill="none">
              <path
                d="M4 18 9 12l4 3 7-9M14 6h6v6"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          )}
        </span>
      </div>

      <div className="mp-metric-value">
        {value}
        {unit && <small>{unit}</small>}
      </div>

      {type === "bmi" && (
        <span className="mp-bmi-label">{progressLabel}</span>
      )}

      <div
        className="mp-metric-track"
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(progress)}
      >
        <span
          style={{
            width: `${progress}%`,
          }}
        />
      </div>

      <p className="mp-metric-description">{description}</p>
    </article>
  );
}

export default function MemberProfile({
  token,
  role,
  onLogout,
  onNavigate,
}) {
  const [me, setMe] = useState(null);
  const [exists, setExists] = useState(false);

  const [form, setForm] = useState({
    height: "",
    weight: "",
    goal: "",
  });

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState(null);

  useEffect(() => {
    let cancelled = false;

    async function loadProfile() {
      setLoading(true);

      try {
        const user = await getCurrentUserApi(token);

        if (!cancelled) {
          setMe(user);
        }

        try {
          const profile = await getMyMemberProfileApi(token);

          if (!cancelled) {
            setExists(true);

            setForm({
              height: profile.height ?? "",
              weight: profile.weight ?? "",
              goal: profile.goal ?? "",
            });
          }
        } catch (error) {
          if (error.status !== 404 && !cancelled) {
            setMsg({
              type: "error",
              text: error.message || "Không thể tải hồ sơ thể chất.",
            });
          }
        }
      } catch (error) {
        if (!cancelled) {
          setMsg({
            type: "error",
            text: error.message || "Không thể tải thông tin tài khoản.",
          });
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    loadProfile();

    return () => {
      cancelled = true;
    };
  }, [token]);

  const updateField = (key) => (event) => {
    setForm((current) => ({
      ...current,
      [key]: event.target.value,
    }));
  };

  const height = Number(form.height);
  const weight = Number(form.weight);

  const hasHeight =
    form.height !== "" &&
    Number.isFinite(height) &&
    height >= 50 &&
    height <= 250;

  const hasWeight =
    form.weight !== "" &&
    Number.isFinite(weight) &&
    weight >= 10 &&
    weight <= 500;

  const bmi =
    hasHeight && hasWeight
      ? weight / ((height / 100) ** 2)
      : null;

  // Bar cập nhật ngay theo giá trị đang nhập.
  const heightProgress = getProgress(
    form.height,
    HEIGHT_SCALE_MAX
  );

  const weightProgress = getProgress(
    form.weight,
    WEIGHT_SCALE_MAX
  );

  const bmiProgress = getProgress(bmi, BMI_SCALE_MAX);

  async function save(event) {
    event.preventDefault();
    setMsg(null);

    if (!hasHeight) {
      setMsg({
        type: "error",
        text: "Chiều cao phải từ 50 đến 250 cm.",
      });
      return;
    }

    if (!hasWeight) {
      setMsg({
        type: "error",
        text: "Cân nặng phải từ 10 đến 500 kg.",
      });
      return;
    }

    setSaving(true);

    try {
      await saveMyMemberProfileApi(
        token,
        {
          height,
          weight,
          goal: form.goal,
        },
        exists
      );

      setExists(true);

      setMsg({
        type: "success",
        text: "Đã lưu hồ sơ thành công!",
      });
    } catch (error) {
      setMsg({
        type: "error",
        text:
          error.status === 409
            ? "Hồ sơ đã tồn tại. Hãy tải lại trang."
            : error.message || "Không thể lưu hồ sơ.",
      });
    } finally {
      setSaving(false);
    }
  }

  const account = me
    ? [
        {
          label: "Họ và tên",
          value: me.name,
          icon: "user",
        },
        {
          label: "Email",
          value: me.email,
          icon: "email",
        },
        {
          label: "Số điện thoại",
          value: me.phone,
          icon: "phone",
        },
        {
          label: "Ngày sinh",
          value: me.dateOfBirth,
          icon: "calendar",
        },
        {
          label: "Giới tính",
          value: GENDER[me.gender] || me.gender,
          icon: "gender",
        },
      ].filter((item) => item.value)
    : [];

  return (
    <div className="layout">
      <Sidebar
        role={role}
        activePage="member-profile"
        onLogout={onLogout}
        onNavigate={onNavigate}
      />

      <main className="main mp-main">
        <div className="mp-page">
          <header className="mp-header">
            <div>
              <div className="mp-eyebrow">
                <span className="mp-eyebrow-dot" />
                MEMBER PORTAL
              </div>

              <h1>Hồ sơ cá nhân</h1>

              <p>
                Quản lý thông tin tài khoản, theo dõi chỉ số cơ thể
                và thiết lập mục tiêu tập luyện của bạn.
              </p>
            </div>

            <div className="mp-header-icon" aria-hidden="true">
              <ProfileIcon type="user" />
            </div>
          </header>

          {msg && (
            <div
              className={`mp-alert mp-alert-${msg.type}`}
              role="status"
            >
              <span className="mp-alert-icon">
                {msg.type === "success" ? "✓" : "!"}
              </span>

              <span className="mp-alert-message">{msg.text}</span>

              <button
                type="button"
                className="mp-alert-close"
                aria-label="Đóng thông báo"
                onClick={() => setMsg(null)}
              >
                ×
              </button>
            </div>
          )}

          {loading ? (
            <div className="mp-loading">
              <span className="mp-spinner" />
              <span>Đang tải hồ sơ của bạn...</span>
            </div>
          ) : (
            <>
              <section className="mp-section">
                <div className="mp-section-heading">
                  <div>
                    <span className="mp-section-kicker">
                      PERSONAL INFORMATION
                    </span>

                    <h2>Thông tin tài khoản</h2>

                    <p>
                      Thông tin cơ bản được liên kết với tài khoản của bạn.
                    </p>
                  </div>

                  <span className="mp-section-badge">
                    <span />
                    Thành viên
                  </span>
                </div>

                {account.length > 0 ? (
                  <div className="mp-account-grid">
                    {account.map((item) => (
                      <div className="mp-account-item" key={item.label}>
                        <div className="mp-account-icon">
                          <ProfileIcon type={item.icon} />
                        </div>

                        <div className="mp-account-content">
                          <span>{item.label}</span>
                          <strong>{item.value}</strong>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="mp-empty-account">
                    Chưa có thông tin tài khoản để hiển thị.
                  </div>
                )}
              </section>

              <section className="mp-section">
                <div className="mp-section-heading">
                  <div>
                    <span className="mp-section-kicker">
                      BODY OVERVIEW
                    </span>

                    <h2>Chỉ số cơ thể</h2>

                    <p>
                      Các thanh hiển thị thay đổi theo số liệu bạn nhập.
                    </p>
                  </div>
                </div>

                <div className="mp-metrics-grid">
                  <MetricCard
                    type="height"
                    label="Chiều cao"
                    value={formatNumber(form.height)}
                    unit="cm"
                    description="Chiều cao hiện tại"
                    progress={heightProgress}
                  />

                  <MetricCard
                    type="weight"
                    label="Cân nặng"
                    value={formatNumber(form.weight)}
                    unit="kg"
                    description="Cân nặng hiện tại"
                    progress={weightProgress}
                  />

                  <MetricCard
                    type="bmi"
                    label="BMI ước tính"
                    value={bmi == null ? "--" : bmi.toFixed(1)}
                    description="Chỉ số khối cơ thể"
                    progress={bmiProgress}
                    progressLabel={getBmiLabel(bmi)}
                  />
                </div>

                <div className="mp-bmi-note">
                  <span className="mp-note-icon">i</span>

                  <p>
                    Thanh chiều cao dùng thang tham chiếu 200 cm,
                    cân nặng dùng thang 100 kg và BMI dùng thang 40.
                    Các mốc này chỉ để trực quan hóa, không phải mục tiêu
                    sức khỏe. BMI chỉ mang tính tham khảo và không thay
                    thế đánh giá chuyên môn.
                  </p>
                </div>
              </section>

              <section className="mp-section mp-edit-section">
                <div className="mp-section-heading">
                  <div>
                    <span className="mp-section-kicker">
                      PROFILE SETTINGS
                    </span>

                    <h2>
                      {exists ? "Cập nhật hồ sơ" : "Tạo hồ sơ thể chất"}
                    </h2>

                    <p>
                      {exists
                        ? "Điều chỉnh thông tin cơ thể và mục tiêu của bạn."
                        : "Hoàn thiện thông tin để bắt đầu theo dõi hành trình tập luyện."}
                    </p>
                  </div>

                  <div className="mp-edit-symbol">✎</div>
                </div>

                <form className="mp-form" onSubmit={save}>
                  <div className="mp-form-grid">
                    <div className="mp-field">
                      <label htmlFor="pf-height">
                        Chiều cao <span>*</span>
                      </label>

                      <div className="mp-input-wrap">
                        <input
                          id="pf-height"
                          type="number"
                          step="0.01"
                          min="50"
                          max="250"
                          placeholder="Ví dụ: 170"
                          value={form.height}
                          onChange={updateField("height")}
                          required
                        />

                        <span className="mp-input-unit">cm</span>
                      </div>

                      <small>Giới hạn từ 50 đến 250 cm</small>
                    </div>

                    <div className="mp-field">
                      <label htmlFor="pf-weight">
                        Cân nặng <span>*</span>
                      </label>

                      <div className="mp-input-wrap">
                        <input
                          id="pf-weight"
                          type="number"
                          step="0.01"
                          min="10"
                          max="500"
                          placeholder="Ví dụ: 65"
                          value={form.weight}
                          onChange={updateField("weight")}
                          required
                        />

                        <span className="mp-input-unit">kg</span>
                      </div>

                      <small>Giới hạn từ 10 đến 500 kg</small>
                    </div>
                  </div>

                  <div className="mp-field mp-goal-field">
                    <label htmlFor="pf-goal">
                      Mục tiêu tập luyện
                    </label>

                    <textarea
                      id="pf-goal"
                      maxLength={1000}
                      rows={5}
                      placeholder="Ví dụ: Tăng cơ, giảm mỡ, cải thiện sức bền..."
                      value={form.goal}
                      onChange={updateField("goal")}
                    />

                    <div className="mp-textarea-footer">
                      <span>
                        Hãy mô tả mục tiêu bạn muốn đạt được.
                      </span>

                      <span>{form.goal.length}/1000</span>
                    </div>
                  </div>

                  <div className="mp-form-footer">
                    <p>
                      <span>*</span> Các trường bắt buộc
                    </p>

                    <button
                      className="mp-save-btn"
                      type="submit"
                      disabled={saving}
                    >
                      {saving ? (
                        <>
                          <span className="mp-btn-spinner" />
                          Đang lưu...
                        </>
                      ) : (
                        <>
                          <ProfileIcon type="save" />
                          {exists ? "Lưu thay đổi" : "Tạo hồ sơ"}
                        </>
                      )}
                    </button>
                  </div>
                </form>
              </section>

              <footer className="mp-footer">
                <span>SPORT CENTER</span>
                <span>
                  Chăm sóc bản thân, chinh phục mục tiêu.
                </span>
              </footer>
            </>
          )}
        </div>
      </main>
    </div>
  );
}
