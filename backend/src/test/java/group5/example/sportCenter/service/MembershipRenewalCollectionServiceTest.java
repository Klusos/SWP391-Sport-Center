package group5.example.sportCenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import group5.example.sportCenter.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class MembershipRenewalCollectionServiceTest {
    @Mock MembershipRenewalCollectionPersistenceService persistence;
    @Mock MembershipRenewalSettlementService settlement;
    @Mock NotificationService notifications;
    @Mock PaymentRepository payments;
    @InjectMocks MembershipRenewalCollectionService service;

    private RenewalSettlementEvidence onlineEvidence;

    @BeforeEach
    void setUp() {
        onlineEvidence = RenewalSettlementEvidence.verifiedOnlineCollection(
                41, 73, new java.math.BigDecimal("850000.00"), "VND", "99123456",
                java.time.LocalDateTime.of(2026, 11, 21, 10, 0),
                "VNPAY_SIGNATURE_VERIFIED", "99123456");
    }

    @Test
    void intendedProviderReferenceUniqueViolationUsesConflictMarkerRecovery() {
        SQLIntegrityConstraintViolationException sql = new SQLIntegrityConstraintViolationException(
                "Violation of UNIQUE KEY constraint '" + MembershipRenewalCollectionService.PROVIDER_REFERENCE_INDEX + "'.",
                "23000", 2601);
        RuntimeException commitFailure = new TransactionSystemException("Commit failed",
                new DataIntegrityViolationException("Unique index collision", sql));
        when(persistence.recordFirstCollection(onlineEvidence)).thenThrow(commitFailure);
        when(payments.findById(73)).thenReturn(Optional.empty());

        RenewalSettlementResult result = service.recordAndSettle(onlineEvidence);

        assertEquals(RenewalSettlementOutcome.RECONCILIATION_REQUIRED, result.outcome());
        verify(persistence).markConcurrentProviderReferenceConflict(73);
        verify(settlement, never()).settle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unrelatedCheckForeignKeyOrUniqueViolationPropagatesWithoutConflictMarker() {
        SQLIntegrityConstraintViolationException sql = new SQLIntegrityConstraintViolationException(
                "The statement conflicted with the CHECK constraint 'ck_payment_other'.", "23000", 547);
        RuntimeException failure = new DataIntegrityViolationException("Unrelated integrity error", sql);
        when(persistence.recordFirstCollection(onlineEvidence)).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.recordAndSettle(onlineEvidence));

        assertSame(failure, thrown);
        verify(persistence, never()).markConcurrentProviderReferenceConflict(73);
    }

    @Test
    void unrelatedForeignKeyViolationPropagatesWithoutConflictMarker() {
        SQLIntegrityConstraintViolationException sql = new SQLIntegrityConstraintViolationException(
                "The INSERT statement conflicted with a FOREIGN KEY constraint 'fk_payment_member'.", "23000", 547);
        RuntimeException failure = new DataIntegrityViolationException("Foreign key violation", sql);
        when(persistence.recordFirstCollection(onlineEvidence)).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.recordAndSettle(onlineEvidence));

        assertSame(failure, thrown);
        verify(persistence, never()).markConcurrentProviderReferenceConflict(73);
    }

    @Test
    void differentUniqueIndexViolationPropagatesWithoutConflictMarker() {
        SQLIntegrityConstraintViolationException sql = new SQLIntegrityConstraintViolationException(
                "Duplicate key in ux_payment_transaction_code", "23000", 2601);
        RuntimeException failure = new DataIntegrityViolationException("Other unique index violation", sql);
        when(persistence.recordFirstCollection(onlineEvidence)).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.recordAndSettle(onlineEvidence));

        assertSame(failure, thrown);
        verify(persistence, never()).markConcurrentProviderReferenceConflict(73);
    }

    @Test
    void unknownIntegrityExceptionPropagatesWithoutConflictMarker() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("Unclassified violation");
        when(persistence.recordFirstCollection(onlineEvidence)).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.recordAndSettle(onlineEvidence));

        assertSame(failure, thrown);
        verify(persistence, never()).markConcurrentProviderReferenceConflict(73);
    }

    @Test
    void onlySqlServerDuplicateCodesWithTheExactIndexNameAreClassified() {
        SQLIntegrityConstraintViolationException intended = new SQLIntegrityConstraintViolationException(
                "Duplicate key in " + MembershipRenewalCollectionService.PROVIDER_REFERENCE_INDEX, "23000", 2627);
        SQLIntegrityConstraintViolationException wrongIndex = new SQLIntegrityConstraintViolationException(
                "Duplicate key in ux_payment_transaction_code", "23000", 2627);

        assertTrue(MembershipRenewalCollectionService.isProviderReferenceUniqueViolation(
                new TransactionSystemException("commit", new RuntimeException(intended))));
        assertFalse(MembershipRenewalCollectionService.isProviderReferenceUniqueViolation(wrongIndex));
        assertFalse(MembershipRenewalCollectionService.isProviderReferenceUniqueViolation(null));
    }
}
