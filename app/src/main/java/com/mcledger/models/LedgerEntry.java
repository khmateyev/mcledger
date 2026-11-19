package com.mcledger.models;

import com.mcledger.money.Money;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public final class LedgerEntry {

    private final UUID id;
    private final String requestId;
    private final UUID accountId;
    private final EntryType type;
    private final Money amount;
    private final LocalDate valueDate;
    private final LocalDate bookedDay;
    private final UUID reversesEntryId;

    private LedgerEntry(Builder builder) {
        this.id = requireNonNull(builder.id, "id");
        this.requestId = requireNonNull(builder.requestId, "requestId");
        this.accountId = requireNonNull(builder.accountId, "accountId");
        this.type = requireNonNull(builder.type, "type");
        this.amount = requireNonNull(builder.amount, "amount");
        this.valueDate = requireNonNull(builder.valueDate, "valueDate");
        this.bookedDay = requireNonNull(builder.bookedDay, "bookedDay");
        this.reversesEntryId = builder.reversesEntryId;
    }

    public UUID id() {
        return id;
    }

    public String requestId() {
        return requestId;
    }

    public UUID accountId() {
        return accountId;
    }

    public EntryType type() {
        return type;
    }

    public Money amount() {
        return amount;
    }

    public LocalDate valueDate() {
        return valueDate;
    }

    public LocalDate bookedDay() {
        return bookedDay;
    }

    public Optional<UUID> reversesEntryId() {
        return Optional.ofNullable(reversesEntryId);
    }

    public static Builder ledgerEntry() {
        return new Builder();
    }

    public static final class Builder {

        private UUID id;
        private String requestId;
        private UUID accountId;
        private EntryType type;
        private Money amount;
        private LocalDate valueDate;
        private LocalDate bookedDay;
        private UUID reversesEntryId;

        private Builder() {
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder accountId(UUID accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder type(EntryType type) {
            this.type = type;
            return this;
        }

        public Builder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder valueDate(LocalDate valueDate) {
            this.valueDate = valueDate;
            return this;
        }

        public Builder bookedDay(LocalDate bookedDay) {
            this.bookedDay = bookedDay;
            return this;
        }

        public Builder reversesEntryId(UUID reversesEntryId) {
            this.reversesEntryId = reversesEntryId;
            return this;
        }

        public LedgerEntry build() {
            return new LedgerEntry(this);
        }
    }
}
