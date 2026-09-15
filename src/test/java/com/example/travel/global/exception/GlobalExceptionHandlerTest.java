package com.example.travel.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationErrorsUseStableReasonsAndDeterministicOrder() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"", "days":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("days"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[1].reason").value("REQUIRED"))
                .andExpect(jsonPath("$.details").value(nullValue()))
                .andExpect(jsonPath("$.adjustments").isEmpty())
                .andExpect(jsonPath("$.retryAfterSeconds").value(nullValue()));
    }

    @Test
    void malformedJsonDoesNotExposeParserMessage() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.details").value(nullValue()))
                .andExpect(jsonPath("$.adjustments").isEmpty())
                .andExpect(jsonPath("$.retryAfterSeconds").value(nullValue()));
    }

    @Test
    void missingHeaderUsesRequiredReason() throws Exception {
        mockMvc.perform(get("/test/required-header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("REQUIRED"));
    }

    @Test
    void methodValidationUsesParameterNameAndStableReason() throws Exception {
        mockMvc.perform(get("/test/days").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("days"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("OUT_OF_RANGE"));
    }

    @ParameterizedTest
    @MethodSource("mappedErrors")
    void apiExceptionsMapToContract(ErrorCode errorCode, int expectedStatus) throws Exception {
        mockMvc.perform(get("/test/errors/{code}", errorCode.name()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.message()))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.details").value(nullValue()))
                .andExpect(jsonPath("$.adjustments").isEmpty())
                .andExpect(jsonPath("$.retryAfterSeconds").value(nullValue()));
    }

    @Test
    void authenticationErrorIncludesBearerChallenge() throws Exception {
        mockMvc.perform(get("/test/errors/{code}", ErrorCode.AUTHENTICATION_REQUIRED.name()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void rateLimitUsesSameRetryValueInHeaderAndBody() throws Exception {
        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(42));
    }

    @Test
    void structuredDetailsAndAdjustmentOrderArePreserved() throws Exception {
        mockMvc.perform(get("/test/capacity"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PLAN_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.details.exceededMinutes").value(30))
                .andExpect(jsonPath("$.adjustments[0]").value("CHANGE_END_TIME"))
                .andExpect(jsonPath("$.adjustments[1]").value("REMOVE_PLACE"))
                .andExpect(jsonPath("$.adjustments.length()").value(2));
    }

    @Test
    void unexpectedExceptionReturnsSafeFixedResponse() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.details").value(nullValue()))
                .andExpect(jsonPath("$.adjustments").isEmpty())
                .andExpect(jsonPath("$.retryAfterSeconds").value(nullValue()));
    }

    @Test
    void missingSpringResourceReturnsSafeCommon404() throws Exception {
        mockMvc.perform(get("/test/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.details").value(nullValue()))
                .andExpect(jsonPath("$.adjustments").isEmpty())
                .andExpect(jsonPath("$.retryAfterSeconds").value(nullValue()));
    }

    private static Stream<Arguments> mappedErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.AUTHENTICATION_REQUIRED, 401),
                Arguments.of(ErrorCode.ACCESS_DENIED, 403),
                Arguments.of(ErrorCode.TRAVEL_PLAN_NOT_FOUND, 404),
                Arguments.of(ErrorCode.REQUEST_IN_PROGRESS, 409),
                Arguments.of(ErrorCode.REQUEST_ALREADY_COMPLETED, 409),
                Arguments.of(ErrorCode.ROUTE_NOT_FOUND, 422),
                Arguments.of(ErrorCode.PLAN_CAPACITY_EXCEEDED, 422),
                Arguments.of(ErrorCode.PLACE_PROVIDER_UNAVAILABLE, 503),
                Arguments.of(ErrorCode.ROUTE_PROVIDER_UNAVAILABLE, 503),
                Arguments.of(ErrorCode.AI_UNAVAILABLE, 503),
                Arguments.of(ErrorCode.AI_RESPONSE_INVALID, 503)
        );
    }

    private record ValidationRequest(
            @NotBlank String title,
            @Min(1) @Max(7) int days
    ) {
    }

    private record CapacityDetails(int exceededMinutes) implements ErrorDetails {
    }

    @RestController
    @RequestMapping("/test")
    private static class TestController {

        @PostMapping("/validation")
        void validate(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/required-header")
        void requiredHeader(@RequestHeader("Idempotency-Key") String requestId) {
        }

        @GetMapping("/days")
        void days(@RequestParam @Min(1) int days) {
        }

        @GetMapping("/errors/{code}")
        void error(@PathVariable ErrorCode code) {
            throw new ApiException(code);
        }

        @GetMapping("/rate-limit")
        void rateLimit() {
            throw new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED, null, List.of(), 42L);
        }

        @GetMapping("/capacity")
        void capacity() {
            throw new ApiException(
                    ErrorCode.PLAN_CAPACITY_EXCEEDED,
                    new CapacityDetails(30),
                    List.of("CHANGE_END_TIME", "REMOVE_PLACE", "CHANGE_END_TIME"),
                    null
            );
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("sensitive internal message");
        }

        @GetMapping("/missing-resource")
        void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/private/request/path", "hidden internal detail");
        }
    }
}
