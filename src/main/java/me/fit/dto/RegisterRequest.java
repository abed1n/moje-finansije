package me.fit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Ime je obavezno") String name,
        @NotBlank(message = "Email je obavezan") @Email(message = "Email nije ispravan") String email,
        @NotBlank(message = "Lozinka je obavezna") @Size(min = 6, message = "Lozinka mora imati bar 6 znakova") String password) {
}
