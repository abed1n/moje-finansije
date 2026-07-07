package me.fit.dto;

import me.fit.model.SavingsGoal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record GoalDto(
        Long id,
        String name,
        BigDecimal targetAmount,
        BigDecimal savedAmount,
        BigDecimal remaining,
        int percent,
        LocalDate deadline,
        boolean achieved,
        Long accountId,
        String accountName) {

    public static GoalDto from(SavingsGoal goal) {
        // Cilj vezan za racun prati njegovo stanje; samostalni cilj prati rucne uplate
        BigDecimal saved = goal.getAccount() != null
                ? goal.getAccount().getBalance().max(BigDecimal.ZERO)
                : goal.getSavedAmount();
        int percent = saved
                .multiply(BigDecimal.valueOf(100))
                .divide(goal.getTargetAmount(), 0, RoundingMode.HALF_UP)
                .intValue();
        BigDecimal remaining = goal.getTargetAmount().subtract(saved).max(BigDecimal.ZERO);
        return new GoalDto(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                saved,
                remaining,
                percent,
                goal.getDeadline(),
                saved.compareTo(goal.getTargetAmount()) >= 0,
                goal.getAccount() != null ? goal.getAccount().getId() : null,
                goal.getAccount() != null ? goal.getAccount().getName() : null);
    }
}
