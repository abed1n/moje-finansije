package me.fit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// Odgovor Google tokeninfo endpointa. Google je vec provjerio potpis i istek;
// mi jos provjeravamo aud (nas client id), iss i da je email potvrdjen.
public record GoogleTokenInfo(
        String aud,
        String sub,
        String iss,
        String email,
        @JsonProperty("email_verified") String emailVerified,
        String name,
        String exp) {
}
