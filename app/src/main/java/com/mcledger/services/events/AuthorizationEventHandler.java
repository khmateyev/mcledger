package com.mcledger.services.events;

import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;
import com.mcledger.models.AuthEvent;
import com.mcledger.models.AuthEventType;
import com.mcledger.models.LedgerEntry;
import com.mcledger.money.Money;
import com.mcledger.repositories.AuthEventRepository;
import com.mcledger.repositories.LedgerEntryRepository;

import java.util.UUID;

import static com.mcledger.models.AuthEvent.authEvent;
import static java.util.UUID.randomUUID;

/**
 * Approves a hold only if ledger balance - active holds - hold stays >= 0.
 * Holds live outside the ledger: they change the available balance, never the ledger balance.
 */
class AuthorizationEventHandler implements EventHandler {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuthEventRepository authEventRepository;

    AuthorizationEventHandler(LedgerEntryRepository ledgerEntryRepository,
                              AuthEventRepository authEventRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.authEventRepository = authEventRepository;
    }

    @Override
    public EventResult handle(EventRequest request) {
        final var existing = authEventRepository.findByRequestId(request.getRequestId());
        if (existing.isPresent()) {
            return new EventResult.Authorized(existing.get());
        }

        final var holdAmount = request.requiredAmount();
        final var available = ledgerBalance(request.getAccountId(), holdAmount)
            .minus(activeHolds(request.getAccountId(), holdAmount));
        final var approved = !available.minus(holdAmount).isNegative();

        final var created = authEventRepository.create(
            authEvent()
                .id(randomUUID())
                .requestId(request.getRequestId())
                .accountId(request.getAccountId())
                .type(approved ? AuthEventType.APPROVED : AuthEventType.DECLINED)
                .amount(holdAmount)
                .bookedDay(request.getBookedDay())
                .build()
        );

        return new EventResult.Authorized(created);
    }

    private Money ledgerBalance(UUID accountId, Money currencyOf) {
        return ledgerEntryRepository.findByAccountId(accountId).stream()
            .map(LedgerEntry::amount)
            .reduce(Money.zero(currencyOf.currency()), Money::plus);
    }

    private Money activeHolds(UUID accountId, Money currencyOf) {
        return authEventRepository.findByAccountId(accountId).stream()
            .filter(it -> it.type() == AuthEventType.APPROVED)
            .map(AuthEvent::amount)
            .reduce(Money.zero(currencyOf.currency()), Money::plus);
    }
}
