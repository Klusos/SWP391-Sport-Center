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

@Entity
@Table(name = "individual_training_plan_revision", uniqueConstraints = @UniqueConstraint(
        name = "uq_individual_training_revision_number", columnNames = { "plan_id", "revision_number" }))
@Getter
@Setter
@NoArgsConstructor
public class IndividualTrainingPlanRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private IndividualTrainingPlan plan;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Nationalized
    @Column(nullable = false, length = 500)
    private String goal;

    @Nationalized
    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revised_by", nullable = false)
    private User revisedBy;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(7)")
    private LocalDateTime createdAt;
}
