package com.mcledger.services;

import com.mcledger.config.ConfigService;
import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;
import com.mcledger.models.RejectionReason;
import com.mcledger.repositories.AccountRepository;
import com.mcledger.repositories.AuthEventRepository;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.RejectedEventRepository;
import com.mcledger.services.events.EventHandlerFactory;

import static com.mcledger.models.RejectedEvent.rejectedEvent;

public final class LedgerService {

    private final AccountRepository accountRepository;
    private final RejectedEventRepository rejectedEventRepository;
    private final ConfigService configService;
    private final EventHandlerFactory eventHandlerFactory;

    public LedgerService(AccountRepository accountRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         AuthEventRepository authEventRepository,
                         RejectedEventRepository rejectedEventRepository,
                         ConfigService configService) {
        this.accountRepository = accountRepository;
        this.rejectedEventRepository = rejectedEventRepository;
        this.configService = configService;
        this.eventHandlerFactory =
            new EventHandlerFactory(ledgerEntryRepository, authEventRepository, rejectedEventRepository);
    }

    public EventResult handleEvent(EventRequest request) {
        final var rejected = rejectedEventRepository.findByRequestId(request.getRequestId());
        if (rejected.isPresent()) {
            return new EventResult.Rejected(rejected.get());
        }

        final var account = accountRepository.getById(request.getAccountId());
        final var amount = request.getAmount();
        if (amount.isPresent() && amount.get().currency() != account.currency()) {
            return reject(request, RejectionReason.CURRENCY_MISMATCH,
                "amount %s does not match account currency %s".formatted(amount.get(), account.currency()));
        }

        if (request.getValueDate().isAfter(request.getBookedDay())) {
            return reject(request, RejectionReason.VALUE_DATE_AFTER_BOOKED_DAY,
                "valueDate %s is after bookedDay %s".formatted(request.getValueDate(), request.getBookedDay()));
        }
        final var earliestAllowedValueDate = request.getBookedDay().minusDays(configService.maxBackValueDays());
        if (request.getValueDate().isBefore(earliestAllowedValueDate)) {
            return reject(request, RejectionReason.VALUE_DATE_TOO_OLD,
                "valueDate %s is more than %d day(s) before bookedDay %s"
                    .formatted(request.getValueDate(), configService.maxBackValueDays(), request.getBookedDay()));
        }

        return eventHandlerFactory.forType(request.getEventType()).handle(request);
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
