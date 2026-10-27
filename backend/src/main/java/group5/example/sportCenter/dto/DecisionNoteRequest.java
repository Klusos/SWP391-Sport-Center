package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Size;

/** Ghi chú của nhân viên khi duyệt/từ chối yêu cầu huỷ gói hoặc xác nhận hoàn tiền. */
public record DecisionNoteRequest(@Size(max = 500, message = "Note must be at most 500 characters") String note) {
}
