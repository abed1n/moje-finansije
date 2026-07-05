package me.fit.security;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import me.fit.model.User;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.Claims;

import java.time.Duration;
import java.util.Set;

@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "app.jwt.token-duration-hours", defaultValue = "24")
    long tokenDurationHours;

    public String generateToken(User user) {
        return Jwt.issuer(issuer)
                .upn(user.getEmail())
                .subject(String.valueOf(user.getId()))
                .groups(Set.of(user.getRole().name()))
                .claim(Claims.full_name.name(), user.getName())
                .expiresIn(Duration.ofHours(tokenDurationHours))
                .sign();
    }
}
