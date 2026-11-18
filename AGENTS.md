# AGENTS.md

Guidance for coding agents working in this repository.

## What this is

An in-memory account ledger core (no web layer, persistence, UI or database). A replay test
feeds a fixed event stream (E1–E10, Day 1 to Day 6) and prints, per day: closing ledger balance,
fee assessments, authorization states and errors. Accounts: ACC-001 (AED) and ACC-002 (BHD).

## Layout

- `app/`: the ledger. All new code goes here, under `com.mcledger.*`.

## Commands

```sh
./gradlew :app:test     # run the app tests
./gradlew :app:build    # compile and test
```

Java 21 (Gradle toolchain). JUnit 5 (Jupiter).

## Design rules (do not break these)

- **Append-only.** Ledger entries, authorization events and rejected events are never updated
  or deleted. Corrections are new entries (reversals, fee refunds, interest adjustments).
- **No hidden rounding.** `Money` is always at its currency's precision (AED 2, BHD 3).
  `Money.of` rejects extra decimals; only `Money.rounded` and `Money.multiply` round, and they take
  the rounding mode as an argument. Never use `double`/`float` for amounts.
- **No cross-currency arithmetic.** `Money` throws on mismatch; never convert implicitly.
- **Every tunable value comes from settings** (fee, interest rate, hold expiry days, back-value
  limit, settlement tolerance, rounding mode, instalment remainder). No magic numbers in services.
  Each one is documented in `NUMBERS.md` with why that value and not half of it.
- **Values in use are written onto records** (e.g. `expiresOn`, the fee amount), so changing
  settings never rewrites history.
- **Rejections are recorded, not thrown,** during replay. Exceptions are for programming errors
  (invalid arguments, broken invariants).
- **Holds live outside the ledger.** They change the available balance, never the ledger balance.

## Code conventions

- Immutable types: records or final classes, `final` locals, `List.copyOf` for returned lists.
- Data classes with more than 2 properties are built through a builder (static factory such as
  `LedgerEntry.ledgerEntry()`, fluent setters, `build()`), never a long positional constructor.
  Data classes with 1–2 properties use a constructor or static factory (e.g. `Money.of`).
- Validate in constructors; fail fast with a message that names the bad value.
- Tests sit next to the package they cover; name them after behavior, not methods.
- Keep amounts in tests as strings (`Money.of(AED, "250.00")`) so precision is visible.

## Commits and docs

- Small, real commits; never squash or rewrite history (the history is a deliverable).
- Do not commit unless asked.
- Keep these files current when behavior changes: `README.md` (how to run and read output),
  `AMBIGUITIES.md`, `NUMBERS.md`, `REJECTED.md` (refused criteria and abandoned approaches),
  `WORKLOG.md` (real timestamps, written by the human).
- Exactly one test is meant to fail against the design; it is tagged and excluded from the default
  test run. Do not "fix" it.
