package com.mcledger;

import com.mcledger.config.ConfigService;
import com.mcledger.money.Money;
import com.mcledger.repositories.AccountRepository;
import com.mcledger.repositories.AccountRepositoryInMemory;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.LedgerEntryRepositoryInMemory;
import com.mcledger.repositories.RejectedEventRepository;
import com.mcledger.repositories.RejectedEventRepositoryInMemory;
import com.mcledger.services.LedgerService;

import java.math.BigDecimal;

import static com.mcledger.config.FixedConfigService.fixedConfigService;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;

public class AppContainer {

    private final ConfigService configService = fixedConfigService()
        .overdraftFee(Money.of(AED, "25.00"))
        .dailyInterestRate(AED, new BigDecimal("0.0004"))
        .dailyInterestRate(BHD, new BigDecimal("0.0004"))
        .holdExpiryDays(7)
        .maxBackValueDays(5)
        .build();

    private final AccountRepository accountRepository = new AccountRepositoryInMemory();
    private final LedgerEntryRepository ledgerEntryRepository = new LedgerEntryRepositoryInMemory();
    private final RejectedEventRepository rejectedEventRepository = new RejectedEventRepositoryInMemory();

    private final LedgerService ledgerService =
        new LedgerService(accountRepository, ledgerEntryRepository, rejectedEventRepository, configService);

    public ConfigService configService() {
        return configService;
    }

    public AccountRepository accountRepository() {
        return accountRepository;
    }

    public LedgerEntryRepository ledgerEntryRepository() {
        return ledgerEntryRepository;
    }

    public RejectedEventRepository rejectedEventRepository() {
        return rejectedEventRepository;
    }

    public LedgerService ledgerService() {
        return ledgerService;
    }
}
