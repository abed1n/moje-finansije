package me.fit.rest.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import me.fit.model.LocationResponse;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@RegisterRestClient(configKey = "location-api")
public interface LocationApi {

    @GET
    @Path("/{ip}/json")
    LocationResponse getLocationByIp(@PathParam("ip") String ipAddress);
}
