package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import me.fit.model.CurrencyResponse;
import me.fit.model.LocationResponse;
import me.fit.model.TimezoneResponse;
import me.fit.model.User;

import java.util.List;

// Snima odgovore vanjskih API-ja kao istoriju po korisniku
@ApplicationScoped
public class IntegrationService {

    @Inject
    EntityManager em;

    @Transactional
    public CurrencyResponse saveCurrencyConversion(User user, CurrencyResponse response) {
        response.setUser(em.getReference(User.class, user.getId()));
        em.persist(response);
        return response;
    }

    @Transactional
    public LocationResponse saveLocation(User user, LocationResponse response) {
        response.setUser(em.getReference(User.class, user.getId()));
        em.persist(response);
        return response;
    }

    @Transactional
    public TimezoneResponse saveTimezone(User user, TimezoneResponse response) {
        response.setUser(em.getReference(User.class, user.getId()));
        em.persist(response);
        return response;
    }

    public List<CurrencyResponse> getCurrencyHistory(User user) {
        return em.createNamedQuery(CurrencyResponse.GET_BY_USER_ID, CurrencyResponse.class)
                .setParameter("id", user.getId())
                .setMaxResults(20)
                .getResultList();
    }

    public List<LocationResponse> getLocationHistory(User user) {
        return em.createNamedQuery(LocationResponse.GET_BY_USER_ID, LocationResponse.class)
                .setParameter("id", user.getId())
                .setMaxResults(20)
                .getResultList();
    }

    public List<TimezoneResponse> getTimezoneHistory(User user) {
        return em.createNamedQuery(TimezoneResponse.GET_BY_USER_ID, TimezoneResponse.class)
                .setParameter("id", user.getId())
                .setMaxResults(20)
                .getResultList();
    }
}
