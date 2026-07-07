package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.fit.dto.GoalDepositRequest;
import me.fit.dto.GoalDto;
import me.fit.dto.GoalRequest;
import me.fit.security.CurrentUser;
import me.fit.service.GoalService;

import java.util.List;

// Ciljevi stednje
@Path("/api/goals")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class GoalResource {

    @Inject
    GoalService goalService;

    @Inject
    CurrentUser currentUser;

    @GET
    public List<GoalDto> getGoals() {
        return goalService.getGoals(currentUser.require());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createGoal(@Valid GoalRequest request) {
        GoalDto goal = goalService.createGoal(currentUser.require(), request);
        return Response.status(Response.Status.CREATED).entity(goal).build();
    }

    @POST
    @Path("/{id}/deposit")
    @Consumes(MediaType.APPLICATION_JSON)
    public GoalDto deposit(@PathParam("id") Long id, @Valid GoalDepositRequest request) {
        return goalService.deposit(currentUser.require(), id, request.amount());
    }

    @DELETE
    @Path("/{id}")
    public Response deleteGoal(@PathParam("id") Long id) {
        goalService.deleteGoal(currentUser.require(), id);
        return Response.noContent().build();
    }
}
