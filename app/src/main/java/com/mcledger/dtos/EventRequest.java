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
    private final Optional<String> reversesRequestId;

    private EventRequest(Builder builder) {
        this.requestId = requireNonNull(builder.requestId, "requestId");
        this.eventType = requireNonNull(builder.eventType, "eventType");
        this.accountId = requireNonNull(builder.accountId, "accountId");
        this.valueDate = requireNonNull(builder.valueDate, "valueDate");
        this.bookedDay = requireNonNull(builder.bookedDay, "bookedDay");
        this.amount = builder.amount;
        this.reversesRequestId = builder.reversesRequestId;

        validate();
    }

    private void validate() {
        if (eventType == EventType.REVERSAL) {
            if (reversesRequestId.isEmpty()) {
                throw new IllegalArgumentException("reversesRequestId has to be present for REVERSAL");
            }
            if (amount.isPresent()) {
                throw new IllegalArgumentException("REVERSAL takes its amount from the reversed entry");
            }
        } else {
            if (reversesRequestId.isPresent()) {
                throw new IllegalArgumentException("only REVERSAL may reference another request");
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

    public Optional<String> getReversesRequestId() {
        return reversesRequestId;
    }

    public String requiredReversesRequestId() {
        return getReversesRequestId()
                .orElseThrow(() -> new IllegalStateException("reversesRequestId is required for REVERSAL"));
    }

    public static final class Builder {

        private String requestId;
        private EventType eventType;
        private UUID accountId;
        private LocalDate valueDate;
        private LocalDate bookedDay;
        private Optional<Money> amount = Optional.empty();
        private Optional<String> reversesRequestId = Optional.empty();

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

        public Builder reversesRequestId(Optional<String> reversesRequestId) {
            this.reversesRequestId = reversesRequestId;
            return this;
        }

        public Builder reversesRequestId(String reversesRequestId) {
            return reversesRequestId(ofNullable(reversesRequestId));
        }

        public EventRequest build() {
            return new EventRequest(this);
        }
    }
}
