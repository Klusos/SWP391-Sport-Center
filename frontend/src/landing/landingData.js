export function getLandingSports(records) {
  return Array.isArray(records) ? records.slice(0, 6) : [];
}

export function getActiveLandingPlans(records) {
  return Array.isArray(records)
    ? records.filter((record) => String(record.status).toUpperCase() === "ACTIVE")
    : [];
}

export function formatLandingPlanPrice(value) {
  if (value == null || value === "") return null;
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount < 0) return null;
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatLandingPlanDuration(months) {
  if (months == null || months === "") return null;
  const duration = Number(months);
  if (!Number.isInteger(duration) || duration <= 0) return null;
  const years = Math.floor(duration / 12);
  const remainingMonths = duration % 12;
  return [years ? `${years} năm` : "", remainingMonths ? `${remainingMonths} tháng` : ""]
    .filter(Boolean)
    .join(" ");
}

function percentageBenefit(label, value) {
  if (value == null || value === "") {
    return { label, state: "unknown", value: "Chưa có dữ liệu" };
  }
  const percentage = Number(value);
  if (!Number.isFinite(percentage) || percentage < 0) {
    return { label, state: "unknown", value: "Chưa có dữ liệu" };
  }
  if (percentage === 0) {
    return { label, state: "excluded", value: "Không áp dụng" };
  }
  const formatted = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(percentage);
  return { label, state: "included", value: `${formatted}%` };
}

function entitlementBenefit(label, value) {
  if (value === true) return { label, state: "included", value: "Được bao gồm" };
  if (value === false) return { label, state: "excluded", value: "Không bao gồm" };
  return { label, state: "unknown", value: "Chưa có dữ liệu" };
}

export function getLandingPlanBenefits(plan) {
  if (!plan) return [];
  const benefits = [percentageBenefit("Ưu đãi khóa học", plan.courseDiscountPercent)];
  if (plan.allowOneOnOneCoaching === true) {
    benefits.push(percentageBenefit("Ưu đãi huấn luyện 1 kèm 1", plan.personalTrainingDiscountPercent));
  }
  benefits.push(
    entitlementBenefit("Giáo án tập luyện cá nhân", plan.allowPersonalTrainingPlan),
    entitlementBenefit("Huấn luyện 1 kèm 1", plan.allowOneOnOneCoaching),
  );
  return benefits;
}
