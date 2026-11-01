import { useEffect, useState } from "react";
import Sidebar from "../components/Sidebar";
import "./Portal.css";
import "./MemberMembership.css";
import {
  getCurrentUserApi,
  getPublicMembershipPlansApi,
  getMembershipsByMemberApi,
} from "../api";

const list = (data) =>
  Array.isArray(data) ? data : data?.content ?? [];

const money = (value) =>
  value == null
    ? "—"
    : `${Number(value).toLocaleString("vi-VN")}đ`;

function membershipStatusLabel(status) {
  const normalized = String(status || "").toUpperCase();

  if (normalized === "ACTIVE") return "Đang hoạt động";
  if (normalized === "EXPIRED") return "Đã hết hạn";
  if (normalized === "CANCELLED") return "Đã hủy";
  if (normalized === "PENDING") return "Đang chờ";

  return status || "—";
}

function membershipStatusClass(status) {
  const normalized = String(status || "").toLowerCase();
  return normalized || "unknown";
}

export default function MemberMembership({
  token,
  role,
  onLogout,
  onNavigate,
}) {
  const [plans, setPlans] = useState(null);
  const [mine, setMine] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    Promise.all([
      getCurrentUserApi(token),
      getPublicMembershipPlansApi(),
    ])
      .then(async ([user, available]) => {
        const memberships =
          await getMembershipsByMemberApi(
            token,
            user.id
          );

        if (active) {
          setMine(list(memberships));
          setPlans(list(available));
        }
      })
      .catch((requestError) => {
        if (active) {
          setError(
            requestError.message ||
              "Không tải được thông tin hội viên."
          );

          setMine([]);
          setPlans([]);
        }
      });

    return () => {
      active = false;
    };
  }, [token]);

  return (
    <div className="layout">
      <Sidebar
        role={role}
        activePage="member-membership"
        onLogout={onLogout}
        onNavigate={onNavigate}
      />

      <main className="main member-membership-page">
        <div className="member-membership-shell">
          <header className="member-membership-header">
            <div>
              <span className="member-membership-eyebrow">
                SPORTS CENTER
              </span>

              <h1>Gói thành viên</h1>

              <p>
                Theo dõi gói đang sử dụng và khám phá
                các quyền lợi thành viên hiện có.
              </p>
            </div>

            <div className="member-membership-summary">
              <span>Gói đang mở</span>

              <strong>
                {plans === null
                  ? "—"
                  : plans.length}
              </strong>

              <small>lựa chọn hiện có</small>
            </div>
          </header>

          {error && (
            <div
              className="banner error member-membership-error"
              role="alert"
            >
              {error}
            </div>
          )}

          <section className="current-membership-section">
            <div className="member-section-heading">
              <div>
                <span className="member-section-label">
                  THÀNH VIÊN
                </span>

                <h2>Gói của tôi</h2>

                <p>
                  Thông tin gói thành viên hiện tại của bạn.
                </p>
              </div>

              {mine !== null && mine.length > 0 && (
                <span className="current-membership-count">
                  {mine.length} gói
                </span>
              )}
            </div>

            {mine === null ? (
              <div
                className="member-membership-state"
                role="status"
              >
                <span className="member-membership-spinner" />
                <strong>Đang tải gói thành viên…</strong>
              </div>
            ) : mine.length === 0 ? (
              <div className="member-membership-empty">
                <div className="member-membership-empty-icon">
                  ◇
                </div>

                <h3>Bạn chưa có gói thành viên</h3>

                <p>
                  Khi có gói đang hoạt động, thông tin sẽ
                  được hiển thị tại đây.
                </p>
              </div>
            ) : (
              <div className="current-membership-list">
                {mine.map((membership) => (
                  <article
                    className="current-membership-card"
                    key={membership.id}
                  >
                    <div className="current-membership-main">
                      <div>
                        <span className="current-membership-kicker">
                          Gói hiện tại
                        </span>

                        <h3>
                          {membership.planName ||
                            "Gói thành viên"}
                        </h3>

                        <div className="membership-validity">
                          <span>
                            <small>Bắt đầu</small>
                            <strong>
                              {membership.startDate ||
                                "—"}
                            </strong>
                          </span>

                          <span className="membership-date-arrow">
                            →
                          </span>

                          <span>
                            <small>Kết thúc</small>
                            <strong>
                              {membership.endDate ||
                                "—"}
                            </strong>
                          </span>
                        </div>
                      </div>

                      <span
                        className={
                          "current-membership-status status-" +
                          membershipStatusClass(
                            membership.status
                          )
                        }
                      >
                        {membershipStatusLabel(
                          membership.status
                        )}
                      </span>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>

          <div
            className="member-membership-note"
            role="note"
          >
            <span aria-hidden="true">i</span>

            <p>
              Trang này dùng để theo dõi và tham khảo gói
              thành viên. Đăng ký, thanh toán và gia hạn
              được thực hiện theo luồng nghiệp vụ riêng
              của hệ thống.
            </p>
          </div>

          <section className="available-membership-section">
            <div className="member-section-heading">
              <div>
                <span className="member-section-label">
                  MEMBERSHIP
                </span>

                <h2>Các gói đang mở</h2>

                <p>
                  So sánh mức giá và quyền lợi để xem gói
                  nào phù hợp với nhu cầu của bạn.
                </p>
              </div>

              {plans !== null && plans.length > 0 && (
                <span className="available-membership-count">
                  {plans.length} gói
                </span>
              )}
            </div>

            {plans === null ? (
              <div
                className="member-membership-state"
                role="status"
              >
                <span className="member-membership-spinner" />
                <strong>Đang tải các gói thành viên…</strong>
              </div>
            ) : plans.length === 0 ? (
              <div className="member-membership-empty">
                <div className="member-membership-empty-icon">
                  ◇
                </div>

                <h3>Hiện chưa có gói nào</h3>

                <p>
                  Các gói mới sẽ xuất hiện tại đây khi
                  được mở.
                </p>
              </div>
            ) : (
              <div className="member-membership-plan-grid">
                {plans.map((plan) => (
                  <article
                    key={plan.id}
                    className="member-membership-plan-card"
                  >
                    <div className="membership-plan-top">
                      <span className="membership-plan-badge">
                        Gói thành viên
                      </span>

                      <span className="membership-plan-duration">
                        {plan.durationMonths} tháng
                      </span>
                    </div>

                    <h3>{plan.name}</h3>

                    <div className="membership-plan-price">
                      {money(plan.price)}
                    </div>

                    <p className="membership-plan-description">
                      {plan.description ||
                        "Gói thành viên với các quyền lợi dành cho hội viên Sports Center."}
                    </p>

                    <div className="membership-plan-benefits">
                      <div>
                        <span>Ưu đãi khóa học</span>

                        <strong>
                          {plan.courseDiscountPercent ??
                            0}
                          %
                        </strong>
                      </div>

                      {plan.personalTrainingDiscountPercent !=
                        null && (
                        <div>
                          <span>
                            Ưu đãi huấn luyện cá nhân
                          </span>

                          <strong>
                            {
                              plan.personalTrainingDiscountPercent
                            }
                            %
                          </strong>
                        </div>
                      )}

                      <div>
                        <span>Thời hạn sử dụng</span>

                        <strong>
                          {plan.durationMonths} tháng
                        </strong>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}
