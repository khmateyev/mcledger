package com.mcledger.config;

import com.mcledger.money.CurrencyCode;
import com.mcledger.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/** Settings fixed at construction and validated once, so bad config fails at startup. */
public final class FixedConfigService implements ConfigService {

    private final Map<CurrencyCode, Money> overdraftFees;
    private final Map<CurrencyCode, BigDecimal> dailyInterestRates;
    private final int holdExpiryDays;
    private final int maxBackValueDays;
    private final RoundingMode roundingMode;
    private final int interestPeriodDays;

    private FixedConfigService(Builder builder) {
        this.overdraftFees = Map.copyOf(builder.overdraftFees);
        this.dailyInterestRates = Map.copyOf(builder.dailyInterestRates);
        this.holdExpiryDays = requireNonNull(builder.holdExpiryDays, "holdExpiryDays");
        this.maxBackValueDays = requireNonNull(builder.maxBackValueDays, "maxBackValueDays");
        this.roundingMode = requireNonNull(builder.roundingMode, "roundingMode");
        this.interestPeriodDays = requireNonNull(builder.interestPeriodDays, "interestPeriodDays");

        overdraftFees.values().forEach(fee -> {
            if (!fee.isPositive()) {
                throw new IllegalArgumentException("overdraftFee must be > 0, was " + fee);
            }
        });
        for (CurrencyCode currency : CurrencyCode.values()) {
            final BigDecimal rate = dailyInterestRates.get(currency);
            if (rate == null) {
                throw new IllegalArgumentException("dailyInterestRate is missing for " + currency);
            }
            if (rate.signum() < 0) {
                throw new IllegalArgumentException("dailyInterestRate for " + currency + " must be >= 0, was " + rate);
            }
        }
        if (holdExpiryDays < 1) {
            throw new IllegalArgumentException("holdExpiryDays must be >= 1, was " + holdExpiryDays);
        }
        if (interestPeriodDays < 1) {
            throw new IllegalArgumentException("interestPeriodDays must be >= 1, was " + interestPeriodDays);
        }
        if (maxBackValueDays < 0) {
            throw new IllegalArgumentException("maxBackValueDays must be >= 0, was " + maxBackValueDays);
        }
    }

    public static Builder fixedConfigService() {
        return new Builder();
    }

    @Override
    public Optional<Money> overdraftFee(CurrencyCode currency) {
        return Optional.ofNullable(overdraftFees.get(requireNonNull(currency, "currency")));
    }

    @Override
    public BigDecimal dailyInterestRate(CurrencyCode currency) {
        return dailyInterestRates.get(requireNonNull(currency, "currency"));
    }

    @Override
    public int holdExpiryDays() {
        return holdExpiryDays;
    }

    @Override
    public int maxBackValueDays() {
        return maxBackValueDays;
    }

    @Override
    public RoundingMode roundingMode() {
        return roundingMode;
    }

    @Override
    public int interestPeriodDays() {
        return interestPeriodDays;
    }

    public static final class Builder {

        private final Map<CurrencyCode, Money> overdraftFees = new EnumMap<>(CurrencyCode.class);
        private final Map<CurrencyCode, BigDecimal> dailyInterestRates = new EnumMap<>(CurrencyCode.class);
        private Integer holdExpiryDays;
        private Integer maxBackValueDays;
        private RoundingMode roundingMode;
        private Integer interestPeriodDays;

        private Builder() {
        }

        public Builder overdraftFee(Money fee) {
            requireNonNull(fee, "fee");
            if (overdraftFees.putIfAbsent(fee.currency(), fee) != null) {
                throw new IllegalArgumentException("overdraftFee already set for " + fee.currency());
            }
            return this;
        }

        public Builder dailyInterestRate(CurrencyCode currency, BigDecimal rate) {
            requireNonNull(currency, "currency");
            requireNonNull(rate, "rate");
            if (dailyInterestRates.putIfAbsent(currency, rate) != null) {
                throw new IllegalArgumentException("dailyInterestRate already set for " + currency);
            }
            return this;
        }

        public Builder holdExpiryDays(int holdExpiryDays) {
            this.holdExpiryDays = holdExpiryDays;
            return this;
        }

        public Builder maxBackValueDays(int maxBackValueDays) {
            this.maxBackValueDays = maxBackValueDays;
            return this;
        }

        public Builder roundingMode(RoundingMode roundingMode) {
            this.roundingMode = roundingMode;
            return this;
        }

        public Builder interestPeriodDays(int interestPeriodDays) {
            this.interestPeriodDays = interestPeriodDays;
            return this;
        }

        public FixedConfigService build() {
            return new FixedConfigService(this);
        }
    }
}
