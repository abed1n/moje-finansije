package me.fit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Trenutna lozinka je obavezna") String currentPassword,
        @NotBlank(message = "Nova lozinka je obavezna") @Size(min = 6, message = "Nova lozinka mora imati bar 6 znakova") String newPassword) {
}
