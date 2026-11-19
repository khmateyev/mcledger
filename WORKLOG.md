# WORKLOG

## 2026-11-19 18:20

- gradle init project with java 21
- added basic models such as money, currency

## 2026-11-19 18:50
- add ConfigService. and FixedConfigService with hardcoded condigs.
the idea is to be able to load configs from anywhere - pass to the app at startup, from db or api call.

## 2026-11-19 19:50
add basic impl of LedgerService, with EventHandlerFactory having CREDIT, DEBIT and REVERSAL

## 2026-11-19 20:04
add EventSimulatorTest replaying events from task

## 2026-11-19 20:19
implement AuthorizationEventHandler

## 2026-11-19 20:23
implement SettlementEventHandler

## 2026-11-19 20:40
use original entryId as idempotency key for reversal and settlement 
add unit tests 

## 2026-11-19 20:42
add DayCloseService with empty method body

## 2026-11-19 21:34
implement DayCloseService, update EventSimulatorTest

## 2026-11-19 23:40
expire holds

## 2026-11-20 01:00
write ambiguities and numbers

## 2026-11-20 01:28
write rejected.md

## 2026-11-20 01:50
add failing test
