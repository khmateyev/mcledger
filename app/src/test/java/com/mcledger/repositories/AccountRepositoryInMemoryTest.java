package com.mcledger.repositories;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.mcledger.models.Account.account;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountRepositoryInMemoryTest {

    private static final LocalDate OPENED = LocalDate.of(2026, 1, 1);

    private final AccountRepository repository = new AccountRepositoryInMemory();

    @Test
    void createdAccountCanBeFound() {
        final var account = account().id(UUID.randomUUID()).number("ACC-001").currency(AED).openedOn(OPENED).build();
        repository.create(account);

        final var found = repository.getById(account.id());
        assertEquals(account, found);
        assertEquals("ACC-001", found.number());
    }

    @Test
    void unknownAccountFails() {
        assertThrows(NullPointerException.class, () -> repository.getById(UUID.randomUUID()));
    }

    @Test
    void existingAccountCannotBeReplaced() {
        final var id = UUID.randomUUID();
        repository.create(account().id(id).number("ACC-001").currency(AED).openedOn(OPENED).build());

        assertThrows(UnsupportedOperationException.class, () -> repository.create(account().id(id).number("ACC-002").currency(BHD).openedOn(OPENED).build()));
        assertEquals(AED, repository.getById(id).currency());
    }
}
