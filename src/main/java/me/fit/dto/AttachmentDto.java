package me.fit.dto;

import me.fit.model.UploadedFile;

import java.time.Instant;

public record AttachmentDto(Long id, String filename, String contentType, long size, Instant uploadedAt) {

    public static AttachmentDto from(UploadedFile file) {
        return new AttachmentDto(file.getId(), file.getFilename(), file.getContentType(),
                file.getSize(), file.getUploadedAt());
    }
}
