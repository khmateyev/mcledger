package com.mcledger.services.events;

import com.mcledger.dtos.EventType;
import com.mcledger.models.AuthEventType;
import com.mcledger.models.EntryType;
import com.mcledger.models.RejectionReason;
import com.mcledger.money.Money;
import com.mcledger.repositories.AuthEventRepository;
import com.mcledger.repositories.AuthEventRepositoryInMemory;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.LedgerEntryRepositoryInMemory;
import com.mcledger.repositories.RejectedEventRepository;
import com.mcledger.repositories.RejectedEventRepositoryInMemory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.services.events.HandlerFixtures.ACC;
import static com.mcledger.services.events.HandlerFixtures.authorized;
import static com.mcledger.services.events.HandlerFixtures.day;
import static com.mcledger.services.events.HandlerFixtures.posted;
import static com.mcledger.services.events.HandlerFixtures.rejected;
import static com.mcledger.services.events.HandlerFixtures.request;
import static com.mcledger.services.events.HandlerFixtures.settlement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SettlementEventHandlerTest {

    private final LedgerEntryRepository ledgerEntryRepository = new LedgerEntryRepositoryInMemory();
    private final AuthEventRepository authEventRepository = new AuthEventRepositoryInMemory();
    private final RejectedEventRepository rejectedEventRepository = new RejectedEventRepositoryInMemory();
    private final PostEntryEventHandler credit = new PostEntryEventHandler(ledgerEntryRepository, EntryType.CREDIT, false);
    private final AuthorizationEventHandler authorize = new AuthorizationEventHandler(ledgerEntryRepository, authEventRepository, HandlerFixtures.CONFIG);
    private final SettlementEventHandler handler =
            new SettlementEventHandler(ledgerEntryRepository, authEventRepository, rejectedEventRepository);

    private UUID fundAndAuthorize(String fund, String hold) {
        credit.handle(request("F1", EventType.CREDIT, fund, 1, 1));
        return authorized(authorize.handle(request("A1", EventType.AUTHORIZATION, hold, 2, 2))).id();
    }

    @Test
    void postsADebitAndRecordsTheSettlement() {
        final var authId = fundAndAuthorize("1200.00", "200.00");

        final var entry = posted(handler.handle(settlement("E5", authId, "185.00", 4)));

        assertEquals(EntryType.DEBIT, entry.type());
        assertEquals(Money.of(AED, "-185.00"), entry.amount());
        assertEquals(day(4), entry.valueDate());
        final var settled = authEventRepository.findByRequestId(authId.toString()).orElseThrow();
        assertEquals(AuthEventType.SETTLED, settled.type());
        assertEquals(authId, settled.authId());
        assertEquals(Money.of(AED, "185.00"), settled.amount());
    }

    @Test
    void authorizationIdIsTheIdempotencyKey() {
        final var authId = fundAndAuthorize("1200.00", "200.00");

        assertEquals(authId.toString(), posted(handler.handle(settlement("E5", authId, "185.00", 4))).requestId());
    }

    @Test
    void anyFurtherSettlementOfTheSameAuthorizationReturnsTheFirstOne() {
        final var authId = fundAndAuthorize("1200.00", "200.00");
        final var first = posted(handler.handle(settlement("S1", authId, "185.00", 4)));

        assertSame(first, posted(handler.handle(settlement("S1", authId, "185.00", 4))));
        assertSame(first, posted(handler.handle(settlement("S2", authId, "50.00", 4))));
        assertEquals(2, ledgerEntryRepository.findByAccountId(ACC).size());
    }

    @Test
    void settlingExactlyTheAuthorizedAmountIsAllowed() {
        final var authId = fundAndAuthorize("1200.00", "200.00");

        posted(handler.handle(settlement("S1", authId, "200.00", 4)));
    }

    @Test
    void unknownAuthorizationIsRejectedAndNothingIsPosted() {
        credit.handle(request("E1", EventType.CREDIT, "1200.00", 1, 1));

        rejected(handler.handle(settlement("E6", UUID.randomUUID(), "180.00", 4)), RejectionReason.AUTHORIZATION_NOT_FOUND);
        assertEquals(1, ledgerEntryRepository.findByAccountId(ACC).size());
    }

    @Test
    void declinedAuthorizationIsRejected() {
        final var authId = fundAndAuthorize("10.00", "500.00");

        rejected(handler.handle(settlement("S1", authId, "5.00", 4)), RejectionReason.AUTHORIZATION_NOT_APPROVED);
    }

    @Test
    void amountAboveTheAuthorizationIsRejected() {
        final var authId = fundAndAuthorize("1200.00", "200.00");

        rejected(handler.handle(settlement("S1", authId, "200.01", 4)), RejectionReason.SETTLEMENT_ABOVE_AUTHORIZED);
        assertEquals(1, ledgerEntryRepository.findByAccountId(ACC).size());
    }
}
