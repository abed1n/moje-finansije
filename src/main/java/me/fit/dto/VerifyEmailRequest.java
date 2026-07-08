package me.fit.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "Token je obavezan") String token) {
}
