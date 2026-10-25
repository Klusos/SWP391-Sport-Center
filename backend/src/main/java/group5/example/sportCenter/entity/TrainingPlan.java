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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Flow 4: the assigned Coach's training plan (goal + exercises) for one CourseClass. */
@Entity
@Table(name = "TrainingPlan")
@Getter
@Setter
@NoArgsConstructor
public class TrainingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classId", nullable = false, unique = true)
    private CourseClass courseClass;

    @Nationalized
    @Column(nullable = false, length = 500)
    private String goal;

    @Nationalized
    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updatedBy", nullable = false)
    private User updatedBy;

    @Column(nullable = false, columnDefinition = "datetime2(7)")
    private LocalDateTime createdAt;

    @Column(nullable = false, columnDefinition = "datetime2(7)")
    private LocalDateTime updatedAt;
}
