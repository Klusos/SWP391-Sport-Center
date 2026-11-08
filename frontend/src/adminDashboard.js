const ADMIN_DASHBOARD_SPORT_PREVIEW_COUNT = 6;

export function visibleAdminDashboardSports(sports, expanded = false) {
  const rows = Array.isArray(sports) ? sports : [];
  return expanded ? rows : rows.slice(0, ADMIN_DASHBOARD_SPORT_PREVIEW_COUNT);
}
