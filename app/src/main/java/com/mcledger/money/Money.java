package com.mcledger.money;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Always held at the currency's precision. No hidden rounding: only {@link #rounded} and
 * {@link #multiply} round, with an explicit mode.
 */
public record Money(CurrencyCode currency, BigDecimal amount) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
        try {
            amount = amount.setScale(currency.scale(), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "%s has more than %d decimal places for %s; round it explicitly with Money.rounded"
                            .formatted(amount.toPlainString(), currency.scale(), currency), e);
        }
    }

    public static Money of(CurrencyCode currency, BigDecimal amount) {
        return new Money(currency, amount);
    }

    public static Money of(CurrencyCode currency, String amount) {
        return new Money(currency, new BigDecimal(amount));
    }

    public static Money rounded(CurrencyCode currency, BigDecimal amount, RoundingMode mode) {
        Objects.requireNonNull(mode, "mode");
        return new Money(currency, amount.setScale(currency.scale(), mode));
    }

    public static Money zero(CurrencyCode currency) {
        return new Money(currency, BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(currency, amount.add(other.amount));
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(currency, amount.subtract(other.amount));
    }

    public Money negate() {
        return new Money(currency, amount.negate());
    }

    public Money multiply(BigDecimal factor, RoundingMode mode) {
        Objects.requireNonNull(factor, "factor");
        return rounded(currency, amount.multiply(factor), mode);
    }

    /** Parts differ by at most one minor unit and always sum back to this amount. */
    public List<Money> split(int parts, RemainderAllocation allocation) {
        if (parts < 1) {
            throw new IllegalArgumentException("parts must be at least 1, was " + parts);
        }
        Objects.requireNonNull(allocation, "allocation");

        final BigInteger units = amount.unscaledValue();
        final BigInteger[] divRem = units.divideAndRemainder(BigInteger.valueOf(parts));
        final BigInteger base = divRem[0];
        final int leftover = divRem[1].abs().intValueExact();
        final BigInteger step = BigInteger.valueOf(units.signum());

        final List<Money> result = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            final boolean getsExtra = allocation == RemainderAllocation.FIRST
                    ? i < leftover
                    : i >= parts - leftover;
            final BigInteger partUnits = getsExtra ? base.add(step) : base;
            result.add(new Money(currency, new BigDecimal(partUnits, currency.scale())));
        }
        return List.copyOf(result);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (currency != other.currency) {
            throw new IllegalArgumentException("currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }
}
