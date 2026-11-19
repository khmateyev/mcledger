package com.mcledger.models;

import com.mcledger.money.CurrencyCode;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

public record Account(UUID id, CurrencyCode currency) {

    public Account {
        requireNonNull(id, "id");
        requireNonNull(currency, "currency");
    }
}
