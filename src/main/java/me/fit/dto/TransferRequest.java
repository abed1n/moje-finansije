package me.fit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferRequest(
        @NotNull(message = "Iznos je obavezan") @Positive(message = "Iznos mora biti pozitivan") BigDecimal amount,
        @NotNull(message = "Datum je obavezan") LocalDate date,
        String description,
        @NotNull(message = "Izvorni račun je obavezan") Long fromAccountId,
        @NotNull(message = "Odredišni račun je obavezan") Long toAccountId) {
}
