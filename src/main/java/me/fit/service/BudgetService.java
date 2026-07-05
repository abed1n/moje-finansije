package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import me.fit.dto.BudgetDto;
import me.fit.dto.BudgetRequest;
import me.fit.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class BudgetService {

    @Inject
    EntityManager em;

    @Inject
    TransactionService transactionService;

    @Inject
    CategoryService categoryService;

    @Transactional
    public List<BudgetDto> getBudgets(User user) {
        return em.createNamedQuery(Budget.GET_BUDGETS_BY_USER_ID, Budget.class)
                .setParameter("id", user.getId())
                .getResultList()
                .stream()
                .map(budget -> BudgetDto.from(budget, computeSpent(user, budget)))
                .toList();
    }

    @Transactional
    public BudgetDto createBudget(User user, BudgetRequest request) {
        Budget budget = new Budget();
        budget.setUser(em.getReference(User.class, user.getId()));
        applyRequest(user, budget, request);
        em.persist(budget);
        return BudgetDto.from(budget, computeSpent(user, budget));
    }

    @Transactional
    public BudgetDto updateBudget(User user, Long id, BudgetRequest request) {
        Budget budget = findOwned(user, id);
        applyRequest(user, budget, request);
        return BudgetDto.from(budget, computeSpent(user, budget));
    }

    @Transactional
    public void deleteBudget(User user, Long id) {
        Budget budget = findOwned(user, id);
        em.remove(budget);
    }

    private Budget findOwned(User user, Long id) {
        Budget budget = em.find(Budget.class, id);
        if (budget == null) {
            throw new NotFoundException("Budžet sa id " + id + " ne postoji");
        }
        if (!budget.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Nemate pristup ovom budžetu");
        }
        return budget;
    }

    private void applyRequest(User user, Budget budget, BudgetRequest request) {
        budget.setName(request.name().trim());
        budget.setLimitAmount(request.limitAmount());
        budget.setPeriod(request.period());
        List<Category> categories = new ArrayList<>();
        if (request.categoryIds() != null) {
            for (Long categoryId : request.categoryIds()) {
                Category category = categoryService.findOwned(user, categoryId);
                if (!categories.contains(category)) {
                    categories.add(category);
                }
            }
        }
        budget.setCategories(categories);
    }

    // potroseno u tekucem periodu; ako budzet nema kategorije racunaju se svi rashodi
    private BigDecimal computeSpent(User user, Budget budget) {
        LocalDate from;
        LocalDate to;
        if (budget.getPeriod() == BudgetPeriod.YEARLY) {
            Year year = Year.now();
            from = year.atDay(1);
            to = year.atMonth(12).atEndOfMonth();
        } else {
            YearMonth month = YearMonth.now();
            from = month.atDay(1);
            to = month.atEndOfMonth();
        }
        return transactionService.sumAmount(user, TransactionType.EXPENSE, from, to, budget.getCategories());
    }
}
