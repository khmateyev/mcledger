package com.mcledger.repositories;

import com.mcledger.models.AuthEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthEventRepository {
    Optional<AuthEvent> findByRequestId(String requestId);
    List<AuthEvent> findByAccountId(UUID accountId);
    Optional<AuthEvent> findById(UUID id);
    AuthEvent create(AuthEvent event);
}
