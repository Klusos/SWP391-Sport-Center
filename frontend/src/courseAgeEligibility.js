export function vietnamBusinessDate(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(now);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function parseDate(value) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
    ? { year, month, day }
    : null;
}

export function ageOnVietnamBusinessDate(dateOfBirth, businessDate = vietnamBusinessDate()) {
  const birth = parseDate(dateOfBirth);
  const today = parseDate(businessDate);
  if (!birth || !today) return null;
  if (dateOfBirth > businessDate) return null;
  let age = today.year - birth.year;
  if (today.month < birth.month || (today.month === birth.month && today.day < birth.day)) age -= 1;
  return age >= 0 ? age : null;
}

export function evaluateCourseAgeEligibility(course, dateOfBirth, businessDate = vietnamBusinessDate()) {
  const minimum = course?.targetAgeMin == null ? null : Number(course.targetAgeMin);
  const maximum = course?.targetAgeMax == null ? null : Number(course.targetAgeMax);
  const policy = String(course?.agePolicy || "RECOMMENDED").toUpperCase();
  const validBounds = (minimum == null || Number.isInteger(minimum) && minimum >= 0)
    && (maximum == null || Number.isInteger(maximum) && maximum >= 0)
    && (minimum == null || maximum == null || minimum <= maximum);

  if (!validBounds || !["RECOMMENDED", "REQUIRED"].includes(policy)) {
    return { decision: "BLOCKED", age: null, message: "Độ tuổi khóa học chưa được cấu hình hợp lệ. Vui lòng liên hệ lễ tân." };
  }
  if (policy === "REQUIRED" && (minimum == null || maximum == null)) {
    return { decision: "BLOCKED", age: null, message: "Khóa học yêu cầu độ tuổi nhưng chưa có đủ tuổi tối thiểu và tối đa." };
  }

  const age = ageOnVietnamBusinessDate(dateOfBirth, businessDate);
  if (age == null) {
    return { decision: "BLOCKED", age: null, message: "Cần ngày sinh hợp lệ để xác minh điều kiện độ tuổi trước khi đăng ký." };
  }

  const belowMinimum = minimum != null && age < minimum;
  const aboveMaximum = maximum != null && age > maximum;
  if (age < 10) {
    if (minimum == null || maximum == null) {
      return { decision: "BLOCKED", age, message: "Thành viên dưới 10 tuổi cần khóa học có đủ khoảng tuổi để xác minh an toàn." };
    }
    if (belowMinimum || aboveMaximum) {
      return { decision: "BLOCKED", age, message: "Thành viên dưới 10 tuổi phải nằm trong khoảng tuổi của khóa học." };
    }
    return { decision: "ALLOWED", age, message: "" };
  }

  if (policy === "REQUIRED" && (belowMinimum || aboveMaximum)) {
    return { decision: "BLOCKED", age, message: "Độ tuổi của bạn nằm ngoài khoảng tuổi bắt buộc của khóa học." };
  }
  if (policy === "RECOMMENDED" && belowMinimum) {
    return { decision: "ALLOWED_WITH_WARNING", age, message: "Độ tuổi của bạn thấp hơn độ tuổi khuyến nghị. Bạn vẫn có thể đăng ký." };
  }
  return { decision: "ALLOWED", age, message: "" };
}

export function courseAgePolicyLabel(course) {
  return String(course?.agePolicy || "RECOMMENDED").toUpperCase() === "REQUIRED"
    ? "Độ tuổi bắt buộc"
    : "Độ tuổi khuyến nghị";
}
