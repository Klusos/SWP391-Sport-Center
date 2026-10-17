import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Img from '../components/Img.jsx'
import { getPublicCoachesApi, getPublicFacilitiesApi, getPublicSportsApi } from '../../api.js'

export default function Home() {
  const [coaches, setCoaches] = useState([])
  const [facilities, setFacilities] = useState([])
  const [sports, setSports] = useState([])
  useEffect(() => {
    let active = true
    Promise.allSettled([getPublicCoachesApi(), getPublicFacilitiesApi(), getPublicSportsApi()]).then(([coachResult, facilityResult, sportResult]) => {
      if (!active) return
      if (coachResult.status === 'fulfilled') setCoaches(Array.isArray(coachResult.value) ? coachResult.value.slice(0, 3) : [])
      if (facilityResult.status === 'fulfilled') setFacilities(Array.isArray(facilityResult.value) ? facilityResult.value.slice(0, 3) : [])
      if (sportResult.status === 'fulfilled') setSports(Array.isArray(sportResult.value) ? sportResult.value.slice(0, 3) : [])
    })
    return () => { active = false }
  }, [])
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
                <Link to="/bo-mon" className="btn btn-ghost">XEM CÁC BỘ MÔN</Link>
              </div>
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
          <Link to="/bo-mon" className="btn btn-cyan mt-3">XEM BỘ MÔN VÀ KHÓA HỌC</Link>
          {sports.length > 0 && <div className="row g-3 mt-3" aria-label="Một số bộ môn đang hoạt động">
            {sports.map((sport) => <div className="col-sm-6 col-lg-4" key={sport.id}>
              <Link to={'/bo-mon/' + sport.id} className="sport-card sport-catalog-card d-block h-100 text-decoration-none">
                <Img src={sport.imageUrl} alt={sport.name} label={sport.name} className="home-catalog-image" />
                <div className="sport-catalog-copy"><h5 className="mt-0">{sport.name}</h5><span className="link-arrow">Xem khóa học</span></div>
              </Link>
            </div>)}
          </div>}
        </div>
      </section>

      {/* CƠ SỞ VẬT CHẤT */}
      <section className="section"><div className="container-xl">
        <p className="label-blue">CƠ SỞ VẬT CHẤT</p><h2 className="h-xl">Không gian truyền cảm hứng vận động</h2>
        <div className="row g-4 mt-2">{facilities.map((facility) => <div className="col-md-4" key={facility.id}>
          <Link to="/co-so-vat-chat" className="space-card">
            <Img src={facility.imageUrl} alt={facility.name} label={facility.name} className="home-catalog-image" />
            <div className="space-body"><h6>{facility.name}</h6><p>{[facility.type, facility.sportName].filter(Boolean).join(' · ')}</p></div>
          </Link>
        </div>)}</div>
        <Link to="/co-so-vat-chat" className="link-arrow mt-3 d-inline-block">Xem tất cả cơ sở</Link>
      </div></section>
      {/* ĐỘI NGŨ HLV */}
      <section className="section coaches-dark"><div className="container-xl">
        <p className="label-cyan">ĐỘI NGŨ HUẤN LUYỆN VIÊN</p><h2 className="h-xl text-white">Được hướng dẫn bởi đội ngũ Sport Center</h2>
        <div className="row g-4 mt-3">{coaches.map((coach) => <div className="col-md-4" key={coach.id}>
          <div className="d-flex gap-3 align-items-center">
            <Img src={coach.imageUrl} alt={coach.name} label={coach.name} className="coach-avatar-image" />
            <h6 className="text-white fw-bold text-uppercase mb-0">{coach.name}</h6>
          </div>
        </div>)}</div>
        <Link to="/huan-luyen-vien" className="btn btn-cyan mt-4">XEM ĐỘI NGŨ</Link>
      </div></section>
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
