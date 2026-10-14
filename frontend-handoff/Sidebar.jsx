export default function Sidebar({ onLogout }) {
  return (
    <div className="sidebar">
      <div className="brand">
        <strong>SPORTS CENTER</strong>
        <span>Receptionist</span>
      </div>
      <div className="nav-item">Tổng quan</div>
      <div className="nav-item">Thành viên</div>
      <div className="nav-item active">Đăng ký thành viên</div>
      <div className="nav-item">Điểm danh</div>
      <div className="nav-item">Lớp học</div>
      <div className="nav-item">Thanh toán &amp; Hóa đơn</div>
      <div className="nav-item">Hỗ trợ</div>
      <div className="nav-footer">
        <div className="nav-item">Cài đặt</div>
        <div className="nav-item clickable" onClick={onLogout}>
          Đăng xuất
        </div>
      </div>
    </div>
  );
}
