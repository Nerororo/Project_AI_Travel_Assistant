package com.example.travel.global.exception;

public record ApiFieldError(String field, ValidationReason reason) implements Comparable<ApiFieldError> {

    @Override
    public int compareTo(ApiFieldError other) {
        int fieldComparison = field.compareTo(other.field);
        if (fieldComparison != 0) {
            return fieldComparison;
        }
        return reason.name().compareTo(other.reason.name());
    }
}
