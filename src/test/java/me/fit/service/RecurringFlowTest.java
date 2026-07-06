package me.fit.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import me.fit.dto.*;
import me.fit.model.Account;
import me.fit.model.AccountType;
import me.fit.model.TransactionType;
import me.fit.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RecurringFlowTest {

    @Inject
    RecurringService recurringService;

    @Inject
    AuthService authService;

    @Inject
    AccountService accountService;

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void pravilo_kreira_transakciju_i_umanjuje_stanje() {
        String email = "rec-" + System.nanoTime() + "@pfm.me";
        authService.register(new RegisterRequest("Rec Test", email, "lozinka123"));
        User user = em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email)
                .getSingleResult();

        AccountDto account = accountService.createAccount(user, new AccountRequest(
                "Test račun", AccountType.CHECKING, "EUR", BigDecimal.ZERO, null));

        RecurringDto rule = recurringService.createRule(user, new RecurringRequest(
                new BigDecimal("100.00"), TransactionType.EXPENSE, "Pretplata", 1, account.id(), null));

        assertTrue(rule.active());
        assertEquals(1, recurringService.getRules(user).size());

        // Simulacija novog mjeseca: pravilo jos nije izvrseno
        em.flush();
        em.createQuery("update RecurringTransaction r set r.lastRun = null where r.id = :id")
                .setParameter("id", rule.id())
                .executeUpdate();
        em.clear();

        int created = recurringService.generateDue();
        assertEquals(1, created);

        em.flush();
        em.clear();
        Account refreshed = em.find(Account.class, account.id());
        assertEquals(0, refreshed.getBalance().compareTo(new BigDecimal("-100.00")),
                "Stanje racuna mora biti umanjeno za iznos pravila");

        // Drugi prolazak istog mjeseca ne smije duplirati transakciju
        assertEquals(0, recurringService.generateDue());

        RecurringDto paused = recurringService.toggleRule(user, rule.id());
        assertFalse(paused.active());

        recurringService.deleteRule(user, rule.id());
        assertTrue(recurringService.getRules(user).isEmpty());
    }
}
