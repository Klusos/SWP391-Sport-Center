import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getPublicMembershipPlansApi } from '../../api.js'
import PageHero from '../components/PageHero.jsx'
import SectionTitle from '../components/SectionTitle.jsx'

const stats = [
  ['bi-people-fill', '24h', 'Phản hồi tư vấn'],
  ['bi-crosshair', '1:1', 'Định hướng lộ trình'],
  ['bi-journal-bookmark-fill', '12', 'Bộ môn đào tạo'],
  ['bi-folder-fill', '100%', 'Thông tin bảo mật'],
]

const steps = [
  ['bi-book-fill', 'Chọn bộ môn'],
  ['bi-telephone-plus-fill', 'Đăng ký tư vấn'],
  ['bi-wallet2', 'Thanh toán'],
  ['bi-pencil-fill', 'Chính thức ghi danh'],
]

function formatPrice(value) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })
    .format(Number(value || 0))
}

function formatDuration(months) {
  const duration = Number(months)
  if (!Number.isFinite(duration) || duration <= 0) return ''
  const years = Math.floor(duration / 12)
  const remainingMonths = duration % 12
  return [years ? `${years} năm` : '', remainingMonths ? `${remainingMonths} tháng` : '']
    .filter(Boolean)
    .join(' ')
}

function planBenefits(plan) {
  const benefits = []
  if (Number(plan.courseDiscountPercent) > 0) {
    benefits.push(`ưu đãi ${plan.courseDiscountPercent}% học phí khóa học`)
  }
  if (Number(plan.personalTrainingDiscountPercent) > 0) {
    benefits.push(`ưu đãi ${plan.personalTrainingDiscountPercent}% huấn luyện cá nhân`)
  }
  if (plan.allowPersonalTrainingPlan) benefits.push('Có thể đăng ký gói huấn luyện cá nhân')
  if (plan.allowOneOnOneCoaching) benefits.push('Có thể đăng ký huấn luyện 1 kèm 1')
  return benefits
}

export default function Admission() {
  const [plans, setPlans] = useState([])
  const [loadingPlans, setLoadingPlans] = useState(true)
  const [plansError, setPlansError] = useState('')

  useEffect(() => {
    let active = true
    getPublicMembershipPlansApi()
      .then((rows) => { if (active) setPlans(Array.isArray(rows) ? rows.filter((plan) => String(plan.status).toUpperCase() === 'ACTIVE') : []) })
      .catch((error) => { if (active) setPlansError(error.message || 'Không thể tải danh sách gói hội viên.') })
      .finally(() => { if (active) setLoadingPlans(false) })
    return () => { active = false }
  }, [])

  return (
    <>
      <PageHero
        title="TUYỂN SINH"
        subtitle="Đăng kí tuyển sinh và bắt đầu hành trình thể chất toàn diện cùng Sport Center"
        image="/images/admission-hero.png"
      />

      <section className="container-xl stat-row">
        <div className="row g-4">
          {stats.map(([icon, num, label]) => (
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

      <section className="container-xl pt-5">
        <SectionTitle plain="QUY TRÌNH" accent="TUYỂN SINH" />
        <div className="row g-4 mt-1">
          {steps.map(([icon, label]) => (
            <div className="col-6 col-lg-3" key={label}>
              <div className="step-icon"><i className={`bi ${icon}`} /></div>
              <h5 className="step-label">{label.toUpperCase()}</h5>
            </div>
          ))}
        </div>

        <div className="info-box mt-5">
          <h3>CHỌN BỘ MÔN</h3>
          <p>
            Khám phá danh sách 12 bộ môn tại Sport Center - từ bơi lội, bóng rổ, tennis đến ballet và taekwondo. Bạn có thể chọn một hoặc nhiều môn phù hợp với sở thích, mục tiêu và thể chất của mình
          </p>
        </div>

        <h5 className="progress-title">TIẾN ĐỘ TUYỂN SINH</h5>
        <div className="progress-segs">
          <span className="on" /><span /><span />
        </div>

        <div className="text-center my-5">
          <Link to="/tuyen-sinh#dang-ky" className="btn btn-brand btn-lg px-5">ĐĂNG KÍ NGAY</Link>
        </div>
      </section>

      <section className="container-xl pb-5">
        <SectionTitle plain="CÁC GÓI" accent="HỘI VIÊN" />
        <p className="quote-bar">
          Tham khảo các gói hội viên đang áp dụng tại Sport Center. Quyền lợi hiển thị bên dưới phản ánh các ưu đãi và lựa chọn được cấu hình cho từng gói.
        </p>
        {loadingPlans && <p className="mt-4" role="status">Đang tải các gói hội viên...</p>}
        {plansError && <div className="alert alert-danger mt-4" role="alert">{plansError}</div>}
        {!loadingPlans && !plansError && plans.length === 0 && (
          <p className="mt-4">Hiện chưa có gói hội viên đang áp dụng. Vui lòng liên hệ lễ tân để được tư vấn.</p>
        )}
        {!loadingPlans && !plansError && plans.length > 0 && (
          <div className="row g-4 mt-2">
            {plans.map((plan) => {
              const benefits = planBenefits(plan)
              return (
                <div className="col-lg-4" key={plan.id}>
                  <article className="plan-card">
                    <span className="plan-tag">ĐANG ÁP DỤNG</span>
                    <h3>{plan.name?.toUpperCase()}</h3>
                    <p className="plan-desc">{plan.description || 'Gói hội viên tại Sport Center.'}</p>
                    <h6>GIÁ GÓI</h6>
                    <p className="mb-3"><strong>{formatPrice(plan.price)}</strong> / {formatDuration(plan.durationMonths)}</p>
                    <ul className="list-unstyled">
                      {(benefits.length ? benefits : ['Quyền lợi chi tiết vui lòng liên hệ lễ tân.']).map((benefit, index) => (
                        <li key={`${plan.id}-benefit-${index}`}><span className="perk-n">{index + 1}</span>{benefit}</li>
                      ))}
                    </ul>
                    <button className="btn btn-outline-plan" type="button">TÌM HIỂU THÊM</button>
                  </article>
                </div>
              )
            })}
          </div>
        )}
      </section>
    </>
  )
}
