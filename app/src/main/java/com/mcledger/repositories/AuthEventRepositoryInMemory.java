package com.mcledger.repositories;

import com.mcledger.models.AuthEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Optional.ofNullable;

public class AuthEventRepositoryInMemory implements AuthEventRepository {

    private final List<AuthEvent> db = new CopyOnWriteArrayList<>();
    private final Map<String, AuthEvent> byRequestId = new ConcurrentHashMap<>();
    private final Map<UUID, AuthEvent> byId = new ConcurrentHashMap<>();

    @Override
    public Optional<AuthEvent> findByRequestId(String requestId) {
        return ofNullable(byRequestId.get(requestId));
    }

    @Override
    public List<AuthEvent> findByAccountId(UUID accountId) {
        return db.stream().filter(it -> it.accountId().equals(accountId)).toList();
    }

    @Override
    public Optional<AuthEvent> findById(UUID id) {
        return ofNullable(byId.get(id));
    }

    @Override
    public AuthEvent create(AuthEvent event) {
        final var previous = byRequestId.putIfAbsent(event.requestId(), event);
        if (previous != null) {
            return previous;
        }
        byId.put(event.id(), event);
        db.add(event);
        return event;
    }
}
