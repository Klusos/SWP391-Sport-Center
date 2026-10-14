import { Link } from 'react-router-dom'
import PageHero from '../components/PageHero.jsx'
import { disciplines } from '../data/content.js'

export default function Disciplines() {
  return (
    <>
      <PageHero
        title="BỘ MÔN"
        subtitle="Chọn môn thể thao phù hợp với sở thích, mục tiêu và cấp độ của bạn."
        image="/images/disciplines-hero.png"
      />
      <section className="section">
        <div className="container-xl">
          <div className="row g-4">
            {disciplines.map((d) => (
              <div className="col-md-6 col-lg-4" key={d.code}>
                <article className="sport-card">
                  <span className="sport-badge" style={{ background: d.color }}>{d.code}</span>
                  <div>
                    <h5>{d.title}</h5>
                    <Link to="/lich-tap" className="link-arrow">Xem lịch lớp <i className="bi bi-arrow-right" /></Link>
                    <p className="muted small mb-0">Từ 3 buổi / tuần</p>
                  </div>
                </article>
              </div>
            ))}
          </div>
        </div>
      </section>
    </>
  )
}
