import "./AdminTopBar.css";
import { Link } from "react-router-dom";

function initialsFrom(name) {
  const parts = String(name || "").trim().split(/\s+/).filter(Boolean).slice(-2);
  return parts.map((part) => part[0]).join("").toUpperCase() || "\u2014";
}

export default function AdminTopBar({
  pageLabel,
  displayName,
  avatarName,
  roleLabel = "Qu\u1ea3n tr\u1ecb vi\u00ean",
  breadcrumbRoleLabel = "QU\u1ea2N TR\u1eca",
  variant = "default",
  className = "",
  breadcrumbItems,
}) {
  const classes = ["admin-topbar", `admin-topbar--${variant}`, className].filter(Boolean).join(" ");
  const name = String(displayName || "").trim() || roleLabel;
  const hasCustomBreadcrumb = Array.isArray(breadcrumbItems) && breadcrumbItems.length > 0;

  return (
    <header className={classes}>
      <nav className="admin-topbar-breadcrumb" aria-label="&#272;i&#7873;u h&#432;&#7899;ng">
        <span>SPORTCENTER</span>
        <i aria-hidden="true">&#8250;</i>
        <span>{breadcrumbRoleLabel}</span>
        {hasCustomBreadcrumb ? breadcrumbItems.map((item, index) => (
          <span className="admin-topbar-breadcrumb-segment" key={`${item.label}-${index}`}>
            <i aria-hidden="true">&#8250;</i>
            {item.to && !item.current
              ? <Link className="admin-topbar-breadcrumb-item" to={item.to} title={item.label}>{item.label}</Link>
              : <b className="admin-topbar-breadcrumb-item" aria-current={item.current ? "page" : undefined} title={item.label}>{item.label}</b>}
          </span>
        )) : <><i aria-hidden="true">&#8250;</i><b>{pageLabel}</b></>}
      </nav>
      <div className="admin-topbar-account">
        <span className="admin-topbar-avatar" aria-hidden="true">{initialsFrom(avatarName)}</span>
        <div>
          <b>{name}</b>
          <small>{roleLabel}</small>
        </div>
      </div>
    </header>
  );
}
