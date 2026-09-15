package com.example.travel.global.exception;

import java.util.List;

public record ErrorResponse(
        ErrorCode code,
        String message,
        List<ApiFieldError> fieldErrors,
        ErrorDetails details,
        List<String> adjustments,
        Long retryAfterSeconds
) {

    public ErrorResponse {
        fieldErrors = List.copyOf(fieldErrors);
        adjustments = List.copyOf(adjustments);
    }

    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(code, code.message(), List.of(), null, List.of(), null);
    }

    public static ErrorResponse validation(List<ApiFieldError> fieldErrors) {
        return new ErrorResponse(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(),
                fieldErrors.stream().distinct().sorted().toList(),
                null,
                List.of(),
                null
        );
    }
}
