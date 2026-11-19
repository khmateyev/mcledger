# REJECTED

## Accepted acceptance criteria

**1. "The Day 2 closing ledger balance, evaluated at end of Day 5 and before any fee is assessed, is
AED −370.00."** The closing balance is by value date ("all entries with value_date ≤ that day"). At the
end of Day 5 the entries valued up to Day 2 are E1 +1200.00, E2 −950.00 and E7 −620.00 (booked Day 5,
valued Day 2): −370.00. E3 is a hold, E4/E5 are valued later, E9 arrives on Day 6, and the only fee is
valued Day 5. The Day 2 close itself showed 250.00 because E7 did not exist yet.

**3. "The Day 4 settlement of Auth-A must be accepted."** Auth-A was approved on Day 2 (250.00 ledger −
200.00 hold ≥ 0) and E5 settles 185.00, below the 200.00 authorized: 15 AED is returned to available balance.

**4. "Any settlement referencing an authorization ID not present in the ledger must be rejected and
the funds must not leave the account."
** E6 references Auth-Z, which was never authorized, so we reject it (described in AMBIGUITIES.md).

**5. "If Auth-B is approved, its hold reduces available balance but not ledger balance."
** point 5 at Non-negotiable rules, says available balance = ledger balance - active holds.
so, yes, if it was approved, then it would reduce available balance, but not ledger balance.
p.s. as per current implementation, Auth-B is rejected.

## Refused acceptance criteria

**2. "E7 causes exactly one overdraft fee to be assessed, on Day 2."**
E7 is booked on Day 5, so the Day 5 close is the first to see it.
So, there is one fee, but on Day 5.

**6. "After E9, all balances and fees return to their pre-E7 values."**
The ledger balance does return (Day 6 is 465.00 again before interest), but not everything does:
Auth-B was declined because of E7 and stays declined (decisions are not re-run), and the Day 5 fee is
not erased but refunded, so the fee and its refund both remain in the append-only ledger.

**7. "The three BHD instalments in E10 must each be BHD 3.334."**
3 × 3.334 = 10.002, not 10.000. The instalments are 3.334 + 3.333 + 3.333, summing to exactly 10.000.

**8. "If the rounded daily accruals do not sum to the capitalized total, the remainder is discarded."**
The credit is built as the sum of the rounded daily accruals, so they always sum exactly and there is
never a remainder. 
