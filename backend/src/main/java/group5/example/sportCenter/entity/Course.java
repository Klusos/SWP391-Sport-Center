package group5.example.sportCenter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Course")
@Getter
@Setter
@NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sportId", nullable = false)
    private Sport sport;

    @Nationalized
    @Column(nullable = false, length = 255)
    private String name;

    @Nationalized
    @Column(columnDefinition = "nvarchar(max)")
    private String description;

    private Integer targetAgeMin;

    private Integer targetAgeMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_policy", nullable = false, length = 20, columnDefinition = "varchar(20)")
    private CourseAgePolicy agePolicy = CourseAgePolicy.RECOMMENDED;

    @Column(length = 50)
    private String level;

    @Nationalized
    @Column(columnDefinition = "nvarchar(max)")
    private String learningOutcome;

    @Column(nullable = false, length = 50)
    private String status;
}
