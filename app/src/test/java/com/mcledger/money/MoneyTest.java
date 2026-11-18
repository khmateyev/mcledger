package com.mcledger.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void amountIsNormalisedToCurrencyPrecision() {
        assertEquals(new BigDecimal("25.00"), Money.of(AED, "25").amount());
        assertEquals(new BigDecimal("10.000"), Money.of(BHD, "10").amount());
    }

    @Test
    void equalAmountsWithDifferentInputScaleAreEqual() {
        assertEquals(Money.of(AED, "25"), Money.of(AED, "25.00"));
    }

    @Test
    void tooManyDecimalsAreRejectedNotSilentlyRounded() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(AED, "0.005"));
        assertThrows(IllegalArgumentException.class, () -> Money.of(BHD, "0.0005"));
    }

    @Test
    void explicitRoundingUsesTheGivenMode() {
        assertEquals(Money.of(AED, "0.01"), Money.rounded(AED, new BigDecimal("0.005"), RoundingMode.HALF_UP));
        assertEquals(Money.of(AED, "0.00"), Money.rounded(AED, new BigDecimal("0.005"), RoundingMode.HALF_EVEN));
    }

    @Test
    void multiplyRoundsToCurrencyPrecision() {
        // 465.00 * 0.04% = 0.186 -> 0.19 (AED, 2 dp)
        assertEquals(Money.of(AED, "0.19"),
                Money.of(AED, "465.00").multiply(new BigDecimal("0.0004"), RoundingMode.HALF_UP));
        // 10.000 * 0.04% = 0.004 exactly (BHD, 3 dp)
        assertEquals(Money.of(BHD, "0.004"),
                Money.of(BHD, "10.000").multiply(new BigDecimal("0.0004"), RoundingMode.HALF_UP));
    }

    @Test
    void arithmetic() {
        final Money balance = Money.of(AED, "1200.00").minus(Money.of(AED, "950.00"));
        assertEquals(Money.of(AED, "250.00"), balance);
        assertEquals(Money.of(AED, "-370.00"), balance.minus(Money.of(AED, "620.00")));
        assertEquals(Money.of(AED, "-25.00"), Money.of(AED, "25.00").negate());
    }

    @Test
    void arithmeticAcrossCurrenciesIsRejected() {
        final Money aed = Money.of(AED, "1.00");
        final Money bhd = Money.of(BHD, "1.000");
        assertThrows(IllegalArgumentException.class, () -> aed.plus(bhd));
        assertThrows(IllegalArgumentException.class, () -> aed.minus(bhd));
        assertThrows(IllegalArgumentException.class, () -> aed.compareTo(bhd));
    }

    @Test
    void signChecks() {
        final Money zero = Money.zero(AED);
        assertTrue(zero.isZero());
        assertFalse(zero.isPositive());
        assertFalse(zero.isNegative());
        assertTrue(Money.of(AED, "-0.01").isNegative());
        assertTrue(Money.of(AED, "0.01").isPositive());
    }

    @Test
    void toStringShowsCurrencyAndFullPrecision() {
        assertEquals("BHD 3.334", Money.of(BHD, "3.334").toString());
        assertEquals("AED -370.00", Money.of(AED, "-370").toString());
    }
}
