package com.mcledger.repositories;

import com.mcledger.models.RejectedEvent;
import com.mcledger.models.RejectionReason;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.mcledger.models.RejectedEvent.rejectedEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RejectedEventRepositoryInMemoryTest {

    private final RejectedEventRepository repository = new RejectedEventRepositoryInMemory();

    private static RejectedEvent rejection(String requestId) {
        return rejectedEvent().requestId(requestId).accountId(UUID.randomUUID()).bookedDay(LocalDate.of(2026, 1, 4))
                .reason(RejectionReason.AUTHORIZATION_NOT_FOUND).message("not found").build();
    }

    @Test
    void createIsIdempotentByRequestId() {
        final var first = repository.create(rejection("E6"));

        assertSame(first, repository.create(rejection("E6")));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void findAllKeepsInsertionOrder() {
        final var e6 = repository.create(rejection("E6"));
        final var x1 = repository.create(rejection("X1"));

        assertEquals(List.of(e6, x1), repository.findAll());
    }

    @Test
    void findByRequestId() {
        final var e6 = repository.create(rejection("E6"));

        assertSame(e6, repository.findByRequestId("E6").orElseThrow());
        assertTrue(repository.findByRequestId("E7").isEmpty());
    }
}
