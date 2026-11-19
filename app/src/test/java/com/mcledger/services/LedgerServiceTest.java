package com.mcledger.services;

import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;
import com.mcledger.dtos.EventType;
import com.mcledger.models.Account;
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
    private final RejectedEventRepository rejectedEventRepository = new RejectedEventRepositoryInMemory();
    private final LedgerService service = new LedgerService(accountRepository, ledgerEntryRepository, rejectedEventRepository,
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

    private static LocalDate day(int n) {
        return LocalDate.of(2026, 1, n);
    }

    private static EventRequest event(String id, EventType type, String amount, int booked, int value) {
        return eventRequest().requestId(id).eventType(type).accountId(ACC)
                .amount(Money.of(AED, amount)).bookedDay(day(booked)).valueDate(day(value)).build();
    }

    private static EventRequest reversal(String id, String reversesId, int booked) {
        return eventRequest().requestId(id).eventType(EventType.REVERSAL).accountId(ACC)
                .reversesRequestId(reversesId).bookedDay(day(booked)).valueDate(day(booked)).build();
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

        final var e9 = posted(service.handleEvent(reversal("E9", "E7", 6)));

        assertEquals(Money.of(AED, "620.00"), e9.amount());
        assertEquals(day(2), e9.valueDate());
        assertEquals(day(6), e9.bookedDay());
        assertEquals(e7.id(), e9.reversesEntryId().orElseThrow());
        assertEquals(Money.of(AED, "250.00"), balanceAsOf(2));
    }

    @Test
    void sameReversalTwiceIsPostedOnce() {
        service.handleEvent(event("E7", EventType.DEBIT, "620.00", 5, 2));
        final var e9 = posted(service.handleEvent(reversal("E9", "E7", 6)));

        assertSame(e9, posted(service.handleEvent(reversal("E9", "E7", 6))));
        assertEquals(2, ledgerEntryRepository.findByAccountId(ACC).size());
    }

    @Test
    void secondReversalOfTheSameEntryIsRejected() {
        service.handleEvent(event("E7", EventType.DEBIT, "620.00", 5, 2));
        service.handleEvent(reversal("E9", "E7", 6));

        rejected(service.handleEvent(reversal("E9b", "E7", 6)), RejectionReason.ALREADY_REVERSED);
    }

    @Test
    void reversalOfUnknownRequestIsRejected() {
        rejected(service.handleEvent(reversal("R1", "E99", 6)), RejectionReason.ENTRY_NOT_FOUND);
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
