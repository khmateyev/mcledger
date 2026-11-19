# NUMBERS

Set in `AppContainer` via `ConfigService`, unless noted.

- **Overdraft fee AED 25.00:** given by the task.
- **Overdraft fee BHD 2.560:** AED 25.00 at 1 USD = 3.6725 AED = 0.376 BHD (both pegs), rounded to 3 decimals.
- **Daily interest rate 0.0004 (AED, BHD):** the task's 0.04% per day; half would contradict it.
- **Interest period 6 days:** the task's window, Day 1 to Day 6; half (3) would capitalize twice instead of once at end of Day 6.
- **Hold expiry 7 days:** a typical pre-authorization hold (UAE banks publish 5–30 days); half (3–4) would expire holds before normal settlement arrives.
- **Max back-value 5 days:** E9 is valued 4 days back (Day 6 → Day 2), so at least 4 is needed, plus one day of margin; half (2) would reject E7 (3 days back) and E9.
- **Rounding HALF_UP:** the usual commercial rounding for money; HALF_EVEN would round 0.005 down to 0.00.
- **Precision AED 2, BHD 3** (`CurrencyCode`): ISO 4217 minor units, as the task states.
- **Instalment remainder FIRST** (`Money.split`, E10): the extra 0.001 goes to the first instalment, so 10.000 = 3.334 + 3.333 + 3.333.
