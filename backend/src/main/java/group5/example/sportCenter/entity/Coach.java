package group5.example.sportCenter.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Coach")
@Getter
@Setter
@NoArgsConstructor
public class Coach {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userId", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 30)
    private String status;

    @Nationalized
    @Column(length = 1000)
    private String bio;

    @Nationalized
    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
