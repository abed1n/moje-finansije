package me.fit.dto;

import me.fit.model.AccountDetails;

import java.time.LocalDate;

public record AccountDetailsDto(String accountNumber, String bankName, LocalDate openedDate) {

    public static AccountDetailsDto from(AccountDetails details) {
        if (details == null) {
            return null;
        }
        return new AccountDetailsDto(details.getAccountNumber(), details.getBankName(), details.getOpenedDate());
    }

    public AccountDetails toEntity() {
        AccountDetails details = new AccountDetails();
        details.setAccountNumber(accountNumber);
        details.setBankName(bankName);
        details.setOpenedDate(openedDate);
        return details;
    }
}
