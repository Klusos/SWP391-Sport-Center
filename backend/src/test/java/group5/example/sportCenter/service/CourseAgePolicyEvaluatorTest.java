package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import group5.example.sportCenter.entity.CourseAgePolicy;
import group5.example.sportCenter.service.CourseAgePolicyEvaluator.Decision;

class CourseAgePolicyEvaluatorTest {

    private final CourseAgePolicyEvaluator evaluator = new CourseAgePolicyEvaluator();
    private final LocalDate businessDate = LocalDate.of(2026, 11, 5);

    @Test
    void appliesRecommendedAndRequiredPolicyMatrix() {
        assertDecision(Decision.ALLOWED, CourseAgePolicy.RECOMMENDED, 12, 16, 25);
        assertDecision(Decision.ALLOWED, CourseAgePolicy.RECOMMENDED, 12, 16, 14);
        assertDecision(Decision.ALLOWED_WITH_WARNING, CourseAgePolicy.RECOMMENDED, 18, 35, 14);
        assertDecision(Decision.BLOCKED, CourseAgePolicy.RECOMMENDED, 12, 16, 8);
        assertDecision(Decision.ALLOWED, CourseAgePolicy.RECOMMENDED, 5, 9, 8);
        assertDecision(Decision.ALLOWED, CourseAgePolicy.REQUIRED, 5, 9, 8);
        assertDecision(Decision.BLOCKED, CourseAgePolicy.REQUIRED, 5, 9, 25);
        assertDecision(Decision.ALLOWED_WITH_WARNING, CourseAgePolicy.RECOMMENDED, 12, 16, 10);
        assertDecision(Decision.BLOCKED, CourseAgePolicy.REQUIRED, 12, 16, 10);
    }

    @Test
    void requiredPolicyNeedsBothBoundsAndRecommendedBoundsMayBeAbsentForAdults() {
        var requiredWithoutMinimum = evaluator.evaluate(CourseAgePolicy.REQUIRED, null, 16,
                dob(14), businessDate);
        var requiredWithoutMaximum = evaluator.evaluate(CourseAgePolicy.REQUIRED, 12, null,
                dob(14), businessDate);
        assertEquals(Decision.BLOCKED, requiredWithoutMinimum.decision());
        assertEquals(Decision.BLOCKED, requiredWithoutMaximum.decision());
        assertNotNull(requiredWithoutMinimum.message());

        assertDecision(Decision.ALLOWED, CourseAgePolicy.RECOMMENDED, null, null, 25);
        assertDecision(Decision.ALLOWED, CourseAgePolicy.RECOMMENDED, null, 16, 25);
        assertDecision(Decision.BLOCKED, CourseAgePolicy.RECOMMENDED, null, null, 8);
    }

    @Test
    void includesBothConfiguredBoundariesAndRejectsInvalidRanges() {
        assertDecision(Decision.ALLOWED, CourseAgePolicy.REQUIRED, 12, 16, 12);
        assertDecision(Decision.ALLOWED, CourseAgePolicy.REQUIRED, 12, 16, 16);
        assertDecision(Decision.BLOCKED, CourseAgePolicy.REQUIRED, 17, 16, 16);
        assertDecision(Decision.BLOCKED, CourseAgePolicy.RECOMMENDED, -1, 16, 14);
    }

    @Test
    void missingAndFutureBirthDatesFailClosed() {
        assertEquals(Decision.BLOCKED,
                evaluator.evaluate(CourseAgePolicy.RECOMMENDED, null, null, null, businessDate).decision());
        assertEquals(Decision.BLOCKED,
                evaluator.evaluate(CourseAgePolicy.REQUIRED, 5, 9, dob(8), null).decision());
        assertEquals(Decision.BLOCKED,
                evaluator.evaluate(CourseAgePolicy.RECOMMENDED, null, null,
                        businessDate.plusDays(1), businessDate).decision());
    }

    private void assertDecision(Decision expected, CourseAgePolicy policy, Integer minimum, Integer maximum, int age) {
        assertEquals(expected, evaluator.evaluate(policy, minimum, maximum, dob(age), businessDate).decision());
    }

    private LocalDate dob(int age) {
        return businessDate.minusYears(age);
    }
}
