package com.example.travel.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    TRAVEL_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "같은 요청을 처리 중입니다."),
    REQUEST_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
    ROUTE_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "이동 경로를 찾을 수 없습니다."),
    PLAN_CAPACITY_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "일정이 허용 시간을 초과합니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "호출 한도를 초과했습니다."),
    PLACE_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "장소 검색 서비스를 일시적으로 사용할 수 없습니다."),
    ROUTE_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "경로 서비스를 일시적으로 사용할 수 없습니다."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스를 일시적으로 사용할 수 없습니다."),
    AI_RESPONSE_INVALID(HttpStatus.SERVICE_UNAVAILABLE, "AI 응답을 처리할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
