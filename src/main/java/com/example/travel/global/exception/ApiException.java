package com.example.travel.global.exception;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final ErrorDetails details;
    private final List<String> adjustments;
    private final Long retryAfterSeconds;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null, List.of(), null);
    }

    public ApiException(
            ErrorCode errorCode,
            ErrorDetails details,
            List<String> adjustments,
            Long retryAfterSeconds
    ) {
        super(errorCode.message());
        this.errorCode = Objects.requireNonNull(errorCode);
        this.details = details;
        this.adjustments = List.copyOf(new LinkedHashSet<>(adjustments));
        this.retryAfterSeconds = validateRetryAfter(errorCode, retryAfterSeconds);
    }

    private static Long validateRetryAfter(ErrorCode errorCode, Long retryAfterSeconds) {
        if (errorCode == ErrorCode.RATE_LIMIT_EXCEEDED) {
            if (retryAfterSeconds == null || retryAfterSeconds < 1) {
                throw new IllegalArgumentException("RATE_LIMIT_EXCEEDED requires a positive retryAfterSeconds");
            }
            return retryAfterSeconds;
        }
        if (retryAfterSeconds != null) {
            throw new IllegalArgumentException("retryAfterSeconds is only allowed for RATE_LIMIT_EXCEEDED");
        }
        return null;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public ErrorDetails details() {
        return details;
    }

    public List<String> adjustments() {
        return adjustments;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
