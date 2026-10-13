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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ConsultationRequest")
@Getter
@Setter
@NoArgsConstructor
public class ConsultationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memberId")
    private User member;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String name;

    @Nationalized
    @Column(nullable = false, length = 20)
    private String phone;

    @Nationalized
    @Column(length = 150)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sportId", nullable = false)
    private Sport sport;

    private Integer age;

    @Nationalized
    @Column(columnDefinition = "nvarchar(max)")
    private String experience;

    @Nationalized
    @Column(columnDefinition = "nvarchar(max)")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsultationStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handledBy")
    private User handledBy;

    private LocalDateTime handledAt;

    @Nationalized
    @Column(length = 1000)
    private String resolutionNote;

    private LocalDateTime completedAt;

    private LocalDateTime cancelledAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ConsultationStatus.PENDING;
        }
    }
}
