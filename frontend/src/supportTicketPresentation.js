export function supportTicketDateTime(value) {
  if (!value) return "—";
  const text = String(value);
  const zoned = /(?:Z|[+-]\d{2}:\d{2})$/i.test(text) ? text : `${text}+07:00`;
  return new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh", dateStyle: "short", timeStyle: "short",
  }).format(new Date(zoned));
}
