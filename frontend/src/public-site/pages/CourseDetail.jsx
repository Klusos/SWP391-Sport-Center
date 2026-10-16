import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import PageHero from '../components/PageHero.jsx'
import { getPublicCourseApi } from '../../api.js'

export default function CourseDetail() {
  const { courseId } = useParams()
  const [course, setCourse] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    Promise.resolve().then(() => {
      if (active) {
        setLoading(true)
        setError('')
      }
      return getPublicCourseApi(courseId)
    })
      .then((data) => { if (active) setCourse(data) })
      .catch((requestError) => { if (active) setError(requestError.message || 'Không tải được thông tin khóa học.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [courseId])

  return (
    <>
      <PageHero title={course?.name || 'CHI TIẾT KHÓA HỌC'} subtitle={course?.sportName || 'Thông tin chương trình học'} />
      <section className="section"><div className="container-xl">
        <p className="mb-4"><Link to={course ? '/bo-mon/' + course.sportId : '/bo-mon'} className="link-arrow">← Quay lại danh sách khóa học</Link></p>
        {loading ? <p role="status">Đang tải thông tin khóa học…</p> : error ? <p className="banner error" role="alert">{error}</p> : course && (
          <article className="course-detail-card">
            <p className="label-blue">{course.sportName}</p>
            <h2>{course.name}</h2>
            {course.level && <p><strong>Trình độ:</strong> {course.level}</p>}
            {course.description && <p>{course.description}</p>}
            {course.learningOutcome && <div><h3>Kết quả mong đợi</h3><p>{course.learningOutcome}</p></div>}
            {(course.targetAgeMin != null || course.targetAgeMax != null) && <p><strong>Độ tuổi:</strong> {course.targetAgeMin ?? 'Không giới hạn'} – {course.targetAgeMax ?? 'Không giới hạn'}</p>}
            <Link to="/tuyen-sinh" className="btn btn-cyan">Đăng ký tư vấn</Link>
          </article>
        )}
      </div></section>
    </>
  )
}
