const DATE_TIME_LOCAL_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/;

function parseDateTimeLocal(value) {
  if (typeof value !== "string") return null;
  const match = DATE_TIME_LOCAL_PATTERN.exec(value);
  if (!match) return null;
  const [, yearText, monthText, dayText, hourText, minuteText, secondText = "00", fractionText = ""] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText);
  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1];
  if (!daysInMonth || year === 0 || day < 1 || day > daysInMonth || hour > 23 || minute > 59 || second > 59) return null;
  const fraction = fractionText.padEnd(3, "0");
  return {
    date: `${yearText}-${monthText}-${dayText}`,
    value: `${yearText}-${monthText}-${dayText}T${hourText}:${minuteText}:${secondText}.${fraction}`,
  };
}

function minuteValue(value) {
  const [date, time] = value.split("T");
  const [year, month, day] = date.split("-").map(Number);
  const [hour, minute] = time.split(":").map(Number);
  // datetime-local is timezone-less wall time, matching Java LocalDateTime.
  return Date.UTC(year, month - 1, day, hour, minute) / 60000;
}

export function validateClassSessionSchedule(form, offering) {
  const start = parseDateTimeLocal(form?.startTime);
  const end = parseDateTimeLocal(form?.endTime);
  if (!start || !end) return "Vui lòng nhập ngày giờ bắt đầu và kết thúc hợp lệ.";

  const offeringStart = offering?.startDate;
  const offeringEnd = offering?.endDate;
  if (!validDate(offeringStart) || !validDate(offeringEnd) || offeringStart > offeringEnd) {
    return "Không xác định được khoảng ngày của đợt học. Vui lòng tải lại đợt học và lớp.";
  }
  if (start.date < offeringStart || start.date > offeringEnd) {
    return "Ngày giờ bắt đầu phải nằm trong khoảng ngày của đợt học.";
  }
  if (end.date < offeringStart || end.date > offeringEnd) {
    return "Ngày giờ kết thúc phải nằm trong khoảng ngày của đợt học.";
  }
  if (end.value <= start.value) return "Thời điểm kết thúc phải sau thời điểm bắt đầu.";
  if (Number.isInteger(offering?.sessionDuration)
    && minuteValue(end.value) - minuteValue(start.value) !== offering.sessionDuration) {
    return `Thời lượng buổi học phải đúng ${offering.sessionDuration} phút theo đợt học.`;
  }
  return null;
}

export function addMinutesToDateTimeLocal(value, minutes) {
  const parsed = parseDateTimeLocal(value);
  if (!parsed || !Number.isInteger(minutes) || minutes <= 0) return "";
  const date = new Date(minuteValue(parsed.value) * 60000 + minutes * 60000);
  const pad = (part) => String(part).padStart(2, "0");
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

export function validateCourseOffering(form, original, now = new Date()) {
  const plannedSessions = form?.plannedSessions === "" || form?.plannedSessions == null
    ? null : Number(form.plannedSessions);
  const sessionDuration = form?.sessionDuration === "" || form?.sessionDuration == null
    ? null : Number(form.sessionDuration);
  const plannedSessionsUnchanged = original
    && plannedSessions === (original.plannedSessions == null ? null : Number(original.plannedSessions));
  const sessionDurationUnchanged = original
    && sessionDuration === (original.sessionDuration == null ? null : Number(original.sessionDuration));
  if ((!plannedSessionsUnchanged && (!Number.isInteger(plannedSessions) || plannedSessions < 1 || plannedSessions > 60))) {
    return "Số buổi dự kiến phải là số nguyên từ 1 đến 60.";
  }
  if ((!sessionDurationUnchanged && (!Number.isInteger(sessionDuration) || sessionDuration < 30 || sessionDuration > 180))) {
    return "Thời lượng buổi học phải là số nguyên từ 30 đến 180 phút.";
  }
  const start = form?.startDate;
  const end = form?.endDate;
  const registration = form?.registrationDeadline;
  const cancellation = form?.cancellationDeadline;
  if (!validDate(start) || !validDate(end) || !parseDateTimeLocal(registration) || !parseDateTimeLocal(cancellation)) {
    return "Vui lòng nhập đầy đủ ngày đợt học và các hạn hợp lệ.";
  }
  if (start >= end) return "Ngày kết thúc đợt học phải sau ngày bắt đầu.";
  const today = `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}`;
  if ((!original || start !== original.startDate) && start < today) {
    return "Ngày bắt đầu của đợt học mới hoặc thay đổi không được ở trong quá khứ.";
  }
  const current = `${today}T${pad2(now.getHours())}:${pad2(now.getMinutes())}`;
  if ((!original || registration !== localDateTime(original.registrationDeadline)) && registration <= current) {
    return "Hạn đăng ký mới hoặc thay đổi phải ở trong tương lai.";
  }
  if ((!original || cancellation !== localDateTime(original.cancellationDeadline)) && cancellation <= current) {
    return "Hạn hủy mới hoặc thay đổi phải ở trong tương lai.";
  }
  if (registration > `${start}T00:00`) return "Hạn đăng ký không được sau ngày bắt đầu đợt học.";
  if (cancellation > `${start}T00:00`) return "Hạn hủy không được sau ngày bắt đầu đợt học.";
  if (registration > cancellation) return "Hạn đăng ký phải vào hoặc trước hạn hủy.";
  return null;
}

export function getSessionActionAvailability(session, now = new Date()) {
  const current = `${now.getFullYear()}-${pad2(now.getMonth() + 1)}-${pad2(now.getDate())}T${pad2(now.getHours())}:${pad2(now.getMinutes())}`;
  const status = String(session?.status || "").toUpperCase();
  const start = localDateTime(session?.startTime);
  const end = localDateTime(session?.endTime);
  const scheduled = status === "SCHEDULED";
  return {
    canComplete: scheduled && Boolean(end) && current >= end,
    canCancel: scheduled && Boolean(start) && current < start,
    completeReason: scheduled && end && current < end ? `Chỉ có thể hoàn thành từ ${end.replace("T", " ")}.` : "",
    cancelReason: scheduled && start && current >= start ? "Không thể hủy sau khi buổi học đã bắt đầu." : "",
  };
}

function validDate(value) {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)
    && Boolean(parseDateTimeLocal(`${value}T00:00`));
}

function localDateTime(value) {
  return value ? String(value).slice(0, 16) : "";
}

function pad2(value) {
  return String(value).padStart(2, "0");
}
