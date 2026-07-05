package me.fit.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotAuthorizedException;
import me.fit.model.User;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

// Ucitava prijavljenog korisnika iz JWT tokena (upn claim = email)
@RequestScoped
public class CurrentUser {

    @Inject
    JsonWebToken jwt;

    @Inject
    EntityManager em;

    private User cached;

    public User require() {
        if (cached != null) {
            return cached;
        }
        if (jwt == null || jwt.getName() == null) {
            throw new NotAuthorizedException("Bearer");
        }
        List<User> users = em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", jwt.getName())
                .getResultList();
        if (users.isEmpty()) {
            throw new NotAuthorizedException("Bearer");
        }
        cached = users.getFirst();
        return cached;
    }
}
