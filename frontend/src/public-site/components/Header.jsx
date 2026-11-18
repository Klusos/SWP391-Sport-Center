import { useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { Moon, Sun } from "../../landing/icons.jsx";
import { usePublicTheme } from "../PublicThemeContext.jsx";
import { isPublicNavLinkActive, navLinks } from "../data/content.js";
import SportCenterMark from "./SportCenterMark.jsx";

export default function Header({ auth, dashboardPath = "/staff/dashboard" }) {
  const [open, setOpen] = useState(false);
  const { pathname } = useLocation();
  const { theme, toggleTheme } = usePublicTheme();
  const ThemeIcon = theme === "dark" ? Sun : Moon;
  const closeMenu = () => setOpen(false);

  return (
    <header className="site-header">
      <nav className="container-xl public-header-inner" aria-label="Điều hướng chính">
        <Link to="/" className="public-brand" onClick={closeMenu} aria-label="SportCenter — Trang chủ">
          <SportCenterMark size={36} />
          <span>Sport<b>Center</b></span>
        </Link>

        <button
          type="button"
          className="public-icon-button nav-toggle"
          aria-label={open ? "Đóng menu" : "Mở menu"}
          aria-expanded={open}
          aria-controls="public-navigation"
          onClick={() => setOpen((value) => !value)}
        >
          <span aria-hidden="true">{open ? "×" : "☰"}</span>
        </button>

        <ul id="public-navigation" className={`nav-list public-nav-list list-unstyled mb-0${open ? " is-open" : ""}`}>
          {navLinks.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.to === "/" || item.to === "/co-so-vat-chat"}
                className={({ isActive }) => {
                  return `nav-item-link${isActive || isPublicNavLinkActive(item.to, pathname) ? " active" : ""}`;
                }}
                aria-current={isPublicNavLinkActive(item.to, pathname) ? "page" : undefined}
                onClick={closeMenu}
              >
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>

        <div className="public-header-actions">
          <button
            type="button"
            className="public-icon-button public-theme-toggle"
            aria-label={theme === "dark" ? "Bật giao diện sáng" : "Bật giao diện tối"}
            title={theme === "dark" ? "Giao diện sáng" : "Giao diện tối"}
            onClick={toggleTheme}
          >
            <ThemeIcon size={18} aria-hidden="true" />
          </button>
          <Link className="btn btn-brand public-account-link" to={auth ? dashboardPath : "/login"} onClick={closeMenu}>
            {auth ? "Khu vực của bạn" : "Đăng nhập"}
          </Link>
        </div>
      </nav>
    </header>
  );
}
