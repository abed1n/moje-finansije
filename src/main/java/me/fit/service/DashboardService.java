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
import java.math.RoundingMode;
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

    @Inject
    EcbRatesService ecbRatesService;

    // months: 1 (tekuci mjesec), 3 ili 12 — period za sume i rashode po kategorijama
    @Transactional
    public DashboardDto getDashboard(User user, int months) {
        int period = (months == 3 || months == 12) ? months : 1;

        List<Account> accounts = em.createNamedQuery(Account.GET_ACCOUNTS_BY_USER_ID, Account.class)
                .setParameter("id", user.getId())
                .getResultList();

        // Racuni u drugim valutama se preracunavaju u EUR po ECB kursu,
        // da zbir ne sabira jabuke i kruske (100 USD nije 100 EUR)
        boolean hasForeign = false;
        BigDecimal totalBalance = BigDecimal.ZERO;
        for (Account account : accounts) {
            BigDecimal value = account.getBalance();
            if (!"EUR".equalsIgnoreCase(account.getCurrency())) {
                hasForeign = true;
                BigDecimal rate = ecbRatesService.rateFromEur(account.getCurrency());
                if (rate != null && rate.signum() > 0) {
                    value = value.divide(rate, 2, RoundingMode.HALF_UP);
                }
            }
            totalBalance = totalBalance.add(value);
        }

        YearMonth currentMonth = YearMonth.now();
        LocalDate periodStart = currentMonth.minusMonths(period - 1).atDay(1);
        LocalDate periodEnd = currentMonth.atEndOfMonth();

        BigDecimal income = transactionService.sumAmount(user, TransactionType.INCOME, periodStart, periodEnd, null);
        BigDecimal expense = transactionService.sumAmount(user, TransactionType.EXPENSE, periodStart, periodEnd, null);

        return new DashboardDto(totalBalance, income, expense, income.subtract(expense), accounts.size(),
                hasForeign,
                spendingByCategory(user, periodStart, periodEnd),
                monthlyFlow(user, period == 12 ? 12 : FLOW_MONTHS),
                recentTransactions(user),
                budgetService.getBudgets(user));
    }

    private List<DashboardDto.CategorySpending> spendingByCategory(User user, LocalDate from, LocalDate to) {
        List<Object[]> rows = em.createQuery(
                        "select c.id, c.name, c.color, sum(t.amount) from Transaction t left join t.category c"
                                + " where t.account.user.id = :userId and t.type = :type"
                                + " and t.date >= :fromDate and t.date <= :toDate"
                                + " group by c.id, c.name, c.color order by sum(t.amount) desc", Object[].class)
                .setParameter("userId", user.getId())
                .setParameter("type", TransactionType.EXPENSE)
                .setParameter("fromDate", from)
                .setParameter("toDate", to)
                .getResultList();
        return rows.stream()
                .map(row -> new DashboardDto.CategorySpending(
                        (Long) row[0],
                        row[1] != null ? (String) row[1] : "Bez kategorije",
                        row[2] != null ? (String) row[2] : "#94a3b8",
                        (BigDecimal) row[3]))
                .toList();
    }

    private List<DashboardDto.MonthlyFlow> monthlyFlow(User user, int flowMonths) {
        YearMonth currentMonth = YearMonth.now();
        List<DashboardDto.MonthlyFlow> flow = new ArrayList<>();
        for (int i = flowMonths - 1; i >= 0; i--) {
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
