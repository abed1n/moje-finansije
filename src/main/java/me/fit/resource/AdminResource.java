package me.fit.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import me.fit.dto.UserDto;
import me.fit.service.UserService;

import java.util.List;

@Path("/api/admin")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    UserService userService;

    @GET
    @Path("/users")
    public List<UserDto> getAllUsers() {
        return userService.getAllUsers();
    }
}
