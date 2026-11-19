package com.mcledger.repositories;

import com.mcledger.models.AuthEvent;
import com.mcledger.models.AuthEventType;
import com.mcledger.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.mcledger.models.AuthEvent.authEvent;
import static com.mcledger.money.CurrencyCode.AED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthEventRepositoryInMemoryTest {

    private static final UUID ACC_1 = UUID.randomUUID();
    private static final UUID ACC_2 = UUID.randomUUID();

    private final AuthEventRepository repository = new AuthEventRepositoryInMemory();

    private static AuthEvent event(String requestId, UUID account, AuthEventType type) {
        final var id = UUID.randomUUID();
        return authEvent().id(id).requestId(requestId).authId(id).accountId(account)
                .type(type).amount(Money.of(AED, "200.00")).bookedDay(LocalDate.of(2026, 1, 2)).build();
    }

    @Test
    void createIsIdempotentByRequestId() {
        final var first = repository.create(event("E3", ACC_1, AuthEventType.APPROVED));

        assertSame(first, repository.create(event("E3", ACC_1, AuthEventType.APPROVED)));
        assertSame(first, repository.findByRequestId("E3").orElseThrow());
        assertEquals(1, repository.findByAccountId(ACC_1).size());
    }

    @Test
    void findByAccountIdReturnsOnlyThatAccount() {
        final var a = repository.create(event("E3", ACC_1, AuthEventType.APPROVED));
        repository.create(event("X1", ACC_2, AuthEventType.APPROVED));

        assertEquals(List.of(a), repository.findByAccountId(ACC_1));
    }

    @Test
    void findById() {
        final var approved = repository.create(event("E3", ACC_1, AuthEventType.APPROVED));

        assertSame(approved, repository.findById(approved.id()).orElseThrow());
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }
}
