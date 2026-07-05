package me.fit.dto;

import me.fit.model.Category;
import me.fit.model.TransactionType;

public record CategoryDto(Long id, String name, TransactionType type, String color, String icon) {

    public static CategoryDto from(Category category) {
        if (category == null) {
            return null;
        }
        return new CategoryDto(category.getId(), category.getName(), category.getType(),
                category.getColor(), category.getIcon());
    }
}
