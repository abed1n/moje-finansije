package me.fit.security;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Zastita od pogadjanja lozinke: broji neuspjele prijave po kljucu (IP + email)
// i privremeno blokira nakon previse pokusaja. Drzi se u memoriji jer aplikacija
// radi kao jedna instanca; scheduler povremeno cisti zastarjele zapise.
@ApplicationScoped
public class LoginAttemptService {

    @ConfigProperty(name = "app.login.max-attempts", defaultValue = "5")
    int maxAttempts;

    @ConfigProperty(name = "app.login.lockout-minutes", defaultValue = "15")
    long lockoutMinutes;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    private static final class Attempt {
        int count;
        Instant lastFailure = Instant.now();
        Instant lockedUntil;
    }

    // Koliko sekundi je preostalo do isteka blokade; 0 znaci da kljuc nije blokiran
    public long secondsUntilUnlock(String key) {
        Attempt a = attempts.get(key);
        if (a == null || a.lockedUntil == null) {
            return 0;
        }
        long remaining = Duration.between(Instant.now(), a.lockedUntil).getSeconds();
        return Math.max(remaining, 0);
    }

    public synchronized void recordFailure(String key) {
        Instant now = Instant.now();
        Attempt a = attempts.get(key);
        // Nov zapis ili je prethodna blokada odavno istekla -> broj krece iznova
        if (a == null || a.lastFailure.isBefore(now.minus(Duration.ofMinutes(lockoutMinutes)))) {
            a = new Attempt();
            attempts.put(key, a);
        }
        a.count++;
        a.lastFailure = now;
        if (a.count >= maxAttempts) {
            a.lockedUntil = now.plus(Duration.ofMinutes(lockoutMinutes));
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    // Uklanja zapise koji vise nisu blokirani i dovoljno su stari da ih ne pamtimo
    @Scheduled(every = "10m")
    void cleanup() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(lockoutMinutes));
        attempts.entrySet().removeIf(entry -> {
            Attempt a = entry.getValue();
            boolean notLocked = a.lockedUntil == null || a.lockedUntil.isBefore(Instant.now());
            return notLocked && a.lastFailure.isBefore(cutoff);
        });
    }
}
