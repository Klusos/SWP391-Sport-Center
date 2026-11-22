package group5.example.sportCenter.service;

import java.math.BigDecimal;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;

/**
 * Benefit values for entitlement readers. Legacy fallback is deliberately tagged and
 * must not be treated as verified historical terms or paid-value evidence.
 */
public record MembershipBenefitValues(
        String planName,
        String planDescription,
        BigDecimal planPrice,
        Integer durationMonths,
        BigDecimal courseDiscountPercent,
        BigDecimal personalTrainingDiscountPercent,
        boolean allowPersonalTrainingPlan,
        boolean allowOneOnOneCoaching,
        Source source) {

    public enum Source {
        VERIFIED_SNAPSHOT,
        LEGACY_PLAN_FALLBACK
    }

    public boolean isVerifiedHistoricalSnapshot() {
        return source == Source.VERIFIED_SNAPSHOT;
    }

    public static MembershipBenefitValues resolve(Membership membership) {
        if (membership == null) throw new IllegalArgumentException("Membership is required");
        if (membership.hasCompleteVerifiedBenefitSnapshot()) {
            return new MembershipBenefitValues(
                    membership.getPlanNameSnapshot(),
                    membership.getPlanDescriptionSnapshot(),
                    membership.getPlanPriceSnapshot(),
                    membership.getPlanDurationMonthsSnapshot(),
                    membership.getCourseDiscountPercentSnapshot(),
                    membership.getPtDiscountPercentSnapshot(),
                    membership.getAllowPtPlanSnapshot(),
                    membership.getAllowOneOnOneSnapshot(),
                    Source.VERIFIED_SNAPSHOT);
        }

        boolean allSnapshotFieldsAbsent = membership.getBenefitSnapshotStatus() == null
                && membership.getBenefitSnapshotSource() == null
                && membership.getPlanNameSnapshot() == null
                && membership.getPlanDescriptionSnapshot() == null
                && membership.getPlanPriceSnapshot() == null
                && membership.getPlanDurationMonthsSnapshot() == null
                && membership.getCourseDiscountPercentSnapshot() == null
                && membership.getPtDiscountPercentSnapshot() == null
                && membership.getAllowPtPlanSnapshot() == null
                && membership.getAllowOneOnOneSnapshot() == null;
        boolean explicitlyUnknown = "UNKNOWN".equalsIgnoreCase(membership.getBenefitSnapshotStatus())
                && "LEGACY_UNKNOWN".equalsIgnoreCase(membership.getBenefitSnapshotSource())
                && membership.getPlanNameSnapshot() == null
                && membership.getPlanDescriptionSnapshot() == null
                && membership.getPlanPriceSnapshot() == null
                && membership.getPlanDurationMonthsSnapshot() == null
                && membership.getCourseDiscountPercentSnapshot() == null
                && membership.getPtDiscountPercentSnapshot() == null
                && membership.getAllowPtPlanSnapshot() == null
                && membership.getAllowOneOnOneSnapshot() == null;
        if (!allSnapshotFieldsAbsent && !explicitlyUnknown) {
            throw new IllegalStateException("Membership has an incomplete or inconsistent benefit snapshot");
        }

        MembershipPlan currentPlan = membership.getPlan();
        if (currentPlan == null) throw new IllegalStateException("Legacy Membership Plan is unavailable");
        return new MembershipBenefitValues(
                currentPlan.getName(),
                currentPlan.getDescription(),
                currentPlan.getPrice(),
                currentPlan.getDurationMonths(),
                currentPlan.getCourseDiscountPercent(),
                currentPlan.getPersonalTrainingDiscountPercent(),
                Boolean.TRUE.equals(currentPlan.getAllowPersonalTrainingPlan()),
                Boolean.TRUE.equals(currentPlan.getAllowOneOnOneCoaching()),
                Source.LEGACY_PLAN_FALLBACK);
    }
}
