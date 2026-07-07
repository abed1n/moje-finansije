package me.fit.dto;

import java.util.List;

public record ImportPreviewDto(List<ImportRowDto> rows, List<String> skipped) {
}
