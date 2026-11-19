package com.mcledger;

import com.mcledger.dtos.EventResult;
import com.mcledger.dtos.EventType;
import com.mcledger.models.LedgerEntry;
import com.mcledger.money.Money;
import com.mcledger.services.DayCloseService;
import com.mcledger.services.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.mcledger.dtos.EventRequest.eventRequest;
import static com.mcledger.models.Account.account;
import static com.mcledger.money.CurrencyCode.AED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Tag("failing-by-design")
class FailingByDesignTest {

    private static final UUID ACC = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AppContainer container = new AppContainer();
    private final LedgerService ledgerService = container.ledgerService();
    private final DayCloseService dayCloseService = container.dayCloseService();

    @BeforeEach
    void createAccount() {
        container.accountRepository().create(account().id(ACC).number("ACC-001").currency(AED).openedOn(day(1)).build());
    }

    private static LocalDate day(int n) {
        return LocalDate.of(2026, 1, n);
    }

    private EventResult post(String id, EventType type, String amount, int booked, int value) {
        return ledgerService.handleEvent(eventRequest().requestId(id).eventType(type).accountId(ACC)
            .amount(Money.of(AED, amount)).bookedDay(day(booked)).valueDate(day(value)).build());
    }

    private Money valueDatedBalance(int day) {
        return container.ledgerEntryRepository().findByAccountId(ACC).stream()
            .filter(it -> !it.valueDate().isAfter(day(day)))
            .map(LedgerEntry::amount)
            .reduce(Money.zero(AED), Money::plus);
    }

    @Test
    void dayFiveReportIsRestatedAfterE9() {
        // ACC-001 of the task: E1, E2, E4, E5 (Auth-A's settlement as a plain debit), then E7.
        post("E1", EventType.CREDIT, "1200.00", 1, 1);
        post("E2", EventType.DEBIT, "950.00", 1, 1);
        post("E4", EventType.CREDIT, "400.00", 3, 3);
        post("E5", EventType.DEBIT, "185.00", 4, 4);
        final var e7 = assertInstanceOf(EventResult.Posted.class, post("E7", EventType.DEBIT, "620.00", 5, 2)).entry();
        final var day5Report = dayCloseService.closeDay(ACC, day(5));

        // E9 reverses E7 on Day 6, valued Day 2: from Day 2 on, every day is restated upward by 620.00.
        ledgerService.handleEvent(eventRequest().requestId("E9").eventType(EventType.REVERSAL).accountId(ACC)
            .reversesEntryId(e7.id()).bookedDay(day(6)).valueDate(day(2)).build());
        dayCloseService.closeDay(ACC, day(6));

        // Passes: by value date, Day 5 is now 465.00 (the Day 5 fee is refunded at the Day 6 close).
        assertEquals(Money.of(AED, "465.00"), valueDatedBalance(5));

        // FAILS (actual -180.00). REVEALS: closeDay returns a snapshot taken at that close and nothing
        // restates it. After E9, the Day 5 report still says -180.00 while the ledger says 465.00, and
        // interest is accrued on the restated 465.00 for Day 5. So one engine shows two views of the same
        // day: a reader of the daily output sees Day 5 overdrawn, yet it earns interest.
        assertEquals(Money.of(AED, "465.00"), day5Report.closingLedgerBalance());
    }
}
