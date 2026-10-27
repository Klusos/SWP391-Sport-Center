package group5.example.sportCenter.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Nationalized;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Flow 4: a Coach's recorded result for one PRESENT member in one ClassSession. */
@Entity
@Table(name = "TrainingResult", uniqueConstraints = @UniqueConstraint(
        name = "uq_training_result_session_member", columnNames = { "classSessionId", "memberId" }))
@Getter
@Setter
@NoArgsConstructor
public class TrainingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classSessionId", nullable = false)
    private ClassSession classSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "memberId", nullable = false)
    private User member;

    /** 1 (needs much work) to 10 (excellent). */
    @Column(nullable = false)
    private Integer score;

    @Nationalized
    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recordedBy", nullable = false)
    private User recordedBy;

    @Column(nullable = false, columnDefinition = "datetime2(7)")
    private LocalDateTime recordedAt;
}
