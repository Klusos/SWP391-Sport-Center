export function counterCoursePaymentPayload(memberId, registrationId) {
  const member = Number(memberId), registration = Number(registrationId);
  if (![member, registration].every((id) => Number.isSafeInteger(id) && id > 0)) {
    throw new Error("A valid member and registration are required.");
  }
  return { memberId: member, method: "COUNTER", purpose: "COURSE_REGISTRATION", referenceId: registration };
}

export function activeSeatCount(registrations, classId, now = Date.now()) {
  return (registrations || []).filter((item) => Number(item.classId) === Number(classId)
    && (item.status === "CONFIRMED" || (item.status === "PENDING_PAYMENT" && vietnamTime(item.paymentDeadline) > now))).length;
}

export function vietnamTime(value) {
  if (!value) return NaN;
  const local = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value);
  const result = new Date(local ? `${value}+07:00` : value).getTime();
  return Number.isFinite(result) ? result : NaN;
}

export function formatVietnamTime(value) {
  const date = new Date(vietnamTime(value));
  if (Number.isNaN(date.getTime())) return "Chưa có hạn";
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Ho_Chi_Minh",
  }).format(date);
}

export function membershipDiscount(memberMemberships, plans, sportDate = new Date()) {
  const date = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit",
  }).format(sportDate);
  const planDiscounts = new Map((plans || []).map((plan) => [Number(plan.id), Number(plan.courseDiscountPercent) || 0]));
  return Math.max(0, ...(memberMemberships || []).filter((membership) => membership.status === "ACTIVE"
    && membership.startDate <= date && membership.endDate > date)
    .map((membership) => planDiscounts.get(Number(membership.planId)) || 0));
}

export function eligibilityIssues(member, course, level, offering, registrations = []) {
  const issues = [];
  if (member?.status !== "ACTIVE" || member?.role !== "MEMBER") issues.push("Tài khoản không phải MEMBER đang hoạt động.");
  const required = String(course?.level || "BEGINNER").toUpperCase();
  const ranks = { BEGINNER: 0, INTERMEDIATE: 1, ADVANCED: 2 };
  if (!(required in ranks)) issues.push("Khóa học có cấp độ không được hỗ trợ.");
  const dob = member?.dateOfBirth;
  if (dob && (course?.targetAgeMin != null || course?.targetAgeMax != null)) {
    const birth = new Date(`${dob}T00:00:00`), today = new Date();
    let age = today.getFullYear() - birth.getFullYear();
    if (today.getMonth() < birth.getMonth() || (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate())) age--;
    if ((course.targetAgeMin != null && age < course.targetAgeMin) || (course.targetAgeMax != null && age > course.targetAgeMax)) {
      issues.push("Thành viên không nằm trong độ tuổi mục tiêu của khóa.");
    }
  } else if (course?.targetAgeMin != null || course?.targetAgeMax != null) {
    issues.push("Cần ngày sinh để kiểm tra độ tuổi.");
  }
  if (required in ranks && required !== "BEGINNER") {
    if (!level?.verifiedAt || !level?.verifiedByCoachId || !(String(level.level).toUpperCase() in ranks)) {
      issues.push("Cần HLV xác minh trình độ trước khi đăng ký khóa này.");
    } else if (ranks[String(level.level).toUpperCase()] < ranks[required]) {
      issues.push("Trình độ đã xác minh chưa đáp ứng yêu cầu của khóa.");
    }
  }
  if (offering?.startDate) {
    const opens = new Date(`${offering.startDate}T00:00:00Z`);
    opens.setUTCDate(opens.getUTCDate() - 30);
    const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
    if (today < opens.toISOString().slice(0, 10)) issues.push(`Đợt đăng ký mở từ ${opens.toLocaleDateString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh" })}.`);
  }
  if (offering?.registrationDeadline && vietnamTime(offering.registrationDeadline) <= Date.now()) {
    issues.push("Đợt đăng ký đã qua hạn.");
  }
  if (offering && registrations.some((registration) => Number(registration.memberId) === Number(member?.id)
    && Number(registration.courseOfferingId) === Number(offering.id)
    && (registration.status === "CONFIRMED" || (registration.status === "PENDING_PAYMENT"
      && vietnamTime(registration.paymentDeadline) > Date.now())))) {
    issues.push("Thành viên đã có đăng ký đang giữ chỗ hoặc đã xác nhận trong đợt này.");
  }
  return issues;
}

export function estimateCoursePrice(price, discountPercent) {
  const original = Number(price);
  if (!Number.isFinite(original) || original < 0) return null;
  const discount = Math.min(100, Math.max(0, Number(discountPercent) || 0));
  const amount = Math.round((original + Number.EPSILON) * 100) / 100;
  const discountAmount = Math.round((amount * discount / 100 + Number.EPSILON) * 100) / 100;
  return { originalPrice: amount, membershipDiscount: discountAmount, finalPrice: Math.round((amount - discountAmount + Number.EPSILON) * 100) / 100 };
}
