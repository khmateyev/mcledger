# mcledger

In-memory account ledger core (Java 21, Gradle). See `AGENTS.md` for the layout and design rules.

## Replay the task's event stream E1–E10 and print each day's close, output is written to standard output stream
```sh
./gradlew :app:test --tests com.mcledger.EventSimulatorTest --rerun
```

## Run the test that fails on purpose against the design
```sh
./gradlew :app:failingTest --rerun
```

## Run all other tests
```sh
./gradlew :app:test
```