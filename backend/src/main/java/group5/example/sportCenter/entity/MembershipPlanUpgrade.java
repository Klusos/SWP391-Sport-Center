package group5.example.sportCenter.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ForeignKey;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "membership_plan_upgrade", uniqueConstraints = @UniqueConstraint(
        name = "uk_membership_plan_upgrade_direction", columnNames = {"from_plan_id", "to_plan_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MembershipPlanUpgrade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_plan_upgrade_from"))
    private MembershipPlan fromPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_plan_upgrade_to"))
    private MembershipPlan toPlan;
}
