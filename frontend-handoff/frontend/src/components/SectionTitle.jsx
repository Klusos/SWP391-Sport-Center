export default function SectionTitle({ plain, accent, sub }) {
  return (
    <div className="section-title">
      <h2>{plain} {accent && <span>{accent}</span>}</h2>
      <div className="title-bar" />
      {sub && <p className="title-sub">{sub}</p>}
    </div>
  )
}
