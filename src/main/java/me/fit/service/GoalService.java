package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import me.fit.dto.GoalDto;
import me.fit.dto.GoalRequest;
import me.fit.model.SavingsGoal;
import me.fit.model.User;

import java.math.BigDecimal;
import java.util.List;

// Ciljevi stednje: koliko zelim skupiti, dokle sam stigao
@ApplicationScoped
public class GoalService {

    @Inject
    EntityManager em;

    @Transactional
    public List<GoalDto> getGoals(User user) {
        return em.createNamedQuery(SavingsGoal.GET_BY_USER_ID, SavingsGoal.class)
                .setParameter("id", user.getId())
                .getResultList()
                .stream().map(GoalDto::from).toList();
    }

    @Transactional
    public GoalDto createGoal(User user, GoalRequest request) {
        SavingsGoal goal = new SavingsGoal();
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        goal.setDeadline(request.deadline());
        goal.setUser(em.getReference(User.class, user.getId()));
        em.persist(goal);
        return GoalDto.from(goal);
    }

    @Transactional
    public GoalDto deposit(User user, Long id, BigDecimal amount) {
        SavingsGoal goal = findOwned(user, id);
        goal.setSavedAmount(goal.getSavedAmount().add(amount));
        return GoalDto.from(goal);
    }

    @Transactional
    public void deleteGoal(User user, Long id) {
        em.remove(findOwned(user, id));
    }

    private SavingsGoal findOwned(User user, Long id) {
        SavingsGoal goal = em.find(SavingsGoal.class, id);
        if (goal == null) {
            throw new NotFoundException("Cilj sa id " + id + " ne postoji");
        }
        if (!goal.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Nemate pristup ovom cilju");
        }
        return goal;
    }
}
