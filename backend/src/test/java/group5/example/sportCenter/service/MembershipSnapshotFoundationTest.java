package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipPlan;

class MembershipSnapshotFoundationTest {

    @Test
    void legacyNullSnapshotUsesExplicitUnverifiedCurrentPlanFallback() {
        MembershipPlan plan = plan();
        Membership membership = new Membership();
        membership.setPlan(plan);

        MembershipBenefitValues values = MembershipBenefitValues.resolve(membership);

        assertEquals(MembershipBenefitValues.Source.LEGACY_PLAN_FALLBACK, values.source());
        assertFalse(values.isVerifiedHistoricalSnapshot());
        assertEquals(new BigDecimal("10.00"), values.courseDiscountPercent());
        assertEquals(new BigDecimal("5.00"), values.personalTrainingDiscountPercent());
        assertTrue(values.allowPersonalTrainingPlan());
        assertFalse(values.allowOneOnOneCoaching());
        assertTrue(membership.verifiedPaidValueBasis().isEmpty());
    }

    @Test
    void explicitUnknownBenefitAndPaidValueSnapshotsRemainUnverified() {
        Membership membership = new Membership();
        membership.setPlan(plan());
        membership.setBenefitSnapshotStatus("UNKNOWN");
        membership.setBenefitSnapshotSource("LEGACY_UNKNOWN");
        membership.setPaidValueBasisStatus("UNKNOWN");
        membership.setPaidValueBasisSource("LEGACY_UNKNOWN");

        MembershipBenefitValues values = MembershipBenefitValues.resolve(membership);

        assertEquals(MembershipBenefitValues.Source.LEGACY_PLAN_FALLBACK, values.source());
        assertFalse(values.isVerifiedHistoricalSnapshot());
        assertTrue(membership.verifiedPaidValueBasis().isEmpty());
    }

    @Test
    void partialSnapshotIsRejectedInsteadOfSilentlyFallingBack() {
        Membership membership = new Membership();
        membership.setPlan(plan());
        membership.setBenefitSnapshotStatus("COMPLETE");
        membership.setBenefitSnapshotSource("PURCHASE");
        membership.setCourseDiscountPercentSnapshot(new BigDecimal("20.00"));

        assertThrows(IllegalStateException.class, () -> MembershipBenefitValues.resolve(membership));
    }

    @Test
    void onlyPositiveVerifiedPaidValueCanBeExposedAsUpgradeEvidence() {
        Membership membership = new Membership();
        membership.setPaidValueBasisStatus("UNKNOWN");
        membership.setPaidValueBasisSource("LEGACY_UNKNOWN");
        membership.setPaidValueBasisAmount(null);
        assertTrue(membership.verifiedPaidValueBasis().isEmpty());

        membership.setPaidValueBasisStatus("VERIFIED");
        membership.setPaidValueBasisSource("PURCHASE_PAYMENT");
        membership.setPaidValueBasisAmount(new BigDecimal("100000.00"));
        assertEquals(new BigDecimal("100000.00"), membership.verifiedPaidValueBasis().orElseThrow());

        membership.setPaidValueBasisAmount(BigDecimal.ZERO);
        assertTrue(membership.verifiedPaidValueBasis().isEmpty());
    }

    private MembershipPlan plan() {
        MembershipPlan plan = new MembershipPlan();
        plan.setCourseDiscountPercent(new BigDecimal("10.00"));
        plan.setPersonalTrainingDiscountPercent(new BigDecimal("5.00"));
        plan.setAllowPersonalTrainingPlan(true);
        plan.setAllowOneOnOneCoaching(false);
        return plan;
    }
}
