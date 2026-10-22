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

    /** Legacy catalog amount retained for pre-9.2A pending-payment settlement compatibility. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Legacy fixed duration retained until all old-contract pending payments are gone. */
    @Column(nullable = false)
    private Integer durationMonths;

    @Column(name = "monthly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyPrice;

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

    /** Safe compatibility for known one-month legacy plans; multi-month plans need reviewed mapping. */
    public BigDecimal getEffectiveMonthlyPrice() {
        if (monthlyPrice != null) return monthlyPrice;
        return Integer.valueOf(1).equals(durationMonths) ? price : null;
    }
}
