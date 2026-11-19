package com.mcledger.config;

import com.mcledger.money.CurrencyCode;
import com.mcledger.money.Money;

import java.math.BigDecimal;
import java.util.Optional;

public interface ConfigService {

    /** Empty when no overdraft fee is configured for the currency; no fee is charged then. */
    Optional<Money> overdraftFee(CurrencyCode currency);

    /** Fraction per day, e.g. 0.0004 for 0.04%. */
    BigDecimal dailyInterestRate(CurrencyCode currency);

    int holdExpiryDays();

    int maxBackValueDays();
}
