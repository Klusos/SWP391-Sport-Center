package group5.example.sportCenter.service;

import java.time.LocalDate;
import java.time.Period;

import org.springframework.stereotype.Component;

import group5.example.sportCenter.entity.CourseAgePolicy;

@Component
public class CourseAgePolicyEvaluator {

    public enum Decision {
        ALLOWED,
        ALLOWED_WITH_WARNING,
        BLOCKED
    }

    public record Result(Decision decision, Integer age, String message) {
        public boolean allowed() {
            return decision != Decision.BLOCKED;
        }
    }

    private static final int CHILD_SAFETY_AGE = 10;

    public Result evaluate(CourseAgePolicy policy, Integer minimum, Integer maximum,
            LocalDate dateOfBirth, LocalDate businessDate) {
        CourseAgePolicy effectivePolicy = policy == null ? CourseAgePolicy.RECOMMENDED : policy;

        if (minimum != null && minimum < 0 || maximum != null && maximum < 0
                || minimum != null && maximum != null && minimum > maximum) {
            return blocked(null, "Course age configuration is invalid.");
        }
        if (effectivePolicy == CourseAgePolicy.REQUIRED && (minimum == null || maximum == null)) {
            return blocked(null, "This Course requires a complete minimum and maximum age range.");
        }
        if (dateOfBirth == null || businessDate == null || dateOfBirth.isAfter(businessDate)) {
            return blocked(null, "A valid Member date of birth is required to verify Course age eligibility.");
        }

        int age = Period.between(dateOfBirth, businessDate).getYears();
        if (age < 0) {
            return blocked(null, "A valid Member date of birth is required to verify Course age eligibility.");
        }

        boolean belowMinimum = minimum != null && age < minimum;
        boolean aboveMaximum = maximum != null && age > maximum;
        if (age < CHILD_SAFETY_AGE) {
            if (minimum == null || maximum == null) {
                return blocked(age, "Members under 10 must have a complete Course age range for safety verification.");
            }
            if (belowMinimum || aboveMaximum) {
                return blocked(age, "Members under 10 must be within the Course age range.");
            }
            return allowed(age);
        }

        if (effectivePolicy == CourseAgePolicy.REQUIRED && (belowMinimum || aboveMaximum)) {
            return blocked(age, "Member age is outside this Course's required age range.");
        }
        if (effectivePolicy == CourseAgePolicy.RECOMMENDED && belowMinimum) {
            return new Result(Decision.ALLOWED_WITH_WARNING, age,
                    "Member is younger than the recommended minimum age; registration is still allowed.");
        }
        return allowed(age);
    }

    private Result allowed(int age) {
        return new Result(Decision.ALLOWED, age, null);
    }

    private Result blocked(Integer age, String message) {
        return new Result(Decision.BLOCKED, age, message);
    }
}
