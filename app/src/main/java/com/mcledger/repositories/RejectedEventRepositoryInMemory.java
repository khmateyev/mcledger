package com.mcledger.repositories;

import com.mcledger.models.RejectedEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Optional.ofNullable;

public class RejectedEventRepositoryInMemory implements RejectedEventRepository {

    private final List<RejectedEvent> db = new CopyOnWriteArrayList<>();
    private final Map<String, RejectedEvent> byRequestId = new ConcurrentHashMap<>();

    @Override
    public Optional<RejectedEvent> findByRequestId(String requestId) {
        return ofNullable(byRequestId.get(requestId));
    }

    @Override
    public List<RejectedEvent> findAll() {
        return List.copyOf(db);
    }

    @Override
    public RejectedEvent create(RejectedEvent event) {
        final var previous = byRequestId.putIfAbsent(event.requestId(), event);
        if (previous != null) {
            return previous;
        }
        db.add(event);
        return event;
    }
}
