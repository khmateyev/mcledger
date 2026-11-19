package com.mcledger.models;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public final class RejectedEvent {

    private final String requestId;
    private final UUID accountId;
    private final LocalDate bookedDay;
    private final RejectionReason reason;
    private final String message;

    private RejectedEvent(Builder builder) {
        this.requestId = requireNonNull(builder.requestId, "requestId");
        this.accountId = requireNonNull(builder.accountId, "accountId");
        this.bookedDay = requireNonNull(builder.bookedDay, "bookedDay");
        this.reason = requireNonNull(builder.reason, "reason");
        this.message = requireNonNull(builder.message, "message");
    }

    public String requestId() {
        return requestId;
    }

    public UUID accountId() {
        return accountId;
    }

    public LocalDate bookedDay() {
        return bookedDay;
    }

    public RejectionReason reason() {
        return reason;
    }

    public String message() {
        return message;
    }

    public static Builder rejectedEvent() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RejectedEvent other)) return false;
        return requestId.equals(other.requestId)
                && accountId.equals(other.accountId)
                && bookedDay.equals(other.bookedDay)
                && reason == other.reason
                && message.equals(other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, accountId, bookedDay, reason, message);
    }

    @Override
    public String toString() {
        return "%s %s %s %s: %s".formatted(requestId, accountId, bookedDay, reason, message);
    }

    public static final class Builder {

        private String requestId;
        private UUID accountId;
        private LocalDate bookedDay;
        private RejectionReason reason;
        private String message;

        private Builder() {
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder accountId(UUID accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder bookedDay(LocalDate bookedDay) {
            this.bookedDay = bookedDay;
            return this;
        }

        public Builder reason(RejectionReason reason) {
            this.reason = reason;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public RejectedEvent build() {
            return new RejectedEvent(this);
        }
    }
}
