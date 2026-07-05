package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import me.fit.dto.DashboardDto;
import me.fit.dto.TransactionDto;
import me.fit.model.Account;
import me.fit.model.Transaction;
import me.fit.model.TransactionType;
import me.fit.model.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DashboardService {

    private static final int FLOW_MONTHS = 6;

    @Inject
    EntityManager em;

    @Inject
    TransactionService transactionService;

    @Inject
    BudgetService budgetService;

    @Transactional
    public DashboardDto getDashboard(User user) {
        List<Account> accounts = em.createNamedQuery(Account.GET_ACCOUNTS_BY_USER_ID, Account.class)
                .setParameter("id", user.getId())
                .getResultList();
        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth currentMonth = YearMonth.now();
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();

        BigDecimal income = transactionService.sumAmount(user, TransactionType.INCOME, monthStart, monthEnd, null);
        BigDecimal expense = transactionService.sumAmount(user, TransactionType.EXPENSE, monthStart, monthEnd, null);

        return new DashboardDto(totalBalance, income, expense, income.subtract(expense), accounts.size(),
                spendingByCategory(user, monthStart, monthEnd),
                monthlyFlow(user),
                recentTransactions(user),
                budgetService.getBudgets(user));
    }

    private List<DashboardDto.CategorySpending> spendingByCategory(User user, LocalDate from, LocalDate to) {
        List<Object[]> rows = em.createQuery(
                        "select c.name, c.color, sum(t.amount) from Transaction t left join t.category c"
                                + " where t.account.user.id = :userId and t.type = :type"
                                + " and t.date >= :fromDate and t.date <= :toDate"
                                + " group by c.name, c.color order by sum(t.amount) desc", Object[].class)
                .setParameter("userId", user.getId())
                .setParameter("type", TransactionType.EXPENSE)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
                .getResultList();
        return rows.stream()
                .map(row -> new DashboardDto.CategorySpending(
                        row[0] != null ? (String) row[0] : "Bez kategorije",
                        row[1] != null ? (String) row[1] : "#94a3b8",
                        (BigDecimal) row[2]))
                .toList();
    }

    private List<DashboardDto.MonthlyFlow> monthlyFlow(User user) {
        YearMonth currentMonth = YearMonth.now();
        List<DashboardDto.MonthlyFlow> flow = new ArrayList<>();
        for (int i = FLOW_MONTHS - 1; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            LocalDate from = month.atDay(1);
            LocalDate to = month.atEndOfMonth();
            flow.add(new DashboardDto.MonthlyFlow(month.toString(),
                    transactionService.sumAmount(user, TransactionType.INCOME, from, to, null),
                    transactionService.sumAmount(user, TransactionType.EXPENSE, from, to, null)));
        }
        return flow;
    }

    private List<TransactionDto> recentTransactions(User user) {
        return em.createQuery(
                        "select t from Transaction t where t.account.user.id = :userId"
                                + " order by t.date desc, t.id desc", Transaction.class)
                .setParameter("userId", user.getId())
                .setMaxResults(5)
                .getResultList()
                .stream()
                .map(TransactionDto::from)
                .toList();
    }
}
