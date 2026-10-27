package group5.example.sportCenter.dto;

import jakarta.validation.constraints.Size;

/** reason bắt buộc khi ADMIN huỷ gói đang chạy; không bắt buộc khi huỷ gói chưa bắt đầu. */
public record CancelMembershipRequest(@Size(max = 500, message = "Reason must be at most 500 characters") String reason) {
}
