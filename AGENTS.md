# AGENTS.md

Guidance for coding agents working in this repository.

## What this is

An in-memory account ledger core (no web layer, persistence, UI or database). `EventSimulatorTest`
replays the task's event stream (E1–E10, Day 1 to Day 6), closes each day and prints, per day and
account: closing ledger balance, fee assessments, authorization states and errors. Interest is
capitalized by the close of the day that ends an interest period (Day 6) and shows in its balance. Accounts: ACC-001 (AED) and ACC-002 (BHD).

## Layout

All code is in the `app` module, under `com.mcledger`:

- `AppContainer`: builds the config, repositories and services (the app's bean container).
- `money`: `Money`, `CurrencyCode`, `RemainderAllocation`.
- `config`: `ConfigService` (settings interface) and `FixedConfigService`.
- `dtos`: requests and results (`EventRequest`, `EventResult`, `DayCloseResult`).
- `models`: stored data (`Account`, `LedgerEntry`, `AuthEvent`, `RejectedEvent`).
- `repositories`: interface + `*InMemory` implementation per model.
- `services`: `LedgerService.handleEvent` (incoming events), `DayCloseService.closeDay` (fees, interest at period end).
- `services.events`: one handler per `EventType`, picked by `EventHandlerFactory`.

Keep it simple: add a method, field or check only when something uses it.

## Commands

```sh
./gradlew :app:test     # run all tests, including the event replay
./gradlew :app:build    # compile and test
```

Java 21 (Gradle toolchain). JUnit 5 (Jupiter).

## Design rules (do not break these)

- **Append-only.** Ledger entries, authorization events and rejected events are never updated or
  deleted. Corrections are new entries: a REVERSAL of an entry, a reversal of a fee (refund).
- **Idempotent.** Every write is keyed and a repeated key returns the stored record:
  - incoming events: the request id;
  - REVERSAL: the reversed ledger entry id (an entry is reversed at most once);
  - SETTLEMENT: the authorization id (an authorization is settled at most once);
  - fee: `FEE:<accountId>:<day>`; fee refund: the fee entry id;
  - interest credit: `INTEREST:<accountId>:<start>:<end>`.
- **No hidden rounding.** `Money` is always at its currency's precision (AED 2, BHD 3).
  `Money.of` rejects extra decimals; only `Money.rounded` and `Money.multiply` round, with an explicit
  mode. Never use `double`/`float` for amounts.
- **Interest:** periods are `interestPeriodDays` long, counted from the account's `openedOn`; closing
  a period's last day capitalizes it. Each day's accrual is rounded on its own; the credit is the
  exact sum of those rounded accruals and is never rounded again. Accruals use the closing ledger
  balance without the period's own credit (no compounding inside a period; earlier credits earn),
  positive days only.
- **Fees** are charged only for the day being closed, from its closing ledger balance (value date
  ≤ day), never retroactively. An earlier fee is refunded once its day, without the fee itself,
  is no longer negative.
- **Holds live outside the ledger.** They change the available balance, never the ledger balance.
  A settlement may not exceed its authorization and releases the whole hold.
- **No cross-currency arithmetic.** `Money` throws on mismatch; an event in another currency than
  its account is rejected.
- **Every tunable value comes from `ConfigService`** (overdraft fee, daily interest rate, hold expiry
  days, back-value limit, rounding mode, interest period days). No magic numbers in services.
- **Rejections are recorded, not thrown,** as `RejectedEvent`s. Exceptions are for programming
  errors (invalid arguments, unknown account, broken invariants).
- **Booked day comes from the event; value date may be in the past.** Days are closed explicitly
  by calling `DayCloseService`; there is no scheduler or clock.

## Code conventions

- Package layout as above; repositories are an interface plus `*InMemory`.
- Data classes with more than 2 properties use a builder (static factory such as
  `EventRequest.eventRequest()`, fluent setters, `build()`); with 1–2 properties, a record or static
  factory (e.g. `Money.of`).
- Validate in constructors; fail fast with a message that names the bad value.
- Tests sit in the package of the class they cover; name them after behavior, not methods.
- Keep amounts in tests as strings (`Money.of(AED, "250.00")`) so precision is visible.

## Commits and docs

- Small, real commits; never squash or rewrite history (the history is a deliverable).
- Do not commit unless asked.
- Keep the task's docs current when behavior changes: `README.md`, `AMBIGUITIES.md`, `NUMBERS.md`,
  `REJECTED.md`, `WORKLOG.md` (real timestamps, written by the human).
