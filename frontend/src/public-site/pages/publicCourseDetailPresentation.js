export function formatPublicCourseAgeRange(course) {
  const hasMinimum = course?.targetAgeMin != null;
  const hasMaximum = course?.targetAgeMax != null;

  if (hasMinimum && hasMaximum) return `${course.targetAgeMin}–${course.targetAgeMax} tuổi`;
  if (hasMinimum) return `Từ ${course.targetAgeMin} tuổi`;
  if (hasMaximum) return `Đến ${course.targetAgeMax} tuổi`;
  return "";
}
