import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import PageHero from '../components/PageHero.jsx'
import Img from '../components/Img.jsx'
import { coachSpotlight } from '../data/content.js'

const topStats = [
  ['bi-people-fill', '15+', 'HLV chuyên nghiệp'],
  ['bi-award-fill', '200', 'Huy chương quốc tế'],
  ['bi-people-fill', '12', 'Bộ môn đào tạo'],
  ['bi-star-fill', '10+', 'Năm kinh nghiệm'],
]

export default function Coaches() {
  const [active, setActive] = useState(0)
  const stripRef = useRef(null)
  const coach = coachSpotlight[active]

  const scroll = (dir) => stripRef.current?.scrollBy({ left: dir * 300, behavior: 'smooth' })

  return (
    <>
      <PageHero
       title="ĐỘI NGŨ HLV"
       subtitle="Những huấn luyện viên tận tâm, giàu kinh nghiệm, đồng hành cùng bạn trên con đường chinh phục thể thao"
       image="/images/coaches-hero.png"
      />
      <section className="container-xl stat-row">
        <div className="row g-4">
          {topStats.map(([icon, num, label]) => (
            <div className="col-6 col-lg-3" key={label}>
              <div className="d-flex align-items-center gap-3">
                <i className={`bi ${icon} stat-icon`} />
                <div>
                  <div className="stat-num">{num}</div>
                  <div className="stat-label">{label}</div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* SPOTLIGHT */}
      <section className="container-xl pt-5">
        <div className="d-flex justify-content-between align-items-end flex-wrap gap-3">
          <div>
            <span className="pill-badge sm">SPOTLIGHT</span>
            <h2 className="h-xl text-uppercase mt-2 mb-0">Huấn luyện viên <span className="text-brand">ngôi sao</span></h2>
          </div>
          <div className="text-end">
            <div className="big-num">{coachSpotlight.length}+</div>
            <div className="big-num-label">CHUYÊN GIA</div>
          </div>
        </div>

        <div className="carousel-strip mt-4">
          <button className="arrow-btn" aria-label="Trước" onClick={() => scroll(-1)}><i className="bi bi-chevron-left" /></button>
          <div className="thumbs" ref={stripRef}>
            {coachSpotlight.map((c, i) => (
              <button
                key={c.id}
                className={`thumb ${i === active ? 'is-active' : ''}`}
                onClick={() => setActive(i)}
                aria-label={`Xem ${c.name}`}
                aria-pressed={i === active}
              >
                <Img src={c.img} alt={c.name} label={c.name.split(' ')[0]} />
              </button>
            ))}
          </div>
          <button className="arrow-btn" aria-label="Sau" onClick={() => scroll(1)}><i className="bi bi-chevron-right" /></button>
        </div>

        {/* CHI TIẾT HLV */}
        <div className="row g-5 mt-3 mb-5">
          <div className="col-lg-5">
            <div className="coach-photo">
              <Img src={coach.img} alt={coach.name} label={coach.name} />
              <div className="coach-photo-info">
                <h3>{coach.name.toUpperCase()}</h3>
                <p>{coach.role.toUpperCase()}</p>
                <div className="skill-tags">
                  {coach.skills.map((s) => <span key={s}>{s}</span>)}
                </div>
              </div>
            </div>
          </div>
          <div className="col-lg-7">
            <div className="sport-title">{coach.sport.toUpperCase()}</div>
            <div className="bio-box">
              {coach.bio.map((p, i) => <p key={i}>{p}</p>)}
            </div>
            <div className="coach-stats">
              {coach.stats.map(([n, l]) => (
                <div key={l}>
                  <div className="cs-num">{n}</div>
                  <div className="cs-label">{l.toUpperCase()}</div>
                </div>
              ))}
            </div>
            <ol className="achievements">
              {coach.achievements.map((a, i) => (
                <li key={a}><b>{String(i + 1).padStart(2, '0')}</b> {a}</li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="container-xl text-center join-coach">
        <h2>BẠN LÀ HUẤN LUYỆN VIÊN</h2>
        <h2 className="text-brand big">CHUYÊN NGHIỆP</h2>
        <p>Gia nhập đội ngũ Sport Center để cùng xây dựng nền tảng thể thao vững chắc cho thế hệ vận động tương lai. Môi trường làm việc quốc tế, cơ sở vật chất hàng đầu</p>
        <Link to="/tuyen-sinh" className="btn btn-brand btn-lg">ĐĂNG KÍ NGAY</Link>
      </section>
    </>
  )
}
