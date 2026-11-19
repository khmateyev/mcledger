package com.mcledger.services.events;

import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;
import com.mcledger.models.EntryType;
import com.mcledger.models.RejectionReason;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.RejectedEventRepository;

import static com.mcledger.models.LedgerEntry.ledgerEntry;
import static com.mcledger.models.RejectedEvent.rejectedEvent;
import static java.util.UUID.randomUUID;

class ReversalEventHandler implements EventHandler {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final RejectedEventRepository rejectedEventRepository;

    ReversalEventHandler(LedgerEntryRepository ledgerEntryRepository,
                         RejectedEventRepository rejectedEventRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.rejectedEventRepository = rejectedEventRepository;
    }

    @Override
    public EventResult handle(EventRequest request) {
        final var existing = ledgerEntryRepository.findByRequestId(request.getRequestId());
        if (existing.isPresent()) {
            return new EventResult.Posted(existing.get());
        }

        final var originalRequestId = request.requiredReversesRequestId();
        final var original = ledgerEntryRepository.findByRequestId(originalRequestId)
            .filter(it -> it.accountId().equals(request.getAccountId()));
        if (original.isEmpty()) {
            return reject(request, RejectionReason.ENTRY_NOT_FOUND,
                "no entry for request %s on account %s".formatted(originalRequestId, request.getAccountId()));
        }
        if (ledgerEntryRepository.findReversalOf(original.get().id()).isPresent()) {
            return reject(request, RejectionReason.ALREADY_REVERSED,
                "request %s is already reversed".formatted(originalRequestId));
        }

        final var created = ledgerEntryRepository.create(
            ledgerEntry()
                .id(randomUUID())
                .requestId(request.getRequestId())
                .accountId(request.getAccountId())
                .type(EntryType.REVERSAL)
                .amount(original.get().amount().negate())
                .valueDate(original.get().valueDate())
                .bookedDay(request.getBookedDay())
                .reversesEntryId(original.get().id())
                .build()
        );

        return new EventResult.Posted(created);
    }

    private EventResult reject(EventRequest request, RejectionReason reason, String message) {
        return new EventResult.Rejected(rejectedEventRepository.create(
            rejectedEvent()
                .requestId(request.getRequestId())
                .accountId(request.getAccountId())
                .bookedDay(request.getBookedDay())
                .reason(reason)
                .message(message)
                .build()
        ));
    }
}
