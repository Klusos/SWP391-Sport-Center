import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  ArrowRight,
  ArrowUpRight,
  CircleCheck,
  Mail,
  MapPin,
  Menu,
  Moon,
  Phone,
  Sun,
  Users,
  X,
} from "./icons.jsx";
import { getPublicCoachesApi, getPublicMembershipPlansApi, getPublicSportsApi } from "../api.js";
import ConsultForm from "../public-site/components/ConsultForm.jsx";
import Img from "../public-site/components/Img.jsx";
import { facilities } from "../public-site/data/content.js";
import SportCenterMark from "../public-site/components/SportCenterMark.jsx";
import { usePublicTheme } from "../public-site/PublicThemeContext.jsx";
import {
  formatLandingPlanDuration,
  formatLandingPlanPrice,
  getActiveLandingPlans,
  getLandingPlanBenefits,
  getLandingSports,
} from "./landingData.js";
import { orderSectionsByDocumentPosition, selectActiveSection } from "./scrollSpy.js";
import { resolveLandingHashTarget, scrollToLandingTarget } from "./anchorNavigation.js";
import "./landing.css";

/** Navbar items scroll to sections on this page instead of changing route. */
const NAV = [
  { id: "trang-chu", label: "Trang chủ" },
  { id: "bo-mon", label: "Bộ môn" },
  { id: "co-so-vat-chat", label: "Cơ sở vật chất" },
  { id: "huan-luyen-vien", label: "Huấn luyện viên" },
  { id: "tuyen-sinh", label: "Hội viên" },
  { id: "public-consultation", label: "Tư vấn" },
];

/* ------------------------------------------------------------------ */
/*  Theme                                                              */
/* ------------------------------------------------------------------ */
const prefersReducedMotion = () =>
  Boolean(window.matchMedia?.("(prefers-reduced-motion: reduce)").matches);

function scrollToSection(id, { updateHash = true } = {}) {
  const el = document.getElementById(id);
  if (!el) return;
  el.scrollIntoView({ behavior: prefersReducedMotion() ? "auto" : "smooth", block: "start" });
  if (updateHash) {
    history.replaceState(null, "", id === "trang-chu" ? window.location.pathname : `#${id}`);
  }
}

/* ------------------------------------------------------------------ */
/*  Hooks                                                              */
/* ------------------------------------------------------------------ */

/** Writes the scroll offset into a CSS variable so layers can parallax in pure CSS. */
function useScrollVar(ref) {
  useEffect(() => {
    const el = ref.current;
    if (!el || prefersReducedMotion()) return undefined;
    let raf = 0;
    const update = () => {
      raf = 0;
      el.style.setProperty("--sy", String(window.scrollY));
      // Element-level parallax: offset relative to the viewport centre.
      const mid = window.innerHeight / 2;
      el.querySelectorAll("[data-parallax]").forEach((node) => {
        const host = node.parentElement.getBoundingClientRect();
        if (host.bottom < -200 || host.top > window.innerHeight + 200) return;
        const offset = (host.top + host.height / 2 - mid) * Number(node.dataset.parallax);
        node.style.transform = `translate3d(0, ${offset.toFixed(1)}px, 0)`;
      });
    };
    const onScroll = () => { if (!raf) raf = requestAnimationFrame(update); };
    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("scroll", onScroll);
    };
  }, [ref]);
}

/** Adds `is-in` to [data-reveal] elements (including ones rendered later) when they scroll into view. */
function useReveal(ref) {
  useEffect(() => {
    const root = ref.current;
    if (!root) return undefined;
    const io = new IntersectionObserver(
      (entries) => entries.forEach((e) => {
        if (e.isIntersecting) {
          e.target.classList.add("is-in");
          io.unobserve(e.target);
        }
      }),
      { threshold: 0.12, rootMargin: "0px 0px -40px 0px" },
    );
    const watch = () => root.querySelectorAll("[data-reveal]:not(.is-in)").forEach((i) => io.observe(i));
    watch();
    const mo = new MutationObserver(watch);
    mo.observe(root, { childList: true, subtree: true });
    return () => {
      io.disconnect();
      mo.disconnect();
    };
  }, [ref]);
}

/** Tracks which section is under the navbar for the active menu state. */
function useScrollSpy(ids) {
  const [active, setActive] = useState(ids[0]);
  useEffect(() => {
    const sections = orderSectionsByDocumentPosition(
      ids.map((id) => document.getElementById(id)).filter(Boolean),
    );
    const orderedIds = sections.map(({ id }) => id);

    const onScroll = () => {
      const line = window.innerHeight * 0.35;
      const sectionTops = Object.fromEntries(
        sections.map((section) => [section.id, section.getBoundingClientRect().top]),
      );
      const atPageEnd = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4;
      setActive(selectActiveSection(orderedIds, sectionTops, line, atPageEnd) || ids[0]);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
    };
  }, [ids]);
  return active;
}

/** Subtle 3D tilt toward the pointer; inner layers shift by depth for an in-card parallax. */
function useTilt() {
  const onPointerMove = useCallback((e) => {
    if (e.pointerType !== "mouse") return;
    const el = e.currentTarget;
    const r = el.getBoundingClientRect();
    const x = (e.clientX - r.left) / r.width - 0.5;
    const y = (e.clientY - r.top) / r.height - 0.5;
    el.style.setProperty("--rx", `${(-y * 5).toFixed(2)}deg`);
    el.style.setProperty("--ry", `${(x * 6).toFixed(2)}deg`);
    el.style.setProperty("--px", x.toFixed(3));
    el.style.setProperty("--py", y.toFixed(3));
    el.style.setProperty("--mx", `${((x + 0.5) * 100).toFixed(1)}%`);
    el.style.setProperty("--my", `${((y + 0.5) * 100).toFixed(1)}%`);
  }, []);
  const onPointerLeave = useCallback((e) => {
    const el = e.currentTarget;
    ["--rx", "--ry"].forEach((k) => el.style.setProperty(k, "0deg"));
    ["--px", "--py"].forEach((k) => el.style.setProperty(k, "0"));
  }, []);
  return { onPointerMove, onPointerLeave };
}

/* ------------------------------------------------------------------ */
/*  Brand + social glyphs (lucide no longer ships brand marks)         */
/* ------------------------------------------------------------------ */
/** Shared runner mark; Landing typography and spacing remain locally styled. */
function Logo({ size = 36 }) { return <SportCenterMark size={size} className="lp-logo" />; }

/** In-page anchor that smooth-scrolls instead of navigating. */
function SectionLink({ id, className, children, onNavigate, ...rest }) {
  return (
    <a
      href={`#${id}`}
      className={className}
      onClick={(e) => {
        e.preventDefault();
        onNavigate?.();
        scrollToSection(id);
      }}
      {...rest}
    >
      {children}
    </a>
  );
}

function SectionHead({ eyebrow, title, lead, id, align = "center", action }) {
  return (
    <div className={`lp-section-head lp-section-head--${align}`} data-reveal>
      <div className="lp-section-head-copy">
        <p className="lp-eyebrow"><span className="lp-dot" /> {eyebrow}</p>
        <h2 id={id}>{title}</h2>
        {lead && <p className="lp-lead">{lead}</p>}
      </div>
      {action}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Sections                                                           */
/* ------------------------------------------------------------------ */
function Navbar({ theme, onToggleTheme, active }) {
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => e.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  const close = () => setOpen(false);
  const ThemeIcon = theme === "dark" ? Sun : Moon;
  const accountPath = "/login";
  const accountLabel = "Đăng nhập";

  return (
    <header className={`lp-nav${scrolled ? " is-scrolled" : ""}${open ? " is-open" : ""}`}>
      <div className="lp-nav-inner lp-container">
        <SectionLink id="trang-chu" className="lp-brand" onNavigate={close} aria-label="Sport Center – Về đầu trang">
          <Logo />
          <span>Sport<b>Center</b></span>
        </SectionLink>

        <nav className="lp-menu" id="lp-menu" aria-label="Điều hướng chính">
          <ul>
            {NAV.map((l) => (
              <li key={l.id}>
                <SectionLink
                  id={l.id}
                  onNavigate={close}
                  className={active === l.id ? "active" : undefined}
                  aria-current={active === l.id ? "true" : undefined}
                >
                  {l.label}
                </SectionLink>
              </li>
            ))}
          </ul>
          <Link to={accountPath} className="lp-btn lp-btn-primary lp-menu-login" onClick={close}>
            {accountLabel}
          </Link>
        </nav>

        <div className="lp-nav-actions">
          <button
            type="button"
            className="lp-icon-btn"
            onClick={onToggleTheme}
            aria-label={theme === "dark" ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối"}
            title={theme === "dark" ? "Giao diện sáng" : "Giao diện tối"}
          >
            <ThemeIcon size={18} strokeWidth={2} aria-hidden="true" />
          </button>
          <Link to={accountPath} className="lp-btn lp-btn-primary lp-nav-login">
            {accountLabel}
          </Link>
          <button
            type="button"
            className="lp-icon-btn lp-burger"
            aria-expanded={open}
            aria-controls="lp-menu"
            aria-label={open ? "Đóng menu" : "Mở menu"}
            onClick={() => setOpen((o) => !o)}
          >
            {open ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>
    </header>
  );
}

function Hero() {
  return (
    <section className="lp-hero lp-anchor" id="trang-chu" aria-labelledby="lp-hero-title">
      <div className="lp-hero-bg" aria-hidden="true">
        <span className="lp-blob lp-blob-a" />
        <span className="lp-blob lp-blob-b" />
        <span className="lp-grid" />
      </div>

      <div className="lp-container lp-hero-inner">
        <div className="lp-hero-copy">
          <p className="lp-eyebrow" data-reveal>
            <span className="lp-dot" /> Không gian dành cho vận động
          </p>
          <h1 id="lp-hero-title" data-reveal style={{ "--d": "80ms" }}>
            Khỏe mỗi ngày.<br /><span className="lp-mark">Mạnh mỗi bước.</span>
          </h1>
          <p className="lp-lead" data-reveal style={{ "--d": "160ms" }}>
            Khám phá các bộ môn, khóa học và không gian tập luyện của Sport Center.
          </p>
          <div className="lp-hero-cta" data-reveal style={{ "--d": "240ms" }}>
            <SectionLink id="public-consultation" className="lp-btn lp-btn-primary lp-btn-lg">
              Đăng ký tư vấn <ArrowRight size={18} />
            </SectionLink>
            <SectionLink id="bo-mon" className="lp-btn lp-btn-ghost lp-btn-lg">
              Khám phá bộ môn
            </SectionLink>
          </div>
        </div>

        <div className="lp-hero-visual" data-reveal style={{ "--d": "200ms" }}>
          <div className="lp-hero-photo-frame">
            <Img src="/images/trungtam.jpg" alt="Không gian Sport Center" label="Sport Center" className="lp-hero-photo" />
            <span className="lp-hero-photo-accent" aria-hidden="true" />
          </div>
        </div>
      </div>
    </section>
  );
}

function BentoCard({ className = "", children, delay = 0, as: Tag = "article", ...rest }) {
  const tilt = useTilt();
  return (
    <Tag className={`lp-card ${className}`} data-reveal style={{ "--d": `${delay}ms` }} {...tilt} {...rest}>
      <div className="lp-card-inner">{children}</div>
    </Tag>
  );
}

function Status({ children, role = "status" }) {
  return <p className="lp-status" role={role}>{children}</p>;
}

function Sports({ sports, loading, failed }) {
  const fromApi = getLandingSports(sports);
  return (
    <section className="lp-section lp-sports-section lp-anchor" id="bo-mon" aria-labelledby="lp-sports-title">
      <div className="lp-container">
        <SectionHead
          id="lp-sports-title"
          align="split"
          eyebrow="Bộ môn"
          title="Tìm bộ môn dành cho bạn"
          lead="Từ người mới bắt đầu đến vận động viên, mỗi bộ môn đều có lộ trình và huấn luyện viên riêng."
          action={<Link to="/bo-mon" className="lp-link">Xem tất cả bộ môn <ArrowUpRight size={16} /></Link>}
        />
        {loading && <Status>Đang tải bộ môn…</Status>}
        {!loading && failed && <Status role="alert">Chưa tải được danh sách bộ môn. Vui lòng thử lại sau.</Status>}
        {!loading && !failed && fromApi.length === 0 && <Status>Hiện chưa có bộ môn đang hoạt động.</Status>}
        <div className="lp-sports">
          {!failed && fromApi.map((s, i) => (
            <BentoCard as={Link} to={`/bo-mon/${s.id}`} className="lp-sport" key={s.id} delay={i * 60}>
              <div className="lp-card-media lp-depth-1">
                <Img src={s.imageUrl} alt={s.name} label={s.name} />
              </div>
              <div className="lp-card-scrim" />
              <div className="lp-sport-copy lp-depth-3">
                <strong>{s.name}</strong>
                <span>Xem khóa học <ArrowUpRight size={15} /></span>
              </div>
            </BentoCard>
          ))}
        </div>
      </div>
    </section>
  );
}

function Coaches({ coaches, loading, failed }) {
  const list = coaches;
  return (
    <section className="lp-section lp-section--tint lp-anchor" id="huan-luyen-vien" aria-labelledby="lp-coaches-title">
      <div className="lp-container">
        <SectionHead
          id="lp-coaches-title"
          align="split"
          eyebrow="Đội ngũ huấn luyện viên"
          title="Người đồng hành trên hành trình của bạn"
        />
        {loading && <Status>Đang tải huấn luyện viên…</Status>}
        {!loading && list.length === 0 && (
          <div className="lp-empty" data-reveal>
            <span className="lp-icon-tile"><Users size={20} /></span>
            <div>
              <h3>{failed ? "Chưa tải được danh sách huấn luyện viên" : "Hiện chưa có huấn luyện viên đang hoạt động."}</h3>
              <p>{"Vui l\u00f2ng th\u1eed l\u1ea1i sau \u0111\u1ec3 ki\u1ec3m tra th\u00f4ng tin hu\u1ea5n luy\u1ec7n vi\u00ean."}</p>
            </div>
          </div>
        )}
        {list.length > 0 && (
          <div className="lp-coaches">
            {list.map((c, i) => (
              <BentoCard className="lp-coach" key={c.id} delay={i * 70}>
                <div className="lp-coach-photo lp-depth-1">
                  <Img src={c.imageUrl} alt={c.name} label={c.name} />
                </div>
                <div className="lp-coach-copy">
                  <h3>{c.name}</h3>
                  <p>
                    {c.sports?.length > 0
                      ? c.sports.map((s) => s.sportName).join(" · ")
                      : "Thông tin bộ môn chưa có"}
                  </p>
                </div>
              </BentoCard>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

function Facilities() {
  const homeFacilities = facilities.filter((facility) => facility.homeKey);
  return (
    <section className="lp-section lp-anchor" id="co-so-vat-chat" aria-labelledby="lp-facilities-title">
      <div className="lp-container">
        <SectionHead
          id="lp-facilities-title"
          align="split"
          eyebrow="Không gian tập luyện"
          title="Không gian tập luyện tại Sport Center"
          lead="Tìm hiểu các khu vực tập luyện được giới thiệu tại trung tâm."
          action={<Link to="/co-so-vat-chat" className="lp-link">Khám phá cơ sở <ArrowUpRight size={16} /></Link>}
        />
        <div className="lp-bento lp-facilities-bento">
          {homeFacilities.map((f, i) => (
            <BentoCard className={`lp-facility ${i === 0 ? "lp-facility--lead" : i === 1 ? "lp-facility--support-one" : "lp-facility--support-two"}`} key={f.title} delay={i * 70}>
              <div className="lp-card-media lp-depth-1">
                <Img src={f.img} alt={f.title} label={f.title} />
              </div>
              <div className="lp-card-scrim" />
              <div className="lp-facility-copy lp-depth-3">
                <h3>{f.title}</h3>
                <p>{f.text}</p>
              </div>
            </BentoCard>
          ))}
        </div>
      </div>
    </section>
  );
}

function Admissions({ plans, loading, error, auth }) {
  const activePlans = getActiveLandingPlans(plans);
  return (
    <section className="lp-section lp-section--tint lp-anchor lp-admissions" id="tuyen-sinh" aria-labelledby="lp-admissions-title">
      <div className="lp-container">
        <SectionHead
          id="lp-admissions-title"
          align="split"
          eyebrow="Hội viên"
          title="Tìm hiểu các gói hội viên đang áp dụng."
          lead="Thông tin gói được tải từ hệ thống Sport Center."
        />
        {loading && <Status>Đang tải gói hội viên…</Status>}
        {!loading && error && <p className="lp-status" role="alert">Chưa tải được gói hội viên. Vui lòng thử lại sau.</p>}
        {!loading && !error && activePlans.length === 0 && <Status>Hiện chưa có gói hội viên đang áp dụng.</Status>}
        {!loading && !error && activePlans.length > 0 && (
          <div className="lp-plan-grid">
            {activePlans.map((plan) => (
              <article className="lp-plan-card" key={plan.id} data-reveal>
                <h3>{plan.name}</h3>
                <p className="lp-plan-description">{plan.description || "Chưa có mô tả cho gói này."}</p>
                <div className="lp-plan-pricing" aria-label="Giá và thời hạn gói hội viên">
                  <div className="lp-plan-price">
                    <strong>{formatLandingPlanPrice(plan.price) || "Chưa có dữ liệu"}</strong>
                    <span>Tổng giá gói</span>
                  </div>
                  <p className="lp-plan-duration">Thời hạn <strong>{formatLandingPlanDuration(plan.durationMonths) || "Chưa cập nhật"}</strong></p>
                </div>
                <div className="lp-plan-benefits">
                  <h4>Quyền lợi theo gói</h4>
                  <ul aria-label={`Quyền lợi gói ${plan.name}`}>
                    {getLandingPlanBenefits(plan).map((benefit) => (
                      <li className={`is-${benefit.state}`} key={benefit.label}>
                        <span className="lp-plan-benefit-icon" aria-hidden="true">
                          {benefit.state === "included" ? <CircleCheck size={18} /> : benefit.state === "excluded" ? "−" : "?"}
                        </span>
                        <span className="lp-plan-benefit-copy">
                          <span>{benefit.label}</span>
                          <strong>{benefit.value}</strong>
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
                {auth?.role === "MEMBER" ? (
                  <Link to="/member/membership" className="lp-btn lp-btn-primary lp-plan-action">
                    Quản lý hội viên <ArrowUpRight size={16} />
                  </Link>
                ) : !auth ? (
                  <Link to="/#public-consultation" className="lp-btn lp-btn-primary lp-plan-action">
                    Đăng ký tư vấn <ArrowUpRight size={16} />
                  </Link>
                ) : null}
              </article>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

function AdmissionBridge() {
  return (
    <section className="lp-admission-bridge" aria-labelledby="lp-admission-bridge-title">
      <div className="lp-container lp-admission-bridge-copy">
        <p className="lp-admission-bridge-eyebrow">SPORTCENTER ĐỒNG HÀNH CÙNG BẠN</p>
        <h2 id="lp-admission-bridge-title">Chưa biết bắt đầu từ đâu?</h2>
        <p>Chia sẻ nhu cầu tập luyện của bạn để chúng tôi có thể hỗ trợ lựa chọn phù hợp.</p>
      </div>
    </section>
  );
}
function Consultation() {
  return (
    <section className="lp-consult lp-anchor" id="public-consultation" aria-labelledby="lp-consult-title">
      <span id="consultation" className="lp-anchor-alias" aria-hidden="true" />
      <div className="lp-consult-bg" data-parallax="-0.18" aria-hidden="true" />
      <div className="lp-container">
       <div className="lp-consult-panel" data-reveal>
        <div className="lp-consult-copy">
          <p className="lp-consult-eyebrow">Tư vấn ghi danh</p>
          <h2 id="lp-consult-title">Cùng tìm lựa chọn phù hợp với bạn</h2>
          <p>Để lại thông tin và bộ môn bạn quan tâm. Trung tâm sẽ tiếp nhận yêu cầu tư vấn.</p>
          <a className="lp-consult-phone" href="tel:19006868">
            <span><Phone size={17} /></span> 1900 6868
          </a>
        </div>
        <div className="lp-consult-form">
          <ConsultForm showFieldPlaceholders={false} />
        </div>
       </div>
      </div>
    </section>
  );
}

function Footer({ auth, dashboardPath }) {
  const year = new Date().getFullYear();
  return (
    <footer className="lp-footer">
      <div className="lp-container">
        <div className="lp-footer-top">
          <div className="lp-footer-brand">
            <SectionLink id="trang-chu" className="lp-brand">
              <Logo />
              <span>Sport<b>Center</b></span>
            </SectionLink>
            <p>Không gian dành cho vận động và rèn luyện, cùng hệ thống quản lý hiện đại phía sau.</p>
          </div>

          <nav className="lp-footer-col" aria-label="Khám phá">
            <h4>Khám phá</h4>
            <SectionLink id="bo-mon">Bộ môn</SectionLink>
            <SectionLink id="huan-luyen-vien">Huấn luyện viên</SectionLink>
            <SectionLink id="co-so-vat-chat">Cơ sở vật chất</SectionLink>
          </nav>

          <nav className="lp-footer-col" aria-label="Hỗ trợ">
            <h4>Hỗ trợ</h4>
            <SectionLink id="tuyen-sinh">Hội viên</SectionLink>
            <Link to={auth ? dashboardPath : "/login"}>{auth ? "Khu vực của bạn" : "Đăng nhập"}</Link>
          </nav>

          <div className="lp-footer-col">
            <h4>Liên hệ</h4>
            <a href="tel:19006868"><Phone size={15} /> 1900 6868</a>
            <a href="mailto:sportcenter@gmail.com"><Mail size={15} /> sportcenter@gmail.com</a>
            <span><MapPin size={15} /> Thành phố Hồ Chí Minh</span>
          </div>
        </div>

        <div className="lp-footer-bottom">
          <span>© {year} Sport Center. Bảo lưu mọi quyền.</span>
          <SectionLink id="trang-chu" className="lp-to-top">
            Lên đầu trang <ArrowUpRight size={14} />
          </SectionLink>
        </div>
      </div>
    </footer>
  );
}

/* ------------------------------------------------------------------ */
/*  Page                                                               */
/* ------------------------------------------------------------------ */
const SECTION_IDS = NAV.map((n) => n.id);

export default function LandingPage({ auth, dashboardPath = "/staff/dashboard" }) {
  const rootRef = useRef(null);
  const location = useLocation();
  const { theme, toggleTheme } = usePublicTheme();
  const [catalog, setCatalog] = useState({ sports: [], coaches: [], loading: true, sportsFailed: false, coachesFailed: false });
  const [plans, setPlans] = useState([]);
  const [plansLoading, setPlansLoading] = useState(true);
  const [plansError, setPlansError] = useState(false);
  const active = useScrollSpy(SECTION_IDS);

  useScrollVar(rootRef);
  useReveal(rootRef);

  useEffect(() => {
    let alive = true;
    Promise.allSettled([getPublicSportsApi(), getPublicCoachesApi(), getPublicMembershipPlansApi()]).then(([s, c, p]) => {
      if (!alive) return;
      setCatalog({
        sports: s.status === "fulfilled" && Array.isArray(s.value) ? s.value : [],
        coaches: c.status === "fulfilled" && Array.isArray(c.value) ? c.value : [],
        sportsFailed: s.status === "rejected",
        coachesFailed: c.status === "rejected",
        loading: false,
      });
      if (p.status === "fulfilled") setPlans(Array.isArray(p.value) ? p.value : []);
      else setPlansError(true);
      setPlansLoading(false);
    });
    return () => { alive = false; };
  }, []);

  useEffect(() => {
    const prevTitle = document.title;
    document.title = "Sport Center — Khám phá bộ môn và khóa học";
    return () => { document.title = prevTitle; };
  }, []);

  useEffect(() => {
    const targetId = resolveLandingHashTarget(location.hash, SECTION_IDS);
    if (!targetId || catalog.loading || plansLoading) return undefined;

    const root = rootRef.current;
    const target = document.getElementById(targetId);
    if (!root || !target) return undefined;

    return scrollToLandingTarget({
      root,
      target,
      reducedMotion: prefersReducedMotion(),
    });
  }, [location.pathname, location.hash, catalog.loading, plansLoading]);

  return (
    <div className="lp" data-theme={theme} ref={rootRef}>
      <a href="#lp-main" className="lp-skip">Bỏ qua đến nội dung chính</a>
      <Navbar
        theme={theme}
        onToggleTheme={toggleTheme}
        active={active}
      />
      <main id="lp-main">
        <Hero />
        <Sports sports={catalog.sports} loading={catalog.loading} failed={catalog.sportsFailed} />
        <Coaches coaches={catalog.coaches} loading={catalog.loading} failed={catalog.coachesFailed} />
        <Facilities />
        <Admissions plans={plans} loading={plansLoading} error={plansError} auth={auth} />
        <AdmissionBridge />
        <Consultation />
      </main>
      <Footer auth={auth} dashboardPath={dashboardPath} />
    </div>
  );
}
