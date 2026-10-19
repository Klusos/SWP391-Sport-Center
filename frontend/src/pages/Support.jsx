import Sidebar from "../components/Sidebar";

// Backend hiện chưa có API nào cho mục Hỗ trợ (không thấy endpoint liên quan
// trong api.js), nên trang này tạm thời là placeholder để không bị lỗi khi
// bấm vào. Khi có API thật, thay nội dung bên trong <main> bằng chức năng
// thật (ví dụ: danh sách ticket hỗ trợ, form tạo yêu cầu...).

export default function Support({ role, onNavigate, onLogout }) {
  return (
    <div className="layout">
      <Sidebar role={role} activePage="support" onNavigate={onNavigate} onLogout={onLogout} />
      <main className="main">
        <h1>Hỗ trợ</h1>
        <p className="subtitle">Mục này đang được xây dựng.</p>
        <div className="queue-state">
          Backend hiện chưa có API cho tính năng hỗ trợ. Trang này sẽ được bổ
          sung khi API tương ứng ra mắt.
        </div>
      </main>
    </div>
  );
}