package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import me.fit.dto.AuthResponse;
import me.fit.dto.GoogleTokenInfo;
import me.fit.dto.UserDto;
import me.fit.model.AuthProvider;
import me.fit.model.User;
import me.fit.rest.client.GoogleTokenInfoApi;
import me.fit.security.TokenService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Prijava/registracija preko Google naloga. Frontend posalje Google ID token,
// backend ga provjeri kod Google-a i dodatno potvrdi da je izdat bas ovoj aplikaciji.
@ApplicationScoped
public class GoogleAuthService {

    private static final Set<String> VALID_ISSUERS =
            Set.of("accounts.google.com", "https://accounts.google.com");

    @Inject
    EntityManager em;

    @Inject
    TokenService tokenService;

    @Inject
    CategoryService categoryService;

    @RestClient
    GoogleTokenInfoApi googleTokenInfo;

    @ConfigProperty(name = "google.client-id")
    Optional<String> clientId;

    public boolean isConfigured() {
        return clientId.isPresent() && !clientId.get().isBlank();
    }

    public String clientId() {
        return clientId.orElse("");
    }

    @Transactional
    public AuthResponse authenticate(String idToken) {
        if (!isConfigured()) {
            throw new ServerErrorException(
                    "Google prijava nije podešena na serveru.", Response.Status.SERVICE_UNAVAILABLE);
        }

        GoogleTokenInfo info;
        try {
            info = googleTokenInfo.verify(idToken);
        } catch (WebApplicationException e) {
            // Google je odbio token (neispravan ili istekao)
            throw unauthorized();
        }

        // Token mora biti izdat bas ovoj aplikaciji i od Google-a, sa potvrdjenim emailom
        if (info == null || !clientId().equals(info.aud())
                || info.iss() == null || !VALID_ISSUERS.contains(info.iss())
                || !"true".equalsIgnoreCase(info.emailVerified())
                || info.email() == null || info.email().isBlank()) {
            throw unauthorized();
        }
        if (info.exp() != null && Long.parseLong(info.exp()) < Instant.now().getEpochSecond()) {
            throw unauthorized();
        }

        String email = info.email().trim().toLowerCase();
        List<User> existing = em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email)
                .getResultList();

        User user;
        if (!existing.isEmpty()) {
            user = existing.getFirst();
            // Google je potvrdio vlasnistvo nad emailom
            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
            }
        } else {
            user = new User();
            user.setEmail(email);
            user.setName(info.name() != null && !info.name().isBlank() ? info.name().trim() : email);
            user.setProvider(AuthProvider.GOOGLE);
            user.setPasswordHash(null);
            user.setEmailVerified(true);
            em.persist(user);
            categoryService.seedDefaultCategories(user);
        }

        return new AuthResponse(tokenService.generateToken(user), UserDto.from(user));
    }

    private ClientErrorException unauthorized() {
        return new ClientErrorException(
                "Google prijava nije uspjela. Pokušajte ponovo.", Response.Status.UNAUTHORIZED);
    }
}
