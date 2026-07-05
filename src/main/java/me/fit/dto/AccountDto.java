package me.fit.dto;

import me.fit.model.Account;
import me.fit.model.AccountType;

import java.math.BigDecimal;

public record AccountDto(Long id, String name, AccountType type, String currency,
                         BigDecimal balance, AccountDetailsDto details) {

    public static AccountDto from(Account account) {
        return new AccountDto(account.getId(), account.getName(), account.getType(),
                account.getCurrency(), account.getBalance(), AccountDetailsDto.from(account.getAccountDetails()));
    }
}
