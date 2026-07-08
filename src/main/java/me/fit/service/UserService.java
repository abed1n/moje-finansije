package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import me.fit.dto.UserDto;
import me.fit.model.Role;
import me.fit.model.User;

import java.util.List;

@ApplicationScoped
public class UserService {

    @Inject
    EntityManager em;

    @Transactional
    public List<UserDto> getAllUsers() {
        return em.createNamedQuery(User.GET_ALL_USERS, User.class)
                .getResultList()
                .stream()
                .map(UserDto::from)
                .toList();
    }

    @Transactional
    public void promoteToAdmin(String email) {
        em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst()
                .ifPresent(user -> user.setRole(Role.ADMIN));
    }

    @Transactional
    public void markEmailVerified(String email) {
        em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst()
                .ifPresent(user -> user.setEmailVerified(true));
    }
}
