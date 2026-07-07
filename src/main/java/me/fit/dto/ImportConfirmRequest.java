package me.fit.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import me.fit.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ImportConfirmRequest(
        @NotNull(message = "Račun je obavezan") Long accountId,
        boolean learnRules,
        @NotEmpty(message = "Nema redova za uvoz") List<@Valid Row> rows) {

    public record Row(
            @NotNull LocalDate date,
            String description,
            @NotNull @Positive BigDecimal amount,
            @NotNull TransactionType type,
            Long categoryId,
            // Ako je postavljen, red je prebacivanje na/sa ovog racuna umjesto transakcije
            Long transferAccountId) {
    }
}
