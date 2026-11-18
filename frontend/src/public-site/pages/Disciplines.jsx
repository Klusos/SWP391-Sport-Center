import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import PageHero from '../components/PageHero.jsx'
import Img from '../components/Img.jsx'
import { getPublicCoursesBySportApi, getPublicSportsApi, resolveImageUrl } from '../../api.js'
import { courseAgePolicyLabel } from '../../courseAgeEligibility.js'
import { formatSportIndex } from './disciplinePresentation.js'
import { getCourseStateForSport, requestCoursesForSport } from './disciplineCourseRequest.js'
import PublicConsultationCta from '../components/PublicConsultationCta.jsx'
import './Disciplines.css'
export default function Disciplines() {
  const { sportId } = useParams()
  const [sports, setSports] = useState([])
  const [courseState, setCourseState] = useState({ sportId: null, status: 'idle', courses: [], error: '' })
  const [sportLoading, setSportLoading] = useState(true)
  const [sportError, setSportError] = useState('')
  useEffect(() => {
    let active = true
    getPublicSportsApi().then((rows) => { if (active) setSports(Array.isArray(rows) ? rows : []) })
      .catch((err) => { if (active) setSportError(err.message || 'Không tải được danh sách bộ môn.') })
      .finally(() => { if (active) setSportLoading(false) })
    return () => { active = false }
  }, [])
  useEffect(() => {
    if (!sportId) return undefined
    return requestCoursesForSport(sportId, getPublicCoursesBySportApi, setCourseState)
  }, [sportId])
  const selectedSport = sports.find((sport) => String(sport.id) === sportId)
  const routeCourseState = getCourseStateForSport(sportId, courseState)
  return <div className={`disciplines-page${sportId ? ' sport-detail-page' : ''}`}>
    <PageHero title={sportId ? (selectedSport?.name || 'Bộ môn') : 'BỘ MÔN'}
      subtitle={sportId ? 'Các khóa học hiện có thuộc bộ môn đã chọn.' : 'Chọn môn thể thao để xem các khóa học đang mở.'}
      image={sportId ? resolveImageUrl(selectedSport?.imageUrl) || '/images/disciplines-hero.png' : '/images/disciplines-hero.png'}
      showBrand={!sportId} />
    <section className="section disciplines-section public-page-content-with-cta"><div className="container-xl disciplines-content">
      {sportId && <p className="disciplines-back"><Link to="/bo-mon" className="link-arrow">
        <span className="disciplines-back-icon" aria-hidden="true">←</span><span>Tất cả bộ môn</span>
      </Link></p>}
      <div className={`disciplines-heading${sportId ? ' disciplines-heading-detail' : ''}`}><div>{!sportId && <span className="disciplines-eyebrow">SPORTS CENTER</span>}
        <h2>{sportId ? 'Khóa học hiện có' : 'Khám phá các bộ môn'}</h2>
        <p>{sportId ? 'Chọn chương trình phù hợp với trình độ và mục tiêu luyện tập của bạn.' : 'Chọn một bộ môn để xem các chương trình và khóa học hiện có.'}</p></div>
        {!sportId && !sportLoading && !sportError && <span className="disciplines-count">{sports.length}<small>bộ môn</small></span>}
        {sportId && routeCourseState.status === 'success' && <span className="disciplines-count">{routeCourseState.courses.length}<small>khóa học</small></span>}
      </div>
      {sportId ? (routeCourseState.status === 'loading' ? <p className="disciplines-state" role="status">Đang tải khóa học…</p>
        : routeCourseState.status === 'error' ? <p className="banner error disciplines-error" role="alert">{routeCourseState.error}</p>
          : routeCourseState.status === 'empty' ? <p className="disciplines-state disciplines-empty">Chưa có khóa học đang hoạt động cho bộ môn này.</p>
            : <div className="disciplines-grid disciplines-course-grid" data-count={routeCourseState.courses.length}>{routeCourseState.courses.map((course) => <article className="disciplines-course-card" key={course.id}>
          <div className="disciplines-course-card-content">
            <h3>{course.name}</h3>
            {course.level && <p className="disciplines-course-meta">Trình độ: {course.level}</p>}
            {course.description
              ? <p>{course.description}</p>
              : course.learningOutcome && <p><span className="disciplines-course-outcome-label">Kết quả mong đợi:</span> {course.learningOutcome}</p>}
            {(course.targetAgeMin != null || course.targetAgeMax != null) && <p className="disciplines-course-meta">
              {courseAgePolicyLabel(course)}: {course.targetAgeMin ?? 'Không giới hạn'} – {course.targetAgeMax ?? 'Không giới hạn'} tuổi
            </p>}
          </div>
          <Link to={`/khoa-hoc/${course.id}`} className="disciplines-course-action">Xem chi tiết <span aria-hidden="true">→</span></Link>
        </article>)}</div>)
        : sportLoading ? <p className="disciplines-state" role="status">Đang tải bộ môn…</p> : sportError ? <p className="banner error disciplines-error" role="alert">{sportError}</p> : sports.length === 0
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
    <PublicConsultationCta eyebrow="Tư vấn ghi danh" title="Bạn đã tìm được bộ môn phù hợp?" description="Xem các lựa chọn ghi danh và gửi yêu cầu tư vấn tại Sport Center." />
  </div>
}
