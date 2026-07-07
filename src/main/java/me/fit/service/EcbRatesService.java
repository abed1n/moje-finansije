package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.fit.dto.FrankfurterResponse;
import me.fit.rest.client.CurrencyApi;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

// Kesirani ECB kursevi prema EUR — za preracunavanje racuna u drugim valutama.
// ECB objavljuje kurseve jednom dnevno pa je 12h keš sasvim dovoljan.
@ApplicationScoped
public class EcbRatesService {

    private static final Logger LOG = Logger.getLogger(EcbRatesService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    @Inject
    @RestClient
    CurrencyApi currencyApi;

    private volatile Map<String, Double> rates = Map.of();
    private volatile Instant fetchedAt = Instant.EPOCH;

    // Kurs EUR -> valuta, ili null ako valuta nije dostupna
    public BigDecimal rateFromEur(String currency) {
        if ("EUR".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        refreshIfStale();
        Double rate = rates.get(currency.toUpperCase());
        return rate != null ? BigDecimal.valueOf(rate) : null;
    }

    private synchronized void refreshIfStale() {
        if (Instant.now().isBefore(fetchedAt.plus(CACHE_TTL)) && !rates.isEmpty()) {
            return;
        }
        try {
            FrankfurterResponse response = currencyApi.latestRates("EUR", null);
            if (response.rates() != null && !response.rates().isEmpty()) {
                rates = Map.copyOf(response.rates());
                fetchedAt = Instant.now();
            }
        } catch (RuntimeException e) {
            // Bez kurseva aplikacija i dalje radi — zbir samo nije preracunat
            fetchedAt = Instant.now().minus(CACHE_TTL).plus(Duration.ofMinutes(5));
            LOG.warnf("ECB kursevi trenutno nisu dostupni: %s", e.getMessage());
        }
    }
}
