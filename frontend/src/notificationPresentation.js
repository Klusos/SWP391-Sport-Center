export function notificationPresentation(notification) {
  const title = String(notification?.title || "");
  const message = String(notification?.message || "");
  const pair = (title + "\n" + message).trim();
  let match;
  if ((match = pair.match(/^New consultation request\s*\nA new consultation request #(\d+) is waiting for review\.?$/i))) {
    return { title: "Yêu cầu tư vấn mới", message: `Yêu cầu tư vấn #${match[1]} đang chờ tiếp nhận.` };
  }
  if ((match = pair.match(/^Consultation completed\s*\nYour consultation request #(\d+) has been completed\.?$/i))) {
    return { title: "Đã hoàn tất tư vấn", message: `Yêu cầu tư vấn #${match[1]} đã được hoàn tất.` };
  }
  if ((match = pair.match(/^Consultation cancelled\s*\nYour consultation request #(\d+) has been cancelled\.?$/i))) {
    return { title: "Đã hủy yêu cầu tư vấn", message: `Yêu cầu tư vấn #${match[1]} đã được hủy.` };
  }
  if ((match = pair.match(/^PT booking confirmed\s*\nYour personal training booking #(\d+) has been confirmed\.?$/i))) {
    return { title: "Lịch huấn luyện cá nhân đã xác nhận", message: `Lịch huấn luyện cá nhân #${match[1]} đã được xác nhận.` };
  }
  if ((match = pair.match(/^New PT session assigned\s*\nPersonal training booking #(\d+) has been confirmed and assigned to you\.?$/i))) {
    return { title: "Lịch huấn luyện cá nhân mới", message: `Lịch #${match[1]} đã được xác nhận và phân công cho bạn.` };
  }
  if ((match = pair.match(/^Class assignment\s*\nYou have been assigned to class #(\d+)\.?$/i))) {
    return { title: "Phân công lớp học", message: `Bạn được phân công phụ trách lớp #${match[1]}.` };
  }
  return { title: title || "Thông báo", message };
}
