package com.mcledger.repositories;

import com.mcledger.models.Account;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

public class AccountRepositoryInMemory implements AccountRepository {

    private final Map<UUID, Account> db = new ConcurrentHashMap<>();

    @Override
    public Account getById(UUID id) {
        return requireNonNull(db.get(id), () -> "Account is not found by ID: %s".formatted(id));
    }

    @Override
    public void create(Account account) {
        if (db.putIfAbsent(account.id(), account) != null) {
            throw new UnsupportedOperationException("The method can only create an account");
        }
    }
}
