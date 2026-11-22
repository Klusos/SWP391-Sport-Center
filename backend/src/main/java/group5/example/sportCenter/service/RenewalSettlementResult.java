package group5.example.sportCenter.service;

import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.Payment;

public record RenewalSettlementResult(
        RenewalSettlementOutcome outcome,
        Payment payment,
        Membership successor,
        String detail) {
    static RenewalSettlementResult of(RenewalSettlementOutcome outcome, Payment payment, String detail) {
        return new RenewalSettlementResult(outcome, payment, null, detail);
    }
}
