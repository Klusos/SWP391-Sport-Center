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
@Table(name = "individual_training_plan_exercise", uniqueConstraints = @UniqueConstraint(
        name = "uq_individual_training_exercise_week_order", columnNames = { "week_id", "sort_order" }))
@Getter
@Setter
@NoArgsConstructor
public class IndividualTrainingPlanExercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_id", nullable = false)
    private IndividualTrainingPlanWeek week;

    @Nationalized
    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "sets")
    private Integer sets;
    @Column(name = "reps")
    private Integer reps;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Nationalized
    @Column(length = 500)
    private String notes;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
