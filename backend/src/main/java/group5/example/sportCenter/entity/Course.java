package group5.example.sportCenter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer targetAgeMin;

    private Integer targetAgeMax;

    @Column(length = 50)
    private String level;

    @Column(columnDefinition = "TEXT")
    private String learningOutcome;

    @Column(nullable = false, length = 50)
    private String status;
}