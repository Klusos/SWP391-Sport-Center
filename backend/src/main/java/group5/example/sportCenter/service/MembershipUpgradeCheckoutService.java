package group5.example.sportCenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import group5.example.sportCenter.dto.MembershipUpgradeCheckoutResponse;
import group5.example.sportCenter.entity.CheckoutAttemptStatus;
import group5.example.sportCenter.entity.CheckoutProvider;
import group5.example.sportCenter.entity.Membership;
import group5.example.sportCenter.entity.MembershipUpgradeCheckoutAttempt;
import group5.example.sportCenter.entity.MembershipUpgradePaymentEvidence;
import group5.example.sportCenter.entity.Payment;
import group5.example.sportCenter.entity.PaymentMethod;
import group5.example.sportCenter.entity.PaymentPurpose;
import group5.example.sportCenter.entity.PaymentStatus;
import group5.example.sportCenter.entity.User;
import group5.example.sportCenter.exception.ResourceNotFoundException;
import group5.example.sportCenter.payment.PaymentGateway;
import group5.example.sportCenter.repository.MembershipPlanUpgradeRepository;
import group5.example.sportCenter.repository.MembershipRepository;
import group5.example.sportCenter.repository.MembershipUpgradeCheckoutAttemptRepository;
import group5.example.sportCenter.repository.MembershipUpgradePaymentEvidenceRepository;
import group5.example.sportCenter.repository.PaymentRepository;
import group5.example.sportCenter.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/** Issues only member-owned URLs for already-created Upgrade drafts. */
@Service
public class MembershipUpgradeCheckoutService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final PaymentRepository payments;
    private final MembershipUpgradePaymentEvidenceRepository evidenceRepository;
    private final MembershipPlanUpgradeRepository paths;
    private final MembershipUpgradeCheckoutAttemptRepository attempts;
    private final PaymentGateway gateway;
    private final Clock clock;
    @PersistenceContext private EntityManager entityManager;

    public MembershipUpgradeCheckoutService(UserRepository users, MembershipRepository memberships,
            PaymentRepository payments, MembershipUpgradePaymentEvidenceRepository evidenceRepository,
            MembershipPlanUpgradeRepository paths, MembershipUpgradeCheckoutAttemptRepository attempts,
            PaymentGateway gateway, Clock clock) {
        this.users=users; this.memberships=memberships; this.payments=payments;
        this.evidenceRepository=evidenceRepository; this.paths=paths; this.attempts=attempts;
        this.gateway=gateway; this.clock=clock;
    }

    @Transactional
    public MembershipUpgradeCheckoutResponse issue(Integer paymentId, Jwt jwt, String clientIp) {
        if (!gateway.supportsUpgradeCheckout()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "VNPay Upgrade checkout is not configured");
        final Integer memberId;
        try { memberId=Integer.valueOf(jwt.getSubject()); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid member identity"); }
        User member=users.findByIdForBookingUpdate(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member account was not found"));
        if (!"MEMBER".equalsIgnoreCase(member.getRole()) || !"ACTIVE".equalsIgnoreCase(member.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an active MEMBER can start Upgrade checkout");
        Payment snapshot=payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        MembershipUpgradePaymentEvidence snapEvidence=evidenceRepository.findByPayment_Id(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade evidence is missing"));
        Membership source=memberships.findByIdForUpgradeUpdate(snapEvidence.getSourceMembership().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade source Membership is missing"));
        Payment payment=payments.findByIdForUpdate(paymentId).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        entityManager.refresh(source, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        MembershipUpgradePaymentEvidence evidence=evidenceRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade evidence is missing"));
        entityManager.refresh(evidence, LockModeType.PESSIMISTIC_WRITE);
        List<MembershipUpgradeCheckoutAttempt> prior=attempts.findByPaymentIdForUpdate(paymentId);

        if (!payment.getMember().getId().equals(memberId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You can only pay for your own Upgrade");
        if (payment.getPurpose()!=PaymentPurpose.UPGRADE || payment.getMethod()!=PaymentMethod.ONLINE)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment is not an ONLINE Upgrade draft");
        if (payment.getStatus()!=PaymentStatus.PENDING) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Upgrade Payment is no longer pending");
        if(prior.stream().anyMatch(a -> a.getStatus()==CheckoutAttemptStatus.RECONCILIATION_REQUIRED
                || a.getStatus()==CheckoutAttemptStatus.CAPTURED))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"An earlier Upgrade attempt requires reconciliation");
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (!now.isBefore(evidence.getExpiresAtUtc())) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Upgrade Payment has expired");
        if(Duration.between(now,evidence.getExpiresAtUtc()).compareTo(Duration.ofMinutes(2))<0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Upgrade checkout window is under the two-minute minimum");
        if (payment.getAmount()==null || evidence.getPayableAmount()==null
                || evidence.getSourcePaidAmount()==null || evidence.getTargetMonthlyPrice()==null
                || evidence.getRemainingCredit()==null || evidence.getSourceStartDate()==null
                || evidence.getSourceEndDate()==null || evidence.getIssuanceBusinessDate()==null
                || evidence.getOriginalDurationDays()==null || evidence.getRemainingDays()==null
                || payment.getAmount().compareTo(evidence.getPayableAmount())!=0
                || payment.getReferenceId()==null || !payment.getReferenceId().equals(evidence.getTargetPlan().getId())
                || evidence.getTargetPlan().getEffectiveMonthlyPrice()==null
                || evidence.getTargetMonthlyPrice().compareTo(evidence.getTargetPlan().getEffectiveMonthlyPrice())!=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upgrade draft evidence is inconsistent");

        LocalDate today=LocalDate.ofInstant(now, BUSINESS_ZONE);
        if (!"ACTIVE".equalsIgnoreCase(source.getStatus()) || source.getStartDate()==null || source.getEndDate()==null
                || source.getStartDate().isAfter(today) || !source.getEndDate().isAfter(today)
                || !source.getMember().getId().equals(memberId)
                || source.getSourcePayment()==null || source.getSourcePayment().getStatus()!=PaymentStatus.SUCCESS
                || source.getSourcePayment().getPurpose()!=PaymentPurpose.MEMBERSHIP
                || source.getSourcePayment().getMember()==null
                || !source.getSourcePayment().getMember().getId().equals(memberId)
                || !Objects.equals(source.getSourcePayment().getReferenceId(),source.getPlan().getId())
                || source.getPaidAmount()==null || source.getPaidAmount().compareTo(evidence.getSourcePaidAmount())!=0
                || source.getSourcePayment().getAmount()==null
                || source.getSourcePayment().getAmount().compareTo(evidence.getSourcePaidAmount())!=0
                || !source.getStartDate().equals(evidence.getSourceStartDate())
                || !source.getEndDate().equals(evidence.getSourceEndDate()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Source Membership is no longer eligible");
        long originalDays=ChronoUnit.DAYS.between(evidence.getSourceStartDate(),evidence.getSourceEndDate());
        long remainingDays=Math.max(0,Math.min(originalDays,
                ChronoUnit.DAYS.between(evidence.getIssuanceBusinessDate(),evidence.getSourceEndDate())));
        if(originalDays<=0 || originalDays!=evidence.getOriginalDurationDays()
                || remainingDays!=evidence.getRemainingDays())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Upgrade period evidence is inconsistent");
        List<Membership> current=memberships.findEffectiveMembershipsForUpgrade(memberId, today);
        if (current.size()!=1 || !current.get(0).getId().equals(source.getId())
                || !memberships.findFutureActiveMembershipsForUpgrade(memberId, today).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership entitlement changed; Upgrade checkout is blocked");
        BigDecimal sourcePrice=source.getPlan().getEffectiveMonthlyPrice();
        BigDecimal targetPrice=evidence.getTargetPlan().getEffectiveMonthlyPrice();
        if (!"ACTIVE".equalsIgnoreCase(source.getPlan().getStatus())
                || !"ACTIVE".equalsIgnoreCase(evidence.getTargetPlan().getStatus())
                || sourcePrice==null || targetPrice==null
                || evidence.getTargetPlan().getEffectiveMonthlyPrice()==null
                || targetPrice.compareTo(sourcePrice)<=0
                || targetPrice.compareTo(evidence.getTargetMonthlyPrice())!=0
                || !paths.existsByFromPlan_IdAndToPlan_Id(source.getPlan().getId(), evidence.getTargetPlan().getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Configured Upgrade path or plan eligibility changed");
        BigDecimal expectedCredit=evidence.getSourcePaidAmount().multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(originalDays),2,RoundingMode.HALF_UP);
        if(expectedCredit.compareTo(evidence.getRemainingCredit())!=0
                || targetPrice.subtract(expectedCredit).compareTo(evidence.getPayableAmount())!=0
                || evidence.getPayableAmount().signum()<=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Immutable Upgrade price or credit evidence is inconsistent");
        if (memberships.existsTimelineOverlap(memberId,source.getId(),today,today.plusMonths(1)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Another Membership overlaps the proposed Upgrade period");
        List<Payment> unresolved=payments.findByMember_IdAndPurposeAndStatusIn(memberId, PaymentPurpose.MEMBERSHIP,
                List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED));
        if (!unresolved.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A conflicting Membership Purchase or Renewal is unresolved");
        List<Payment> otherUpgrades=payments.findByMember_IdAndPurposeAndStatusIn(memberId, PaymentPurpose.UPGRADE,
                List.of(PaymentStatus.PENDING, PaymentStatus.REFUND_REQUIRED));
        if (otherUpgrades.stream().anyMatch(p -> !p.getId().equals(paymentId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another Upgrade Payment is unresolved");

        MembershipUpgradeCheckoutAttempt live=prior.stream().filter(a -> a.getStatus()==CheckoutAttemptStatus.URL_ISSUED
                && now.isBefore(a.getExpiresAtUtc())).findFirst().orElse(null);
        if (live!=null) return response(live);
        for (MembershipUpgradeCheckoutAttempt old: prior) if (old.getStatus()==CheckoutAttemptStatus.URL_ISSUED
                && !now.isBefore(old.getExpiresAtUtc())) old.setStatus(CheckoutAttemptStatus.FAILED);
        attempts.flush(); // release the filtered one-issued-attempt index before replacement insert.
        LocalDateTime createdLocal=LocalDateTime.ofInstant(now, BUSINESS_ZONE).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        LocalDateTime expiresLocal=LocalDateTime.ofInstant(evidence.getExpiresAtUtc(), BUSINESS_ZONE).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        if (!expiresLocal.isAfter(createdLocal)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Upgrade checkout window is too short to issue safely");
        MembershipUpgradeCheckoutAttempt attempt=new MembershipUpgradeCheckoutAttempt();
        attempt.setPayment(payment); attempt.setProvider(CheckoutProvider.VNPAY);
        attempt.setTransactionReference("UGP"+UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        attempt.setAmount(payment.getAmount()); attempt.setCreatedAtUtc(now); attempt.setExpiresAtUtc(evidence.getExpiresAtUtc());
        attempt.setStatus(CheckoutAttemptStatus.CREATED);
        attempt.setCheckoutUrl(gateway.createUpgradePaymentUrl(attempt.getTransactionReference(), payment.getAmount(),
                clientIp, createdLocal, expiresLocal));
        attempt.setStatus(CheckoutAttemptStatus.URL_ISSUED);
        return response(attempts.saveAndFlush(attempt));
    }

    private static MembershipUpgradeCheckoutResponse response(MembershipUpgradeCheckoutAttempt attempt) {
        return new MembershipUpgradeCheckoutResponse(attempt.getPayment().getId(), attempt.getId(),
                attempt.getTransactionReference(), attempt.getAmount(), attempt.getExpiresAtUtc(), attempt.getCheckoutUrl());
    }
}
