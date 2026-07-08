package me.fit.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import me.fit.model.RefreshToken;
import me.fit.model.User;
import me.fit.security.Tokens;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Izdavanje, rotacija i opoziv refresh tokena. Sama vrijednost tokena se vraca
// pozivaocu (ide u kolacic), a u bazi ostaje samo njen SHA-256 hash.
@ApplicationScoped
public class RefreshTokenService {

    @Inject
    EntityManager em;

    @ConfigProperty(name = "app.jwt.refresh-token-days", defaultValue = "30")
    long refreshDays;

    // Novi refresh token za korisnika; vraca sirovu vrijednost za kolacic
    @Transactional
    public String issue(Long userId) {
        String raw = Tokens.random();
        RefreshToken token = new RefreshToken();
        token.setUser(em.getReference(User.class, userId));
        token.setTokenHash(Tokens.sha256(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshDays)));
        em.persist(token);
        return raw;
    }

    // Provjeri stari token, ponisti ga i izdaj novi (rotacija). Vraca id korisnika i novu sirovu vrijednost.
    @Transactional
    public Rotated rotate(String rawToken) {
        RefreshToken existing = find(rawToken);
        if (existing == null || existing.isExpired()) {
            if (existing != null) {
                em.remove(existing);
            }
            throw new ClientErrorException("Sesija je istekla, prijavite se ponovo", Response.Status.UNAUTHORIZED);
        }
        Long userId = existing.getUser().getId();
        em.remove(existing);
        String raw = issue(userId);
        return new Rotated(userId, raw);
    }

    @Transactional
    public void revoke(String rawToken) {
        RefreshToken existing = find(rawToken);
        if (existing != null) {
            em.remove(existing);
        }
    }

    // Ponisti sve sesije korisnika (npr. nakon reset lozinke)
    @Transactional
    public void revokeAllForUser(Long userId) {
        em.createQuery("delete from RefreshToken t where t.user.id = :id")
                .setParameter("id", userId)
                .executeUpdate();
    }

    // Povremeno brise istekle tokene da tabela ne raste
    @Scheduled(every = "6h")
    @Transactional
    void cleanup() {
        em.createQuery("delete from RefreshToken t where t.expiresAt < :now")
                .setParameter("now", Instant.now())
                .executeUpdate();
    }

    public long refreshDays() {
        return refreshDays;
    }

    private RefreshToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        List<RefreshToken> found = em.createNamedQuery(RefreshToken.GET_BY_HASH, RefreshToken.class)
                .setParameter("hash", Tokens.sha256(rawToken))
                .getResultList();
        return found.isEmpty() ? null : found.getFirst();
    }

    public record Rotated(Long userId, String rawToken) {
    }
}
