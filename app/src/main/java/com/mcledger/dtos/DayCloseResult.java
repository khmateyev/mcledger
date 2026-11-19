package com.mcledger.dtos;

import com.mcledger.models.AuthEvent;
import com.mcledger.models.LedgerEntry;
import com.mcledger.models.RejectedEvent;
import com.mcledger.money.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public final class DayCloseResult {

    private final LocalDate closingDay;
    private final UUID accountId;
    private final Money closingLedgerBalance;
    private final List<LedgerEntry> feeAssessments;
    private final List<AuthEvent> authorizationStates;
    private final List<RejectedEvent> errors;

    private DayCloseResult(Builder builder) {
        this.closingDay = requireNonNull(builder.closingDay, "closingDay");
        this.accountId = requireNonNull(builder.accountId, "accountId");
        this.closingLedgerBalance = requireNonNull(builder.closingLedgerBalance, "closingLedgerBalance");
        this.feeAssessments = List.copyOf(builder.feeAssessments);
        this.authorizationStates = List.copyOf(builder.authorizationStates);
        this.errors = List.copyOf(builder.errors);
    }

    public LocalDate closingDay() {
        return closingDay;
    }

    public UUID accountId() {
        return accountId;
    }

    public Money closingLedgerBalance() {
        return closingLedgerBalance;
    }

    public List<LedgerEntry> feeAssessments() {
        return feeAssessments;
    }

    public List<AuthEvent> authorizationStates() {
        return authorizationStates;
    }

    public List<RejectedEvent> errors() {
        return errors;
    }

    public static Builder dayCloseResult() {
        return new Builder();
    }

    public static final class Builder {

        private LocalDate closingDay;
        private UUID accountId;
        private Money closingLedgerBalance;
        private List<LedgerEntry> feeAssessments = List.of();
        private List<AuthEvent> authorizationStates = List.of();
        private List<RejectedEvent> errors = List.of();

        private Builder() {
        }

        public Builder closingDay(LocalDate closingDay) {
            this.closingDay = closingDay;
            return this;
        }

        public Builder accountId(UUID accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder closingLedgerBalance(Money closingLedgerBalance) {
            this.closingLedgerBalance = closingLedgerBalance;
            return this;
        }

        public Builder feeAssessments(List<LedgerEntry> feeAssessments) {
            this.feeAssessments = feeAssessments;
            return this;
        }

        public Builder authorizationStates(List<AuthEvent> authorizationStates) {
            this.authorizationStates = authorizationStates;
            return this;
        }

        public Builder errors(List<RejectedEvent> errors) {
            this.errors = errors;
            return this;
        }

        public DayCloseResult build() {
            return new DayCloseResult(this);
        }
    }
}
