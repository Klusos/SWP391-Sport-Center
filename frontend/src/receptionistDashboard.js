const rows = (value) => Array.isArray(value) ? value : value?.content ?? [];

function countValue(value) {
  if (value == null) return null;
  if (Array.isArray(value)) return value.length;
  if (Array.isArray(value?.content)) return Number.isFinite(value.totalElements) ? value.totalElements : null;
  return null;
}

export function receptionistDashboardMetrics({ members, sports, plans, pendingRequests, inProgressRequests }) {
  const activePlans = plans == null ? null : rows(plans).filter((plan) => plan?.status === "ACTIVE").length;
  return {
    members: countValue(members),
    sports: countValue(sports),
    activePlans,
    pendingConsultations: countValue(pendingRequests),
    inProgressConsultations: countValue(inProgressRequests),
  };
}

export function searchReceptionistMembers(members, query) {
  const term = String(query ?? "").trim().toLocaleLowerCase("vi-VN");
  if (!term) return [];
  return rows(members).filter((member) =>
    [member?.id, member?.name, member?.phone, member?.email]
      .some((value) => String(value ?? "").toLocaleLowerCase("vi-VN").includes(term))
  );
}

export function pendingConsultationRows(page) {
  return rows(page)
    .filter((request) => request?.status === "PENDING")
    .slice()
    .sort((a, b) => new Date(a.createdAt || 0) - new Date(b.createdAt || 0));
}

export function activeMembershipPlanRows(plans) {
  return rows(plans).filter((plan) => plan?.status === "ACTIVE");
}
