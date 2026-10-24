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
  const payload = {
    name: String(form.name || "").trim(),
    description: form.description || "",
    targetAgeMin: form.targetAgeMin === "" ? null : Number(form.targetAgeMin),
    targetAgeMax: form.targetAgeMax === "" ? null : Number(form.targetAgeMax),
    level: form.level,
    learningOutcome: form.learningOutcome || "",
  };
  if (!editing) payload.sportId = sportId;
  if (editing) payload.status = form.status;
  return payload;
}
