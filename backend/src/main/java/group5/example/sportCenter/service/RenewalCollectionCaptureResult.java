package group5.example.sportCenter.service;

/** Result of committing the first collection facts independently from Membership settlement. */
public record RenewalCollectionCaptureResult(Status status, RenewalSettlementEvidence evidence, String detail) {
    public enum Status { RECORDED, IDEMPOTENT, CONFLICT, NOT_COLLECTED, INVALID }

    static RenewalCollectionCaptureResult of(Status status, RenewalSettlementEvidence evidence, String detail) {
        return new RenewalCollectionCaptureResult(status, evidence, detail);
    }
}
