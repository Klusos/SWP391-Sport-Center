import { Link } from 'react-router-dom'
import { disciplines, coachesHome } from '../data/content.js'

export default function Home() {
  return (
    <>
      {/* HERO */}
      <section className="home-hero">
        <div className="container-xl">
          <div className="row align-items-center g-5">
            <div className="col-lg-7">
              <p className="eyebrow">TRUNG TÂM THỂ THAO HIỆN ĐẠI</p>
              <h1>KHỎE MỖI NGÀY.<br />MẠNH MỖI BƯỚC.</h1>
              <p className="lead-text">
                Không gian luyện tập chuyên nghiệp, huấn luyện viên tận tâm và hệ thống lớp học đa dạng dành cho mọi mục tiêu.
              </p>
              <div className="d-flex flex-wrap gap-3 my-4">
                <Link to="/bo-mon" className="btn btn-cyan">KHÁM PHÁ NGAY</Link>
                <Link to="/lich-tap" className="btn btn-ghost">XEM LỊCH TẬP</Link>
              </div>
              <p className="hero-stats">20+ BỘ MÔN <span>35+ HLV</span> <span>10K+ HỘI VIÊN</span></p>
            </div>
            <div className="col-lg-5 d-none d-lg-block">
              <div className="hero-art" aria-hidden="true">
                <div className="blob blob-1" />
                <div className="blob blob-2" />
                <div className="hero-card">
                  <div className="fig-head" />
                  <div className="fig-body" />
                  <div className="fig-arm fig-arm-l" />
                  <div className="fig-arm fig-arm-r" />
                  <div className="fig-leg fig-leg-l" />
                  <div className="fig-leg fig-leg-r" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* VỀ SPORT CENTER */}
      <section className="section">
        <div className="container-xl">
          <p className="label-blue">VỀ SPORT CENTER</p>
          <h2 className="h-xl">Một nơi để bạn yêu vận động hơn mỗi ngày</h2>
          <p className="muted maxw">
            Từ người mới bắt đầu đến vận động viên chuyên nghiệp, Sport Center mang đến chương trình luyện tập phù hợp với từng thể trạng và mục tiêu.
          </p>
          <div className="row g-4 mt-3">
            {[
              ['01', 'Không gian chuẩn', 'Khu luyện tập thoáng, sạch và trang thiết bị hiện đại.', '#146edb'],
              ['02', 'HLV tận tâm', 'Đồng hành 1:1 và thiết kế giáo án theo mục tiêu.', '#0fb9e3'],
              ['03', 'Lịch linh hoạt', 'Nhiều khung giờ, lớp học đa dạng cho người bận rộn.', '#146edb'],
            ].map(([n, t, d, c]) => (
              <div className="col-md-4" key={n}>
                <div className="d-flex gap-3 align-items-start">
                  <span className="num-dot" style={{ background: c }}>{n}</span>
                  <div>
                    <h5 className="mb-1 fw-bold">{t}</h5>
                    <p className="muted small mb-0">{d}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CÁC BỘ MÔN */}
      <section className="section bg-soft">
        <div className="container-xl">
          <p className="label-blue">CÁC BỘ MÔN</p>
          <h2 className="h-xl">Chọn môn thể thao phù hợp với bạn</h2>
          <p className="muted">Tập luyện theo sở thích, mục tiêu và cấp độ của riêng bạn.</p>
          <div className="row g-4 mt-2">
            {disciplines.map((d) => (
              <div className="col-md-6 col-lg-4" key={d.code}>
                <article className="sport-card">
                  <span className="sport-badge" style={{ background: d.color }}>{d.code}</span>
                  <div>
                    <h5>{d.title}</h5>
                    <Link to="/bo-mon" className="link-arrow">Xem lớp học <i className="bi bi-arrow-right" /></Link>
                    <p className="muted small mb-0">Từ 3 buổi / tuần</p>
                  </div>
                </article>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CƠ SỞ VẬT CHẤT */}
      <section className="section">
        <div className="container-xl">
          <p className="label-blue">CƠ SỞ VẬT CHẤT</p>
          <h2 className="h-xl">Không gian truyền cảm hứng vận động</h2>
          <div className="row g-4 mt-2">
            {[
              ['G', 'PHÒNG GYM', 'Thiết bị sức mạnh & cardio', false],
              ['S', 'HỒ BƠI', 'Khu bơi tiêu chuẩn, sạch sẽ', true],
              ['Y', 'STUDIO', 'Yoga, dance & group class', false],
            ].map(([l, t, d, dark]) => (
              <div className="col-md-4" key={t}>
                <Link to="/co-so-vat-chat" className={`space-card ${dark ? 'is-dark' : ''}`}>
                  <div className="space-top"><span className="space-letter">{l}</span></div>
                  <div className="space-body">
                    <h6>{t}</h6>
                    <p>{d}</p>
                  </div>
                </Link>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ĐỘI NGŨ HLV */}
      <section className="section coaches-dark">
        <div className="container-xl">
          <p className="label-cyan">ĐỘI NGŨ HUẤN LUYỆN VIÊN</p>
          <h2 className="h-xl text-white">Được hướng dẫn bởi những người hiểu bạn</h2>
          <div className="row g-4 mt-3">
            {coachesHome.map((c) => (
              <div className="col-md-4" key={c.name}>
                <div className="d-flex gap-3 align-items-center">
                  <span className="coach-avatar" style={{ background: c.color }}>{c.initials}</span>
                  <div>
                    <h6 className="text-white fw-bold text-uppercase mb-1">{c.name}</h6>
                    <p className="coach-role mb-1">{c.role}</p>
                    <p className="coach-desc mb-0">{c.desc}</p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="cta-band">
        <div className="container-xl">
          <div className="row align-items-center g-4">
            <div className="col-md-8">
              <h2>SẴN SÀNG BẮT ĐẦU HÀNH TRÌNH?</h2>
              <p>Đăng ký buổi trải nghiệm và nhận tư vấn lộ trình luyện tập phù hợp.</p>
              <Link to="/tuyen-sinh" className="btn btn-white">ĐĂNG KÝ NGAY</Link>
            </div>
            <div className="col-md-4 text-md-center">
              <p className="cta-phone">1900 6868</p>
              <p className="cta-hours">08:00 — 22:00 | Thứ 2 — Chủ nhật</p>
            </div>
          </div>
        </div>
      </section>
    </>
  )
}
