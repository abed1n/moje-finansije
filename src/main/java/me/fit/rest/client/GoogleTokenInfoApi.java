package me.fit.rest.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import me.fit.dto.GoogleTokenInfo;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// Google tokeninfo endpoint: provjerava ID token (potpis i istek) i vraca njegove podatke.
// Ako je token neispravan ili istekao, vraca gresku (4xx).
@Path("/tokeninfo")
@RegisterRestClient(configKey = "google-oauth")
public interface GoogleTokenInfoApi {

    @GET
    GoogleTokenInfo verify(@QueryParam("id_token") String idToken);
}
