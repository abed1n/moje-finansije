package me.fit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import me.fit.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransactionRequest(
        @NotNull(message = "Iznos je obavezan") @Positive(message = "Iznos mora biti pozitivan") BigDecimal amount,
        @NotNull(message = "Datum je obavezan") LocalDate date,
        @NotNull(message = "Tip transakcije je obavezan") TransactionType type,
        String description,
        @NotNull(message = "Račun je obavezan") Long accountId,
        Long categoryId,
        List<String> tags) {
}
