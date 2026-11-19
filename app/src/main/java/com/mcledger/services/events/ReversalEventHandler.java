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

/**
 * The reversed ledger entry id is the idempotency key of the reversal entry, so an entry can be
 * reversed only once: any further reversal of it returns the existing reversal.
 */
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
        final var originalEntryId = request.requiredReversesEntryId();
        final var idempotencyKey = originalEntryId.toString();

        final var existing = ledgerEntryRepository.findByRequestId(idempotencyKey);
        if (existing.isPresent()) {
            return new EventResult.Posted(existing.get());
        }

        final var original = ledgerEntryRepository.findById(originalEntryId)
            .filter(it -> it.accountId().equals(request.getAccountId()));
        if (original.isEmpty()) {
            return new EventResult.Rejected(rejectedEventRepository.create(
                rejectedEvent()
                    .requestId(request.getRequestId())
                    .accountId(request.getAccountId())
                    .bookedDay(request.getBookedDay())
                    .reason(RejectionReason.ENTRY_NOT_FOUND)
                    .message("no entry %s on account %s".formatted(originalEntryId, request.getAccountId()))
                    .build()
            ));
        }

        final var created = ledgerEntryRepository.create(
            ledgerEntry()
                .id(randomUUID())
                .requestId(idempotencyKey)
                .accountId(request.getAccountId())
                .type(EntryType.REVERSAL)
                .amount(original.get().amount().negate())
                .valueDate(original.get().valueDate())
                .bookedDay(request.getBookedDay())
                .reversesEntryId(originalEntryId)
                .build()
        );

        return new EventResult.Posted(created);
    }
}
