const idDescending = (a, b) => Number(b?.id || 0) - Number(a?.id || 0);

export function sortMembershipHistory(items) {
  return [...(items || [])].sort((a, b) =>
    String(b?.startDate || "").localeCompare(String(a?.startDate || "")) || idDescending(a, b));
}

export function sortPaymentHistory(items) {
  return [...(items || [])].sort((a, b) =>
    String(b?.createdAt || "").localeCompare(String(a?.createdAt || "")) || idDescending(a, b));
}

export function filterPaymentHistory(items, { status = "", purpose = "" } = {}) {
  return sortPaymentHistory(items).filter((payment) =>
    (!status || payment?.status === status) && (!purpose || payment?.purpose === purpose));
}

export function paginateItems(items, requestedPage, pageSize) {
  const pageCount = Math.max(1, Math.ceil((items?.length || 0) / pageSize));
  const page = Math.min(Math.max(0, Number(requestedPage) || 0), pageCount - 1);
  return { page, pageCount, items: (items || []).slice(page * pageSize, (page + 1) * pageSize) };
}
