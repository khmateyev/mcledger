package com.mcledger.models;

import com.mcledger.money.Money;

import java.time.LocalDate;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public final class AuthEvent {

    private final UUID id;
    private final String requestId;
    private final String authRequestId;
    private final UUID accountId;
    private final AuthEventType type;
    private final Money amount;
    private final LocalDate bookedDay;

    private AuthEvent(Builder builder) {
        this.id = requireNonNull(builder.id, "id");
        this.requestId = requireNonNull(builder.requestId, "requestId");
        this.authRequestId = requireNonNull(builder.authRequestId, "authRequestId");
        this.accountId = requireNonNull(builder.accountId, "accountId");
        this.type = requireNonNull(builder.type, "type");
        this.amount = requireNonNull(builder.amount, "amount");
        this.bookedDay = requireNonNull(builder.bookedDay, "bookedDay");
    }

    public UUID id() {
        return id;
    }

    public String requestId() {
        return requestId;
    }

    public String authRequestId() {
        return authRequestId;
    }

    public UUID accountId() {
        return accountId;
    }

    public AuthEventType type() {
        return type;
    }

    public Money amount() {
        return amount;
    }

    public LocalDate bookedDay() {
        return bookedDay;
    }

    public static Builder authEvent() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "AuthEvent[%s auth=%s %s %s %s %s]".formatted(requestId, authRequestId, accountId, type, amount, bookedDay);
    }

    public static final class Builder {

        private UUID id;
        private String requestId;
        private String authRequestId;
        private UUID accountId;
        private AuthEventType type;
        private Money amount;
        private LocalDate bookedDay;

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

        public Builder authRequestId(String authRequestId) {
            this.authRequestId = authRequestId;
            return this;
        }

        public Builder accountId(UUID accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder type(AuthEventType type) {
            this.type = type;
            return this;
        }

        public Builder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public Builder bookedDay(LocalDate bookedDay) {
            this.bookedDay = bookedDay;
            return this;
        }

        public AuthEvent build() {
            return new AuthEvent(this);
        }
    }
}
