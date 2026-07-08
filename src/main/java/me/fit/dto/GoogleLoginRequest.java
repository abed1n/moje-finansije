package me.fit.dto;

import jakarta.validation.constraints.NotBlank;

// ID token koji frontend dobije od Google Identity Services
public record GoogleLoginRequest(
        @NotBlank(message = "Google token je obavezan") String idToken) {
}
