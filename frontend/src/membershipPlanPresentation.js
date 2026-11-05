export function membershipPlanBenefits(plan) {
  const percent = (value) => value == null ? "—" : `${value}%`;
  const entitlement = (value) => typeof value === "boolean" ? (value ? "Bao gồm" : "Không bao gồm") : "—";
  return [
    { label: "Ưu đãi khóa học", value: percent(plan?.courseDiscountPercent) },
    { label: "Ưu đãi huấn luyện cá nhân", value: percent(plan?.personalTrainingDiscountPercent) },
    { label: "Giáo án cá nhân", value: entitlement(plan?.allowPersonalTrainingPlan) },
    { label: "Huấn luyện 1-1", value: entitlement(plan?.allowOneOnOneCoaching) },
  ];
}
