package com.mcledger.models;

import com.mcledger.money.CurrencyCode;

import java.time.LocalDate;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public final class Account {

    private final UUID id;
    private final String number;
    private final CurrencyCode currency;
    private final LocalDate openedOn;

    private Account(Builder builder) {
        this.id = requireNonNull(builder.id, "id");
        this.number = requireNonNull(builder.number, "number");
        this.currency = requireNonNull(builder.currency, "currency");
        this.openedOn = requireNonNull(builder.openedOn, "openedOn");
    }

    public UUID id() {
        return id;
    }

    public String number() {
        return number;
    }

    public CurrencyCode currency() {
        return currency;
    }

    public LocalDate openedOn() {
        return openedOn;
    }

    public static Builder account() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id;
        private String number;
        private CurrencyCode currency;
        private LocalDate openedOn;

        private Builder() {
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder number(String number) {
            this.number = number;
            return this;
        }

        public Builder currency(CurrencyCode currency) {
            this.currency = currency;
            return this;
        }

        public Builder openedOn(LocalDate openedOn) {
            this.openedOn = openedOn;
            return this;
        }

        public Account build() {
            return new Account(this);
        }
    }
}
