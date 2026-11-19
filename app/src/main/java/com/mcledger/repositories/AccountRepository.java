package com.mcledger.repositories;

import com.mcledger.models.Account;

import java.util.UUID;

public interface AccountRepository {
    Account getById(UUID id);
    void create(Account account);
}
