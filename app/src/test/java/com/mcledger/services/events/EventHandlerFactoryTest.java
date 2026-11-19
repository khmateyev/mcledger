package com.mcledger.services.events;

import com.mcledger.dtos.EventType;
import com.mcledger.repositories.AuthEventRepositoryInMemory;
import com.mcledger.repositories.LedgerEntryRepositoryInMemory;
import com.mcledger.repositories.RejectedEventRepositoryInMemory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EventHandlerFactoryTest {

    private final EventHandlerFactory factory = new EventHandlerFactory(
            new LedgerEntryRepositoryInMemory(), new AuthEventRepositoryInMemory(), new RejectedEventRepositoryInMemory());

    @Test
    void everyEventTypeHasItsHandler() {
        assertInstanceOf(PostEntryEventHandler.class, factory.forType(EventType.CREDIT));
        assertInstanceOf(PostEntryEventHandler.class, factory.forType(EventType.DEBIT));
        assertInstanceOf(AuthorizationEventHandler.class, factory.forType(EventType.AUTHORIZATION));
        assertInstanceOf(SettlementEventHandler.class, factory.forType(EventType.SETTLEMENT));
        assertInstanceOf(ReversalEventHandler.class, factory.forType(EventType.REVERSAL));
    }

    @Test
    void noEventTypeIsLeftWithoutAHandler() {
        for (EventType type : EventType.values()) {
            factory.forType(type);
        }
    }
}
