package com.mcledger.repositories;

import com.mcledger.models.LedgerEntry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Optional.ofNullable;

public class LedgerEntryRepositoryInMemory implements LedgerEntryRepository {

    private final List<LedgerEntry> db = new CopyOnWriteArrayList<>();
    private final Map<String, LedgerEntry> byRequestId = new ConcurrentHashMap<>();
    private final Map<UUID, LedgerEntry> reversalByEntryId = new ConcurrentHashMap<>();

    @Override
    public Optional<LedgerEntry> findByRequestId(String requestId) {
        return ofNullable(byRequestId.get(requestId));
    }

    @Override
    public Optional<LedgerEntry> findReversalOf(UUID entryId) {
        return ofNullable(reversalByEntryId.get(entryId));
    }

    @Override
    public List<LedgerEntry> findByAccountId(UUID accountId) {
        return db.stream().filter(it -> it.accountId().equals(accountId)).toList();
    }

    @Override
    public LedgerEntry create(LedgerEntry entry) {
        final var previous = byRequestId.putIfAbsent(entry.requestId(), entry);
        if (previous != null) {
            return previous;
        }
        entry.reversesEntryId().ifPresent(id -> reversalByEntryId.putIfAbsent(id, entry));
        db.add(entry);
        return entry;
    }
}
