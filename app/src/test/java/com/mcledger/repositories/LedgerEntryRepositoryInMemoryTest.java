package com.mcledger.repositories;

import com.mcledger.models.EntryType;
import com.mcledger.models.LedgerEntry;
import com.mcledger.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.mcledger.models.LedgerEntry.ledgerEntry;
import static com.mcledger.money.CurrencyCode.AED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerEntryRepositoryInMemoryTest {

    private static final UUID ACC_1 = UUID.randomUUID();
    private static final UUID ACC_2 = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 1, 1);

    private final LedgerEntryRepository repository = new LedgerEntryRepositoryInMemory();

    private static LedgerEntry entry(String requestId, UUID account, String amount) {
        return ledgerEntry().id(UUID.randomUUID()).requestId(requestId).accountId(account).type(EntryType.CREDIT)
                .amount(Money.of(AED, amount)).valueDate(DAY).bookedDay(DAY).build();
    }

    @Test
    void createStoresAndFindsByRequestId() {
        final var e1 = repository.create(entry("E1", ACC_1, "10.00"));

        assertSame(e1, repository.findByRequestId("E1").orElseThrow());
        assertTrue(repository.findByRequestId("E2").isEmpty());
    }

    @Test
    void createWithAnExistingRequestIdReturnsTheStoredEntry() {
        final var first = repository.create(entry("E1", ACC_1, "10.00"));

        assertSame(first, repository.create(entry("E1", ACC_1, "10.00")));
        assertEquals(1, repository.findByAccountId(ACC_1).size());
    }

    @Test
    void findByAccountIdReturnsOnlyThatAccountInInsertionOrder() {
        final var e1 = repository.create(entry("E1", ACC_1, "10.00"));
        repository.create(entry("E2", ACC_2, "20.00"));
        final var e3 = repository.create(entry("E3", ACC_1, "30.00"));

        assertEquals(List.of(e1, e3), repository.findByAccountId(ACC_1));
        assertTrue(repository.findByAccountId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void findById() {
        final var e1 = repository.create(entry("E1", ACC_1, "10.00"));

        assertSame(e1, repository.findById(e1.id()).orElseThrow());
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void createWithAnExistingRequestIdDoesNotIndexTheNewId() {
        final var first = repository.create(entry("E1", ACC_1, "10.00"));
        final var duplicate = entry("E1", ACC_1, "10.00");

        assertSame(first, repository.create(duplicate));
        assertTrue(repository.findById(duplicate.id()).isEmpty());
    }
}
