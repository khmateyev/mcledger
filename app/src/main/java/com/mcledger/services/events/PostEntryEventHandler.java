package com.mcledger.services.events;

import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;
import com.mcledger.models.EntryType;
import com.mcledger.repositories.LedgerEntryRepository;

import static com.mcledger.models.LedgerEntry.ledgerEntry;
import static java.util.UUID.randomUUID;

class PostEntryEventHandler implements EventHandler {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final EntryType type;
    private final boolean debit;

    PostEntryEventHandler(LedgerEntryRepository ledgerEntryRepository, EntryType type, boolean debit) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.type = type;
        this.debit = debit;
    }

    @Override
    public EventResult handle(EventRequest request) {
        final var amount = request.requiredAmount();

        final var created = ledgerEntryRepository.create(
            ledgerEntry()
                .id(randomUUID())
                .requestId(request.getRequestId())
                .accountId(request.getAccountId())
                .type(type)
                .amount(debit ? amount.negate() : amount)
                .valueDate(request.getValueDate())
                .bookedDay(request.getBookedDay())
                .build()
        );

        return new EventResult.Posted(created);
    }
}
