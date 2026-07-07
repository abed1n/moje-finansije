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
        boolean achieved) {

    public static GoalDto from(SavingsGoal goal) {
        int percent = goal.getSavedAmount()
                .multiply(BigDecimal.valueOf(100))
                .divide(goal.getTargetAmount(), 0, RoundingMode.HALF_UP)
                .intValue();
        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getSavedAmount()).max(BigDecimal.ZERO);
        return new GoalDto(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                goal.getSavedAmount(),
                remaining,
                percent,
                goal.getDeadline(),
                goal.getSavedAmount().compareTo(goal.getTargetAmount()) >= 0);
    }
}
