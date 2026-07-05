package me.fit.rest.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import me.fit.model.TimezoneResponse;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/time/current")
@RegisterRestClient(configKey = "timezone-api")
public interface TimezoneApi {

    @GET
    @Path("/ip")
    TimezoneResponse getTimeByIp(@QueryParam("ipAddress") String ipAddress);
}
