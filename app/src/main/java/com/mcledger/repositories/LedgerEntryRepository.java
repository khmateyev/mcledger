package com.mcledger.repositories;

import com.mcledger.models.LedgerEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository {
    Optional<LedgerEntry> findByRequestId(String requestId);
    Optional<LedgerEntry> findReversalOf(UUID entryId);
    List<LedgerEntry> findByAccountId(UUID accountId);
    LedgerEntry create(LedgerEntry entry);
}
