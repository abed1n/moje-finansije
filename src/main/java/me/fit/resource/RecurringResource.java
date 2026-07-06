package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.fit.dto.RecurringDto;
import me.fit.dto.RecurringRequest;
import me.fit.security.CurrentUser;
import me.fit.service.RecurringService;

import java.util.List;

// Ponavljajuce transakcije - mjesecna pravila po korisniku
@Path("/api/recurring")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class RecurringResource {

    @Inject
    RecurringService recurringService;

    @Inject
    CurrentUser currentUser;

    @GET
    public List<RecurringDto> getRules() {
        return recurringService.getRules(currentUser.require());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createRule(@Valid RecurringRequest request) {
        RecurringDto rule = recurringService.createRule(currentUser.require(), request);
        return Response.status(Response.Status.CREATED).entity(rule).build();
    }

    @PUT
    @Path("/{id}/toggle")
    public RecurringDto toggleRule(@PathParam("id") Long id) {
        return recurringService.toggleRule(currentUser.require(), id);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteRule(@PathParam("id") Long id) {
        recurringService.deleteRule(currentUser.require(), id);
        return Response.noContent().build();
    }
}
