package group5.example.sportCenter.dto;

import java.time.LocalDateTime;

public record PtBookingOutcomeResponse(Integer id, String status, LocalDateTime endTime) { }
