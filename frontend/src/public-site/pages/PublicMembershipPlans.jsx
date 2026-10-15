import { useEffect, useState } from "react";
import SectionTitle from "../components/SectionTitle.jsx";
import { getPublicMembershipPlansApi } from "../../api.js";

function formatPrice(value) {
  return new Intl.NumberFormat("vi-VN", { minimumFractionDigits: 0, maximumFractionDigits: 2 })
    .format(Number(value || 0));
}

export default function PublicMembershipPlans() {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    getPublicMembershipPlansApi()
      .then((rows) => { if (active) setPlans(Array.isArray(rows) ? rows : []); })
      .catch((requestError) => { if (active) setError(requestError.message || "Could not load membership plans."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  return (
    <section className="container-xl pt-5 pb-5">
      <SectionTitle plain="MEMBERSHIP" accent="PLANS" />
      <p className="quote-bar">Explore the active plans currently offered by Sport Center. Contact reception for questions about eligibility and terms.</p>
      {loading && <p role="status" className="mt-4">Loading available plans?</p>}
      {error && <div className="alert alert-danger mt-4" role="alert">{error}</div>}
      {!loading && !error && plans.length === 0 && <p className="mt-4">There are no active membership plans at this time.</p>}
      {!loading && !error && plans.length > 0 && (
        <div className="row g-4 mt-2">
          {plans.map((plan) => {
            const perks = [];
            if (Number(plan.courseDiscountPercent) > 0) perks.push("Course discount: " + plan.courseDiscountPercent + "%");
            if (Number(plan.personalTrainingDiscountPercent) > 0) perks.push("Personal training discount: " + plan.personalTrainingDiscountPercent + "%");
            if (plan.allowPersonalTrainingPlan) perks.push("Personal training plan access");
            if (plan.allowOneOnOneCoaching) perks.push("One-on-one coaching access");
            if (perks.length === 0) perks.push("Standard membership benefits");
            return (
              <div className="col-lg-4" key={plan.id}>
                <article className="plan-card">
                  <span className="plan-tag">ACTIVE PLAN</span>
                  <h3>{plan.name}</h3>
                  <p className="plan-desc">{plan.description || "Membership plan from Sport Center."}</p>
                  <h6>{formatPrice(plan.price)} / {plan.durationMonths} months</h6>
                  <ul className="list-unstyled">
                    {perks.map((perk) => <li key={perk}><span className="perk-n">?</span>{perk}</li>)}
                  </ul>
                </article>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
