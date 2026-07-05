package me.fit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import me.fit.model.AccountType;

import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank(message = "Naziv računa je obavezan") String name,
        @NotNull(message = "Tip računa je obavezan") AccountType type,
        @NotBlank(message = "Valuta je obavezna") @Size(min = 3, max = 3, message = "Valuta mora biti troslovna oznaka") String currency,
        BigDecimal initialBalance,
        AccountDetailsDto details) {
}
