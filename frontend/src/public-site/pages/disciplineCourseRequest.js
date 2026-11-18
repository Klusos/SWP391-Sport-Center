const INVALID_SPORT_ID_MESSAGE = "Mã bộ môn không hợp lệ.";
const COURSE_REQUEST_ERROR_MESSAGE = "Không thể tải danh sách khóa học.";

export function isValidPublicSportId(sportId) {
  return typeof sportId === "string" && /^[1-9]\d*$/.test(sportId);
}

export function getCourseStateForSport(sportId, currentState) {
  if (currentState.sportId === sportId) return currentState;
  return isValidPublicSportId(sportId)
    ? { sportId, status: "loading", courses: [], error: "" }
    : { sportId, status: "error", courses: [], error: INVALID_SPORT_ID_MESSAGE };
}

/** Load courses for one route ID and ignore all callbacks after that request is invalidated. */
export function requestCoursesForSport(sportId, fetchCourses, onState) {
  let active = true;
  const publish = (state) => {
    if (active) onState({ sportId, ...state });
  };

  if (!isValidPublicSportId(sportId)) {
    publish({ status: "error", courses: [], error: INVALID_SPORT_ID_MESSAGE });
    return () => { active = false; };
  }

  publish({ status: "loading", courses: [], error: "" });
  Promise.resolve()
    .then(() => fetchCourses(sportId))
    .then((rows) => publish({
      status: Array.isArray(rows) && rows.length > 0 ? "success" : "empty",
      courses: Array.isArray(rows) ? rows : [],
      error: "",
    }))
    .catch((error) => publish({
      status: "error",
      courses: [],
      error: error?.message || COURSE_REQUEST_ERROR_MESSAGE,
    }));

  return () => { active = false; };
}
