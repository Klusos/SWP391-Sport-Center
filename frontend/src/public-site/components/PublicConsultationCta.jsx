import { Link } from "react-router-dom";

export default function PublicConsultationCta({ eyebrow, title, description }) {
  return (
    <section className="public-page-consultation-cta" aria-label={eyebrow}>
      <div className="container-xl public-page-consultation-cta-inner">
        <div>
          <p className="public-page-consultation-cta-eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
          <p className="public-page-consultation-cta-description">{description}</p>
        </div>
        <Link className="public-page-consultation-cta-link" to="/#public-consultation">
          Đến khu vực tư vấn <i className="bi bi-arrow-right" aria-hidden="true" />
        </Link>
      </div>
    </section>
  );
}
