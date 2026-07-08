package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import me.fit.model.User;
import me.fit.security.Tokens;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Potvrda email adrese: pri registraciji se salje token na email, a klik na
// link ga razmjenjuje za potvrdu. Hash tokena i rok vazenja se cuvaju na korisniku.
@ApplicationScoped
public class EmailVerificationService {

    @Inject
    EntityManager em;

    @Inject
    EmailService emailService;

    @ConfigProperty(name = "app.email-verification.token-hours", defaultValue = "24")
    long tokenHours;

    // Generise token, sacuva mu hash i rok na korisniku i posalje email s linkom
    @Transactional
    public void sendFor(Long userId) {
        User user = em.find(User.class, userId);
        if (user == null || user.isEmailVerified()) {
            return;
        }
        String raw = Tokens.random();
        user.setEmailVerificationToken(Tokens.sha256(raw));
        user.setEmailVerificationExpires(Instant.now().plus(Duration.ofHours(tokenHours)));
        emailService.sendVerification(user.getEmail(), user.getName(), raw);
    }

    // Ponovno slanje po email adresi (za neprijavljenog korisnika sa login ekrana).
    // Namjerno tiho: ne otkriva postoji li nalog niti da li je vec potvrdjen.
    @Transactional
    public void resendByEmail(String email) {
        List<User> users = em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email.trim().toLowerCase())
                .getResultList();
        if (users.isEmpty() || users.getFirst().isEmailVerified()) {
            return;
        }
        sendFor(users.getFirst().getId());
    }

    @Transactional
    public void verify(String rawToken) {
        User user = findByToken(rawToken);
        if (user == null) {
            throw new ClientErrorException(
                    "Link za potvrdu je neispravan ili je već iskorišćen", Response.Status.BAD_REQUEST);
        }
        Instant expires = user.getEmailVerificationExpires();
        if (expires != null && expires.isBefore(Instant.now())) {
            // Istekao token se ponistava; korisnik moze zatraziti novi
            user.setEmailVerificationToken(null);
            user.setEmailVerificationExpires(null);
            throw new ClientErrorException(
                    "Link za potvrdu je istekao. Zatražite novi.", Response.Status.BAD_REQUEST);
        }
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpires(null);
    }

    private User findByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        List<User> users = em.createQuery(
                        "select u from User u where u.emailVerificationToken = :hash", User.class)
                .setParameter("hash", Tokens.sha256(rawToken))
                .getResultList();
        return users.isEmpty() ? null : users.getFirst();
    }
}
