package com.mcledger.dtos;

import com.mcledger.models.AuthEvent;
import com.mcledger.models.LedgerEntry;
import com.mcledger.models.RejectedEvent;

public sealed interface EventResult {

    record Posted(LedgerEntry entry) implements EventResult {
    }

    record Rejected(RejectedEvent rejection) implements EventResult {
    }

    record Authorized(AuthEvent authEvent) implements EventResult {
    }
}
