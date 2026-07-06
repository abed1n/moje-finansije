package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.fit.dto.TransferDto;
import me.fit.dto.TransferRequest;
import me.fit.security.CurrentUser;
import me.fit.service.TransferService;

import java.util.List;

// Prebacivanje novca izmedju vlastitih racuna
@Path("/api/transfers")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class TransferResource {

    @Inject
    TransferService transferService;

    @Inject
    CurrentUser currentUser;

    @GET
    public List<TransferDto> getTransfers(@QueryParam("limit") @DefaultValue("20") int limit) {
        return transferService.getTransfers(currentUser.require(), limit);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createTransfer(@Valid TransferRequest request) {
        TransferDto transfer = transferService.createTransfer(currentUser.require(), request);
        return Response.status(Response.Status.CREATED).entity(transfer).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteTransfer(@PathParam("id") Long id) {
        transferService.deleteTransfer(currentUser.require(), id);
        return Response.noContent().build();
    }
}
