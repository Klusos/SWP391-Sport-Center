package group5.example.sportCenter.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "MembershipPlan")
@Getter
@Setter
@NoArgsConstructor
public class MembershipPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String name;

    @Nationalized
    @Column(columnDefinition = "nvarchar(max)")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer durationMonths;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal courseDiscountPercent;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal personalTrainingDiscountPercent;

    @Column(nullable = false)
    private Boolean allowPersonalTrainingPlan;

    @Column(nullable = false)
    private Boolean allowOneOnOneCoaching;

    @Column(nullable = false, length = 30)
    private String status;
}
