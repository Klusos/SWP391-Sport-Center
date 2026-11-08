import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import PageHero from '../components/PageHero.jsx'
import Img from '../components/Img.jsx'
import { getPublicCoursesBySportApi, getPublicSportsApi } from '../../api.js'
import { formatSportIndex } from './disciplinePresentation.js'
import './Disciplines.css'
export default function Disciplines() {
  const { sportId } = useParams()
  const [sports, setSports] = useState([])
  const [courses, setCourses] = useState([])
  const [sportLoading, setSportLoading] = useState(true)
  const [courseLoading, setCourseLoading] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    getPublicSportsApi().then((rows) => { if (active) setSports(Array.isArray(rows) ? rows : []) })
      .catch((err) => { if (active) setError(err.message || 'Không tải được danh sách bộ môn.') })
      .finally(() => { if (active) setSportLoading(false) })
    return () => { active = false }
  }, [])
  useEffect(() => {
    if (!sportId) return undefined
    let active = true
    Promise.resolve().then(() => { if (active) setCourseLoading(true); return getPublicCoursesBySportApi(sportId) }).then((rows) => { if (active) setCourses(Array.isArray(rows) ? rows : []) })
      .catch((err) => { if (active) setError(err.message || 'Không tải được khóa học.') })
      .finally(() => { if (active) setCourseLoading(false) })
    return () => { active = false }
  }, [sportId])
  const selectedSport = sports.find((sport) => String(sport.id) === sportId)
  return <div className="disciplines-page">
    <PageHero title={sportId ? (selectedSport?.name || 'KHÓA HỌC') : 'BỘ MÔN'}
      subtitle={sportId ? 'Các khóa học hiện có thuộc bộ môn đã chọn.' : 'Chọn môn thể thao để xem các khóa học đang mở.'}
      image="/images/disciplines-hero.png" />
    <section className="section disciplines-section"><div className="container-xl disciplines-content">
      {sportId && <p className="disciplines-back"><Link to="/bo-mon" className="link-arrow">← Tất cả bộ môn</Link></p>}
      <div className="disciplines-heading"><div><span className="disciplines-eyebrow">SPORTS CENTER</span>
        <h2>{sportId ? `Khám phá khóa học${selectedSport?.name ? ` · ${selectedSport.name}` : ''}` : 'Khám phá các bộ môn'}</h2>
        <p>{sportId ? 'Chọn chương trình phù hợp với trình độ và mục tiêu luyện tập của bạn.' : 'Chọn một bộ môn để xem các chương trình và khóa học hiện có.'}</p></div>
        {!sportId && !sportLoading && !error && <span className="disciplines-count">{sports.length}<small>bộ môn</small></span>}
        {sportId && !courseLoading && courses.length > 0 && <span className="disciplines-count">{courses.length}<small>khóa học</small></span>}
      </div>
      {error && <p className="banner error disciplines-error" role="alert">{error}</p>}
      {sportId ? (courseLoading ? <p className="disciplines-state" role="status">Đang tải khóa học…</p> : courses.length === 0
        ? <p className="disciplines-state disciplines-empty">Chưa có khóa học đang hoạt động cho bộ môn này.</p>
        : <div className="row g-4 disciplines-grid">{courses.map((course) => <div className="col-md-6 col-lg-4 disciplines-grid-item" key={course.id}>
          <article className="sport-card h-100 disciplines-course-card"><div><span className="sport-badge">{course.level || 'COURSE'}</span>
            <h3>{course.name}</h3><p className="muted small">{course.description || course.learningOutcome || 'Thông tin chi tiết khóa học.'}</p>
            <Link to={'/khoa-hoc/' + course.id} className="link-arrow">Xem chi tiết <i className="bi bi-arrow-right" /></Link>
          </div></article></div>)}</div>)
        : sportLoading ? <p className="disciplines-state" role="status">Đang tải bộ môn…</p> : sports.length === 0
          ? <p className="disciplines-state disciplines-empty">Hiện chưa có bộ môn nào đang hoạt động.</p>
          : <div className="disciplines-grid">{sports.map((sport, index) => <Link
            key={sport.id}
            to={`/bo-mon/${sport.id}`}
            className="disciplines-sport-card"
          >
            <div className="disciplines-sport-image-wrap">
              <Img src={sport.imageUrl} alt={sport.name} label={sport.name} className="disciplines-sport-image" />
              <span className="disciplines-sport-index" aria-hidden="true">{formatSportIndex(index)}</span>
            </div>
            <div className="disciplines-sport-card-body">
              <span className="disciplines-sport-small-label">BỘ MÔN</span>
              <h3>{sport.name}</h3>
              <div className="disciplines-sport-card-action">
                <span>Xem khóa học</span>
                <span className="disciplines-sport-arrow" aria-hidden="true">→</span>
              </div>
            </div>
          </Link>)}</div>}
    </div></section>
  </div>
}
