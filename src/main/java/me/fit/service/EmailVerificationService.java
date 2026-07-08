package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import me.fit.model.User;
import me.fit.security.Tokens;

import java.util.List;

// Potvrda email adrese: pri registraciji se salje token na email, a klik na
// link ga razmjenjuje za potvrdu. Hash tokena se cuva na samom korisniku.
@ApplicationScoped
public class EmailVerificationService {

    @Inject
    EntityManager em;

    @Inject
    EmailService emailService;

    // Generise token, sacuva mu hash na korisniku i posalje email s linkom
    @Transactional
    public void sendFor(Long userId) {
        User user = em.find(User.class, userId);
        if (user == null || user.isEmailVerified()) {
            return;
        }
        String raw = Tokens.random();
        user.setEmailVerificationToken(Tokens.sha256(raw));
        emailService.sendVerification(user.getEmail(), user.getName(), raw);
    }

    @Transactional
    public void verify(String rawToken) {
        User user = findByToken(rawToken);
        if (user == null) {
            throw new ClientErrorException(
                    "Link za potvrdu je neispravan ili je već iskorišćen", Response.Status.BAD_REQUEST);
        }
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
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
