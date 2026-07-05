package me.fit.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ProfileUpdateRequest(
        @NotBlank(message = "Ime je obavezno") String name,
        String address,
        String phone,
        LocalDate dateOfBirth) {
}
