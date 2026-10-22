package group5.example.sportCenter.service;

public record MembershipUpgradeSettlementResult(Outcome outcome, Integer paymentId,
        Integer membershipId, String reason) {
    public enum Outcome { APPLIED, ALREADY_APPLIED, RECONCILIATION_REQUIRED, NOT_APPLICABLE }
}
