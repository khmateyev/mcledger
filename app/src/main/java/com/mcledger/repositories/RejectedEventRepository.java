package com.mcledger.repositories;

import com.mcledger.models.RejectedEvent;

import java.util.List;
import java.util.Optional;

public interface RejectedEventRepository {
    Optional<RejectedEvent> findByRequestId(String requestId);
    List<RejectedEvent> findAll();
    RejectedEvent create(RejectedEvent event);
}
