import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import PageHero from '../components/PageHero.jsx'
import Img from '../components/Img.jsx'
import { getPublicCoachesApi } from '../../api.js'
export default function Coaches() {
  const [coaches, setCoaches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [sportFilter, setSportFilter] = useState('all')
  const [expanded, setExpanded] = useState(() => new Set())
  useEffect(() => {
    let active = true
    getPublicCoachesApi().then((rows) => { if (active) setCoaches(Array.isArray(rows) ? rows : []) })
      .catch((err) => { if (active) setError(err.message || 'Không tải được danh sách huấn luyện viên.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  const sports = [...new Map(coaches.flatMap((coach) => coach.sports || []).map((sport) => [sport.sportId, sport.sportName])).entries()]
  const visibleCoaches = sportFilter === 'all' ? coaches : coaches.filter((coach) => coach.sports?.some((sport) => String(sport.sportId) === sportFilter))
  const sportCount = new Set(coaches.flatMap((coach) => coach.sports || []).map((sport) => sport.sportId)).size
  const toggleBio = (id) => setExpanded((previous) => {
    const next = new Set(previous)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    return next
  })
  return <>
    <PageHero title="ĐỘI NGŨ HLV" subtitle="Gặp gỡ đội ngũ huấn luyện viên của Sport Center." image="/images/coaches-hero.png" />
    <section className="section"><div className="container-xl">
      <div className="coach-intro">
        <div><p className="label-blue">HUẤN LUYỆN VIÊN</p><h2 className="h-xl">Đồng hành cùng bạn</h2>
          <p className="muted">Đội ngũ huấn luyện viên hỗ trợ bạn xây dựng thói quen vận động phù hợp với mục tiêu và nhịp sống riêng.</p>
          <p className="muted">Chúng tôi đề cao nền tảng kỹ thuật, sự kiên trì và lộ trình tập luyện có thể điều chỉnh theo từng người.</p>
        </div>
        {!loading && !error && <div className="coach-stats" aria-label="Thống kê huấn luyện viên">
          <div><strong>{coaches.length}</strong><span>Huấn luyện viên đang hoạt động</span></div>
          {sportCount > 0 && <div><strong>{sportCount}</strong><span>Bộ môn được hướng dẫn</span></div>}
        </div>}
      </div>
      {!loading && !error && coaches.length > 0 && <div className="coach-filter">
        <label htmlFor="coach-sport-filter">Lọc theo bộ môn</label>
        <select id="coach-sport-filter" value={sportFilter} onChange={(event) => setSportFilter(event.target.value)}>
          <option value="all">Tất cả bộ môn</option>
          {sports.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
        </select>
      </div>}
      {error && <p className="banner error" role="alert">{error}</p>}
      {loading ? <p role="status">Đang tải huấn luyện viên…</p> : coaches.length === 0 ? <p>Hiện chưa có huấn luyện viên đang hoạt động.</p>
        : visibleCoaches.length === 0 ? <p>Không có huấn luyện viên nào thuộc bộ môn đã chọn.</p> : <div className="row g-4 mt-2">{visibleCoaches.map((coach) => <div className="col-sm-6 col-lg-4" key={coach.id}>
          <article className="coach-catalog-card">
            <Img src={coach.imageUrl} alt={coach.name} label={coach.name} className="coach-catalog-image" />
            <div className="coach-catalog-copy">
              <h3>{coach.name}</h3>
              {coach.bio?.trim() && <div className="coach-bio-wrap"><p className={expanded.has(coach.id) ? 'coach-catalog-bio expanded' : 'coach-catalog-bio'}>{coach.bio}</p>{coach.bio.length > 170 && <button type="button" className="coach-expand" aria-expanded={expanded.has(coach.id)} onClick={() => toggleBio(coach.id)}>{expanded.has(coach.id) ? 'Thu gọn' : 'Đọc thêm'}</button>}</div>}
              {Array.isArray(coach.sports) && coach.sports.length > 0 && <ul className="coach-catalog-sports">
                {coach.sports.map((sport) => <li key={sport.sportId}>
                  <strong>{sport.sportName}</strong>
                  {Number.isInteger(sport.experienceYears) && <span>{sport.experienceYears} năm kinh nghiệm</span>}
                  {sport.certification?.trim() && <span><b>Chứng nhận:</b> {sport.certification}</span>}
                </li>)}
              </ul>}
            </div>
          </article>
        </div>)}</div>}
    </div></section>
    <section className="coach-cta"><div className="container-xl"><div><p className="label-cyan">BẮT ĐẦU HÀNH TRÌNH</p><h2>Bạn muốn tìm lộ trình phù hợp?</h2><p>Gửi yêu cầu tư vấn để đội ngũ trung tâm trao đổi về mục tiêu tập luyện của bạn.</p></div><Link to="/tuyen-sinh" className="btn btn-cyan">ĐĂNG KÝ TƯ VẤN</Link></div></section>
  </>
}
