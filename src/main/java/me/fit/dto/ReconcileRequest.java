package me.fit.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ReconcileRequest(
        @NotNull(message = "Stvarno stanje je obavezno") BigDecimal actualBalance) {
}
