import { Link } from 'react-router-dom'
import ConsultForm from './ConsultForm.jsx'

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="footer-left">
        <h4 className="brand-light">SPORT CENTER</h4>
        <div className="socials">
          <a href="#" aria-label="Facebook"><i className="bi bi-facebook" /></a>
          <a href="#" aria-label="Instagram"><i className="bi bi-instagram" /></a>
          <a href="#" aria-label="YouTube"><i className="bi bi-youtube" /></a>
        </div>
        <div className="row g-4 mt-1">
          <div className="col-12 col-md-4">
            <h6>THÔNG TIN LIÊN HỆ</h6>
            <p><i className="bi bi-telephone" /> 1900 6868</p>
            <p><i className="bi bi-envelope" /> sportcenter@gmail.com</p>
            <p><i className="bi bi-geo-alt" /> Thành phố Hồ Chí Minh</p>
          </div>
          <div className="col-6 col-md-4">
            <h6>KHÁM PHÁ</h6>
            <p><Link to="/bo-mon">Bộ môn</Link></p>
            <p><Link to="/lich-tap">Lịch tập</Link></p>
            <p><Link to="/huan-luyen-vien">Huấn luyện viên</Link></p>
          </div>
          <div className="col-6 col-md-4">
            <h6>HỖ TRỢ</h6>
            <p><Link to="/tuyen-sinh">Đăng ký</Link></p>
            <p><a href="#">Chính sách</a></p>
            <p><a href="#">Liên hệ</a></p>
          </div>
        </div>
        <p className="copyright">© 2026 Sport Center. All rights reserved.</p>
      </div>
      <div className="footer-right">
        <ConsultForm />
      </div>
    </footer>
  )
}
