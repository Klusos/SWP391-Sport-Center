package group5.example.sportCenter.entity;

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
@Table(name = "individual_training_plan_week", uniqueConstraints = @UniqueConstraint(
        name = "uq_individual_training_week_number", columnNames = { "revision_id", "week_number" }))
@Getter
@Setter
@NoArgsConstructor
public class IndividualTrainingPlanWeek {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private IndividualTrainingPlanRevision revision;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Nationalized
    @Column(length = 150)
    private String title;

    @Nationalized
    @Column(length = 1000)
    private String description;
}
