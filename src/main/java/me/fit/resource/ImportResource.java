package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import me.fit.dto.ImportConfirmRequest;
import me.fit.dto.ImportPreviewDto;
import me.fit.dto.ImportResultDto;
import me.fit.security.CurrentUser;
import me.fit.service.ImportService;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;

// Uvoz bankovnog izvoda: prvo pregled sa predlozima, pa potvrda
@Path("/api/import")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {

    @Inject
    ImportService importService;

    @Inject
    CurrentUser currentUser;

    @POST
    @Path("/preview")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public ImportPreviewDto preview(@RestForm("file") FileUpload file,
                                    @RestForm("accountId") String accountId) {
        if (file == null) {
            throw new BadRequestException("Fajl izvoda je obavezan");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new BadRequestException("Račun je obavezan");
        }
        try (var input = Files.newInputStream(file.uploadedFile())) {
            return importService.preview(currentUser.require(), Long.valueOf(accountId), input);
        } catch (IOException e) {
            throw new UncheckedIOException("Čitanje fajla nije uspjelo", e);
        }
    }

    @POST
    @Path("/confirm")
    @Consumes(MediaType.APPLICATION_JSON)
    public ImportResultDto confirmImport(@Valid ImportConfirmRequest request) {
        return importService.confirmImport(currentUser.require(), request);
    }

    // Kad je posljednji put uvezen izvod - za podsjetnik u centru obavjestenja
    @GET
    @Path("/status")
    public Map<String, Object> importStatus() {
        var lastImportAt = currentUser.require().getLastImportAt();
        return java.util.Collections.singletonMap("lastImportAt",
                lastImportAt != null ? lastImportAt.toString() : null);
    }
}
