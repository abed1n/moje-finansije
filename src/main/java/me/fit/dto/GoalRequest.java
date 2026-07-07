package me.fit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalRequest(
        @NotBlank(message = "Naziv je obavezan") String name,
        @NotNull(message = "Ciljni iznos je obavezan") @Positive(message = "Ciljni iznos mora biti pozitivan") BigDecimal targetAmount,
        LocalDate deadline,
        Long accountId) {
}
