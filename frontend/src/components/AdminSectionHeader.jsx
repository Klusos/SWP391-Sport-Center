import "./AdminSectionHeader.css";

export default function AdminSectionHeader({ title, description, icon, summary, actions }) {
  return (
    <header className="admin-section-header">
      <div className="admin-section-intro">
        <p className="admin-section-eyebrow">
          <span className="admin-section-eyebrow-icon" aria-hidden="true">{icon}</span>
          QUẢN TRỊ NỘI DUNG
        </p>
        <h1>{title}</h1>
        <p className="admin-section-description">{description}</p>
      </div>
      <div className="admin-section-meta">
        <div className="admin-section-summary">{summary}</div>
        {actions && <div className="admin-section-actions">{actions}</div>}
      </div>
    </header>
  );
}
