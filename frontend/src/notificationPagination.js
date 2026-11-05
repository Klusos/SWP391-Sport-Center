export function shouldShowNotificationPagination(page) {
  return Number(page?.totalPages) > 1;
}
