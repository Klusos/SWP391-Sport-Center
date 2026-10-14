export default function Sidebar({ onLogout }) {
  return (
    <aside className="sidebar">
      <div className="brand">
        <strong>SPORTS CENTER</strong>
        <span>Receptionist</span>
      </div>
      <nav aria-label="Staff navigation">
        <div className="nav-item">Overview</div>
        <div className="nav-item">Members</div>
        <div className="nav-item active">Register member</div>
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
