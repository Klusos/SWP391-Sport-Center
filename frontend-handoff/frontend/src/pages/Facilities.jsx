import { Link } from 'react-router-dom'
import Img from '../components/Img.jsx'
import { timeline, coreValues, facilities } from '../data/content.js'

export default function Facilities() {
  return (
    <>
      {/* Lịch sử */}
      <section className="history-hero">
        <Img src="/images/stadium-hero.png" alt="Sân thi đấu trong nhà" className="history-bg" label="" />
        <div className="history-overlay" />
        <div className="container-xl history-text">
          <h1>Lịch sử hình thành & phát triển</h1>
          <p className="hero-sub">Hành trình xây dựng một trung tâm thể thao chuyên nghiệp, hiện đại và dành cho mọi người.</p>
        </div>
      </section>

      <section className="section-sm bg-soft">
        <div className="container-xl">
          <ol className="timeline list-unstyled">
            {timeline.map((t) => (
              <li key={t.year}>
                <span className="tl-dot" />
                <span className="tl-year">{t.year}</span>
                <div>
                  <h5>{t.title}</h5>
                  <p>{t.text}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* Giá trị cốt lõi */}
      <section className="section">
        <div className="container-xl">
          <p className="label-blue mb-1">GIÁ TRỊ CỐT LÕI</p>
          <h2 className="h-xl">Khỏe hơn mỗi ngày. Tốt hơn mỗi ngày.</h2>
          <div className="row g-4 mt-1">
            {coreValues.map((v) => (
              <div className="col-lg-6" key={v.n}>
                <article className="value-card">
                  <div className="value-icon"><i className={`bi ${v.icon}`} /></div>
                  <div className="value-body">
                    <span className="value-n">{v.n}</span>
                    <h5>{v.title}</h5>
                    <p>{v.text}</p>
                  </div>
                </article>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Không gian */}
      <section className="space-hero">
        <div className="container-xl">
          <h1>Không gian tập luyện hiện đại</h1>
          <p className="hero-sub">Thiết kế tiện nghi, an toàn và đầy đủ trang thiết bị cho nhiều nhu cầu vận động.</p>
        </div>
      </section>

      <section className="section">
        <div className="container-xl">
          <h2 className="h-xl mb-5">Mỗi khu vực, một trải nghiệm</h2>
          <div className="d-flex flex-column gap-4">
            {facilities.map((f, i) => (
              <article className={`facility-row ${i % 2 ? 'reverse' : ''}`} key={f.title}>
                <div className="facility-img">
                  <Img src={f.img} alt={f.title} label={f.title} />
                </div>
                <div className="facility-info">
                  <h3>{f.title}</h3>
                  <p>{f.text}</p>
                  <Link to="/tuyen-sinh" className="link-arrow">Khám phá <i className="bi bi-arrow-right" /></Link>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>
    </>
  )
}
