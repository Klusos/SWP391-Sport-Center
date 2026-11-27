import "./AdminSectionHeader.css";

export default function AdminSectionHeader({ title, description, summary, actions }) {
  return (
    <header className="admin-section-header">
      <div className="admin-section-intro">
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
