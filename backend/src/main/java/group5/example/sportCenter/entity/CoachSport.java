package group5.example.sportCenter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(
    name = "CoachSport",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {"coachId", "sportId"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class CoachSport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coachId", nullable = false)
    private Coach coach;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sportId", nullable = false)
    private Sport sport;

    @Column(nullable = false)
    private Integer experienceYears;

    @Nationalized
    @Column(length = 255)
    private String certification;
}
