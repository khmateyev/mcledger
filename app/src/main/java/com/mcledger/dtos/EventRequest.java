package com.mcledger.dtos;

import com.mcledger.money.Money;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class EventRequest {

    private final String requestId;
    private final EventType eventType;
    private final UUID accountId;
    private final LocalDate valueDate;
    private final LocalDate bookedDay;
    private final Optional<Money> amount;
    private final Optional<UUID> reversesEntryId;
    private final Optional<UUID> authId;

    private EventRequest(Builder builder) {
        this.requestId = requireNonNull(builder.requestId, "requestId");
        this.eventType = requireNonNull(builder.eventType, "eventType");
        this.accountId = requireNonNull(builder.accountId, "accountId");
        this.valueDate = requireNonNull(builder.valueDate, "valueDate");
        this.bookedDay = requireNonNull(builder.bookedDay, "bookedDay");
        this.amount = builder.amount;
        this.reversesEntryId = builder.reversesEntryId;
        this.authId = builder.authId;

        validate();
    }

    private void validate() {
        if ((eventType == EventType.SETTLEMENT) != authId.isPresent()) {
            throw new IllegalArgumentException("authId has to be present for SETTLEMENT only");
        }
        if (eventType == EventType.REVERSAL) {
            if (reversesEntryId.isEmpty()) {
                throw new IllegalArgumentException("reversesEntryId has to be present for REVERSAL");
            }
            if (amount.isPresent()) {
                throw new IllegalArgumentException("REVERSAL takes its amount from the reversed entry");
            }
        } else {
            if (reversesEntryId.isPresent()) {
                throw new IllegalArgumentException("only REVERSAL may reference a ledger entry");
            }
            if (amount.isEmpty() || !amount.get().isPositive()) {
                throw new IllegalArgumentException("positive amount has to be present for " + eventType);
            }
        }
    }

    public static Builder eventRequest() {
        return new Builder();
    }

    public String getRequestId() {
        return requestId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public LocalDate getBookedDay() {
        return bookedDay;
    }

    public Optional<Money> getAmount() {
        return amount;
    }

    public Money requiredAmount() {
        return getAmount()
                .orElseThrow(() -> new IllegalStateException("amount is required for " + eventType));
    }

    public Optional<UUID> getReversesEntryId() {
        return reversesEntryId;
    }

    public UUID requiredReversesEntryId() {
        return getReversesEntryId()
                .orElseThrow(() -> new IllegalStateException("reversesEntryId is required for REVERSAL"));
    }

    public Optional<UUID> getAuthId() {
        return authId;
    }

    public UUID requiredAuthId() {
        return getAuthId()
                .orElseThrow(() -> new IllegalStateException("authId is required for SETTLEMENT"));
    }

    public static final class Builder {

        private String requestId;
        private EventType eventType;
        private UUID accountId;
        private LocalDate valueDate;
        private LocalDate bookedDay;
        private Optional<Money> amount = Optional.empty();
        private Optional<UUID> reversesEntryId = Optional.empty();
        private Optional<UUID> authId = Optional.empty();

        private Builder() {
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder eventType(EventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder accountId(UUID accountId) {
            this.accountId = accountId;
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

        public Builder amount(Optional<Money> amount) {
            this.amount = amount;
            return this;
        }

        public Builder amount(Money amount) {
            return amount(ofNullable(amount));
        }

        public Builder reversesEntryId(Optional<UUID> reversesEntryId) {
            this.reversesEntryId = reversesEntryId;
            return this;
        }

        public Builder reversesEntryId(UUID reversesEntryId) {
            return reversesEntryId(ofNullable(reversesEntryId));
        }

        public Builder authId(UUID authId) {
            this.authId = ofNullable(authId);
            return this;
        }

        public EventRequest build() {
            return new EventRequest(this);
        }
    }
}
