package com.mcledger.models;

import com.mcledger.money.Money;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public final class AuthEvent {

    private final UUID id;
    private final String requestId;
    private final UUID authId;
    private final UUID accountId;
    private final AuthEventType type;
    private final Money amount;
    private final LocalDate bookedDay;
    private final LocalDate expiresOn;

    private AuthEvent(Builder builder) {
        this.id = requireNonNull(builder.id, "id");
        this.requestId = requireNonNull(builder.requestId, "requestId");
        this.authId = requireNonNull(builder.authId, "authId");
        this.accountId = requireNonNull(builder.accountId, "accountId");
        this.type = requireNonNull(builder.type, "type");
        this.amount = requireNonNull(builder.amount, "amount");
        this.bookedDay = requireNonNull(builder.bookedDay, "bookedDay");
        this.expiresOn = builder.expiresOn;
    }

    public UUID id() {
        return id;
    }

    public String requestId() {
        return requestId;
    }

    /** The approved authorization this event belongs to; its own id for APPROVED and DECLINED. */
    public UUID authId() {
        return authId;
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

    /** Set on APPROVED only: the hold counts until this day's close and is released by it. */
    public Optional<LocalDate> expiresOn() {
        return Optional.ofNullable(expiresOn);
    }

    public static Builder authEvent() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "AuthEvent[%s auth=%s %s %s %s %s]".formatted(requestId, authId, accountId, type, amount, bookedDay);
    }

    public static final class Builder {

        private UUID id;
        private String requestId;
        private UUID authId;
        private UUID accountId;
        private AuthEventType type;
        private Money amount;
        private LocalDate bookedDay;
        private LocalDate expiresOn;

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

        public Builder authId(UUID authId) {
            this.authId = authId;
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

        public Builder expiresOn(LocalDate expiresOn) {
            this.expiresOn = expiresOn;
            return this;
        }

        public AuthEvent build() {
            return new AuthEvent(this);
        }
    }
}
