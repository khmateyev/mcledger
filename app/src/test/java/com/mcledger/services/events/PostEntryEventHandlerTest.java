package com.mcledger.services.events;

import com.mcledger.dtos.EventType;
import com.mcledger.models.EntryType;
import com.mcledger.money.Money;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.LedgerEntryRepositoryInMemory;
import org.junit.jupiter.api.Test;

import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.services.events.HandlerFixtures.ACC;
import static com.mcledger.services.events.HandlerFixtures.day;
import static com.mcledger.services.events.HandlerFixtures.posted;
import static com.mcledger.services.events.HandlerFixtures.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostEntryEventHandlerTest {

    private final LedgerEntryRepository repository = new LedgerEntryRepositoryInMemory();
    private final PostEntryEventHandler credit = new PostEntryEventHandler(repository, EntryType.CREDIT, false);
    private final PostEntryEventHandler debit = new PostEntryEventHandler(repository, EntryType.DEBIT, true);

    @Test
    void creditIsStoredPositive() {
        final var entry = posted(credit.handle(request("E1", EventType.CREDIT, "1200.00", 1, 1)));

        assertEquals(EntryType.CREDIT, entry.type());
        assertEquals(Money.of(AED, "1200.00"), entry.amount());
    }

    @Test
    void debitIsStoredNegative() {
        final var entry = posted(debit.handle(request("E2", EventType.DEBIT, "950.00", 1, 1)));

        assertEquals(EntryType.DEBIT, entry.type());
        assertEquals(Money.of(AED, "-950.00"), entry.amount());
    }

    @Test
    void entryKeepsRequestDataAndBackdatedValueDate() {
        final var entry = posted(debit.handle(request("E7", EventType.DEBIT, "620.00", 5, 2)));

        assertEquals("E7", entry.requestId());
        assertEquals(ACC, entry.accountId());
        assertEquals(day(5), entry.bookedDay());
        assertEquals(day(2), entry.valueDate());
        assertTrue(entry.reversesEntryId().isEmpty());
    }

    @Test
    void sameRequestReturnsTheStoredEntry() {
        final var first = posted(credit.handle(request("E1", EventType.CREDIT, "1200.00", 1, 1)));

        assertSame(first, posted(credit.handle(request("E1", EventType.CREDIT, "1200.00", 1, 1))));
        assertEquals(1, repository.findByAccountId(ACC).size());
    }
}
