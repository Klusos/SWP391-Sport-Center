import { useMemo, useState } from "react";
import {
  confirmClassSessionGenerationApi,
  previewClassSessionGenerationApi,
} from "../api";
import "./ClassSessionGenerationPanel.css";

const WEEKDAYS = [
  ["MONDAY", "Thứ Hai"], ["TUESDAY", "Thứ Ba"], ["WEDNESDAY", "Thứ Tư"],
  ["THURSDAY", "Thứ Năm"], ["FRIDAY", "Thứ Sáu"], ["SATURDAY", "Thứ Bảy"], ["SUNDAY", "Chủ nhật"],
];
const rows = (items) => Array.isArray(items) ? items : items?.content ?? [];

export default function ClassSessionGenerationPanel({ token, courseClass, offering, facilities, sessions, onGenerated }) {
  const occupied = useMemo(() => rows(sessions).filter((session) => String(session?.status).toUpperCase() !== "CANCELLED").length, [sessions]);
  const planned = Number(offering?.plannedSessions) || 0;
  const remaining = Math.max(0, planned - occupied);
  const [weekdays, setWeekdays] = useState([]);
  const [startTime, setStartTime] = useState("");
  const [facilityId, setFacilityId] = useState("");
  const [firstDate, setFirstDate] = useState(offering?.startDate || "");
  const [count, setCount] = useState(String(remaining));
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const activeFacilities = rows(facilities).filter((facility) => String(facility?.status).toUpperCase() === "ACTIVE");
  const selectedFacility = activeFacilities.find((facility) => String(facility.id) === facilityId);

  function payload() {
    if (weekdays.length === 0) throw new Error("Chọn ít nhất một ngày trong tuần.");
    if (!startTime) throw new Error("Nhập giờ bắt đầu mỗi ngày.");
    if (!firstDate) throw new Error("Chọn ngày bắt đầu áp dụng.");
    if (firstDate < offering.startDate || firstDate > offering.endDate) throw new Error("Ngày bắt đầu phải nằm trong thời hạn khóa học.");
    const requestedSessionCount = Number(count);
    if (!Number.isInteger(requestedSessionCount) || requestedSessionCount <= 0) throw new Error("Số buổi cần tạo phải là số nguyên dương.");
    if (requestedSessionCount > remaining) throw new Error(`Chỉ còn ${remaining} buổi trong chỉ tiêu của lớp.`);
    return {
      courseClassId: Number(courseClass.id),
      weekdays,
      startTime,
      facilityId: facilityId ? Number(facilityId) : null,
      firstEligibleDate: firstDate,
      requestedSessionCount,
    };
  }

  async function requestPreview(event) {
    event.preventDefault();
    setError(""); setNotice(""); setPreview(null);
    let request;
    try { request = payload(); }
    catch (validationError) { setError(validationError.message); return; }
    setBusy(true);
    try { setPreview(await previewClassSessionGenerationApi(token, request)); }
    catch (requestError) { setError(requestError.message || "Không thể xem trước lịch."); }
    finally { setBusy(false); }
  }

  async function confirmGeneration() {
    if (!preview?.canGenerate || busy) return;
    let request;
    try { request = payload(); }
    catch (validationError) { setError(validationError.message); return; }
    if (!window.confirm(`Tạo ${preview.requestedSessionCount} buổi cho lớp ${courseClass.name}? Lịch sẽ được kiểm tra lại trước khi lưu.`)) return;
    setBusy(true); setError(""); setNotice("");
    try {
      const result = await confirmClassSessionGenerationApi(token, request);
      setNotice(`Đã tạo thành công ${result.createdCount} buổi học.`);
      setPreview(null);
      setCount(String(Math.max(0, remaining - result.createdCount)));
      await onGenerated();
    } catch (requestError) { setError(requestError.message || "Không thể tạo lịch; dữ liệu đã được kiểm tra lại trên máy chủ."); }
    finally { setBusy(false); }
  }

  function toggleWeekday(day) {
    setWeekdays((current) => current.includes(day) ? current.filter((value) => value !== day) : [...current, day]);
    setPreview(null);
  }

  return <section className="session-generation-card">
    <div className="session-generation-heading">
      <div><p className="pt-eyebrow">LẬP LỊCH THEO TUẦN</p><h2>Tạo lịch tự động</h2></div>
      <span>{courseClass.name || `Lớp #${courseClass.id}`}</span>
    </div>
    <div className="session-generation-facts">
      <span>Đợt học: <b>{offering.name}</b> · {offering.startDate} – {offering.endDate}</span>
      <span>Thời lượng: <b>{offering.sessionDuration} phút/buổi</b></span>
      <span>Chỉ tiêu: <b>{planned}</b> · Đã có: <b>{occupied}</b> · Còn: <b>{remaining}</b></span>
    </div>
    {remaining === 0 && <p className="session-generation-warning" role="note">Lớp đã đủ hoặc vượt số buổi dự kiến. Buổi CANCELLED không tính vào chỉ tiêu.</p>}
    {error && <p className="session-generation-error" role="alert">{error}</p>}
    {notice && <p className="session-generation-success" role="status">{notice}</p>}
    <form onSubmit={requestPreview}>
      <fieldset className="session-generation-weekdays"><legend>Ngày học</legend>
        {WEEKDAYS.map(([value, label]) => <label key={value}>
          <input type="checkbox" checked={weekdays.includes(value)} onChange={() => toggleWeekday(value)} /> {label}
        </label>)}
      </fieldset>
      <div className="session-generation-fields">
        <label>Giờ bắt đầu<input type="time" value={startTime} onChange={(event) => { setStartTime(event.target.value); setPreview(null); }} required /></label>
        <label>Ngày áp dụng đầu tiên<input type="date" value={firstDate} min={offering.startDate} max={offering.endDate} onChange={(event) => { setFirstDate(event.target.value); setPreview(null); }} required /></label>
        <label>Số buổi cần tạo<input type="number" min="1" max={remaining || undefined} step="1" value={count} onChange={(event) => { setCount(event.target.value); setPreview(null); }} disabled={remaining === 0} required /></label>
        <label>Cơ sở (không bắt buộc)<select value={facilityId} onChange={(event) => { setFacilityId(event.target.value); setPreview(null); }}>
          <option value="">Chưa chọn cơ sở</option>{activeFacilities.map((facility) => <option value={facility.id} key={facility.id}>{facility.name}</option>)}
        </select></label>
      </div>
      <p className="session-generation-hint">Giờ kết thúc được tính theo thời lượng của đợt học. Ngày kết thúc của đợt được tính trọn ngày. Các buổi hiện có giữ nguyên; lịch thủ công và lịch tự động cùng dùng chỉ tiêu này.</p>
      <button type="submit" disabled={busy || remaining === 0}>{busy ? "Đang xử lý…" : "Xem trước lịch"}</button>
    </form>
    {preview && <div className="session-generation-preview">
      <h3>Xem trước · {preview.candidates?.length || 0}/{preview.requestedSessionCount} buổi</h3>
      <p>HLV: {preview.coachName || `#${preview.coachId}`} · Cơ sở: {selectedFacility?.name || preview.facilityName || "Chưa chọn"}</p>
      {preview.conflicts?.length > 0 && <ul className="session-generation-conflicts" role="alert">{preview.conflicts.map((item, index) => <li key={`${index}-${item}`}>{item}</li>)}</ul>}
      {!preview.candidates?.length ? <p>Không có ngày phù hợp trong thời hạn đợt học.</p> : <div className="session-generation-table-wrap"><table>
        <thead><tr><th>Bắt đầu</th><th>Kết thúc</th><th>Kiểm tra</th></tr></thead>
        <tbody>{preview.candidates.map((candidate, index) => <tr key={`${candidate.startTime}-${index}`}>
          <td>{candidate.startTime?.replace("T", " ") || "—"}</td><td>{candidate.endTime?.replace("T", " ") || "—"}</td>
          <td>{candidate.conflicts?.length ? candidate.conflicts.join("; ") : "Hợp lệ"}</td>
        </tr>)}</tbody>
      </table></div>}
      <button type="button" onClick={() => void confirmGeneration()} disabled={busy || !preview.canGenerate}>
        {busy ? "Đang lưu…" : "Xác nhận tạo lịch"}
      </button>
      {!preview.canGenerate && <small>Hãy xử lý các xung đột trước khi xác nhận.</small>}
    </div>}
  </section>;
}
