package me.fit.rest.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import me.fit.dto.FrankfurterResponse;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// Frankfurter API - dnevni kursevi Evropske centralne banke, bez kljuca
@Path("/v1")
@RegisterRestClient(configKey = "currency-api")
public interface CurrencyApi {

    @GET
    @Path("/latest")
    FrankfurterResponse latestRates(@QueryParam("base") String base, @QueryParam("symbols") String symbols);
}
