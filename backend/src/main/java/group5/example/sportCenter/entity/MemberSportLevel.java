package group5.example.sportCenter.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "MemberSportLevel", uniqueConstraints = @UniqueConstraint(
        name = "uk_member_sport_level", columnNames = {"memberId", "sportId"}))
@Getter @Setter @NoArgsConstructor
public class MemberSportLevel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "memberId", nullable = false)
    private User member;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sportId", nullable = false)
    private Sport sport;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private SportLevel level;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifiedByCoachId")
    private Coach verifiedByCoach;
    private LocalDateTime verifiedAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }
}
