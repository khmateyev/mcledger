package com.mcledger.services.events;

import com.mcledger.config.ConfigService;
import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;
import com.mcledger.dtos.EventType;
import com.mcledger.models.AuthEvent;
import com.mcledger.models.LedgerEntry;
import com.mcledger.models.RejectedEvent;
import com.mcledger.models.RejectionReason;
import com.mcledger.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

import static com.mcledger.config.FixedConfigService.fixedConfigService;
import static com.mcledger.dtos.EventRequest.eventRequest;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class HandlerFixtures {

    static final UUID ACC = UUID.fromString("00000000-0000-0000-0000-000000000001");

    static final ConfigService CONFIG = fixedConfigService()
            .overdraftFee(Money.of(AED, "25.00"))
            .dailyInterestRate(AED, new BigDecimal("0.0004"))
            .dailyInterestRate(BHD, new BigDecimal("0.0004"))
            .holdExpiryDays(7)
            .maxBackValueDays(5)
            .roundingMode(RoundingMode.HALF_UP)
            .interestPeriodDays(6)
            .build();

    private HandlerFixtures() {
    }

    static LocalDate day(int n) {
        return LocalDate.of(2026, 1, n);
    }

    static EventRequest request(String id, EventType type, String amount, int booked, int value) {
        return eventRequest().requestId(id).eventType(type).accountId(ACC)
                .amount(Money.of(AED, amount)).bookedDay(day(booked)).valueDate(day(value)).build();
    }

    static EventRequest reversal(String id, UUID entryId, int booked) {
        return eventRequest().requestId(id).eventType(EventType.REVERSAL).accountId(ACC)
                .reversesEntryId(entryId).bookedDay(day(booked)).valueDate(day(booked)).build();
    }

    static EventRequest settlement(String id, UUID authId, String amount, int day) {
        return eventRequest().requestId(id).eventType(EventType.SETTLEMENT).accountId(ACC)
                .authId(authId).amount(Money.of(AED, amount)).bookedDay(day(day)).valueDate(day(day)).build();
    }

    static LedgerEntry posted(EventResult result) {
        return assertInstanceOf(EventResult.Posted.class, result).entry();
    }

    static AuthEvent authorized(EventResult result) {
        return assertInstanceOf(EventResult.Authorized.class, result).authEvent();
    }

    static RejectedEvent rejected(EventResult result, RejectionReason reason) {
        final var rejection = assertInstanceOf(EventResult.Rejected.class, result).rejection();
        assertEquals(reason, rejection.reason());
        return rejection;
    }
}
