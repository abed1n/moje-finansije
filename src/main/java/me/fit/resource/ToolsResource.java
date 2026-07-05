package me.fit.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import me.fit.dto.FrankfurterResponse;
import me.fit.model.CurrencyResponse;
import me.fit.model.LocationResponse;
import me.fit.model.TimezoneResponse;
import me.fit.rest.client.CurrencyApi;
import me.fit.rest.client.IpApi;
import me.fit.rest.client.LocationApi;
import me.fit.rest.client.TimezoneApi;
import me.fit.security.CurrentUser;
import me.fit.service.IntegrationService;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

// Konverzija valuta, lokacija i vremenska zona preko vanjskih API-ja
@Path("/api/tools")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ToolsResource {

    @Inject
    @RestClient
    IpApi ipApi;

    @Inject
    @RestClient
    TimezoneApi timezoneApi;

    @Inject
    @RestClient
    LocationApi locationApi;

    @Inject
    @RestClient
    CurrencyApi currencyApi;

    @Inject
    IntegrationService integrationService;

    @Inject
    CurrentUser currentUser;

    @GET
    @Path("/currency")
    public CurrencyResponse convertCurrency(@QueryParam("from") String from,
                                            @QueryParam("to") String to,
                                            @QueryParam("value") double value) {
        String fromCode = from == null ? "" : from.trim().toUpperCase(Locale.ROOT);
        String toCode = to == null ? "" : to.trim().toUpperCase(Locale.ROOT);
        if (fromCode.isEmpty() || toCode.isEmpty()) {
            throw new BadRequestException("Parametri from i to su obavezni");
        }

        CurrencyResponse response = new CurrencyResponse();
        response.setFrom(fromCode);
        response.setTo(toCode);
        response.setSource("frankfurter.dev (ECB)");

        if (fromCode.equals(toCode)) {
            response.setRate(1.0);
            response.setDate(LocalDate.now().toString());
        } else {
            FrankfurterResponse rates;
            try {
                rates = currencyApi.latestRates(fromCode, toCode);
            } catch (WebApplicationException e) {
                // Frankfurter vraca 404 za valute koje ECB ne kotira
                throw new BadRequestException("Nepodržana valuta ili kombinacija: " + fromCode + " → " + toCode);
            } catch (RuntimeException e) {
                throw new ServerErrorException("Vanjski servis trenutno nije dostupan", Response.Status.BAD_GATEWAY, e);
            }
            Double rate = rates.rates() == null ? null : rates.rates().get(toCode);
            if (rate == null) {
                throw new BadRequestException("Nepodržana valuta: " + toCode);
            }
            response.setRate(rate);
            response.setDate(rates.date());
        }

        response.setValue(value);
        response.setConvertedValue(response.getRate() * value);
        return integrationService.saveCurrencyConversion(currentUser.require(), response);
    }

    @GET
    @Path("/currency/history")
    public List<CurrencyResponse> currencyHistory() {
        return integrationService.getCurrencyHistory(currentUser.require());
    }

    @GET
    @Path("/location")
    public LocationResponse getLocation() {
        LocationResponse response = callExternal(() -> {
            String ip = ipApi.getPublicIp().trim();
            return locationApi.getLocationByIp(ip);
        });
        return integrationService.saveLocation(currentUser.require(), response);
    }

    @GET
    @Path("/location/history")
    public List<LocationResponse> locationHistory() {
        return integrationService.getLocationHistory(currentUser.require());
    }

    @GET
    @Path("/timezone")
    public TimezoneResponse getTimezone() {
        TimezoneResponse response = callExternal(() -> {
            String ip = ipApi.getPublicIp().trim();
            return timezoneApi.getTimeByIp(ip);
        });
        return integrationService.saveTimezone(currentUser.require(), response);
    }

    @GET
    @Path("/timezone/history")
    public List<TimezoneResponse> timezoneHistory() {
        return integrationService.getTimezoneHistory(currentUser.require());
    }

    private <T> T callExternal(Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            throw new ServerErrorException("Vanjski servis trenutno nije dostupan", Response.Status.BAD_GATEWAY, e);
        }
    }
}
