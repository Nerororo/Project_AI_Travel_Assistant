package com.example.travel.travelplan.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.travelplan.domain.TravelPlanShare;
import com.example.travel.travelplan.dto.TravelPlanDetailResponse;
import com.example.travel.travelplan.dto.TravelPlanShareResponse;
import com.example.travel.travelplan.repository.TravelPlanRepository;
import com.example.travel.travelplan.repository.TravelPlanShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class TravelPlanShareService {
    private static final Duration LIFETIME = Duration.ofDays(30);
    private final TravelPlanRepository plans;
    private final TravelPlanShareRepository shares;
    private final TravelPlanReadService reads;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TravelPlanShareService(TravelPlanRepository plans, TravelPlanShareRepository shares,
            TravelPlanReadService reads, Clock clock) {
        this.plans = plans;
        this.shares = shares;
        this.reads = reads;
        this.clock = clock;
    }

    @Transactional
    public TravelPlanShareResponse issue(long userId, long travelPlanId) {
        plans.findOwnedForUpdate(travelPlanId, userId).orElseGet(() -> {
            throw new ApiException(plans.existsById(travelPlanId)
                    ? ErrorCode.ACCESS_DENIED : ErrorCode.TRAVEL_PLAN_NOT_FOUND);
        });
        shares.findById(travelPlanId).ifPresent(shares::delete);
        shares.flush();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(LIFETIME);
        shares.save(new TravelPlanShare(travelPlanId, hash(token), createdAt, expiresAt));
        return new TravelPlanShareResponse(token, expiresAt);
    }

    @Transactional(readOnly = true)
    public TravelPlanDetailResponse read(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw missing();
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(token);
        } catch (IllegalArgumentException exception) {
            throw missing();
        }
        if (bytes.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token))
            throw missing();
        TravelPlanShare share = shares.findByTokenHash(hash(token)).orElseThrow(TravelPlanShareService::missing);
        if (!clock.instant().isBefore(share.expiresAt())) throw missing();
        return reads.sharedDetail(share.travelPlanId());
    }

    private static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ApiException missing() { return new ApiException(ErrorCode.TRAVEL_PLAN_NOT_FOUND); }
}
