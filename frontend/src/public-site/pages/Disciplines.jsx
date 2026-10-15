import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import PageHero from '../components/PageHero.jsx'
import { getPublicCoursesBySportApi, getPublicSportsApi } from '../../api.js'

export default function Disciplines() {
  const { sportId } = useParams()
  const [sports, setSports] = useState([])
  const [courses, setCourses] = useState([])
  const [sportLoading, setSportLoading] = useState(true)
  const [courseLoading, setCourseLoading] = useState(Boolean(sportId))
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    getPublicSportsApi()
      .then((data) => { if (active) setSports(Array.isArray(data) ? data : []) })
      .catch((requestError) => { if (active) setError(requestError.message || 'Không tải được danh sách bộ môn.') })
      .finally(() => { if (active) setSportLoading(false) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!sportId) return undefined
    let active = true
    Promise.resolve().then(() => {
      if (active) {
        setCourseLoading(true)
        setError('')
      }
      return getPublicCoursesBySportApi(sportId)
    })
      .then((data) => { if (active) setCourses(Array.isArray(data) ? data : []) })
      .catch((requestError) => { if (active) setError(requestError.message || 'Không tải được khóa học.') })
      .finally(() => { if (active) setCourseLoading(false) })
    return () => { active = false }
  }, [sportId])

  const selectedSport = sports.find((sport) => String(sport.id) === sportId)

  return (
    <>
      <PageHero
        title={sportId ? (selectedSport?.name || 'KHÓA HỌC') : 'BỘ MÔN'}
        subtitle={sportId ? 'Các khóa học hiện có thuộc bộ môn đã chọn.' : 'Chọn môn thể thao để xem các khóa học đang mở.'}
        image="/images/disciplines-hero.png"
      />
      <section className="section">
        <div className="container-xl">
          {sportId && <p className="mb-4"><Link to="/bo-mon" className="link-arrow">← Tất cả bộ môn</Link></p>}
          {error && <p className="banner error" role="alert">{error}</p>}
          {sportId ? (
            courseLoading ? <p role="status">Đang tải khóa học…</p> : courses.length === 0 ? <p>Chưa có khóa học đang hoạt động cho bộ môn này.</p> : (
              <div className="row g-4">
                {courses.map((course) => (
                  <div className="col-md-6 col-lg-4" key={course.id}>
                    <article className="sport-card h-100">
                      <div>
                        <span className="sport-badge">{course.level || 'COURSE'}</span>
                        <h5 className="mt-3">{course.name}</h5>
                        <p className="muted small">{course.description || course.learningOutcome || 'Thông tin chi tiết khóa học.'}</p>
                        <Link to={'/khoa-hoc/' + course.id} className="link-arrow">Xem chi tiết <i className="bi bi-arrow-right" /></Link>
                      </div>
                    </article>
                  </div>
                ))}
              </div>
            )
          ) : sportLoading ? <p role="status">Đang tải bộ môn…</p> : sports.length === 0 ? <p>Hiện chưa có bộ môn nào đang hoạt động.</p> : (
            <div className="row g-4">
              {sports.map((sport) => (
                <div className="col-md-6 col-lg-4" key={sport.id}>
                  <Link to={'/bo-mon/' + sport.id} className="sport-card d-block h-100 text-decoration-none">
                    <span className="sport-badge">{sport.name.slice(0, 3).toUpperCase()}</span>
                    <h5 className="mt-3">{sport.name}</h5>
                    <span className="link-arrow">Xem khóa học <i className="bi bi-arrow-right" /></span>
                  </Link>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </>
  )
}
