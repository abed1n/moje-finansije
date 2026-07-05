package me.fit.dto;

import me.fit.model.Tag;
import me.fit.model.Transaction;
import me.fit.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TransactionDto(Long id, BigDecimal amount, LocalDate date, TransactionType type,
                             String description, Long accountId, String accountName,
                             Long categoryId, String categoryName, String categoryColor,
                             List<String> tags, List<AttachmentDto> attachments, Instant createdAt) {

    public static TransactionDto from(Transaction t) {
        return new TransactionDto(
                t.getId(), t.getAmount(), t.getDate(), t.getType(), t.getDescription(),
                t.getAccount().getId(), t.getAccount().getName(),
                t.getCategory() != null ? t.getCategory().getId() : null,
                t.getCategory() != null ? t.getCategory().getName() : null,
                t.getCategory() != null ? t.getCategory().getColor() : null,
                t.getTags().stream().map(Tag::getName).toList(),
                t.getUploadedFiles().stream().map(AttachmentDto::from).toList(),
                t.getCreatedAt());
    }
}
