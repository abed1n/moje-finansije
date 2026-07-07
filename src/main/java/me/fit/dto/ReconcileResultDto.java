package me.fit.dto;

import java.math.BigDecimal;

public record ReconcileResultDto(
        boolean adjusted,
        BigDecimal difference,
        BigDecimal newBalance) {
}
