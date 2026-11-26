package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import group5.example.sportCenter.dto.UpdateMembershipPlanRequest;
import group5.example.sportCenter.entity.MembershipPlan;
import group5.example.sportCenter.repository.MembershipPlanRepository;

@ExtendWith(MockitoExtension.class)
class MembershipPlanAuditTest {
    @Mock MembershipPlanRepository plans;
    @Mock AuditLogService auditLogs;

    @Test
    void updateAuditCapturesChangedEntitlementAndPriceValues() {
        MembershipPlan plan = new MembershipPlan();
        plan.setId(82);
        plan.setName("Standard");
        plan.setPrice(new BigDecimal("500000.00"));
        plan.setDurationMonths(1);
        plan.setCourseDiscountPercent(new BigDecimal("10.00"));
        plan.setPersonalTrainingDiscountPercent(new BigDecimal("5.00"));
        plan.setAllowPersonalTrainingPlan(false);
        plan.setAllowOneOnOneCoaching(false);
        plan.setStatus("ACTIVE");
        when(plans.findById(82)).thenReturn(Optional.of(plan));
        when(plans.save(plan)).thenReturn(plan);

        UpdateMembershipPlanRequest request = new UpdateMembershipPlanRequest();
        request.setName("Premium");
        request.setPrice(new BigDecimal("900000.00"));
        request.setDurationMonths(3);
        request.setCourseDiscountPercent(new BigDecimal("20.00"));
        request.setPersonalTrainingDiscountPercent(new BigDecimal("12.00"));
        request.setAllowPersonalTrainingPlan(true);
        request.setAllowOneOnOneCoaching(true);
        request.setStatus("INACTIVE");

        new MembershipPlanService(plans, auditLogs).updatePlan(82, request);

        var description = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(auditLogs).record(eq("UPDATE"), eq("MEMBERSHIP_PLAN"), eq(82), description.capture());
        String value = description.getValue();
        assertTrue(value.contains("name=Standard->Premium"));
        assertTrue(value.contains("price=500000.00->900000.00"));
        assertTrue(value.contains("durationMonths=1->3"));
        assertTrue(value.contains("courseDiscountPercent=10.00->20.00"));
        assertTrue(value.contains("ptDiscountPercent=5.00->12.00"));
        assertTrue(value.contains("allowPtPlan=false->true"));
        assertTrue(value.contains("allowOneOnOne=false->true"));
        assertTrue(value.contains("status=ACTIVE->INACTIVE"));
    }
}
