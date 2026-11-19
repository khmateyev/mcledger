package com.mcledger.repositories;

import com.mcledger.models.Account;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountRepositoryInMemoryTest {

    private final AccountRepository repository = new AccountRepositoryInMemory();

    @Test
    void createdAccountCanBeFound() {
        final var account = new Account(UUID.randomUUID(), AED);
        repository.create(account);

        assertEquals(account, repository.getById(account.id()));
    }

    @Test
    void unknownAccountFails() {
        assertThrows(NullPointerException.class, () -> repository.getById(UUID.randomUUID()));
    }

    @Test
    void existingAccountCannotBeReplaced() {
        final var id = UUID.randomUUID();
        repository.create(new Account(id, AED));

        assertThrows(UnsupportedOperationException.class, () -> repository.create(new Account(id, BHD)));
        assertEquals(AED, repository.getById(id).currency());
    }
}
