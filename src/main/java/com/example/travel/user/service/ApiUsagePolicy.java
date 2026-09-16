package com.example.travel.user.service;

import com.example.travel.user.domain.UsageScopeType;
import com.example.travel.user.domain.UsageWindowType;
import com.example.travel.user.dto.UsageFeature;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class ApiUsagePolicy {

	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final String SERVICE_SCOPE_ID = "GLOBAL";

	private final Clock clock;

	public ApiUsagePolicy(Clock clock) {
		this.clock = clock;
	}

	public List<UsageWindow> windows(long userId, UsageFeature feature) {
		if (userId <= 0) {
			throw new IllegalArgumentException("userId must be positive");
		}
		FeatureLimits limits = FeatureLimits.forFeature(feature);
		Instant now = clock.instant();
		Instant minuteStart = now.truncatedTo(ChronoUnit.MINUTES);
		ZonedDateTime seoulNow = now.atZone(SEOUL);
		Instant dayStart = seoulNow.toLocalDate().atStartOfDay(SEOUL).toInstant();
		Instant nextDay = seoulNow.toLocalDate().plusDays(1).atStartOfDay(SEOUL).toInstant();

		List<UsageWindow> windows = new ArrayList<>();
		windows.add(new UsageWindow(UsageScopeType.USER, Long.toString(userId), UsageWindowType.MINUTE,
				minuteStart, minuteStart.plus(1, ChronoUnit.MINUTES), limits.userMinute()));
		windows.add(new UsageWindow(UsageScopeType.USER, Long.toString(userId), UsageWindowType.DAY,
				dayStart, nextDay, limits.userDay()));
		if (limits.serviceDay() != null) {
			windows.add(new UsageWindow(UsageScopeType.SERVICE, SERVICE_SCOPE_ID, UsageWindowType.DAY,
					dayStart, nextDay, limits.serviceDay()));
		}
		return List.copyOf(windows);
	}

	public long retryAfterSeconds(UsageWindow window) {
		long seconds = ChronoUnit.SECONDS.between(clock.instant(), window.expiresAt());
		return Math.max(1, seconds);
	}

	public record UsageWindow(
			UsageScopeType scopeType,
			String scopeId,
			UsageWindowType windowType,
			Instant windowStart,
			Instant expiresAt,
			long limit
	) {
	}

	private record FeatureLimits(long userMinute, long userDay, Long serviceDay) {

		private static FeatureLimits forFeature(UsageFeature feature) {
			return switch (feature) {
				case PLACE_SEARCH -> new FeatureLimits(20, 300, 90_000L);
				case CAR_ROUTE -> new FeatureLimits(60, 120, 9_000L);
				case PUBLIC_TRANSIT_ROUTE -> new FeatureLimits(60, 120, 900L);
				case AI_REGION_RECOMMENDATION -> new FeatureLimits(2, 10, null);
				case AI_MENU_ANALYSIS -> new FeatureLimits(3, 15, null);
			};
		}
	}
}
