package com.example.travel.global.exception;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        ErrorCode code = exception.errorCode();
        ErrorResponse response = new ErrorResponse(
                code,
                code.message(),
                List.of(),
                exception.details(),
                exception.adjustments(),
                exception.retryAfterSeconds()
        );

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status());
        if (code == ErrorCode.AUTHENTICATION_REQUIRED) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        if (code == ErrorCode.RATE_LIMIT_EXCEEDED) {
            builder.header(HttpHeaders.RETRY_AFTER, exception.retryAfterSeconds().toString());
        }
        return builder.body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<ApiFieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return validationResponse(fieldErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException exception) {
        if (exception.isForReturnValue()) {
            return internalServerError();
        }

        List<ApiFieldError> fieldErrors = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            String field = result.getMethodParameter().getParameterName();
            if (field == null) {
                field = "argument" + result.getMethodParameter().getParameterIndex();
            }
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                fieldErrors.add(new ApiFieldError(field, reasonFor(error.getCodes())));
            }
        }
        return validationResponse(fieldErrors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
        return validationResponse(List.of(new ApiFieldError(exception.getHeaderName(), ValidationReason.REQUIRED)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        return validationResponse(List.of(new ApiFieldError(exception.getParameterName(), ValidationReason.REQUIRED)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return validationResponse(List.of(new ApiFieldError(exception.getName(), ValidationReason.INVALID_FORMAT)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage() {
        return validationResponse(List.of());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleResourceNotFound() {
        ErrorCode code = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException() {
        return internalServerError();
    }

    private ResponseEntity<ErrorResponse> validationResponse(List<ApiFieldError> fieldErrors) {
        return ResponseEntity.badRequest().body(ErrorResponse.validation(fieldErrors));
    }

    private ResponseEntity<ErrorResponse> internalServerError() {
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
    }

    private ApiFieldError toFieldError(FieldError error) {
        return new ApiFieldError(error.getField(), reasonFor(error.getCodes()));
    }

    private ValidationReason reasonFor(String[] codes) {
        List<String> codeList = codes == null ? List.of() : Arrays.asList(codes);
        if (hasCode(codeList, "NotNull", "NotBlank", "NotEmpty")) {
            return ValidationReason.REQUIRED;
        }
        if (hasCode(codeList, "Pattern", "Email", "typeMismatch")) {
            return ValidationReason.INVALID_FORMAT;
        }
        if (hasCode(codeList, "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero")) {
            return ValidationReason.OUT_OF_RANGE;
        }
        if (hasCode(codeList, "Size")) {
            return ValidationReason.INVALID_SIZE;
        }
        return ValidationReason.INVALID_COMBINATION;
    }

    private boolean hasCode(List<String> codes, String... candidates) {
        return codes.stream().anyMatch(code -> Arrays.stream(candidates)
                .anyMatch(candidate -> code.equals(candidate) || code.startsWith(candidate + ".")));
    }
}
