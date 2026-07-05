package me.fit.dto;

import me.fit.model.Budget;
import me.fit.model.BudgetPeriod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record BudgetDto(Long id, String name, BigDecimal limitAmount, BudgetPeriod period,
                        List<CategoryDto> categories, BigDecimal spent, BigDecimal remaining, int percentUsed) {

    public static BudgetDto from(Budget budget, BigDecimal spent) {
        BigDecimal remaining = budget.getLimitAmount().subtract(spent);
        int percentUsed = budget.getLimitAmount().signum() > 0
                ? spent.multiply(BigDecimal.valueOf(100))
                        .divide(budget.getLimitAmount(), 0, RoundingMode.HALF_UP)
                        .intValue()
                : 0;
        return new BudgetDto(budget.getId(), budget.getName(), budget.getLimitAmount(), budget.getPeriod(),
                budget.getCategories().stream().map(CategoryDto::from).toList(),
                spent, remaining, percentUsed);
    }
}
