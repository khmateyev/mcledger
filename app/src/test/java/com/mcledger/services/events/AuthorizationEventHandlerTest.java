package com.mcledger.services.events;

import com.mcledger.dtos.EventType;
import com.mcledger.models.AuthEventType;
import com.mcledger.models.EntryType;
import com.mcledger.repositories.AuthEventRepository;
import com.mcledger.repositories.AuthEventRepositoryInMemory;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.LedgerEntryRepositoryInMemory;
import com.mcledger.repositories.RejectedEventRepositoryInMemory;
import org.junit.jupiter.api.Test;

import static com.mcledger.services.events.HandlerFixtures.ACC;
import static com.mcledger.services.events.HandlerFixtures.authorized;
import static com.mcledger.services.events.HandlerFixtures.request;
import static com.mcledger.services.events.HandlerFixtures.settlement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationEventHandlerTest {

    private final LedgerEntryRepository ledgerEntryRepository = new LedgerEntryRepositoryInMemory();
    private final AuthEventRepository authEventRepository = new AuthEventRepositoryInMemory();
    private final PostEntryEventHandler credit = new PostEntryEventHandler(ledgerEntryRepository, EntryType.CREDIT, false);
    private final PostEntryEventHandler debit = new PostEntryEventHandler(ledgerEntryRepository, EntryType.DEBIT, true);
    private final SettlementEventHandler settle = new SettlementEventHandler(
            ledgerEntryRepository, authEventRepository, new RejectedEventRepositoryInMemory());
    private final AuthorizationEventHandler handler = new AuthorizationEventHandler(ledgerEntryRepository, authEventRepository);

    private AuthEventType authorize(String id, String amount) {
        return authorized(handler.handle(request(id, EventType.AUTHORIZATION, amount, 2, 2))).type();
    }

    @Test
    void approvedWhenAvailableStaysAtOrAboveZero() {
        credit.handle(request("E1", EventType.CREDIT, "250.00", 1, 1));

        assertEquals(AuthEventType.APPROVED, authorize("A1", "250.00"));
    }

    @Test
    void declinedWhenAvailableWouldGoNegative() {
        credit.handle(request("E1", EventType.CREDIT, "250.00", 1, 1));

        assertEquals(AuthEventType.DECLINED, authorize("A1", "250.01"));
    }

    @Test
    void declinedOnAnEmptyAccount() {
        assertEquals(AuthEventType.DECLINED, authorize("A1", "0.01"));
    }

    @Test
    void approvedHoldsReduceAvailableButDeclinedOnesDoNot() {
        credit.handle(request("E1", EventType.CREDIT, "250.00", 1, 1));

        assertEquals(AuthEventType.APPROVED, authorize("A1", "200.00"));
        assertEquals(AuthEventType.DECLINED, authorize("A2", "100.00"));
        assertEquals(AuthEventType.APPROVED, authorize("A3", "50.00"));
    }

    @Test
    void settledHoldNoLongerCounts() {
        credit.handle(request("E1", EventType.CREDIT, "1200.00", 1, 1));
        debit.handle(request("E2", EventType.DEBIT, "950.00", 1, 1));
        authorize("E3", "200.00");
        settle.handle(settlement("E5", authEventRepository.findByRequestId("E3").orElseThrow().id(), "185.00", 4));

        // ledger 65.00, no active hold
        assertEquals(AuthEventType.APPROVED, authorize("A2", "65.00"));
    }

    @Test
    void backdatedDebitsCountTowardsTheBalance() {
        credit.handle(request("E1", EventType.CREDIT, "1200.00", 1, 1));
        debit.handle(request("E7", EventType.DEBIT, "1200.00", 5, 1));

        assertEquals(AuthEventType.DECLINED, authorize("E8", "90.00"));
    }

    @Test
    void holdDoesNotTouchTheLedger() {
        credit.handle(request("E1", EventType.CREDIT, "250.00", 1, 1));
        authorize("A1", "200.00");

        assertEquals(1, ledgerEntryRepository.findByAccountId(ACC).size());
    }

    @Test
    void sameRequestReturnsTheStoredDecision() {
        credit.handle(request("E1", EventType.CREDIT, "250.00", 1, 1));
        final var first = authorized(handler.handle(request("A1", EventType.AUTHORIZATION, "200.00", 2, 2)));

        assertSame(first, authorized(handler.handle(request("A1", EventType.AUTHORIZATION, "200.00", 2, 2))));
        assertEquals(1, authEventRepository.findByAccountId(ACC).size());
        assertTrue(authEventRepository.findByRequestId("A1").isPresent());
    }
}
