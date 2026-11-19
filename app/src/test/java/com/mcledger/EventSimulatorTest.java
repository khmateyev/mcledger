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
import java.util.function.Supplier;

import static com.mcledger.dtos.EventRequest.eventRequest;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static com.mcledger.money.RemainderAllocation.FIRST;

class EventSimulatorTest {

    private static final UUID ACC_001 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACC_002 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    // Auth-Z has no preceding authorization event, so its id matches nothing.
    private static final UUID AUTH_Z = UUID.fromString("00000000-0000-0000-0000-00000000000f");

    private final AppContainer container = new AppContainer();
    private final LedgerService ledgerService = container.ledgerService();

    private static LocalDate day(int n) {
        return LocalDate.of(2026, 1, n);
    }

    private static EventRequest event(String id, EventType type, UUID account, Money amount, int booked, int value) {
        return eventRequest().requestId(id).eventType(type).accountId(account)
            .amount(amount).bookedDay(day(booked)).valueDate(day(value)).build();
    }

    private static EventRequest settlement(String id, UUID authId, Money amount, int day) {
        return eventRequest().requestId(id).eventType(EventType.SETTLEMENT).accountId(ACC_001)
            .authId(authId).amount(amount).bookedDay(day(day)).valueDate(day(day)).build();
    }

    @Test
    void replaysTheEventStream() {
        container.accountRepository().create(new Account(ACC_001, AED));
        container.accountRepository().create(new Account(ACC_002, BHD));

        final var e10 = Money.of(BHD, "10.000").split(3, FIRST);

        // Suppliers, because E5 and E9 need ids (E3's authorization, E7's entry) that exist only after those are handled.
        final List<Supplier<EventRequest>> events = List.of(
            () -> event("E1", EventType.CREDIT, ACC_001, Money.of(AED, "1200.00"), 1, 1),
            () -> event("E2", EventType.DEBIT, ACC_001, Money.of(AED, "950.00"), 1, 1),
            () -> event("E3", EventType.AUTHORIZATION, ACC_001, Money.of(AED, "200.00"), 2, 2),
            () -> event("E4", EventType.CREDIT, ACC_001, Money.of(AED, "400.00"), 3, 3),
            () -> settlement("E5", authIdOf("E3"), Money.of(AED, "185.00"), 4),
            () -> settlement("E6", AUTH_Z, Money.of(AED, "180.00"), 4),
            () -> event("E7", EventType.DEBIT, ACC_001, Money.of(AED, "620.00"), 5, 2),
            () -> event("E8", EventType.AUTHORIZATION, ACC_001, Money.of(AED, "90.00"), 5, 5),
            () -> eventRequest().requestId("E9").eventType(EventType.REVERSAL).accountId(ACC_001)
                .reversesEntryId(entryIdOf("E7")).bookedDay(day(6)).valueDate(day(2)).build(),
            () -> event("E10-1", EventType.CREDIT, ACC_002, e10.get(0), 5, 5),
            () -> event("E10-2", EventType.CREDIT, ACC_002, e10.get(1), 5, 5),
            () -> event("E10-3", EventType.CREDIT, ACC_002, e10.get(2), 5, 5)
        );

        for (Supplier<EventRequest> next : events) {
            final var event = next.get();
            System.out.println(event.getRequestId() + " -> " + ledgerService.handleEvent(event));
        }
    }

    private UUID authIdOf(String requestId) {
        return container.authEventRepository().findByRequestId(requestId).orElseThrow().id();
    }

    private UUID entryIdOf(String requestId) {
        return container.ledgerEntryRepository().findByRequestId(requestId).orElseThrow().id();
    }
}
