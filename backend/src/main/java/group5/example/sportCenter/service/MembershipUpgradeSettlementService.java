package group5.example.sportCenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import group5.example.sportCenter.payment.VerifiedUpgradeCapture;
import group5.example.sportCenter.entity.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Internal Upgrade settlement boundary. Online capture types can only be constructed inside
 * the gateway package after provider signature and merchant verification. */
@Service
public class MembershipUpgradeSettlementService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final MembershipUpgradeSettlementTransactions transactions;
    private final Clock clock;

    public MembershipUpgradeSettlementService(MembershipUpgradeSettlementTransactions transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional
    public MembershipUpgradeSettlementResult settleVerifiedOnlineCapture(VerifiedUpgradeCapture capture) {
        Instant receivedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String key = evidenceKey(capture);
        MembershipUpgradeSettlementResult recorded;
        try {
            recorded = transactions.recordCapture(capture, key, receivedAt);
        } catch (DataIntegrityViolationException integrity) {
            recorded = transactions.recordProviderCollision(capture, key, receivedAt);
        }
        if (recorded.outcome() != MembershipUpgradeSettlementResult.Outcome.APPLIED) return recorded;
        try {
            return transactions.applyCapture(recorded.paymentId(), key,
                    LocalDateTime.ofInstant(receivedAt, BUSINESS_ZONE), receivedAt);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            return transactions.requireReconciliation(recorded.paymentId(), key,
                    "A database relationship or uniqueness constraint prevented Upgrade application");
        }
    }

    @Transactional
    public MembershipUpgradeSettlementResult recordVerifiedOnlineFailure(group5.example.sportCenter.payment.VerifiedUpgradeFailure failure) {
        Instant receivedAt=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String canonical=failure.attemptReference()+"|"+failure.providerTransactionId()+"|"+failure.responseCode()+"/"+failure.transactionStatus()+"|"+failure.amount();
        String key=sha256(canonical);
        return transactions.recordFailure(failure,key,receivedAt);
    }

    /** Counter receipts are trusted only after PaymentService authenticates active staff and checks the request. */
    @Transactional
    public MembershipUpgradeSettlementResult settleCounterReceipt(Integer paymentId, User staff, String note) {
        if(staff==null || staff.getId()==null || !("ADMIN".equalsIgnoreCase(staff.getRole())
                || "RECEPTIONIST".equalsIgnoreCase(staff.getRole())) || !"ACTIVE".equalsIgnoreCase(staff.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Active ADMIN or RECEPTIONIST authorization required");
        Instant receivedAt=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        CounterReceiptRecordingResult recorded=transactions.recordCounterReceipt(
                new VerifiedUpgradeCounterReceipt(paymentId,staff,note,receivedAt));
        if(recorded.outcome()==MembershipUpgradeSettlementResult.Outcome.ALREADY_APPLIED)
            return transactions.alreadyAppliedCounterReceipt(paymentId);
        if(recorded.outcome()!=MembershipUpgradeSettlementResult.Outcome.APPLIED)
            return new MembershipUpgradeSettlementResult(recorded.outcome(),paymentId,null,recorded.reason());
        try {
            return transactions.applyCounterReceipt(paymentId,recorded.receiptId(),
                    LocalDateTime.ofInstant(receivedAt,BUSINESS_ZONE),receivedAt);
        } catch(org.springframework.dao.DataIntegrityViolationException integrity) {
            return transactions.requireCounterReconciliation(paymentId,"A database constraint prevented counter Upgrade application");
        }
    }

    private static String evidenceKey(VerifiedUpgradeCapture capture) {
        String amount=capture.amount()==null?"null":capture.amount().toPlainString();
        return sha256(capture.transactionCode()+"|"+capture.providerTransactionId()+"|"+capture.responseCode()+"|"+amount+"|"+capture.capturedAt());
    }

    private static String sha256(String canonical) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable",exception); }
    }
}
