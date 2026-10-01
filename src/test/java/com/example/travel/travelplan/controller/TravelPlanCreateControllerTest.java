package com.example.travel.travelplan.controller;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.exception.GlobalExceptionHandler;
import com.example.travel.global.security.AuthenticatedUser;
import com.example.travel.route.algorithm.TravelMode;
import com.example.travel.travelplan.dto.TravelPlanCreateApiResponse;
import com.example.travel.travelplan.service.TravelPlanCreateApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelPlanCreateControllerTest {
    private static final UUID KEY = new UUID(0, 1);
    private TravelPlanCreateApiService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(TravelPlanCreateApiService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TravelPlanCreateController(service))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }
                    @Override public Object resolveArgument(MethodParameter parameter,
                            ModelAndViewContainer container, NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory factory) {
                        return new AuthenticatedUser(7L);
                    }
                }).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsCreatedLocationAndSavedOnlyFields() throws Exception {
        when(service.create(eq(7L), eq(KEY), any())).thenReturn(new TravelPlanCreateApiResponse(
                42, "Trip", new TravelPlanCreateApiResponse.Region("KR-30", "대전광역시"),
                TravelMode.CAR, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1),
                List.of("ESTIMATED_TRAVEL_TIMES_USED"), List.of()));

        mvc.perform(post("/api/travel-plans").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/travel-plans/42"))
                .andExpect(jsonPath("$.travelPlanId").value(42))
                .andExpect(jsonPath("$.warnings[0]").value("ESTIMATED_TRAVEL_TIMES_USED"))
                .andExpect(jsonPath("$.coordinate").doesNotExist());
    }

    @Test
    void mapsCapacityAndProviderFailureWithoutSavingResponse() throws Exception {
        when(service.create(eq(7L), eq(KEY), any()))
                .thenThrow(new ApiException(ErrorCode.PLAN_CAPACITY_EXCEEDED))
                .thenThrow(new ApiException(ErrorCode.ROUTE_PROVIDER_UNAVAILABLE));
        for (String code : List.of("PLAN_CAPACITY_EXCEEDED", "ROUTE_PROVIDER_UNAVAILABLE")) {
            mvc.perform(post("/api/travel-plans").header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body()))
                    .andExpect(status().is(code.equals("PLAN_CAPACITY_EXCEEDED") ? 422 : 503))
                    .andExpect(jsonPath("$.code").value(code));
        }
    }

    @Test
    void mapsDuplicateRequestStatesToConflict() throws Exception {
        when(service.create(eq(7L), eq(KEY), any()))
                .thenThrow(new ApiException(ErrorCode.REQUEST_IN_PROGRESS))
                .thenThrow(new ApiException(ErrorCode.REQUEST_ALREADY_COMPLETED));
        for (String code : List.of("REQUEST_IN_PROGRESS", "REQUEST_ALREADY_COMPLETED")) {
            mvc.perform(post("/api/travel-plans").header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(body()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(code));
        }
    }

    @Test
    void requiresIdempotencyKey() throws Exception {
        mvc.perform(post("/api/travel-plans").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
        verifyNoInteractions(service);
    }

    private static String body() {
        return """
                {"title":"Trip","regionId":"KR-30","travelMode":"CAR",
                 "startDate":"2026-10-01","endDate":"2026-10-01",
                 "startBoundarySelectionToken":"start","endBoundarySelectionToken":"end",
                 "days":[{"date":"2026-10-01","activityStartTime":"09:00","activityEndTime":"20:00"}],
                 "places":[],"foods":["menu"],"meals":[]}
                """;
    }
}
