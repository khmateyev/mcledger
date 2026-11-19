package com.mcledger.services;

import com.mcledger.AppContainer;
import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;
import com.mcledger.dtos.EventType;
import com.mcledger.models.AuthEvent;
import com.mcledger.models.AuthEventType;
import com.mcledger.models.EntryType;
import com.mcledger.models.LedgerEntry;
import com.mcledger.models.RejectionReason;
import com.mcledger.money.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.mcledger.models.Account.account;
import static com.mcledger.dtos.EventRequest.eventRequest;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayCloseServiceTest {

    private static final UUID ACC = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACC_BHD = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final AppContainer container = new AppContainer();
    private final LedgerService ledgerService = container.ledgerService();
    private final DayCloseService service = container.dayCloseService();

    @BeforeEach
    void createAccounts() {
        container.accountRepository().create(account().id(ACC).number("ACC-001").currency(AED).openedOn(day(1)).build());
        container.accountRepository().create(account().id(ACC_BHD).number("ACC-002").currency(BHD).openedOn(day(1)).build());
    }

    private static LocalDate day(int n) {
        return LocalDate.of(2026, 1, n);
    }

    private EventResult handle(String id, EventType type, String amount, int booked, int value) {
        return ledgerService.handleEvent(eventRequest().requestId(id).eventType(type).accountId(ACC)
                .amount(Money.of(AED, amount)).bookedDay(day(booked)).valueDate(day(value)).build());
    }

    private LedgerEntry posted(EventResult result) {
        return assertInstanceOf(EventResult.Posted.class, result).entry();
    }

    private List<LedgerEntry> fees() {
        return container.ledgerEntryRepository().findByAccountId(ACC).stream()
                .filter(it -> it.type() == EntryType.FEE).toList();
    }

    @Test
    void positiveDayHasNoFee() {
        handle("E1", EventType.CREDIT, "1200.00", 1, 1);
        handle("E2", EventType.DEBIT, "950.00", 1, 1);

        final var result = service.closeDay(ACC, day(1));

        assertEquals(day(1), result.closingDay());
        assertEquals(ACC, result.accountId());
        assertEquals(Money.of(AED, "250.00"), result.closingLedgerBalance());
        assertTrue(result.feeAssessments().isEmpty());
    }

    @Test
    void zeroBalanceHasNoFee() {
        handle("E1", EventType.CREDIT, "10.00", 1, 1);
        handle("E2", EventType.DEBIT, "10.00", 1, 1);

        assertTrue(service.closeDay(ACC, day(1)).feeAssessments().isEmpty());
    }

    @Test
    void negativeDayIsChargedOneFeeOnThatDay() {
        handle("E2", EventType.DEBIT, "155.00", 5, 5);

        final var result = service.closeDay(ACC, day(5));

        assertEquals(Money.of(AED, "-180.00"), result.closingLedgerBalance());
        assertEquals(1, result.feeAssessments().size());
        final var fee = result.feeAssessments().get(0);
        assertEquals(EntryType.FEE, fee.type());
        assertEquals(Money.of(AED, "-25.00"), fee.amount());
        assertEquals(day(5), fee.valueDate());
        assertEquals(day(5), fee.bookedDay());
    }

    @Test
    void closingTheSameDayAgainChargesNothingMore() {
        handle("E2", EventType.DEBIT, "155.00", 5, 5);

        service.closeDay(ACC, day(5));
        final var again = service.closeDay(ACC, day(5));

        assertEquals(1, fees().size());
        assertEquals(Money.of(AED, "-180.00"), again.closingLedgerBalance());
    }

    @Test
    void backdatedDebitIsChargedOnTheClosingDayNotRetroactively() {
        handle("E1", EventType.CREDIT, "1200.00", 1, 1);
        handle("E2", EventType.DEBIT, "950.00", 1, 1);
        handle("E4", EventType.CREDIT, "400.00", 3, 3);
        handle("E5", EventType.DEBIT, "185.00", 4, 4);
        handle("E7", EventType.DEBIT, "620.00", 5, 2);

        service.closeDay(ACC, day(5));

        assertEquals(1, fees().size());
        assertEquals(day(5), fees().get(0).valueDate());
    }

    @Test
    void feeIsRefundedOnceItsDayIsNoLongerNegative() {
        handle("E1", EventType.CREDIT, "1200.00", 1, 1);
        handle("E2", EventType.DEBIT, "950.00", 1, 1);
        handle("E4", EventType.CREDIT, "400.00", 3, 3);
        handle("E5", EventType.DEBIT, "185.00", 4, 4);
        final var e7 = posted(handle("E7", EventType.DEBIT, "620.00", 5, 2));
        final var fee = service.closeDay(ACC, day(5)).feeAssessments().get(0);
        ledgerService.handleEvent(eventRequest().requestId("E9").eventType(EventType.REVERSAL).accountId(ACC)
                .reversesEntryId(e7.id()).bookedDay(day(6)).valueDate(day(2)).build());

        final var result = service.closeDay(ACC, day(6));

        assertEquals(1, result.feeAssessments().size());
        final var refund = result.feeAssessments().get(0);
        assertEquals(EntryType.REVERSAL, refund.type());
        assertEquals(Money.of(AED, "25.00"), refund.amount());
        assertEquals(day(5), refund.valueDate());
        assertEquals(day(6), refund.bookedDay());
        assertEquals(fee.id(), refund.reversesEntryId().orElseThrow());
        // day 6 ends the interest period, so the balance includes the 1.03 interest credit
        assertEquals(Money.of(AED, "466.03"), result.closingLedgerBalance());
    }

    @Test
    void feeStaysWhileItsDayIsStillNegative() {
        handle("E2", EventType.DEBIT, "100.00", 5, 5);
        service.closeDay(ACC, day(5));
        handle("E3", EventType.CREDIT, "50.00", 6, 5);

        final var result = service.closeDay(ACC, day(6));

        // day 5 without its fee is -50.00, so the fee stays; day 6 is negative too and gets its own fee
        assertEquals(2, fees().size());
        assertEquals(1, result.feeAssessments().size());
        assertEquals(EntryType.FEE, result.feeAssessments().get(0).type());
        assertEquals(Money.of(AED, "-100.00"), result.closingLedgerBalance());
    }

    @Test
    void feeDoesNotJustifyItself() {
        handle("E2", EventType.DEBIT, "10.00", 5, 5);
        service.closeDay(ACC, day(5));
        handle("E3", EventType.CREDIT, "10.00", 6, 5);

        final var result = service.closeDay(ACC, day(6));

        // without the fee day 5 is 0.00, so the fee is refunded even though it made the day -25.00
        assertEquals(EntryType.REVERSAL, result.feeAssessments().get(0).type());
        assertEquals(Money.zero(AED), result.closingLedgerBalance());
    }

    @Test
    void currencyWithoutConfiguredFeeIsNeverCharged() {
        ledgerService.handleEvent(eventRequest().requestId("B1").eventType(EventType.DEBIT).accountId(ACC_BHD)
                .amount(Money.of(BHD, "1.000")).bookedDay(day(1)).valueDate(day(1)).build());

        final var result = service.closeDay(ACC_BHD, day(1));

        assertEquals(Money.of(BHD, "-1.000"), result.closingLedgerBalance());
        assertTrue(result.feeAssessments().isEmpty());
    }

    @Test
    void reportsAuthorizationStateChangesBookedThatDay() {
        handle("E1", EventType.CREDIT, "1200.00", 1, 1);
        handle("E2", EventType.DEBIT, "950.00", 1, 1);
        final var e3 = assertInstanceOf(EventResult.Authorized.class, handle("E3", EventType.AUTHORIZATION, "200.00", 2, 2)).authEvent();
        ledgerService.handleEvent(eventRequest().requestId("E5").eventType(EventType.SETTLEMENT).accountId(ACC)
                .authId(e3.id()).amount(Money.of(AED, "185.00")).bookedDay(day(4)).valueDate(day(4)).build());

        final var day2 = service.closeDay(ACC, day(2)).authorizationStates();
        final var day3 = service.closeDay(ACC, day(3)).authorizationStates();
        final var day4 = service.closeDay(ACC, day(4)).authorizationStates();

        assertEquals(1, day2.size());
        assertEquals(AuthEventType.APPROVED, day2.get(0).type());
        assertTrue(day3.isEmpty());
        assertEquals(1, day4.size());
        assertEquals(AuthEventType.SETTLED, day4.get(0).type());
        assertEquals(e3.id(), day4.get(0).authId());
    }

    @Test
    void reportsErrorsBookedThatDay() {
        ledgerService.handleEvent(eventRequest().requestId("E6").eventType(EventType.SETTLEMENT).accountId(ACC)
                .authId(UUID.randomUUID()).amount(Money.of(AED, "180.00")).bookedDay(day(4)).valueDate(day(4)).build());

        final var errors = service.closeDay(ACC, day(4)).errors();

        assertEquals(1, errors.size());
        assertEquals(RejectionReason.AUTHORIZATION_NOT_FOUND, errors.get(0).reason());
        assertTrue(service.closeDay(ACC, day(5)).errors().isEmpty());
    }

    @Test
    void interestOnTheTaskStreamIs103AndUsesTheRestatedHistory() {
        handle("E1", EventType.CREDIT, "1200.00", 1, 1);
        handle("E2", EventType.DEBIT, "950.00", 1, 1);
        handle("E4", EventType.CREDIT, "400.00", 3, 3);
        handle("E5", EventType.DEBIT, "185.00", 4, 4);
        final var e7 = posted(handle("E7", EventType.DEBIT, "620.00", 5, 2));
        service.closeDay(ACC, day(5));
        ledgerService.handleEvent(eventRequest().requestId("E9").eventType(EventType.REVERSAL).accountId(ACC)
                .reversesEntryId(e7.id()).bookedDay(day(6)).valueDate(day(2)).build());

        service.closeDay(ACC, day(6));

        // 250.00, 250.00, 650.00, 465.00, 465.00, 465.00 -> 0.10 + 0.10 + 0.26 + 0.19 + 0.19 + 0.19
        final var credit = interestCredits(ACC).get(0);
        assertEquals(Money.of(AED, "1.03"), credit.amount());
        assertEquals(day(6), credit.valueDate());
        assertEquals(day(6), credit.bookedDay());
    }

    @Test
    void bhdInterestUsesThreeDecimals() {
        for (int i = 1; i <= 3; i++) {
            ledgerService.handleEvent(eventRequest().requestId("E10-" + i).eventType(EventType.CREDIT).accountId(ACC_BHD)
                    .amount(Money.of(BHD, i == 1 ? "3.334" : "3.333")).bookedDay(day(5)).valueDate(day(5)).build());
        }

        final var result = service.closeDay(ACC_BHD, day(6));

        // 10.000 on day 5 and day 6 -> 0.004 + 0.004
        assertEquals(Money.of(BHD, "0.008"), interestCredits(ACC_BHD).get(0).amount());
        assertEquals(Money.of(BHD, "10.008"), result.closingLedgerBalance());
    }

    @Test
    void negativeDaysEarnNothing() {
        handle("E1", EventType.CREDIT, "100.00", 1, 1);
        handle("E2", EventType.DEBIT, "200.00", 2, 2);

        service.closeDay(ACC, day(6));

        // only day 1 (100.00) is positive -> 0.04
        assertEquals(Money.of(AED, "0.04"), interestCredits(ACC).get(0).amount());
    }

    @Test
    void eachDayIsRoundedBeforeSumming() {
        handle("E1", EventType.CREDIT, "12.50", 1, 1);

        service.closeDay(ACC, day(6));

        // 12.50 * 0.0004 = 0.005 -> 0.01 per day = 0.06; rounding the unrounded sum would give 0.03
        assertEquals(Money.of(AED, "0.06"), interestCredits(ACC).get(0).amount());
    }

    @Test
    void noInterestCreditWhenNothingAccrues() {
        handle("E2", EventType.DEBIT, "100.00", 1, 1);

        service.closeDay(ACC, day(6));

        assertTrue(interestCredits(ACC).isEmpty());
    }

    private List<LedgerEntry> interestCredits(UUID accountId) {
        return container.ledgerEntryRepository().findByAccountId(accountId).stream()
                .filter(it -> it.type() == EntryType.INTEREST).toList();
    }

    @Test
    void closingTheLastDayOfThePeriodCapitalizesInterest() {
        handle("E1", EventType.CREDIT, "1000.00", 1, 1);

        final var result = service.closeDay(ACC, day(6));

        // 1000.00 * 0.0004 = 0.40 for each of the 6 days
        final var credit = interestCredits(ACC).get(0);
        assertEquals(Money.of(AED, "2.40"), credit.amount());
        assertEquals(day(6), credit.valueDate());
        assertEquals(Money.of(AED, "1002.40"), result.closingLedgerBalance());
    }

    @Test
    void otherDaysDoNotCapitalize() {
        handle("E1", EventType.CREDIT, "1000.00", 1, 1);

        for (int d = 1; d <= 5; d++) {
            service.closeDay(ACC, day(d));
        }

        assertTrue(interestCredits(ACC).isEmpty());
    }

    @Test
    void closingThePeriodEndAgainCreditsOnce() {
        handle("E1", EventType.CREDIT, "1000.00", 1, 1);

        service.closeDay(ACC, day(6));
        final var again = service.closeDay(ACC, day(6));

        assertEquals(Money.of(AED, "1002.40"), again.closingLedgerBalance());
        assertEquals(1, interestCredits(ACC).size());
    }

    @Test
    void periodIsCountedFromTheAccountsOpeningDay() {
        final var late = UUID.randomUUID();
        container.accountRepository().create(account().id(late).number("ACC-003").currency(AED).openedOn(day(3)).build());
        ledgerService.handleEvent(eventRequest().requestId("L1").eventType(EventType.CREDIT).accountId(late)
                .amount(Money.of(AED, "1000.00")).bookedDay(day(3)).valueDate(day(3)).build());

        service.closeDay(late, day(6));
        assertTrue(interestCredits(late).isEmpty());

        service.closeDay(late, day(8));
        // days 3..8
        assertEquals(Money.of(AED, "2.40"), interestCredits(late).get(0).amount());
        assertEquals(day(8), interestCredits(late).get(0).valueDate());
    }

    @Test
    void nextPeriodStartsAfterThePreviousOneAndItsCreditEarnsInterest() {
        handle("E1", EventType.CREDIT, "100000.00", 1, 1);
        service.closeDay(ACC, day(6));

        service.closeDay(ACC, day(12));

        // the first period's 240.00 credit earns interest now: 100240.00 * 0.0004 = 40.096 -> 40.10 a day
        assertEquals(Money.of(AED, "240.00"), interestCredits(ACC).get(0).amount());
        assertEquals(Money.of(AED, "240.60"), interestCredits(ACC).get(1).amount());
        assertEquals(day(12), interestCredits(ACC).get(1).valueDate());
    }

    @Test
    void periodWithNothingAccruedStillEndsOnTime() {
        handle("E2", EventType.DEBIT, "100.00", 1, 1);
        service.closeDay(ACC, day(6));
        assertTrue(interestCredits(ACC).isEmpty());

        handle("E3", EventType.CREDIT, "1100.00", 7, 7);
        service.closeDay(ACC, day(12));

        assertEquals("INTEREST:%s:%s:%s".formatted(ACC, day(7), day(12)), interestCredits(ACC).get(0).requestId());
    }

    private AuthEvent authorize(String id, String amount, int day) {
        return assertInstanceOf(EventResult.Authorized.class, handle(id, EventType.AUTHORIZATION, amount, day, day)).authEvent();
    }

    private List<AuthEvent> expired() {
        return container.authEventRepository().findByAccountId(ACC).stream()
                .filter(it -> it.type() == AuthEventType.EXPIRED).toList();
    }

    @Test
    void holdExpiresAtTheCloseOfItsExpiryDay() {
        handle("E1", EventType.CREDIT, "250.00", 1, 1);
        final var hold = authorize("A1", "200.00", 2);

        service.closeDay(ACC, day(8));
        assertTrue(expired().isEmpty());

        final var result = service.closeDay(ACC, day(9));

        assertEquals(1, expired().size());
        assertEquals(hold.authId(), expired().get(0).authId());
        assertEquals(day(9), expired().get(0).bookedDay());
        // reported as that day's authorization state change
        assertEquals(AuthEventType.EXPIRED, result.authorizationStates().get(0).type());
    }

    @Test
    void expiredHoldIsReleasedFromTheAvailableBalance() {
        handle("E1", EventType.CREDIT, "250.00", 1, 1);
        authorize("A1", "200.00", 2);
        assertEquals(AuthEventType.DECLINED, authorize("A2", "100.00", 3).type());

        service.closeDay(ACC, day(9));

        assertEquals(AuthEventType.APPROVED, authorize("A3", "100.00", 10).type());
    }

    @Test
    void closingTheExpiryDayAgainExpiresOnce() {
        handle("E1", EventType.CREDIT, "250.00", 1, 1);
        authorize("A1", "200.00", 2);

        service.closeDay(ACC, day(9));
        service.closeDay(ACC, day(10));

        assertEquals(1, expired().size());
    }

    @Test
    void settledAndDeclinedHoldsDoNotExpire() {
        handle("E1", EventType.CREDIT, "250.00", 1, 1);
        final var settled = authorize("A1", "200.00", 2);
        ledgerService.handleEvent(eventRequest().requestId("S1").eventType(EventType.SETTLEMENT).accountId(ACC)
                .authId(settled.id()).amount(Money.of(AED, "185.00")).bookedDay(day(4)).valueDate(day(4)).build());
        authorize("A2", "500.00", 2);

        service.closeDay(ACC, day(9));

        assertTrue(expired().isEmpty());
    }

    @Test
    void settlementAfterExpiryIsStillAccepted() {
        handle("E1", EventType.CREDIT, "250.00", 1, 1);
        final var hold = authorize("A1", "200.00", 2);
        service.closeDay(ACC, day(9));

        final var settlement = ledgerService.handleEvent(eventRequest().requestId("S1").eventType(EventType.SETTLEMENT)
                .accountId(ACC).authId(hold.id()).amount(Money.of(AED, "185.00")).bookedDay(day(10)).valueDate(day(10)).build());

        assertEquals(Money.of(AED, "-185.00"), posted(settlement).amount());
    }
}
