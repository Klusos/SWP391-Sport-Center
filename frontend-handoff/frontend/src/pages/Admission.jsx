import { Link } from 'react-router-dom'
import PageHero from '../components/PageHero.jsx'
import SectionTitle from '../components/SectionTitle.jsx'
import { plans } from '../data/content.js'

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

export default function Admission() {
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
        <SectionTitle plain="CÁC GÓI" accent="HUẤN LUYỆN" />
        <p className="quote-bar">
          Các chương trình được dẫn dắt bởi đội ngũ HLV chuyên nghiệp, trong môi trường luyện tạo chuẩn quốc tế, giúp học viên phát huy tối đa tiềm năng và bản lĩnh thể thao
        </p>
        <div className="row g-4 mt-2">
          {plans.map((p) => (
            <div className="col-lg-4" key={p.tag}>
              <article className="plan-card">
                <span className="plan-tag">{p.tag}</span>
                <h3>{p.title.toUpperCase()}</h3>
                <p className="plan-desc">{p.desc}</p>
                <h6>GIÁ TRỊ NHẬN ĐƯỢC</h6>
                <ul className="list-unstyled">
                  {p.perks.map((perk, i) => (
                    <li key={i}><span className="perk-n">{i + 1}</span>{perk}</li>
                  ))}
                </ul>
                <button className="btn btn-outline-plan">TÌM HIỂU THÊM</button>
              </article>
            </div>
          ))}
        </div>
      </section>
    </>
  )
}
