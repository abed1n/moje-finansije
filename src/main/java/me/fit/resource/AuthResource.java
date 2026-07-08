package me.fit.resource;

import io.quarkus.security.Authenticated;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import me.fit.dto.*;
import me.fit.exception.TooManyRequestsException;
import me.fit.security.CurrentUser;
import me.fit.security.LoginAttemptService;
import me.fit.service.AuthService;
import me.fit.service.PasswordResetService;
import me.fit.service.RefreshTokenService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final String REFRESH_COOKIE = "refresh_token";

    @Inject
    AuthService authService;

    @Inject
    CurrentUser currentUser;

    @Inject
    LoginAttemptService loginAttempts;

    @Inject
    RefreshTokenService refreshTokens;

    @Inject
    PasswordResetService passwordReset;

    @ConfigProperty(name = "app.auth.cookie-secure", defaultValue = "false")
    boolean cookieSecure;

    @POST
    @Path("/register")
    @PermitAll
    public Response register(@Valid RegisterRequest request) {
        AuthResponse response = authService.register(request);
        String refresh = refreshTokens.issue(response.user().id());
        return Response.status(Response.Status.CREATED)
                .entity(response)
                .cookie(refreshCookie(refresh))
                .build();
    }

    @POST
    @Path("/login")
    @PermitAll
    public Response login(@Valid LoginRequest request, @Context HttpServerRequest http) {
        String key = clientIp(http) + "|" + request.email().trim().toLowerCase();
        long wait = loginAttempts.secondsUntilUnlock(key);
        if (wait > 0) {
            long minutes = (wait + 59) / 60;
            throw new TooManyRequestsException(
                    "Previše neuspjelih pokušaja prijave. Pokušajte ponovo za " + minutes + " min.", wait);
        }
        try {
            AuthResponse response = authService.login(request);
            loginAttempts.recordSuccess(key);
            String refresh = refreshTokens.issue(response.user().id());
            return Response.ok(response).cookie(refreshCookie(refresh)).build();
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()) {
                loginAttempts.recordFailure(key);
            }
            throw e;
        }
    }

    // Kratkotrajni pristupni token je istekao: refresh kolacic ga tiho obnavlja, uz rotaciju
    @POST
    @Path("/refresh")
    @PermitAll
    @Consumes(MediaType.WILDCARD)
    public Response refresh(@CookieParam(REFRESH_COOKIE) String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokens.rotate(refreshToken);
        AuthResponse response = authService.issueFor(rotated.userId());
        return Response.ok(response).cookie(refreshCookie(rotated.rawToken())).build();
    }

    @POST
    @Path("/logout")
    @PermitAll
    @Consumes(MediaType.WILDCARD)
    public Response logout(@CookieParam(REFRESH_COOKIE) String refreshToken) {
        if (refreshToken != null) {
            refreshTokens.revoke(refreshToken);
        }
        return Response.noContent().cookie(expiredRefreshCookie()).build();
    }

    // Zahtjev za reset lozinke: uvijek 204 da se ne otkrije postoji li nalog
    @POST
    @Path("/forgot-password")
    @PermitAll
    public Response forgotPassword(@Valid ForgotPasswordRequest request) {
        passwordReset.requestReset(request.email());
        return Response.noContent().build();
    }

    @POST
    @Path("/reset-password")
    @PermitAll
    public Response resetPassword(@Valid ResetPasswordRequest request) {
        passwordReset.reset(request.token(), request.newPassword());
        return Response.noContent().build();
    }

    private NewCookie refreshCookie(String value) {
        return baseCookie(value)
                .maxAge((int) Duration.ofDays(refreshTokens.refreshDays()).toSeconds())
                .build();
    }

    private NewCookie expiredRefreshCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private NewCookie.Builder baseCookie(String value) {
        return new NewCookie.Builder(REFRESH_COOKIE)
                .value(value)
                .path("/api/auth")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(NewCookie.SameSite.STRICT);
    }

    // IP klijenta - iza proxija se cita iz X-Forwarded-For, inace direktna adresa
    private String clientIp(HttpServerRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        SocketAddress address = request.remoteAddress();
        return address != null ? address.hostAddress() : "unknown";
    }

    @GET
    @Path("/me")
    @Authenticated
    @Transactional
    public UserDto me() {
        return UserDto.from(currentUser.require());
    }

    @PUT
    @Path("/profile")
    @Authenticated
    public UserDto updateProfile(@Valid ProfileUpdateRequest request) {
        return authService.updateProfile(currentUser.require().getId(), request);
    }

    @POST
    @Path("/change-password")
    @Authenticated
    public Response changePassword(@Valid ChangePasswordRequest request) {
        authService.changePassword(currentUser.require().getId(), request);
        return Response.noContent().build();
    }
}
