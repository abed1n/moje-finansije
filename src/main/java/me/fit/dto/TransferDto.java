package me.fit.dto;

import me.fit.model.Transfer;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferDto(
        Long id,
        BigDecimal amount,
        LocalDate date,
        String description,
        Long fromAccountId,
        String fromAccountName,
        Long toAccountId,
        String toAccountName) {

    public static TransferDto from(Transfer transfer) {
        return new TransferDto(
                transfer.getId(),
                transfer.getAmount(),
                transfer.getDate(),
                transfer.getDescription(),
                transfer.getFromAccount().getId(),
                transfer.getFromAccount().getName(),
                transfer.getToAccount().getId(),
                transfer.getToAccount().getName());
    }
}
