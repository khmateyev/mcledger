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
 * Posts the settled amount as a debit and releases the whole hold. The authorization id is the
 * idempotency key of the settlement, so an authorization can be settled only once: any further
 * settlement of it returns the existing one.
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
        final var authId = request.requiredAuthId();
        final var idempotencyKey = authId.toString();

        final var existing = ledgerEntryRepository.findByRequestId(idempotencyKey);
        if (existing.isPresent()) {
            return new EventResult.Posted(existing.get());
        }

        final var auth = authEventRepository.findById(authId)
            .filter(it -> it.accountId().equals(request.getAccountId()));
        if (auth.isEmpty()) {
            return reject(request, RejectionReason.AUTHORIZATION_NOT_FOUND,
                "authorization %s not found on account %s".formatted(authId, request.getAccountId()));
        }
        if (auth.get().type() != AuthEventType.APPROVED) {
            return reject(request, RejectionReason.AUTHORIZATION_NOT_APPROVED,
                "authorization %s was %s".formatted(authId, auth.get().type()));
        }
        final var settleAmount = request.requiredAmount();
        if (settleAmount.compareTo(auth.get().amount()) > 0) {
            return reject(request, RejectionReason.SETTLEMENT_ABOVE_AUTHORIZED,
                "settlement %s is above authorized %s".formatted(settleAmount, auth.get().amount()));
        }

        final var created = ledgerEntryRepository.create(
            ledgerEntry()
                .id(randomUUID())
                .requestId(idempotencyKey)
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
                .requestId(idempotencyKey)
                .authId(authId)
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
