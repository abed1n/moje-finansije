package me.fit.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import me.fit.dto.*;
import me.fit.exception.ConflictException;
import me.fit.model.Profile;
import me.fit.model.User;
import me.fit.security.TokenService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class AuthService {

    @Inject
    EntityManager em;

    @Inject
    TokenService tokenService;

    @Inject
    CategoryService categoryService;

    // Kad je iskljucena, registracija odmah verifikuje nalog i prijava ne trazi potvrdu.
    @ConfigProperty(name = "app.email-verification.required", defaultValue = "true")
    boolean emailVerificationRequired;

    // Registracija ne prijavljuje korisnika: prvo mora potvrditi email adresu
    // (osim kad je verifikacija iskljucena - tada je nalog odmah potvrdjen).
    @Transactional
    public UserDto register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (!findByEmail(email).isEmpty()) {
            throw new ConflictException("Korisnik sa ovim emailom već postoji");
        }
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(BcryptUtil.bcryptHash(request.password()));
        if (!emailVerificationRequired) {
            user.setEmailVerified(true);
        }
        em.persist(user);
        categoryService.seedDefaultCategories(user);
        return UserDto.from(user);
    }

    public AuthResponse login(LoginRequest request) {
        // Email se pri registraciji sprema kao lowercase, pa i ovdje normalizujemo
        List<User> users = findByEmail(request.email().trim().toLowerCase());
        User user = users.isEmpty() ? null : users.getFirst();
        // Nalozi kreirani preko Google-a nemaju lozinku
        if (user == null || user.getPasswordHash() == null
                || !BcryptUtil.matches(request.password(), user.getPasswordHash())) {
            throw new ClientErrorException("Pogrešan email ili lozinka", Response.Status.UNAUTHORIZED);
        }
        if (emailVerificationRequired && !user.isEmailVerified()) {
            throw new ClientErrorException(
                    "Potvrdite email adresu prije prijave. Provjerite inbox za link za potvrdu.",
                    Response.Status.FORBIDDEN);
        }
        return new AuthResponse(tokenService.generateToken(user), UserDto.from(user));
    }

    // Novi pristupni token za vec prijavljenog korisnika (koristi se pri osvjezavanju)
    @Transactional
    public AuthResponse issueFor(Long userId) {
        User user = em.find(User.class, userId);
        return new AuthResponse(tokenService.generateToken(user), UserDto.from(user));
    }

    @Transactional
    public UserDto updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = em.find(User.class, userId);
        user.setName(request.name().trim());
        Profile profile = user.getProfile();
        if (profile == null) {
            profile = new Profile();
            user.setProfile(profile);
        }
        profile.setAddress(request.address());
        profile.setPhone(request.phone());
        profile.setDateOfBirth(request.dateOfBirth());
        return UserDto.from(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = em.find(User.class, userId);
        if (user.getPasswordHash() == null) {
            throw new ClientErrorException(
                    "Ovaj nalog koristi Google prijavu i nema lozinku.", Response.Status.BAD_REQUEST);
        }
        if (!BcryptUtil.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ClientErrorException("Trenutna lozinka nije ispravna", Response.Status.BAD_REQUEST);
        }
        user.setPasswordHash(BcryptUtil.bcryptHash(request.newPassword()));
    }

    private List<User> findByEmail(String email) {
        return em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email)
                .getResultList();
    }
}
