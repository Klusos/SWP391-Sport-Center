export function availableUpgradeTargets(source, plans, paths) {
  if (!source || source.status?.toUpperCase() !== "ACTIVE") return [];
  const sourcePrice = Number(source.monthlyPrice);
  if (!Number.isFinite(sourcePrice)) return [];
  return (plans || []).filter((target) => target.id !== source.id
    && target.status?.toUpperCase() === "ACTIVE"
    && Number.isFinite(Number(target.monthlyPrice))
    && Number(target.monthlyPrice) > sourcePrice
    && !(paths || []).some((path) => Number(path.fromPlanId) === Number(source.id)
      && Number(path.toPlanId) === Number(target.id)));
}

export function upgradePathRequestError(status) {
  return ({
    400: "Hai gói phải đang hoạt động, khác nhau và gói đích phải có giá tháng cao hơn.",
    401: "Phiên đăng nhập đã hết hạn.",
    403: "Chỉ ADMIN được quản lý hướng nâng cấp.",
    404: "Không tìm thấy một trong hai gói hoặc hướng nâng cấp.",
    409: "Hướng nâng cấp đã tồn tại hoặc xung đột với cấu hình hiện tại.",
  })[status] || null;
}
