export default function PageHero({ title, subtitle, image, dark = false }) {
  const style = image
  ? { backgroundImage: `linear-gradient(90deg, rgba(10,22,40,.55) 0%, rgba(10,22,40,.15) 45%, rgba(10,22,40,0) 100%), url(${image})` }
  : {}
  return (
    <section className={`page-hero ${dark ? 'page-hero-dark' : ''} ${image ? 'has-image' : ''}`} style={style}>
      <div className="container-xl">
        <span className="pill-badge">SPORT CENTER</span>
        <h1>{title}</h1>
        <p className="hero-sub">{subtitle}</p>
      </div>
    </section>
  )
}
