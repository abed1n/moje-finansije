package me.fit.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import me.fit.dto.*;
import me.fit.model.TransactionType;
import me.fit.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GoalFlowTest {

    @Inject
    GoalService goalService;

    @Inject
    AuthService authService;

    @Inject
    AccountService accountService;

    @Inject
    TransactionService transactionService;

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void cilj_prati_napredak_i_dostizanje() {
        String email = "goal-" + System.nanoTime() + "@pfm.me";
        authService.register(new RegisterRequest("Goal Test", email, "lozinka123"));
        User user = em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email)
                .getSingleResult();

        GoalDto goal = goalService.createGoal(user, new GoalRequest(
                "Ljetovanje", new BigDecimal("1500.00"), LocalDate.now().plusMonths(2), null));
        assertEquals(0, goal.percent());
        assertFalse(goal.achieved());

        GoalDto after = goalService.deposit(user, goal.id(), new BigDecimal("620.00"));
        assertEquals(41, after.percent());
        assertFalse(after.achieved());
        assertEquals(0, after.remaining().compareTo(new BigDecimal("880.00")));

        GoalDto done = goalService.deposit(user, goal.id(), new BigDecimal("880.00"));
        assertTrue(done.achieved());
        assertEquals(100, done.percent());
        assertEquals(0, done.remaining().compareTo(BigDecimal.ZERO));

        goalService.deleteGoal(user, goal.id());
        assertTrue(goalService.getGoals(user).isEmpty());
    }

    @Test
    @Transactional
    void cilj_vezan_za_racun_prati_njegovo_stanje() {
        String email = "goal-acc-" + System.nanoTime() + "@pfm.me";
        authService.register(new RegisterRequest("Goal Account", email, "lozinka123"));
        User user = em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email)
                .getSingleResult();
        AccountDto savings = accountService.createAccount(user, new AccountRequest(
                "Štednja", me.fit.model.AccountType.SAVINGS, "EUR", new BigDecimal("200.00"), null));

        GoalDto goal = goalService.createGoal(user, new GoalRequest(
                "Rezerva", new BigDecimal("1000.00"), null, savings.id()));
        assertEquals(0, goal.savedAmount().compareTo(new BigDecimal("200.00")),
                "Vezani cilj cita stanje racuna");
        assertEquals(20, goal.percent());
        assertEquals(savings.id(), goal.accountId());

        // Rucna uplata na vezani cilj nije dozvoljena
        assertThrows(jakarta.ws.rs.BadRequestException.class,
                () -> goalService.deposit(user, goal.id(), BigDecimal.TEN));

        // Novac stigne na racun -> cilj sam napreduje
        transactionService.createTransaction(user, new TransactionRequest(
                new BigDecimal("300.00"), LocalDate.now(), TransactionType.INCOME,
                "Uplata na stednju", savings.id(), null, null));
        GoalDto refreshed = goalService.getGoals(user).getFirst();
        assertEquals(50, refreshed.percent());
        assertEquals(0, refreshed.savedAmount().compareTo(new BigDecimal("500.00")));
    }
}
