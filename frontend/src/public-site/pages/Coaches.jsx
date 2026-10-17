import { useEffect, useState } from 'react'
import PageHero from '../components/PageHero.jsx'
import Img from '../components/Img.jsx'
import { getPublicCoachesApi } from '../../api.js'
export default function Coaches() {
  const [coaches, setCoaches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    getPublicCoachesApi().then((rows) => { if (active) setCoaches(Array.isArray(rows) ? rows : []) })
      .catch((err) => { if (active) setError(err.message || 'Không tải được danh sách huấn luyện viên.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  return <>
    <PageHero title="ĐỘI NGŨ HLV" subtitle="Gặp gỡ đội ngũ huấn luyện viên của Sport Center." image="/images/coaches-hero.png" />
    <section className="section"><div className="container-xl">
      <p className="label-blue">HUẤN LUYỆN VIÊN</p><h2 className="h-xl">Đồng hành cùng bạn</h2>
      {error && <p className="banner error" role="alert">{error}</p>}
      {loading ? <p role="status">Đang tải huấn luyện viên…</p> : coaches.length === 0 ? <p>Hiện chưa có huấn luyện viên đang hoạt động.</p>
        : <div className="row g-4 mt-2">{coaches.map((coach) => <div className="col-sm-6 col-lg-4" key={coach.id}>
          <article className="coach-catalog-card">
            <Img src={coach.imageUrl} alt={coach.name} label={coach.name} className="coach-catalog-image" />
            <div className="coach-catalog-copy">
              <h3>{coach.name}</h3>
              {coach.bio?.trim() && <p className="coach-catalog-bio">{coach.bio}</p>}
              {Array.isArray(coach.sports) && coach.sports.length > 0 && <ul className="coach-catalog-sports">
                {coach.sports.map((sport) => <li key={sport.sportId}>
                  <strong>{sport.sportName}</strong>
                  {Number.isInteger(sport.experienceYears) && <span>{sport.experienceYears} năm kinh nghiệm</span>}
                  {sport.certification?.trim() && <span>{sport.certification}</span>}
                </li>)}
              </ul>}
            </div>
          </article>
        </div>)}</div>}
    </div></section>
  </>
}
