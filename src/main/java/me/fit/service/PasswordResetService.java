package me.fit.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import me.fit.model.PasswordResetToken;
import me.fit.model.User;
import me.fit.security.Tokens;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Resetovanje zaboravljene lozinke: zahtjev salje token na email, potvrda ga
// razmjenjuje za novu lozinku. Token vazi jednom i kratko traje.
@ApplicationScoped
public class PasswordResetService {

    @Inject
    EntityManager em;

    @Inject
    EmailService emailService;

    @Inject
    RefreshTokenService refreshTokens;

    @ConfigProperty(name = "app.password-reset.token-minutes", defaultValue = "30")
    long tokenMinutes;

    // Namjerno ne otkrivamo da li nalog postoji - odgovor je isti u oba slucaja
    @Transactional
    public void requestReset(String email) {
        List<User> users = em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email.trim().toLowerCase())
                .getResultList();
        if (users.isEmpty()) {
            return;
        }
        User user = users.getFirst();
        // Poništi ranije zahtjeve da vrijedi samo najnoviji link
        em.createQuery("delete from PasswordResetToken t where t.user.id = :id")
                .setParameter("id", user.getId())
                .executeUpdate();
        String raw = Tokens.random();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(Tokens.sha256(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(tokenMinutes)));
        em.persist(token);
        emailService.sendPasswordReset(user.getEmail(), user.getName(), raw);
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken token = find(rawToken);
        if (token == null || token.isExpired()) {
            if (token != null) {
                em.remove(token);
            }
            throw new ClientErrorException(
                    "Link za resetovanje je neispravan ili je istekao", Response.Status.BAD_REQUEST);
        }
        User user = token.getUser();
        user.setPasswordHash(BcryptUtil.bcryptHash(newPassword));
        em.remove(token);
        // Nova lozinka poništava sve postojeće sesije
        refreshTokens.revokeAllForUser(user.getId());
    }

    private PasswordResetToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        List<PasswordResetToken> found = em.createNamedQuery(PasswordResetToken.GET_BY_HASH, PasswordResetToken.class)
                .setParameter("hash", Tokens.sha256(rawToken))
                .getResultList();
        return found.isEmpty() ? null : found.getFirst();
    }
}
