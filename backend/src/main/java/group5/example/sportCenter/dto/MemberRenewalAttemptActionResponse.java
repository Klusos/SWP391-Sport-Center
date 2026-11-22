package group5.example.sportCenter.dto;

public record MemberRenewalAttemptActionResponse(
        MemberRenewalIntentResponse intent,
        MemberRenewalPaymentAttemptResponse attempt,
        String checkoutUrl) {
}
