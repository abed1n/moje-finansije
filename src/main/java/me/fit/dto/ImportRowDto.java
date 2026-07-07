package me.fit.dto;

import me.fit.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

// Jedan red izvoda u pregledu prije uvoza
public record ImportRowDto(
        int line,
        LocalDate date,
        String description,
        BigDecimal amount,
        TransactionType type,
        Long suggestedCategoryId,
        String suggestedCategoryName,
        boolean duplicate,
        boolean possibleTransfer) {
}
