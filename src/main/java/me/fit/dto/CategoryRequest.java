package me.fit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.fit.model.TransactionType;

public record CategoryRequest(
        @NotBlank(message = "Naziv kategorije je obavezan") String name,
        @NotNull(message = "Tip kategorije je obavezan") TransactionType type,
        String color,
        String icon) {
}
