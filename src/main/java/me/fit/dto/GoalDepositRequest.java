package me.fit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record GoalDepositRequest(
        @NotNull(message = "Iznos je obavezan") @Positive(message = "Iznos mora biti pozitivan") BigDecimal amount) {
}
