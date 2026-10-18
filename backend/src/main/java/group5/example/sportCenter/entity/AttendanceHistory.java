package group5.example.sportCenter.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "AttendanceHistory")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendanceId", nullable = false)
    private Attendance attendance;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AttendanceStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttendanceStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changedBy", nullable = false)
    private User changedBy;

    @Column(nullable = false, columnDefinition = "datetime2(7)")
    private LocalDateTime changedAt;

    @Nationalized
    @Column(length = 500)
    private String reason;
}
