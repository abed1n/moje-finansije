package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.fit.dto.AttachmentDto;
import me.fit.dto.TransactionDto;
import me.fit.dto.TransactionRequest;
import me.fit.model.TransactionType;
import me.fit.model.UploadedFile;
import me.fit.security.CurrentUser;
import me.fit.service.TransactionService;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

@Path("/api/transactions")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TransactionResource {

    @Inject
    TransactionService transactionService;

    @Inject
    CurrentUser currentUser;

    @GET
    public List<TransactionDto> getTransactions(@QueryParam("accountId") Long accountId,
                                                @QueryParam("categoryId") Long categoryId,
                                                @QueryParam("type") TransactionType type,
                                                @QueryParam("from") LocalDate from,
                                                @QueryParam("to") LocalDate to,
                                                @QueryParam("search") String search,
                                                @QueryParam("limit") @DefaultValue("200") int limit) {
        return transactionService.getTransactions(currentUser.require(), accountId, categoryId,
                type, from, to, search, limit);
    }

    @GET
    @Path("/{id}")
    public TransactionDto getTransaction(@PathParam("id") Long id) {
        return transactionService.getTransaction(currentUser.require(), id);
    }

    @POST
    public Response createTransaction(@Valid TransactionRequest request) {
        TransactionDto transaction = transactionService.createTransaction(currentUser.require(), request);
        return Response.status(Response.Status.CREATED).entity(transaction).build();
    }

    @PUT
    @Path("/{id}")
    public TransactionDto updateTransaction(@PathParam("id") Long id, @Valid TransactionRequest request) {
        return transactionService.updateTransaction(currentUser.require(), id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteTransaction(@PathParam("id") Long id) {
        transactionService.deleteTransaction(currentUser.require(), id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/attachments")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadAttachment(@PathParam("id") Long id, @RestForm("file") FileUpload file) {
        AttachmentDto attachment = transactionService.addAttachment(currentUser.require(), id, file);
        return Response.status(Response.Status.CREATED).entity(attachment).build();
    }

    @GET
    @Path("/attachments/{attachmentId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadAttachment(@PathParam("attachmentId") Long attachmentId) {
        UploadedFile file = transactionService.getAttachment(currentUser.require(), attachmentId);
        String contentType = file.getContentType() != null
                ? file.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM;
        return Response.ok(new File(file.getStoredPath()))
                .type(contentType)
                .header("Content-Disposition", "attachment; filename=\"" + file.getFilename() + "\"")
                .build();
    }

    @DELETE
    @Path("/attachments/{attachmentId}")
    public Response deleteAttachment(@PathParam("attachmentId") Long attachmentId) {
        transactionService.deleteAttachment(currentUser.require(), attachmentId);
        return Response.noContent().build();
    }
}
