package com.mcledger.services.events;

import com.mcledger.dtos.EventType;
import com.mcledger.models.EntryType;
import com.mcledger.models.RejectionReason;
import com.mcledger.money.Money;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.LedgerEntryRepositoryInMemory;
import com.mcledger.repositories.RejectedEventRepository;
import com.mcledger.repositories.RejectedEventRepositoryInMemory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.mcledger.dtos.EventRequest.eventRequest;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.services.events.HandlerFixtures.ACC;
import static com.mcledger.services.events.HandlerFixtures.day;
import static com.mcledger.services.events.HandlerFixtures.posted;
import static com.mcledger.services.events.HandlerFixtures.rejected;
import static com.mcledger.services.events.HandlerFixtures.request;
import static com.mcledger.services.events.HandlerFixtures.reversal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReversalEventHandlerTest {

    private final LedgerEntryRepository ledgerEntryRepository = new LedgerEntryRepositoryInMemory();
    private final RejectedEventRepository rejectedEventRepository = new RejectedEventRepositoryInMemory();
    private final PostEntryEventHandler debit = new PostEntryEventHandler(ledgerEntryRepository, EntryType.DEBIT, true);
    private final ReversalEventHandler handler = new ReversalEventHandler(ledgerEntryRepository, rejectedEventRepository);

    @Test
    void reversalNegatesTheOriginalAndKeepsItsValueDate() {
        final var e7 = posted(debit.handle(request("E7", EventType.DEBIT, "620.00", 5, 2)));

        final var e9 = posted(handler.handle(reversal("E9", e7.id(), 6)));

        assertEquals(EntryType.REVERSAL, e9.type());
        assertEquals(Money.of(AED, "620.00"), e9.amount());
        assertEquals(day(2), e9.valueDate());
        assertEquals(day(6), e9.bookedDay());
        assertEquals(e7.id(), e9.reversesEntryId().orElseThrow());
    }

    @Test
    void reversedEntryIdIsTheIdempotencyKey() {
        final var e7 = posted(debit.handle(request("E7", EventType.DEBIT, "620.00", 5, 2)));

        final var e9 = posted(handler.handle(reversal("E9", e7.id(), 6)));

        assertEquals(e7.id().toString(), e9.requestId());
    }

    @Test
    void anyFurtherReversalOfTheSameEntryReturnsTheFirstOne() {
        final var e7 = posted(debit.handle(request("E7", EventType.DEBIT, "620.00", 5, 2)));
        final var e9 = posted(handler.handle(reversal("E9", e7.id(), 6)));

        assertSame(e9, posted(handler.handle(reversal("E9", e7.id(), 6))));
        assertSame(e9, posted(handler.handle(reversal("E9b", e7.id(), 6))));
        assertEquals(2, ledgerEntryRepository.findByAccountId(ACC).size());
    }

    @Test
    void unknownEntryIsRejected() {
        rejected(handler.handle(reversal("R1", UUID.randomUUID(), 6)), RejectionReason.ENTRY_NOT_FOUND);
        assertEquals(1, rejectedEventRepository.findAll().size());
    }

    @Test
    void entryOfAnotherAccountIsRejected() {
        final var other = posted(debit.handle(eventRequest().requestId("B1").eventType(EventType.DEBIT)
                .accountId(UUID.randomUUID()).amount(Money.of(AED, "1.00")).bookedDay(day(1)).valueDate(day(1)).build()));

        rejected(handler.handle(reversal("R1", other.id(), 6)), RejectionReason.ENTRY_NOT_FOUND);
    }
}
