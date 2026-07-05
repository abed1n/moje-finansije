package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.fit.dto.*;
import me.fit.security.CurrentUser;
import me.fit.service.AuthService;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    CurrentUser currentUser;

    @POST
    @Path("/register")
    @PermitAll
    public Response register(@Valid RegisterRequest request) {
        return Response.status(Response.Status.CREATED).entity(authService.register(request)).build();
    }

    @POST
    @Path("/login")
    @PermitAll
    public AuthResponse login(@Valid LoginRequest request) {
        return authService.login(request);
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
