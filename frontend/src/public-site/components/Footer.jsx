import { Link } from "react-router-dom";
import { ArrowUpRight, Mail, MapPin, Phone } from "../../landing/icons.jsx";
import SportCenterMark from "./SportCenterMark.jsx";

export default function Footer({ auth, dashboardPath = "/staff/dashboard" }) {
  const year = new Date().getFullYear();
  return (
    <footer className="site-footer public-site-footer">
      <div className="container-xl public-footer-inner">
        <div className="public-footer-top">
          <div className="public-footer-brand">
            <Link to="/" className="public-brand" aria-label="SportCenter — Trang chủ">
              <SportCenterMark size={36} />
              <span>Sport<b>Center</b></span>
            </Link>
            <p>Không gian dành cho vận động và rèn luyện, cùng hệ thống quản lý hiện đại phía sau.</p>
          </div>

          <nav className="public-footer-column" aria-label="Khám phá">
            <h2>Khám phá</h2>
            <Link to="/bo-mon">Bộ môn</Link>
            <Link to="/#huan-luyen-vien">Huấn luyện viên</Link>
            <Link to="/co-so-vat-chat">Cơ sở vật chất</Link>
            <Link to="/#tuyen-sinh">Hội viên</Link>
          </nav>

          <nav className="public-footer-column" aria-label="Tài khoản">
            <h2>Tài khoản</h2>
            <Link to={auth ? dashboardPath : "/login"}>{auth ? "Khu vực của bạn" : "Đăng nhập"}</Link>
            <Link to="/#public-consultation">Tư vấn</Link>
          </nav>

          <div className="public-footer-column public-footer-contact">
            <h2>Liên hệ</h2>
            <a href="tel:19006868"><Phone size={15} aria-hidden="true" /> <span>1900 6868</span></a>
            <a href="mailto:sportcenter@gmail.com"><Mail size={15} aria-hidden="true" /> <span>sportcenter@gmail.com</span></a>
            <span><MapPin size={15} aria-hidden="true" /> <span>Thành phố Hồ Chí Minh</span></span>
          </div>
        </div>

        <div className="public-footer-bottom">
          <span>© {year} Sport Center. Bảo lưu mọi quyền.</span>
          <button
            type="button"
            className="public-footer-top-link"
            onClick={() => window.scrollTo({ top: 0, behavior: window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" })}
          >
            Lên đầu trang <ArrowUpRight size={14} aria-hidden="true" />
          </button>
        </div>
      </div>
    </footer>
  );
}
