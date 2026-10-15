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
        {canAccessConsultations && (
          <button className={"nav-item clickable nav-button " + (activePage === "members" ? "active" : "")}
            type="button" onClick={() => onNavigate("members")}>
            Members
          </button>
        )}
        {canAccessConsultations && (
          <button className={"nav-item clickable nav-button " + (activePage === "membership-plans" ? "active" : "")}
            type="button" onClick={() => onNavigate("membership-plans")}>
            Membership plans
          </button>
        )}
        {role === "ADMIN" && (
          <button className={"nav-item clickable nav-button " + (activePage === "staff" ? "active" : "")}
            type="button" onClick={() => onNavigate("staff")}>
            Staff management
          </button>
        )}
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
