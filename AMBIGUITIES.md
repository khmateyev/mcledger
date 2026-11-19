# AMBIGUITIES

## Settlement amount above the authorized amount

The task does not say what happens when a settlement exceeds its hold.

**Resolution:** a settlement is accepted only if its amount is less than or equal to the
authorized amount (E5: 185.00 against a 200.00 hold).
Anything above is rejected. This is intentionally simplified.

**Normally:** card networks let some merchants settle above the authorized amount (tips, fuel,
hotels). A real issuer would have a config (a percentage and/or absolute limit) that decides
whether such a settlement is accepted or parked for manual review.

## "Closing ledger balance" for daily interest

The task says interest is 0.04% per day "on the closing ledger balance" and capitalizes at the end
of Day 6, but not whether a day's balance is the one seen at that day's close or the one known at
Day 6, after backdated and late entries.

**Resolution:** each day's balance is taken by value date, as restated when interest is capitalized
(Day 6). Backdated entries (E7, reversed by E9) and late postings (E10) count on the days they are
valued. The task defines the closing ledger balance by value date ("all entries with value_date ≤
that day"), and criterion 1 evaluates a past day's closing balance later, so it is not a snapshot.

**Alternative:** snapshot balances at each close would give AED 0.84 instead of 1.03 for ACC-001
(Day 5 seen as -180.00 before E9) and only Day 6 interest for ACC-002 (E10 arrives after the Day 5
close).

**Normally:** banks calculate interest on value-dated balances and recalculate it when a backdated
entry arrives. Fees are different: they are charged only on the processing day, never retroactively.

## Rounding daily interest

**Resolution:** each day's accrual is rounded (HALF_UP) and the credit is their exact sum, as the task
requires ("rounded daily accruals must sum exactly to the capitalized total").

**Trade-off:** small balances drift from exact interest by up to half a minor unit per day:
12.50 AED for 4 days accrues 4 × 0.01 = 0.04 instead of the exact 0.02. Rounding once at the end
would be exact but the rounded daily accruals would no longer sum to the credit.

## E10 (Day 5) arrives after E9 (Day 6)

**Resolution:** E10 is accepted as booked on Day 5, not rejected as late. We assume ACC-002 (BHD) sits
in a Bahraini legal entity whose business day can differ from the UAE one (time zone, Friday–Saturday
weekend), so a Day 5 Bahrain posting may reach the stream after a UAE Day 6 event. It is picked up by
the Day 6 close and earns interest from Day 5 (its value date).

## How events arrive

**Resolution:** the task does not say how events arrive (message queue, RPC, HTTP), so there is no
transport layer: `LedgerService.handleEvent` is the API, with its implementation and tests. Any
transport can call it; idempotency by request id already covers redelivered messages.

## Overdraft fee timing

**Resolution:** a fee is charged only for the day being closed, from its closing ledger balance by
value date; it is never charged retroactively. A refund, by contrast, is backdated to the fee's day
once that day (without the fee) is no longer negative. So E7 (booked Day 5, valued Day 2) causes one
fee on Day 5, not on Day 2: Day 2 closed at 250.00, and Day 5 is the first close that sees E7.
E9 then makes Day 5 positive again, so the Day 6 close refunds the fee (valued Day 5).

## E6: settlement without an authorization (criterion 4)

**Resolution:** criterion 4 is accepted: E6 (Auth-Z, no preceding authorization) is rejected and no
money leaves the account. Despite there is practice of effectively a force post or offline transactions.
Also, despite authorization event could be missed. 
Initially, I had an idea of introducing config with 
an amount under which settlement is accepted despite auth is absent, otherwise park the settlement event for manual handling.


## How a day is closed

**Resolution:** the task does not say what closes a day: a scheduled job, an event, manually in BO, or
something else. 
That is why `DayCloseService.closeDay(accountId, day)` is the API, with
its implementation and tests, and any trigger can call it.

**Not validated:** closing day N does not check that day N-1 was closed, although that would be
reasonable (a real end-of-day batch closes days strictly in order). 
Currently, closing the same day again is safe: fees, refunds, expiries and interest are idempotent. 
However, maybe closing same day again should not be allowed.


## Authorizations that are never settled

**Resolution:** the task does not say what happens to an approved authorization that is never
settled. An approved hold stores `expiresOn` = approval day + `holdExpiryDays` (7 by current configs) and is released by an
EXPIRED event at the close of that day; until then it reduces the available balance. A settlement that
arrives after expiry is still accepted (late clearing). 

Also, since we accept late clearing, then reasonable to add a feature of disputing it by chargeback if needed.
