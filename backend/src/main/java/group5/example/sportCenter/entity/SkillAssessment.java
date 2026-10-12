package group5.example.sportCenter.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "SkillAssessment")
@Getter @Setter @NoArgsConstructor
public class SkillAssessment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "memberId", nullable = false)
    private User member;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sportId", nullable = false)
    private Sport sport;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignedCoachId", nullable = false)
    private Coach assignedCoach;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requestedByUserId", nullable = false)
    private User requestedByUser;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AssessmentStatus status;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private SportLevel assessedLevel;
    @Nationalized
    @Column(length = 1000)
    private String notes;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime assessedAt;
    private LocalDateTime cancelledAt;
    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}
