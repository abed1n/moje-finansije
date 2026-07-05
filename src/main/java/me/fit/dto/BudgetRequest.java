package me.fit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import me.fit.model.BudgetPeriod;

import java.math.BigDecimal;
import java.util.List;

public record BudgetRequest(
        @NotBlank(message = "Naziv budžeta je obavezan") String name,
        @NotNull(message = "Limit je obavezan") @Positive(message = "Limit mora biti pozitivan") BigDecimal limitAmount,
        @NotNull(message = "Period je obavezan") BudgetPeriod period,
        List<Long> categoryIds) {
}
