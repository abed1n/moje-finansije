package me.fit.dto;

import java.util.List;

public record ErrorResponse(String error, List<String> details) {

    public static ErrorResponse of(String error) {
        return new ErrorResponse(error, List.of());
    }
}
