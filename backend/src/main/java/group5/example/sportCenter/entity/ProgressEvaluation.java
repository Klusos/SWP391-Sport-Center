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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Flow 4: append-only progress evaluation of a member in a CourseClass; the latest row is current. */
@Entity
@Table(name = "ProgressEvaluation")
@Getter
@Setter
@NoArgsConstructor
public class ProgressEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classId", nullable = false)
    private CourseClass courseClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "memberId", nullable = false)
    private User member;

    /** 1 (well below goal) to 5 (goal exceeded). */
    @Column(nullable = false)
    private Integer rating;

    @Nationalized
    @Column(nullable = false, length = 1000)
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluatedBy", nullable = false)
    private User evaluatedBy;

    @Column(nullable = false, columnDefinition = "datetime2(7)")
    private LocalDateTime evaluatedAt;
}
