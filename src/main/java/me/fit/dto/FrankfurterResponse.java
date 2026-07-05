package me.fit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

// Odgovor Frankfurter API-ja (kursevi Evropske centralne banke)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FrankfurterResponse(double amount, String base, String date, Map<String, Double> rates) {
}
