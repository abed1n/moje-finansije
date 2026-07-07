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
import jakarta.ws.rs.core.Response;
import me.fit.dto.*;
import me.fit.exception.TooManyRequestsException;
import me.fit.security.CurrentUser;
import me.fit.security.LoginAttemptService;
import me.fit.service.AuthService;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    CurrentUser currentUser;

    @Inject
    LoginAttemptService loginAttempts;

    @POST
    @Path("/register")
    @PermitAll
    public Response register(@Valid RegisterRequest request) {
        return Response.status(Response.Status.CREATED).entity(authService.register(request)).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    public AuthResponse login(@Valid LoginRequest request, @Context HttpServerRequest http) {
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
            return response;
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()) {
                loginAttempts.recordFailure(key);
            }
            throw e;
        }
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
