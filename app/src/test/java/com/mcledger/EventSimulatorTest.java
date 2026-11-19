package com.mcledger;

import com.mcledger.dtos.DayCloseResult;
import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;
import com.mcledger.dtos.EventType;
import com.mcledger.models.EntryType;
import com.mcledger.money.Money;
import com.mcledger.services.DayCloseService;
import com.mcledger.services.LedgerService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static com.mcledger.models.Account.account;
import static com.mcledger.dtos.EventRequest.eventRequest;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static com.mcledger.money.RemainderAllocation.FIRST;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EventSimulatorTest {

    private static final UUID ACC_001 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACC_002 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    // Auth-Z has no preceding authorization event, so its id matches nothing.
    private static final UUID AUTH_Z = UUID.fromString("00000000-0000-0000-0000-00000000000f");

    private final AppContainer container = new AppContainer();
    private final LedgerService ledgerService = container.ledgerService();
    private final DayCloseService dayCloseService = container.dayCloseService();

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
        container.accountRepository().create(account().id(ACC_001).number("ACC-001").currency(AED).openedOn(day(1)).build());
        container.accountRepository().create(account().id(ACC_002).number("ACC-002").currency(BHD).openedOn(day(1)).build());

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

        // A day ends when the stream reaches a later booked day; the last day ends with the stream.
        // E10 is booked on Day 5 but arrives after E9 (Day 6), so it is a late posting picked up by the Day 6 close.
        LocalDate openDay = null;
        for (Supplier<EventRequest> next : events) {
            final var event = next.get();
            if (openDay != null && event.getBookedDay().isAfter(openDay)) {
                closeDays(openDay, event.getBookedDay().minusDays(1));
            }
            if (openDay == null || event.getBookedDay().isAfter(openDay)) {
                openDay = event.getBookedDay();
            }
            final var eventResult = ledgerService.handleEvent(event);
//            System.out.println(event.getRequestId() + " -> " + eventResult);
        }
        closeDays(openDay, openDay);

        // ACC-001 closing balances by value date: 250.00, 250.00, 650.00, 465.00, 465.00, 465.00
        // (E7 is reversed by E9 and the Day 5 fee is refunded) -> 0.10 + 0.10 + 0.26 + 0.19 + 0.19 + 0.19
        assertInterestCredit(ACC_001, Money.of(AED, "1.03"));
        // ACC-002 holds the E10 total (10.000) on Day 5 and Day 6 only -> 0.004 + 0.004, no compounding
        assertInterestCredit(ACC_002, Money.of(BHD, "0.008"));
    }

    private void assertInterestCredit(UUID accountId, Money expected) {
        final var credits = container.ledgerEntryRepository().findByAccountId(accountId).stream()
            .filter(it -> it.type() == EntryType.INTEREST)
            .toList();
        assertEquals(1, credits.size(), "one interest credit for the period");
        assertEquals(expected, credits.get(0).amount());
        assertEquals(day(6), credits.get(0).valueDate());
        assertEquals(day(6), credits.get(0).bookedDay());
    }

    private void closeDays(LocalDate from, LocalDate to) {
        for (var day = from; !day.isAfter(to); day = day.plusDays(1)) {
            for (UUID accountId : List.of(ACC_001, ACC_002)) {
                print(dayCloseService.closeDay(accountId, day));
            }
        }
    }

    private void print(DayCloseResult result) {
        System.out.println("Day " + result.closingDay().getDayOfMonth()
            + " " + container.accountRepository().getById(result.accountId()).number()
            + " balance=" + result.closingLedgerBalance()
            + " fees=" + result.feeAssessments().stream()
                .map(it -> it.type() + " " + it.amount() + " value=Day " + it.valueDate().getDayOfMonth()).toList()
            + " auths=" + result.authorizationStates().stream()
                .map(it -> authNumberOf(it.authId()) + " " + it.type() + " " + it.amount()).toList()
            + " errors=" + result.errors().stream()
                .map(it -> it.requestId() + " " + AUTH_NUMBERS.getOrDefault(it.requestId(), "") + " " + it.reason())
                .toList());
    }

    /** Auth numbers from the task, keyed by the event that carries them. */
    private static final Map<String, String> AUTH_NUMBERS = Map.of("E3", "Auth-A", "E6", "Auth-Z", "E8", "Auth-B");

    private String authNumberOf(UUID authId) {
        final var authorizationRequestId = container.authEventRepository().findById(authId).orElseThrow().requestId();
        return AUTH_NUMBERS.getOrDefault(authorizationRequestId, authorizationRequestId);
    }

    private UUID authIdOf(String requestId) {
        return container.authEventRepository().findByRequestId(requestId).orElseThrow().id();
    }

    private UUID entryIdOf(String requestId) {
        return container.ledgerEntryRepository().findByRequestId(requestId).orElseThrow().id();
    }
}
