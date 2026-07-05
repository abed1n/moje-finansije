package me.fit.rest.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@RegisterRestClient(configKey = "ip-api")
public interface IpApi {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    String getPublicIp();
}
