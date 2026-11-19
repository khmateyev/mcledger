# AMBIGUITIES

## Settlement amount above the authorized amount

The task does not say what happens when a settlement exceeds its hold.

**Resolution:** a settlement is accepted only if its amount is less than or equal to the
authorized amount (E5: 185.00 against a 200.00 hold).
Anything above is rejected. This is intentionally simplified.

**Normally:** card networks let some merchants settle above the authorized amount (tips, fuel,
hotels). A real issuer would have a config (a percentage and/or absolute limit) that decides
whether such a settlement is accepted or parked for manual review.
