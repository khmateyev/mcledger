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