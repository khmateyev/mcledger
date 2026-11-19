package com.mcledger.dtos;

import com.mcledger.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.mcledger.dtos.EventRequest.eventRequest;
import static com.mcledger.money.CurrencyCode.AED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventRequestTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 1);

    private static EventRequest.Builder request(EventType type) {
        return eventRequest().requestId("R1").eventType(type).accountId(UUID.randomUUID())
                .bookedDay(DAY).valueDate(DAY);
    }

    @Test
    void creditDebitAndAuthorizationNeedAPositiveAmount() {
        for (EventType type : new EventType[]{EventType.CREDIT, EventType.DEBIT, EventType.AUTHORIZATION}) {
            assertDoesNotThrow(() -> request(type).amount(Money.of(AED, "1.00")).build());
            assertThrows(IllegalArgumentException.class, () -> request(type).build());
            assertThrows(IllegalArgumentException.class, () -> request(type).amount(Money.zero(AED)).build());
            assertThrows(IllegalArgumentException.class, () -> request(type).amount(Money.of(AED, "-1.00")).build());
        }
    }

    @Test
    void reversalNeedsTheReversedEntryAndNoAmount() {
        assertDoesNotThrow(() -> request(EventType.REVERSAL).reversesEntryId(UUID.randomUUID()).build());
        assertThrows(IllegalArgumentException.class, () -> request(EventType.REVERSAL).build());
        assertThrows(IllegalArgumentException.class,
                () -> request(EventType.REVERSAL).reversesEntryId(UUID.randomUUID()).amount(Money.of(AED, "1.00")).build());
    }

    @Test
    void settlementNeedsTheAuthorizationAndAnAmount() {
        assertDoesNotThrow(() -> request(EventType.SETTLEMENT).authId(UUID.randomUUID()).amount(Money.of(AED, "1.00")).build());
        assertThrows(IllegalArgumentException.class,
                () -> request(EventType.SETTLEMENT).amount(Money.of(AED, "1.00")).build());
        assertThrows(IllegalArgumentException.class, () -> request(EventType.SETTLEMENT).authId(UUID.randomUUID()).build());
    }

    @Test
    void onlyReversalAndSettlementMayReferenceAnotherRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> request(EventType.DEBIT).amount(Money.of(AED, "1.00")).reversesEntryId(UUID.randomUUID()).build());
        assertThrows(IllegalArgumentException.class,
                () -> request(EventType.DEBIT).amount(Money.of(AED, "1.00")).authId(UUID.randomUUID()).build());
    }

    @Test
    void requiredFieldsAreChecked() {
        assertThrows(NullPointerException.class,
                () -> request(EventType.CREDIT).requestId(null).amount(Money.of(AED, "1.00")).build());
        assertThrows(NullPointerException.class,
                () -> request(EventType.CREDIT).accountId(null).amount(Money.of(AED, "1.00")).build());
        assertThrows(NullPointerException.class,
                () -> request(EventType.CREDIT).valueDate(null).amount(Money.of(AED, "1.00")).build());
    }

    @Test
    void requiredAccessorsFailWhenAbsent() {
        final var credit = request(EventType.CREDIT).amount(Money.of(AED, "1.00")).build();

        assertEquals(Money.of(AED, "1.00"), credit.requiredAmount());
        assertThrows(IllegalStateException.class, credit::requiredReversesEntryId);
        assertThrows(IllegalStateException.class, credit::requiredAuthId);
    }
}
