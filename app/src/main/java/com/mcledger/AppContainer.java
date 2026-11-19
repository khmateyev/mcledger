package com.mcledger;

import com.mcledger.config.ConfigService;
import com.mcledger.money.Money;
import com.mcledger.repositories.AccountRepository;
import com.mcledger.repositories.AccountRepositoryInMemory;
import com.mcledger.repositories.AuthEventRepository;
import com.mcledger.repositories.AuthEventRepositoryInMemory;
import com.mcledger.repositories.LedgerEntryRepository;
import com.mcledger.repositories.LedgerEntryRepositoryInMemory;
import com.mcledger.repositories.RejectedEventRepository;
import com.mcledger.repositories.RejectedEventRepositoryInMemory;
import com.mcledger.services.DayCloseService;
import com.mcledger.services.LedgerService;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.mcledger.config.FixedConfigService.fixedConfigService;
import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;

public class AppContainer {

    private final ConfigService configService = fixedConfigService()
        .overdraftFee(Money.of(AED, "25.00"))
        .overdraftFee(Money.of(BHD, "2.560"))
        .dailyInterestRate(AED, new BigDecimal("0.0004"))
        .dailyInterestRate(BHD, new BigDecimal("0.0004"))
        .holdExpiryDays(7)
        .maxBackValueDays(5)
        .roundingMode(RoundingMode.HALF_UP)
        .interestPeriodDays(6)
        .build();

    private final AccountRepository accountRepository = new AccountRepositoryInMemory();
    private final LedgerEntryRepository ledgerEntryRepository = new LedgerEntryRepositoryInMemory();
    private final AuthEventRepository authEventRepository = new AuthEventRepositoryInMemory();
    private final RejectedEventRepository rejectedEventRepository = new RejectedEventRepositoryInMemory();

    private final LedgerService ledgerService = new LedgerService(
        accountRepository, ledgerEntryRepository, authEventRepository, rejectedEventRepository, configService);

    private final DayCloseService dayCloseService = new DayCloseService(
        accountRepository, ledgerEntryRepository, authEventRepository, rejectedEventRepository, configService);

    public ConfigService configService() {
        return configService;
    }

    public AccountRepository accountRepository() {
        return accountRepository;
    }

    public LedgerEntryRepository ledgerEntryRepository() {
        return ledgerEntryRepository;
    }

    public AuthEventRepository authEventRepository() {
        return authEventRepository;
    }

    public RejectedEventRepository rejectedEventRepository() {
        return rejectedEventRepository;
    }

    public LedgerService ledgerService() {
        return ledgerService;
    }

    public DayCloseService dayCloseService() {
        return dayCloseService;
    }
}
