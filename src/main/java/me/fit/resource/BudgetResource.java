package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.fit.dto.BudgetDto;
import me.fit.dto.BudgetRequest;
import me.fit.security.CurrentUser;
import me.fit.service.BudgetService;

import java.util.List;

@Path("/api/budgets")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BudgetResource {

    @Inject
    BudgetService budgetService;

    @Inject
    CurrentUser currentUser;

    @GET
    public List<BudgetDto> getBudgets() {
        return budgetService.getBudgets(currentUser.require());
    }

    @POST
    public Response createBudget(@Valid BudgetRequest request) {
        BudgetDto budget = budgetService.createBudget(currentUser.require(), request);
        return Response.status(Response.Status.CREATED).entity(budget).build();
    }

    @PUT
    @Path("/{id}")
    public BudgetDto updateBudget(@PathParam("id") Long id, @Valid BudgetRequest request) {
        return budgetService.updateBudget(currentUser.require(), id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteBudget(@PathParam("id") Long id) {
        budgetService.deleteBudget(currentUser.require(), id);
        return Response.noContent().build();
    }
}
