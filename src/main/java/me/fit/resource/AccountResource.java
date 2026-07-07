package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.fit.dto.AccountDto;
import me.fit.dto.AccountRequest;
import me.fit.dto.ReconcileRequest;
import me.fit.dto.ReconcileResultDto;
import me.fit.security.CurrentUser;
import me.fit.service.AccountService;

import java.util.List;

@Path("/api/accounts")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccountResource {

    @Inject
    AccountService accountService;

    @Inject
    CurrentUser currentUser;

    @GET
    public List<AccountDto> getAccounts() {
        return accountService.getAccounts(currentUser.require());
    }

    @GET
    @Path("/{id}")
    public AccountDto getAccount(@PathParam("id") Long id) {
        return accountService.getAccount(currentUser.require(), id);
    }

    @POST
    public Response createAccount(@Valid AccountRequest request) {
        AccountDto account = accountService.createAccount(currentUser.require(), request);
        return Response.status(Response.Status.CREATED).entity(account).build();
    }

    @PUT
    @Path("/{id}")
    public AccountDto updateAccount(@PathParam("id") Long id, @Valid AccountRequest request) {
        return accountService.updateAccount(currentUser.require(), id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteAccount(@PathParam("id") Long id) {
        accountService.deleteAccount(currentUser.require(), id);
        return Response.noContent().build();
    }

    // Uskladjivanje sa stvarnim stanjem (izvod banke / prebrojani novcanik):
    // razlika se knjizi kao transakcija uskladjivanja pa se stanja garantovano poklope
    @POST
    @Path("/{id}/reconcile")
    public ReconcileResultDto reconcile(@PathParam("id") Long id, @Valid ReconcileRequest request) {
        return accountService.reconcile(currentUser.require(), id, request.actualBalance());
    }
}
