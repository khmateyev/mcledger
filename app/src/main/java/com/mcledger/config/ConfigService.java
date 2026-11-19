package com.mcledger.config;

import com.mcledger.money.CurrencyCode;
import com.mcledger.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public interface ConfigService {

    /** Empty when no overdraft fee is configured for the currency; no fee is charged then. */
    Optional<Money> overdraftFee(CurrencyCode currency);

    /** Fraction per day, e.g. 0.0004 for 0.04%. */
    BigDecimal dailyInterestRate(CurrencyCode currency);

    int holdExpiryDays();

    int maxBackValueDays();

    /** Used wherever an amount is rounded to its currency's precision (daily interest). */
    RoundingMode roundingMode();

    /** Length of an interest period, counted from the account's opening day; interest is capitalized on its last day. */
    int interestPeriodDays();
}
