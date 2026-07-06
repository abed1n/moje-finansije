package me.fit.dto;

import me.fit.model.RecurringTransaction;
import me.fit.model.TransactionType;

import java.math.BigDecimal;

public record RecurringDto(
        Long id,
        BigDecimal amount,
        TransactionType type,
        String description,
        int dayOfMonth,
        boolean active,
        Long accountId,
        String accountName,
        Long categoryId,
        String categoryName) {

    public static RecurringDto from(RecurringTransaction rule) {
        return new RecurringDto(
                rule.getId(),
                rule.getAmount(),
                rule.getType(),
                rule.getDescription(),
                rule.getDayOfMonth(),
                rule.isActive(),
                rule.getAccount().getId(),
                rule.getAccount().getName(),
                rule.getCategory() != null ? rule.getCategory().getId() : null,
                rule.getCategory() != null ? rule.getCategory().getName() : null);
    }
}
