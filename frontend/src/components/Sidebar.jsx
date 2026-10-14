export default function Sidebar({ onLogout, onNavigate, activePage, role }) {
  const canAccessConsultations = role === "RECEPTIONIST" || role === "ADMIN";

  return (
    <aside className="sidebar">
      <div className="brand">
        <strong>SPORTS CENTER</strong>
        <span>{role === "ADMIN" ? "Administrator" : "Receptionist"}</span>
      </div>
      <nav aria-label="Staff navigation">
        <div className="nav-item">Overview</div>
        <div className="nav-item">Members</div>
        <button className={"nav-item clickable nav-button " + (activePage === "register" ? "active" : "")}
          type="button" onClick={() => onNavigate("register")}>
          Register member
        </button>
        {canAccessConsultations && (
          <button className={"nav-item clickable nav-button " + (activePage === "consultations" ? "active" : "")}
            type="button" onClick={() => onNavigate("consultations")}>
            Consultation queue
          </button>
        )}
        <div className="nav-item">Attendance</div>
        <div className="nav-item">Classes</div>
        <div className="nav-item">Payments</div>
        <div className="nav-item">Support</div>
      </nav>
      <div className="nav-footer">
        <div className="nav-item">Settings</div>
        <button className="nav-item clickable logout-button" type="button" onClick={onLogout}>Sign out</button>
      </div>
    </aside>
  );
}
