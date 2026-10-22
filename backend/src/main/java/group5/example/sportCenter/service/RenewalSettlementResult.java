package group5.example.sportCenter.service;

/** Result of applying or reconciling a renewal capture. */
public record RenewalSettlementResult(
        Outcome outcome,
        Integer paymentId,
        Integer attemptId,
        Integer membershipId,
        String reason) {
    public enum Outcome { APPLIED, ALREADY_APPLIED, RECONCILIATION_REQUIRED }
}
