package group5.example.sportCenter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "Facility")
@Getter
@Setter
@NoArgsConstructor
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // Retained as a compatibility mirror for the existing required sport_id column.
    @JoinColumn(name = "sportId", nullable = false)
    private Sport sport;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "facility_sport",
            joinColumns = @JoinColumn(name = "facility_id"),
            inverseJoinColumns = @JoinColumn(name = "sport_id"))
    private Set<Sport> sports = new LinkedHashSet<>();

    @Nationalized
    @Column(nullable = false, length = 100)
    private String name;

    @Nationalized
    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 30)
    private String status;

    @Nationalized
    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
