import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Img from '../components/Img.jsx'
import { getPublicCoachesApi, getPublicSportsApi } from '../../api.js'

export default function Home() {
  const [coaches, setCoaches] = useState([])
  const [sports, setSports] = useState([])
  const [catalogError, setCatalogError] = useState({ coaches: false, sports: false })
  const [catalogLoading, setCatalogLoading] = useState(true)
  const sportsTrack = useRef(null)
  const coachesTrack = useRef(null)
  useEffect(() => {
    let active = true
    Promise.allSettled([getPublicCoachesApi(), getPublicSportsApi()]).then(([coachResult, sportResult]) => {
      if (!active) return
      if (coachResult.status === 'fulfilled') setCoaches(Array.isArray(coachResult.value) ? coachResult.value : [])
      else setCatalogError((state) => ({ ...state, coaches: true }))
      if (sportResult.status === 'fulfilled') setSports(Array.isArray(sportResult.value) ? sportResult.value.slice(0, 6) : [])
      else setCatalogError((state) => ({ ...state, sports: true }))
      setCatalogLoading(false)
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
          {catalogLoading && <p className="muted mt-3" role="status">Đang tải bộ môn…</p>}
          {catalogError.sports && <p className="muted mt-3" role="alert">Chưa tải được danh sách bộ môn. Vui lòng thử lại sau.</p>}
          {!catalogError.sports && sports.length === 0 && <p className="muted mt-3" role="status">Hiện chưa có bộ môn đang hoạt động.</p>}
          {sports.length > 0 && <div className="catalog-carousel mt-3" aria-label="Bộ môn đang hoạt động">
            <button type="button" className="catalog-arrow" aria-label="Cuộn bộ môn sang trái" onClick={() => sportsTrack.current?.scrollBy({ left: -sportsTrack.current.clientWidth, behavior: 'smooth' })}><i className="bi bi-chevron-left" /></button>
            <div className="catalog-carousel-track" ref={sportsTrack}>
            {sports.map((sport) => <div className="catalog-carousel-item sport-slide" key={sport.id}>
              <Link to={'/bo-mon/' + sport.id} className="sport-card sport-catalog-card d-block h-100 text-decoration-none">
                <Img src={sport.imageUrl} alt={sport.name} label={sport.name} className="home-catalog-image" />
                <div className="sport-catalog-copy"><h5 className="mt-0">{sport.name}</h5><span className="link-arrow">Xem khóa học</span></div>
              </Link>
            </div>)}
            </div>
            <button type="button" className="catalog-arrow" aria-label="Cuộn bộ môn sang phải" onClick={() => sportsTrack.current?.scrollBy({ left: sportsTrack.current.clientWidth, behavior: 'smooth' })}><i className="bi bi-chevron-right" /></button>
          </div>}
        </div>
      </section>

      {/* CƠ SỞ VẬT CHẤT */}
      <section className="section">
        <div className="container-xl">
          <p className="label-blue">{"Cơ sở vật chất"}</p>
          <h2 className="h-xl">{"Không gian truyền cảm hứng vận động"}</h2>
          <div className="row g-4 mt-2">
            {[
              ["G", "PHÒNG GYM", "Thiết bị sức mạnh & cardio", false],
              ["S", "HỒ BƠI", "Khu bơi tiêu chuẩn, sạch sẽ", true],
              ["Y", "STUDIO", "Yoga, dance & group class", false],
            ].map(([letter, title, description, featured]) => (
              <div className="col-md-4" key={title}>
                <Link to="/co-so-vat-chat" className={`space-card ${featured ? 'is-dark' : ''}`}>
                  <div className="space-top"><span className="space-letter">{letter}</span></div>
                  <div className="space-body">
                    <h6>{title}</h6>
                    <p>{description}</p>
                  </div>
                </Link>
              </div>
            ))}
          </div>
        </div>
      </section>
      {/* COACH CAROUSEL */}
      <section className="section coaches-dark"><div className="container-xl">
        <p className="label-cyan">{"\u0110\u1ed9i ng\u0169 hu\u1ea5n luy\u1ec7n vi\u00ean"}</p>
        <h2 className="h-xl text-white">{"\u0110\u01b0\u1ee3c h\u01b0\u1edbng d\u1eabn b\u1edfi \u0111\u1ed9i ng\u0169 Sport Center"}</h2>
        {catalogLoading ? <p className="text-white-50 mt-3" role="status">{"\u0110ang t\u1ea3i hu\u1ea5n luy\u1ec7n vi\u00ean\u2026"}</p>
          : catalogError.coaches ? <p className="text-white-50 mt-3" role="alert">{"Ch\u01b0a t\u1ea3i \u0111\u01b0\u1ee3c danh s\u00e1ch hu\u1ea5n luy\u1ec7n vi\u00ean."}</p>
            : coaches.length === 0 ? <p className="text-white-50 mt-3">{"Hi\u1ec7n ch\u01b0a c\u00f3 hu\u1ea5n luy\u1ec7n vi\u00ean \u0111ang ho\u1ea1t \u0111\u1ed9ng."}</p>
              : <div className="catalog-carousel coach-carousel mt-3" aria-label="Huấn luyện viên đang hoạt động">
                <button type="button" className="catalog-arrow" aria-label="Cuộn huấn luyện viên sang trái" onClick={() => coachesTrack.current?.scrollBy({ left: -coachesTrack.current.clientWidth, behavior: 'smooth' })}><i className="bi bi-chevron-left" /></button>
                <div className="catalog-carousel-track" ref={coachesTrack}>
                  {coaches.map((coach) => <article className="catalog-carousel-item coach-slide" key={coach.id}>
                    <Img src={coach.imageUrl} alt={coach.name} label={coach.name} className="coach-home-image" />
                    <h3>{coach.name}</h3>
                    {coach.sports?.length > 0 && <p>{coach.sports.map((sport) => sport.sportName).join(' · ')}</p>}
                  </article>)}
                </div>
                <button type="button" className="catalog-arrow" aria-label="Cuộn huấn luyện viên sang phải" onClick={() => coachesTrack.current?.scrollBy({ left: coachesTrack.current.clientWidth, behavior: 'smooth' })}><i className="bi bi-chevron-right" /></button>
              </div>}
        <Link to="/huan-luyen-vien" className="btn btn-cyan mt-4">{"Xem \u0111\u1ed9i ng\u0169"}</Link>
      </div></section>
      {/* CTA */}
      <section className="cta-band">
        <div className="container-xl">
          <div className="row align-items-center g-4">
            <div className="col-md-8">
              <h2>{"S\u1eb5n s\u00e0ng b\u1eaft \u0111\u1ea7u h\u00e0nh tr\u00ecnh?"}</h2>
              <p>{"\u0110\u0103ng k\u00fd bu\u1ed5i tr\u1ea3i nghi\u1ec7m v\u00e0 nh\u1eadn t\u01b0 v\u1ea5n l\u1ed9 tr\u00ecnh luy\u1ec7n t\u1eadp ph\u00f9 h\u1ee3p."}</p>
              <Link to="/tuyen-sinh" className="btn btn-white">{"\u0110\u0103ng k\u00fd ngay"}</Link>
            </div>
            <div className="col-md-4 text-md-center">
              <p className="cta-phone">1900 6868</p>
              <p className="cta-hours">08:00 — 22:00 | {"Th\u1ee9 2 — Ch\u1ee7 nh\u1eadt"}</p>
            </div>
          </div>
        </div>
      </section>
    </>
  )
}