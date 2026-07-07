package me.fit.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import me.fit.dto.GoalDto;
import me.fit.dto.GoalRequest;
import me.fit.dto.RegisterRequest;
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
                "Ljetovanje", new BigDecimal("1500.00"), LocalDate.now().plusMonths(2)));
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
}
