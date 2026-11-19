package com.mcledger.services;

import com.mcledger.config.ConfigService;
import com.mcledger.dtos.DayCloseResult;
import com.mcledger.models.AuthEvent;
import com.mcledger.models.AuthEventType;
import com.mcledger.models.EntryType;
import com.mcledger.models.LedgerEntry;
import com.mcledger.money.Money;
import com.mcledger.money.CurrencyCode;
import com.mcledger.repositories.AccountRepository;
import com.mcledger.repositories.AuthEventRepository;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.RejectedEventRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.mcledger.dtos.DayCloseResult.dayCloseResult;
import static com.mcledger.models.AuthEvent.authEvent;
import static com.mcledger.models.LedgerEntry.ledgerEntry;
import static java.util.UUID.randomUUID;

/**
 * Closes one account's day. Fees are charged only for the day being closed (never retroactively),
 * and earlier fees are refunded once their day is no longer negative. Closing the same day again
 * changes nothing: the fee's idempotency key is account + day, a refund's is the fee entry id.
 */
public final class DayCloseService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuthEventRepository authEventRepository;
    private final RejectedEventRepository rejectedEventRepository;
    private final ConfigService configService;

    public DayCloseService(AccountRepository accountRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           AuthEventRepository authEventRepository,
                           RejectedEventRepository rejectedEventRepository,
                           ConfigService configService) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.authEventRepository = authEventRepository;
        this.rejectedEventRepository = rejectedEventRepository;
        this.configService = configService;
    }

    public DayCloseResult closeDay(UUID accountId, LocalDate day) {
        final var account = accountRepository.getById(accountId);
        final var currency = account.currency();

        expireHolds(accountId, day);
        refundFeesThatNoLongerApply(accountId, currency, day);
        assessFee(accountId, currency, day);
        interestPeriodStart(account.openedOn(), day)
            .ifPresent(periodStart -> capitalizeInterest(accountId, currency, periodStart, day));

        return dayCloseResult()
            .closingDay(day)
            .accountId(accountId)
            .closingLedgerBalance(balanceAsOf(accountId, currency, day))
            .feeAssessments(feeEntriesBookedOn(accountId, day))
            .authorizationStates(authEventRepository.findByAccountId(accountId).stream()
                .filter(it -> it.bookedDay().equals(day))
                .toList())
            .errors(rejectedEventRepository.findAll().stream()
                .filter(it -> it.accountId().equals(accountId) && it.bookedDay().equals(day))
                .toList())
            .build();
    }

    /**
     * Interest periods are {@code interestPeriodDays} long, counted from the opening day. Returns the
     * period's first day when {@code day} is the last day of a period, empty otherwise.
     */
    private Optional<LocalDate> interestPeriodStart(LocalDate openedOn, LocalDate day) {
        if (day.isBefore(openedOn)) {
            return Optional.empty();
        }
        final var periodDays = configService.interestPeriodDays();
        final var dayNumber = ChronoUnit.DAYS.between(openedOn, day) + 1;
        return dayNumber % periodDays == 0 ? Optional.of(day.minusDays(periodDays - 1)) : Optional.empty();
    }

    /**
     * Accrues daily interest on each day's positive closing ledger balance, each day rounded to the
     * currency's precision, and capitalizes their sum as one INTEREST credit on {@code endDate}.
     * The sum itself is never rounded, so the credit equals the rounded daily accruals exactly.
     * Balances come from the ledger as it is now (backdated entries included) and leave out this
     * period's own INTEREST credit, so interest does not compound before capitalization and closing
     * the same day again gives the same credit. Earlier periods' credits do earn interest.
     */
    private void capitalizeInterest(UUID accountId, CurrencyCode currency, LocalDate startDate, LocalDate endDate) {
        final var rate = configService.dailyInterestRate(currency);

        var total = Money.zero(currency);
        for (var day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            final var balance = balanceAsOf(accountId, currency, day,
                it -> it.type() != EntryType.INTEREST || it.valueDate().isBefore(startDate));
            if (balance.isPositive()) {
                total = total.plus(balance.multiply(rate, configService.roundingMode()));
            }
        }
        if (!total.isPositive()) {
            return;
        }

        ledgerEntryRepository.create(
            ledgerEntry()
                .id(randomUUID())
                .requestId("INTEREST:%s:%s:%s".formatted(accountId, startDate, endDate))
                .accountId(accountId)
                .type(EntryType.INTEREST)
                .amount(total)
                .valueDate(endDate)
                .bookedDay(endDate)
                .build()
        );
    }

    /** Releases every active hold whose expiry day has come; a later settlement is still accepted. */
    private void expireHolds(UUID accountId, LocalDate day) {
        final var authEvents = authEventRepository.findByAccountId(accountId);
        final var releasedAuthIds = authEvents.stream()
            .filter(it -> it.type() == AuthEventType.SETTLED || it.type() == AuthEventType.EXPIRED)
            .map(AuthEvent::authId)
            .collect(Collectors.toSet());

        authEvents.stream()
            .filter(it -> it.type() == AuthEventType.APPROVED && !releasedAuthIds.contains(it.authId()))
            .filter(it -> it.expiresOn().map(expiresOn -> !expiresOn.isAfter(day)).orElse(false))
            .forEach(hold -> authEventRepository.create(
                authEvent()
                    .id(randomUUID())
                    .requestId("EXPIRED:" + hold.authId())
                    .authId(hold.authId())
                    .accountId(accountId)
                    .type(AuthEventType.EXPIRED)
                    .amount(hold.amount())
                    .bookedDay(day)
                    .build()
            ));
    }

    private void refundFeesThatNoLongerApply(UUID accountId, CurrencyCode currency, LocalDate day) {
        final var activeFees = ledgerEntryRepository.findByAccountId(accountId).stream()
            .filter(it -> it.type() == EntryType.FEE)
            .filter(it -> it.valueDate().isBefore(day))
            .filter(it -> ledgerEntryRepository.findByRequestId(it.id().toString()).isEmpty())
            .sorted(Comparator.comparing(LedgerEntry::valueDate))
            .toList();

        for (LedgerEntry fee : activeFees) {
            final var balanceWithoutFee = balanceAsOf(accountId, currency, fee.valueDate()).minus(fee.amount());
            if (!balanceWithoutFee.isNegative()) {
                ledgerEntryRepository.create(
                    ledgerEntry()
                        .id(randomUUID())
                        .requestId(fee.id().toString())
                        .accountId(accountId)
                        .type(EntryType.REVERSAL)
                        .amount(fee.amount().negate())
                        .valueDate(fee.valueDate())
                        .bookedDay(day)
                        .reversesEntryId(fee.id())
                        .build()
                );
            }
        }
    }

    private void assessFee(UUID accountId, CurrencyCode currency, LocalDate day) {
        final var fee = configService.overdraftFee(currency);
        if (fee.isEmpty() || !balanceAsOf(accountId, currency, day).isNegative()) {
            return;
        }
        ledgerEntryRepository.create(
            ledgerEntry()
                .id(randomUUID())
                .requestId("FEE:%s:%s".formatted(accountId, day))
                .accountId(accountId)
                .type(EntryType.FEE)
                .amount(fee.get().negate())
                .valueDate(day)
                .bookedDay(day)
                .build()
        );
    }

    private Money balanceAsOf(UUID accountId, CurrencyCode currency, LocalDate day) {
        return balanceAsOf(accountId, currency, day, it -> true);
    }

    private Money balanceAsOf(UUID accountId, CurrencyCode currency, LocalDate day, Predicate<LedgerEntry> include) {
        return ledgerEntryRepository.findByAccountId(accountId).stream()
            .filter(it -> !it.valueDate().isAfter(day))
            .filter(include)
            .map(LedgerEntry::amount)
            .reduce(Money.zero(currency), Money::plus);
    }

    /** Fees charged and fees refunded on this day. */
    private List<LedgerEntry> feeEntriesBookedOn(UUID accountId, LocalDate day) {
        return ledgerEntryRepository.findByAccountId(accountId).stream()
            .filter(it -> it.bookedDay().equals(day))
            .filter(it -> it.type() == EntryType.FEE || it.reversesEntryId()
                .flatMap(ledgerEntryRepository::findById)
                .map(target -> target.type() == EntryType.FEE)
                .orElse(false))
            .toList();
    }
}
