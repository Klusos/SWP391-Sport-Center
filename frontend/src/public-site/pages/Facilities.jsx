import { useEffect, useState } from 'react'
import Img from '../components/Img.jsx'
import { timeline, coreValues } from '../data/content.js'
import { getPublicFacilitiesApi } from '../../api.js'

export default function Facilities() {
  const [facilities, setFacilities] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    getPublicFacilitiesApi().then((rows) => { if (active) setFacilities(Array.isArray(rows) ? rows : []) })
      .catch((err) => { if (active) setError(err.message || 'Không tải được cơ sở vật chất.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
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
    {error && <p className="banner error" role="alert">{error}</p>}
    {loading ? <p role="status">Đang tải cơ sở vật chất…</p> : facilities.length === 0 ? <p>Hiện chưa có cơ sở vật chất đang hoạt động.</p> : (
      <div className="d-flex flex-column gap-4">
        {facilities.map((facility, index) => (
          <article className={'facility-row ' + (index % 2 ? 'reverse' : '')} key={facility.id}>
            <div className="facility-img"><Img src={facility.imageUrl} alt={facility.name} label={facility.name} /></div>
            <div className="facility-info"><h3>{facility.name}</h3>
              <p>{[facility.type, facility.sportName].filter(Boolean).join(' · ')}</p></div>
          </article>
        ))}
      </div>
    )}
  </div>
</section>
    </>
  )
}