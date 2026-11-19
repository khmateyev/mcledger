package com.mcledger.services.events;

import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;
import com.mcledger.models.AuthEventType;
import com.mcledger.models.EntryType;
import com.mcledger.models.RejectionReason;
import com.mcledger.repositories.AuthEventRepository;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.RejectedEventRepository;

import static com.mcledger.models.AuthEvent.authEvent;
import static com.mcledger.models.LedgerEntry.ledgerEntry;
import static com.mcledger.models.RejectedEvent.rejectedEvent;
import static java.util.UUID.randomUUID;

/**
 * Posts the settled amount as a debit and releases the whole hold. Only an approved,
 * not yet settled authorization can be settled, for at most its authorized amount.
 */
class SettlementEventHandler implements EventHandler {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuthEventRepository authEventRepository;
    private final RejectedEventRepository rejectedEventRepository;

    SettlementEventHandler(LedgerEntryRepository ledgerEntryRepository,
                           AuthEventRepository authEventRepository,
                           RejectedEventRepository rejectedEventRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.authEventRepository = authEventRepository;
        this.rejectedEventRepository = rejectedEventRepository;
    }

    @Override
    public EventResult handle(EventRequest request) {
        final var existing = ledgerEntryRepository.findByRequestId(request.getRequestId());
        if (existing.isPresent()) {
            return new EventResult.Posted(existing.get());
        }

        final var authRequestId = request.requiredAuthRequestId();
        final var auth = authEventRepository.findByRequestId(authRequestId)
            .filter(it -> it.accountId().equals(request.getAccountId()));
        if (auth.isEmpty()) {
            return reject(request, RejectionReason.AUTHORIZATION_NOT_FOUND,
                "authorization %s not found on account %s".formatted(authRequestId, request.getAccountId()));
        }
        if (auth.get().type() != AuthEventType.APPROVED) {
            return reject(request, RejectionReason.AUTHORIZATION_NOT_APPROVED,
                "authorization %s was %s".formatted(authRequestId, auth.get().type()));
        }
        final var alreadySettled = authEventRepository.findByAuthRequestId(authRequestId).stream()
            .anyMatch(it -> it.type() == AuthEventType.SETTLED);
        if (alreadySettled) {
            return reject(request, RejectionReason.AUTHORIZATION_ALREADY_SETTLED,
                "authorization %s is already settled".formatted(authRequestId));
        }
        final var settleAmount = request.requiredAmount();
        if (settleAmount.compareTo(auth.get().amount()) > 0) {
            return reject(request, RejectionReason.SETTLEMENT_ABOVE_AUTHORIZED,
                "settlement %s is above authorized %s".formatted(settleAmount, auth.get().amount()));
        }

        final var created = ledgerEntryRepository.create(
            ledgerEntry()
                .id(randomUUID())
                .requestId(request.getRequestId())
                .accountId(request.getAccountId())
                .type(EntryType.DEBIT)
                .amount(settleAmount.negate())
                .valueDate(request.getValueDate())
                .bookedDay(request.getBookedDay())
                .build()
        );

        authEventRepository.create(
            authEvent()
                .id(randomUUID())
                .requestId(request.getRequestId())
                .authRequestId(authRequestId)
                .accountId(request.getAccountId())
                .type(AuthEventType.SETTLED)
                .amount(settleAmount)
                .bookedDay(request.getBookedDay())
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
