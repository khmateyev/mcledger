package com.mcledger.services.events;

import com.mcledger.dtos.EventType;
import com.mcledger.models.EntryType;
import com.mcledger.repositories.AuthEventRepository;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.RejectedEventRepository;

import java.util.Map;

public class EventHandlerFactory {

    private final Map<EventType, EventHandler> handlers;

    public EventHandlerFactory(LedgerEntryRepository ledgerEntryRepository,
                               AuthEventRepository authEventRepository,
                               RejectedEventRepository rejectedEventRepository) {
        this.handlers = Map.of(
            EventType.CREDIT, new PostEntryEventHandler(ledgerEntryRepository, EntryType.CREDIT, false),
            EventType.DEBIT, new PostEntryEventHandler(ledgerEntryRepository, EntryType.DEBIT, true),
            EventType.AUTHORIZATION, new AuthorizationEventHandler(ledgerEntryRepository, authEventRepository),
            EventType.REVERSAL, new ReversalEventHandler(ledgerEntryRepository, rejectedEventRepository)
        );
    }

    public EventHandler forType(EventType type) {
        final var handler = handlers.get(type);
        if (handler == null) {
            throw new UnsupportedOperationException("no handler registered for event type " + type);
        }
        return handler;
    }
}
