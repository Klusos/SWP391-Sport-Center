import PageHero from '../components/PageHero.jsx'
import { schedule } from '../data/content.js'

export default function Schedule() {
  return (
    <>
      <PageHero dark title="LỊCH TẬP" subtitle="Nhiều khung giờ linh hoạt, phù hợp với lịch trình của bạn." />
      <section className="section">
        <div className="container-xl">
          <div className="row g-4">
            {schedule.map((d) => (
              <div className="col-md-6 col-lg-4" key={d.day}>
                <div className="day-card">
                  <h5>{d.day}</h5>
                  <ul className="list-unstyled mb-0">
                    {d.slots.map(([time, name, place]) => (
                      <li key={time}>
                        <strong>{time}</strong>
                        <span>{name}<small>{place}</small></span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>
    </>
  )
}
