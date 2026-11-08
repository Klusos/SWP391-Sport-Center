export function filterCourses(courses, { search = "", sportId = "", status = "" } = {}) {
  const query = search.trim().toLocaleLowerCase("vi-VN");
  return (Array.isArray(courses) ? courses : []).filter((course) => {
    const matchesName = !query || String(course.name || "").toLocaleLowerCase("vi-VN").includes(query);
    const matchesSport = !sportId || String(course.sportId) === String(sportId);
    const matchesStatus = !status || String(course.status).toUpperCase() === status.toUpperCase();
    return matchesName && matchesSport && matchesStatus;
  });
}

export function buildCoursePayload(form, editing = false) {
  const sportId = Number(form.sportId);
  if (!Number.isInteger(sportId) || sportId <= 0) {
    throw new Error("Vui lòng chọn Sport cho khóa học.");
  }
  const agePolicy = String(form.agePolicy || "RECOMMENDED").toUpperCase();
  if (!["RECOMMENDED", "REQUIRED"].includes(agePolicy)) throw new Error("Chính sách độ tuổi không hợp lệ.");
  const targetAgeMin = form.targetAgeMin === "" || form.targetAgeMin == null ? null : Number(form.targetAgeMin);
  const targetAgeMax = form.targetAgeMax === "" || form.targetAgeMax == null ? null : Number(form.targetAgeMax);
  if ([targetAgeMin, targetAgeMax].some((age) => age != null && (!Number.isInteger(age) || age < 0))) {
    throw new Error("Độ tuổi phải là số nguyên không âm.");
  }
  if (targetAgeMin != null && targetAgeMax != null && targetAgeMin > targetAgeMax) {
    throw new Error("Tuổi tối thiểu không được lớn hơn tuổi tối đa.");
  }
  if (agePolicy === "REQUIRED" && (targetAgeMin == null || targetAgeMax == null)) {
    throw new Error("Độ tuổi bắt buộc cần có cả tuổi tối thiểu và tối đa.");
  }
  const payload = {
    name: String(form.name || "").trim(),
    description: form.description || "",
    targetAgeMin,
    targetAgeMax,
    agePolicy,
    level: form.level,
    learningOutcome: form.learningOutcome || "",
  };
  if (!editing) payload.sportId = sportId;
  if (editing) payload.status = form.status;
  return payload;
}
