package me.fit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import me.fit.model.TransactionType;

import java.math.BigDecimal;

public record RecurringRequest(
        @NotNull(message = "Iznos je obavezan") @Positive(message = "Iznos mora biti pozitivan") BigDecimal amount,
        @NotNull(message = "Tip transakcije je obavezan") TransactionType type,
        String description,
        @NotNull(message = "Dan u mjesecu je obavezan")
        @Min(value = 1, message = "Dan mora biti između 1 i 31")
        @Max(value = 31, message = "Dan mora biti između 1 i 31") Integer dayOfMonth,
        @NotNull(message = "Račun je obavezan") Long accountId,
        Long categoryId) {
}
