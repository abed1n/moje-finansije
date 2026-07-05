package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import me.fit.dto.DashboardDto;
import me.fit.security.CurrentUser;
import me.fit.service.DashboardService;

@Path("/api/dashboard")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @Inject
    DashboardService dashboardService;

    @Inject
    CurrentUser currentUser;

    @GET
    public DashboardDto getDashboard() {
        return dashboardService.getDashboard(currentUser.require());
    }
}
