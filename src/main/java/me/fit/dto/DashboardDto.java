package me.fit.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardDto(BigDecimal totalBalance,
                           BigDecimal incomeThisMonth,
                           BigDecimal expenseThisMonth,
                           BigDecimal netThisMonth,
                           int accountCount,
                           boolean hasForeignCurrency,
                           List<CategorySpending> spendingByCategory,
                           List<MonthlyFlow> monthlyFlow,
                           List<TransactionDto> recentTransactions,
                           List<BudgetDto> budgets) {

    public record CategorySpending(Long id, String name, String color, BigDecimal amount) {
    }

    public record MonthlyFlow(String month, BigDecimal income, BigDecimal expense) {
    }
}
