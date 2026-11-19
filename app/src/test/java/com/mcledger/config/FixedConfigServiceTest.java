package com.mcledger.config;

import com.mcledger.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.mcledger.config.FixedConfigService.fixedConfigService;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedConfigServiceTest {

    private static FixedConfigService.Builder valid() {
        return fixedConfigService()
                .overdraftFee(Money.of(AED, "25.00"))
                .dailyInterestRate(AED, new BigDecimal("0.0004"))
                .dailyInterestRate(BHD, new BigDecimal("0.0004"))
                .holdExpiryDays(7)
                .maxBackValueDays(5);
    }

    @Test
    void returnsConfiguredValues() {
        final ConfigService config = valid().build();

        assertEquals(Money.of(AED, "25.00"), config.overdraftFee(AED).orElseThrow());
        assertEquals(new BigDecimal("0.0004"), config.dailyInterestRate(AED));
        assertEquals(new BigDecimal("0.0004"), config.dailyInterestRate(BHD));
        assertEquals(7, config.holdExpiryDays());
        assertEquals(5, config.maxBackValueDays());
    }

    @Test
    void currencyWithoutOverdraftFeeHasNoFee() {
        assertTrue(valid().build().overdraftFee(BHD).isEmpty());
    }

    @Test
    void zeroBackValueDaysAndZeroInterestAreAllowed() {
        final ConfigService config = fixedConfigService()
                .dailyInterestRate(AED, BigDecimal.ZERO)
                .dailyInterestRate(BHD, BigDecimal.ZERO)
                .holdExpiryDays(1)
                .maxBackValueDays(0)
                .build();
        assertEquals(0, config.maxBackValueDays());
        assertEquals(BigDecimal.ZERO, config.dailyInterestRate(AED));
    }

    @Test
    void interestRateIsRequiredForEveryCurrency() {
        assertThrows(IllegalArgumentException.class, () -> fixedConfigService()
                .dailyInterestRate(AED, new BigDecimal("0.0004"))
                .holdExpiryDays(7)
                .maxBackValueDays(5)
                .build());
    }

    @Test
    void invalidValuesFailAtBuild() {
        assertThrows(IllegalArgumentException.class, () -> valid().holdExpiryDays(0).build());
        assertThrows(IllegalArgumentException.class, () -> valid().maxBackValueDays(-1).build());
        assertThrows(IllegalArgumentException.class, () -> fixedConfigService()
                .overdraftFee(Money.zero(AED))
                .dailyInterestRate(AED, BigDecimal.ZERO)
                .dailyInterestRate(BHD, BigDecimal.ZERO)
                .holdExpiryDays(7)
                .maxBackValueDays(5)
                .build());
        assertThrows(IllegalArgumentException.class, () -> fixedConfigService()
                .dailyInterestRate(AED, new BigDecimal("-0.0001"))
                .dailyInterestRate(BHD, BigDecimal.ZERO)
                .holdExpiryDays(7)
                .maxBackValueDays(5)
                .build());
    }

    @Test
    void missingRequiredValuesFailAtBuild() {
        assertThrows(NullPointerException.class, () -> fixedConfigService()
                .dailyInterestRate(AED, BigDecimal.ZERO)
                .dailyInterestRate(BHD, BigDecimal.ZERO)
                .maxBackValueDays(5)
                .build());
        assertThrows(NullPointerException.class, () -> fixedConfigService()
                .dailyInterestRate(AED, BigDecimal.ZERO)
                .dailyInterestRate(BHD, BigDecimal.ZERO)
                .holdExpiryDays(7)
                .build());
    }

    @Test
    void settingTheSameCurrencyTwiceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> fixedConfigService()
                .overdraftFee(Money.of(AED, "25.00"))
                .overdraftFee(Money.of(AED, "30.00")));
        assertThrows(IllegalArgumentException.class, () -> fixedConfigService()
                .dailyInterestRate(AED, BigDecimal.ONE)
                .dailyInterestRate(AED, BigDecimal.ZERO));
    }
}
