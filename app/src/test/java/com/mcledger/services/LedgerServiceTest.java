package com.mcledger.services;

import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;
import com.mcledger.dtos.EventType;
import com.mcledger.models.Account;
import com.mcledger.models.AuthEvent;
import com.mcledger.models.AuthEventType;
import com.mcledger.repositories.AuthEventRepository;
import com.mcledger.repositories.AuthEventRepositoryInMemory;
import com.mcledger.models.LedgerEntry;
import com.mcledger.models.RejectionReason;
import com.mcledger.money.Money;
import com.mcledger.repositories.AccountRepository;
import com.mcledger.repositories.AccountRepositoryInMemory;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.LedgerEntryRepositoryInMemory;
import com.mcledger.repositories.RejectedEventRepository;
import com.mcledger.repositories.RejectedEventRepositoryInMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static com.mcledger.config.FixedConfigService.fixedConfigService;
import static com.mcledger.dtos.EventRequest.eventRequest;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerServiceTest {

    private static final UUID ACC = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACC_BHD = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final AccountRepository accountRepository = new AccountRepositoryInMemory();
    private final LedgerEntryRepository ledgerEntryRepository = new LedgerEntryRepositoryInMemory();
    private final AuthEventRepository authEventRepository = new AuthEventRepositoryInMemory();
    private final RejectedEventRepository rejectedEventRepository = new RejectedEventRepositoryInMemory();
    private final LedgerService service = new LedgerService(
            accountRepository, ledgerEntryRepository, authEventRepository, rejectedEventRepository,
            fixedConfigService()
                    .overdraftFee(Money.of(AED, "25.00"))
                    .dailyInterestRate(AED, new BigDecimal("0.0004"))
                    .dailyInterestRate(BHD, new BigDecimal("0.0004"))
                    .holdExpiryDays(7)
                    .maxBackValueDays(5)
                    .build());

    @BeforeEach
    void createAccounts() {
        accountRepository.create(new Account(ACC, AED));
        accountRepository.create(new Account(ACC_BHD, BHD));
    }

    @Test
    void amountInAnotherCurrencyIsRejected() {
        rejected(service.handleEvent(eventRequest().requestId("X").eventType(EventType.CREDIT).accountId(ACC)
                        .amount(Money.of(BHD, "10.000")).bookedDay(day(1)).valueDate(day(1)).build()),
                RejectionReason.CURRENCY_MISMATCH);
    }

    @Test
    void unknownAccountFails() {
        assertThrows(NullPointerException.class, () -> service.handleEvent(eventRequest().requestId("X")
                .eventType(EventType.CREDIT).accountId(UUID.randomUUID()).amount(Money.of(AED, "1.00"))
                .bookedDay(day(1)).valueDate(day(1)).build()));
    }

    private AuthEvent authorized(EventResult result) {
        return assertInstanceOf(EventResult.Authorized.class, result).authEvent();
    }

    @Test
    void holdWithinAvailableBalanceIsApprovedAndLeavesLedgerUntouched() {
        service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1));
        service.handleEvent(event("E2", EventType.DEBIT, "950.00", 1, 1));

        final var auth = authorized(service.handleEvent(event("E3", EventType.AUTHORIZATION, "200.00", 2, 2)));

        assertEquals(AuthEventType.APPROVED, auth.type());
        assertEquals(Money.of(AED, "250.00"), balanceAsOf(2));
    }

    @Test
    void holdUsingExactlyTheAvailableBalanceIsApproved() {
        service.handleEvent(event("E1", EventType.CREDIT, "250.00", 1, 1));

        assertEquals(AuthEventType.APPROVED,
                authorized(service.handleEvent(event("A1", EventType.AUTHORIZATION, "250.00", 1, 1))).type());
    }

    @Test
    void activeHoldsReduceAvailableBalance() {
        service.handleEvent(event("E1", EventType.CREDIT, "250.00", 1, 1));
        service.handleEvent(event("A1", EventType.AUTHORIZATION, "200.00", 1, 1));

        assertEquals(AuthEventType.DECLINED,
                authorized(service.handleEvent(event("A2", EventType.AUTHORIZATION, "50.01", 1, 1))).type());
        assertEquals(AuthEventType.APPROVED,
                authorized(service.handleEvent(event("A3", EventType.AUTHORIZATION, "50.00", 1, 1))).type());
    }

    @Test
    void e8IsDeclinedAfterBackdatedE7() {
        service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1));
        service.handleEvent(event("E2", EventType.DEBIT, "950.00", 1, 1));
        service.handleEvent(event("E4", EventType.CREDIT, "400.00", 3, 3));
        service.handleEvent(event("E7", EventType.DEBIT, "620.00", 5, 2));

        assertEquals(AuthEventType.DECLINED,
                authorized(service.handleEvent(event("E8", EventType.AUTHORIZATION, "90.00", 5, 5))).type());
    }

    @Test
    void sameAuthorizationTwiceIsRecordedOnce() {
        service.handleEvent(event("E1", EventType.CREDIT, "250.00", 1, 1));
        final var first = authorized(service.handleEvent(event("A1", EventType.AUTHORIZATION, "200.00", 1, 1)));

        assertSame(first, authorized(service.handleEvent(event("A1", EventType.AUTHORIZATION, "200.00", 1, 1))));
        assertEquals(1, authEventRepository.findByAccountId(ACC).size());
    }

    private static EventRequest settlement(String id, UUID authId, String amount, int day) {
        return eventRequest().requestId(id).eventType(EventType.SETTLEMENT).accountId(ACC)
                .authId(authId).amount(Money.of(AED, amount)).bookedDay(day(day)).valueDate(day(day)).build();
    }

    @Test
    void e5SettlesAuthABelowTheHoldAndReleasesIt() {
        service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1));
        service.handleEvent(event("E2", EventType.DEBIT, "950.00", 1, 1));
        final var e3 = authorized(service.handleEvent(event("E3", EventType.AUTHORIZATION, "200.00", 2, 2)));

        final var e5 = posted(service.handleEvent(settlement("E5", e3.id(), "185.00", 4)));

        assertEquals(Money.of(AED, "-185.00"), e5.amount());
        assertEquals(Money.of(AED, "65.00"), balanceAsOf(4));
        // the whole 200.00 hold is released: 65.00 is available again
        assertEquals(AuthEventType.APPROVED,
                authorized(service.handleEvent(event("A2", EventType.AUTHORIZATION, "65.00", 4, 4))).type());
    }

    @Test
    void e6SettlementWithoutAuthorizationIsRejectedAndMovesNoMoney() {
        service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1));

        rejected(service.handleEvent(settlement("E6", UUID.randomUUID(), "180.00", 4)), RejectionReason.AUTHORIZATION_NOT_FOUND);
        assertEquals(Money.of(AED, "1200.00"), balanceAsOf(4));
    }

    @Test
    void settlementAboveTheAuthorizedAmountIsRejected() {
        service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1));
        final var a1 = authorized(service.handleEvent(event("A1", EventType.AUTHORIZATION, "200.00", 2, 2)));

        rejected(service.handleEvent(settlement("S1", a1.id(), "200.01", 4)), RejectionReason.SETTLEMENT_ABOVE_AUTHORIZED);
        posted(service.handleEvent(settlement("S2", a1.id(), "200.00", 4)));
    }

    @Test
    void declinedAuthorizationCannotBeSettled() {
        service.handleEvent(event("E1", EventType.CREDIT, "100.00", 1, 1));
        final var a1 = authorized(service.handleEvent(event("A1", EventType.AUTHORIZATION, "500.00", 2, 2)));

        rejected(service.handleEvent(settlement("S1", a1.id(), "50.00", 4)), RejectionReason.AUTHORIZATION_NOT_APPROVED);
    }

    @Test
    void anAuthorizationIsSettledOnlyOnceWhateverTheRequest() {
        service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1));
        final var a1 = authorized(service.handleEvent(event("A1", EventType.AUTHORIZATION, "200.00", 2, 2)));
        final var first = posted(service.handleEvent(settlement("S1", a1.id(), "185.00", 4)));

        assertSame(first, posted(service.handleEvent(settlement("S1", a1.id(), "185.00", 4))));
        assertSame(first, posted(service.handleEvent(settlement("S2", a1.id(), "50.00", 4))));
        assertEquals(Money.of(AED, "1015.00"), balanceAsOf(4));
    }

    private static LocalDate day(int n) {
        return LocalDate.of(2026, 1, n);
    }

    private static EventRequest event(String id, EventType type, String amount, int booked, int value) {
        return eventRequest().requestId(id).eventType(type).accountId(ACC)
                .amount(Money.of(AED, amount)).bookedDay(day(booked)).valueDate(day(value)).build();
    }

    private static EventRequest reversal(String id, UUID entryId, int booked) {
        return eventRequest().requestId(id).eventType(EventType.REVERSAL).accountId(ACC)
                .reversesEntryId(entryId).bookedDay(day(booked)).valueDate(day(booked)).build();
    }

    private static LedgerEntry posted(EventResult result) {
        return assertInstanceOf(EventResult.Posted.class, result).entry();
    }

    private static void rejected(EventResult result, RejectionReason reason) {
        assertEquals(reason, assertInstanceOf(EventResult.Rejected.class, result).rejection().reason());
    }

    private Money balanceAsOf(int valueDay) {
        return ledgerEntryRepository.findByAccountId(ACC).stream()
                .filter(e -> !e.valueDate().isAfter(day(valueDay)))
                .map(LedgerEntry::amount)
                .reduce(Money.zero(AED), Money::plus);
    }

    @Test
    void postsEachTypeWithItsSign() {
        assertEquals(Money.of(AED, "1200.00"), posted(service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1))).amount());
        assertEquals(Money.of(AED, "-950.00"), posted(service.handleEvent(event("E2", EventType.DEBIT, "950.00", 1, 1))).amount());
    }

    @Test
    void sameRequestTwiceIsPostedOnce() {
        final var first = posted(service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1)));

        assertSame(first, posted(service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1))));
        assertEquals(1, ledgerEntryRepository.findByAccountId(ACC).size());
    }

    @Test
    void backdatedDebitChangesThePastBalance() {
        service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1));
        service.handleEvent(event("E2", EventType.DEBIT, "950.00", 1, 1));
        service.handleEvent(event("E7", EventType.DEBIT, "620.00", 5, 2));

        assertEquals(Money.of(AED, "-370.00"), balanceAsOf(2));
    }

    @Test
    void e9ReversesE7WithTheOriginalValueDate() {
        service.handleEvent(event("E1", EventType.CREDIT, "1200.00", 1, 1));
        service.handleEvent(event("E2", EventType.DEBIT, "950.00", 1, 1));
        final var e7 = posted(service.handleEvent(event("E7", EventType.DEBIT, "620.00", 5, 2)));

        final var e9 = posted(service.handleEvent(reversal("E9", e7.id(), 6)));

        assertEquals(Money.of(AED, "620.00"), e9.amount());
        assertEquals(e7.id().toString(), e9.requestId());
        assertEquals(day(2), e9.valueDate());
        assertEquals(day(6), e9.bookedDay());
        assertEquals(e7.id(), e9.reversesEntryId().orElseThrow());
        assertEquals(Money.of(AED, "250.00"), balanceAsOf(2));
    }

    @Test
    void anEntryIsReversedOnlyOnceWhateverTheRequest() {
        final var e7 = posted(service.handleEvent(event("E7", EventType.DEBIT, "620.00", 5, 2)));
        final var e9 = posted(service.handleEvent(reversal("E9", e7.id(), 6)));

        assertSame(e9, posted(service.handleEvent(reversal("E9", e7.id(), 6))));
        assertSame(e9, posted(service.handleEvent(reversal("E9b", e7.id(), 6))));
        assertEquals(2, ledgerEntryRepository.findByAccountId(ACC).size());
    }

    @Test
    void reversalOfUnknownEntryIsRejected() {
        rejected(service.handleEvent(reversal("R1", UUID.randomUUID(), 6)), RejectionReason.ENTRY_NOT_FOUND);
    }

    @Test
    void valueDateRulesAreApplied() {
        rejected(service.handleEvent(event("X1", EventType.CREDIT, "10.00", 1, 2)), RejectionReason.VALUE_DATE_AFTER_BOOKED_DAY);
        rejected(service.handleEvent(event("X2", EventType.CREDIT, "10.00", 7, 1)), RejectionReason.VALUE_DATE_TOO_OLD);
        posted(service.handleEvent(event("X3", EventType.CREDIT, "10.00", 7, 2)));
    }

    @Test
    void sameRejectedRequestTwiceIsRecordedOnce() {
        final var first = assertInstanceOf(EventResult.Rejected.class,
                service.handleEvent(event("X1", EventType.CREDIT, "10.00", 1, 2))).rejection();

        assertSame(first, assertInstanceOf(EventResult.Rejected.class,
                service.handleEvent(event("X1", EventType.CREDIT, "10.00", 1, 2))).rejection());
        assertEquals(1, rejectedEventRepository.findAll().size());
    }
}
