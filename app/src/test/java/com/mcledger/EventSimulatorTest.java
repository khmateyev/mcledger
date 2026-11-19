package com.mcledger;

import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventType;
import com.mcledger.models.Account;
import com.mcledger.money.Money;
import com.mcledger.services.LedgerService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.mcledger.dtos.EventRequest.eventRequest;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static com.mcledger.money.RemainderAllocation.FIRST;

class EventSimulatorTest {

    private static final UUID ACC_001 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACC_002 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final AppContainer container = new AppContainer();
    private final LedgerService ledgerService = container.ledgerService();

    private static LocalDate day(int n) {
        return LocalDate.of(2026, 1, n);
    }

    private static EventRequest event(String id, EventType type, UUID account, Money amount, int booked, int value) {
        return eventRequest().requestId(id).eventType(type).accountId(account)
            .amount(amount).bookedDay(day(booked)).valueDate(day(value)).build();
    }

    @Test
    void replaysTheEventStream() {
        container.accountRepository().create(new Account(ACC_001, AED));
        container.accountRepository().create(new Account(ACC_002, BHD));

        final var e10 = Money.of(BHD, "10.000").split(3, FIRST);

        final List<EventRequest> events = List.of(
            event("E1", EventType.CREDIT, ACC_001, Money.of(AED, "1200.00"), 1, 1),
            event("E2", EventType.DEBIT, ACC_001, Money.of(AED, "950.00"), 1, 1),
            event("E3", EventType.AUTHORIZATION, ACC_001, Money.of(AED, "200.00"), 2, 2),
            event("E4", EventType.CREDIT, ACC_001, Money.of(AED, "400.00"), 3, 3),
            // E5: SETTLEMENT Auth-A for AED 185.00, Day 4 (no SETTLEMENT event type yet)
            // E6: SETTLEMENT Auth-Z for AED 180.00, Day 4 (no SETTLEMENT event type yet)
            event("E7", EventType.DEBIT, ACC_001, Money.of(AED, "620.00"), 5, 2),
            event("E8", EventType.AUTHORIZATION, ACC_001, Money.of(AED, "90.00"), 5, 5),
            eventRequest().requestId("E9").eventType(EventType.REVERSAL).accountId(ACC_001)
                .reversesRequestId("E7").bookedDay(day(6)).valueDate(day(2)).build(),
            event("E10-1", EventType.CREDIT, ACC_002, e10.get(0), 5, 5),
            event("E10-2", EventType.CREDIT, ACC_002, e10.get(1), 5, 5),
            event("E10-3", EventType.CREDIT, ACC_002, e10.get(2), 5, 5)
        );

        for (EventRequest event : events) {
            System.out.println(event.getRequestId() + " -> " + ledgerService.handleEvent(event));
        }
    }
}
