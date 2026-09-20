package com.example.travel.place.service;

import com.example.travel.place.config.SelectionTokenProperties;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.PlaceSearchRegionCriteria;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.global.exception.ApiException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SelectionTokenService {

	private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS256;
	private static final JOSEObjectType TYPE = new JOSEObjectType("place-selection+jwt");
	private static final String AUDIENCE = "place-selection";
	private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
	private static final Set<String> CLAIM_NAMES = Set.of(
			"iss", "aud", "sub", "iat", "exp", "regionId", "placeRole",
			"kakaoPlaceId", "placeUrl", "latitude", "longitude");
	private static final Set<String> HEADER_NAMES = Set.of("alg", "typ", "kid");

	private final SelectionTokenProperties properties;
	private final Clock clock;
	private final PlaceSearchRegionValidator regionValidator;
	private final Map<String, byte[]> keys;

	public SelectionTokenService(
			SelectionTokenProperties properties,
			Clock clock,
			PlaceSearchRegionValidator regionValidator
	) {
		this.properties = properties;
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
		this.regionValidator = Objects.requireNonNull(regionValidator, "regionValidator must not be null");
		this.keys = validateAndDecode(properties);
	}

	public String issue(SelectionTokenPlace place) {
		Objects.requireNonNull(place, "place must not be null");
		regionValidator.validate(new PlaceSearchRegionCriteria(
				place.regionId(), null, place.placeRole()));
		Instant issuedAt = clock.instant();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(properties.issuer())
				.audience(AUDIENCE)
				.subject(Long.toString(place.userId()))
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(issuedAt.plus(TOKEN_TTL)))
				.claim("regionId", place.regionId())
				.claim("placeRole", place.placeRole().name())
				.claim("kakaoPlaceId", place.kakaoPlaceId())
				.claim("placeUrl", place.placeUrl().toString())
				.claim("latitude", place.latitude())
				.claim("longitude", place.longitude())
				.build();
		SignedJWT token = new SignedJWT(new JWSHeader.Builder(ALGORITHM)
				.type(TYPE)
				.keyID(properties.activeKeyId())
				.build(), claims);
		return jose(() -> {
			token.sign(new MACSigner(keys.get(properties.activeKeyId())));
			return token.serialize();
		});
	}

	public SelectionTokenPlace verify(String serializedToken, long authenticatedUserId,
			String expectedRegionId, PlaceRole expectedRole) {
		try {
			SignedJWT token = SignedJWT.parse(serializedToken);
			JWSHeader header = token.getHeader();
			byte[] key = keys.get(header.getKeyID());
			if (!HEADER_NAMES.equals(header.getIncludedParams())
					|| !ALGORITHM.equals(header.getAlgorithm()) || !TYPE.equals(header.getType())
					|| key == null || !token.verify(new MACVerifier(key))) {
				throw new InvalidSelectionTokenException();
			}

			JWTClaimsSet claims = token.getJWTClaimsSet();
			Instant issuedAt = requireDate(claims.getIssueTime());
			Instant expiresAt = requireDate(claims.getExpirationTime());
			Instant now = clock.instant();
			if (!CLAIM_NAMES.equals(claims.getClaims().keySet())
					|| !properties.issuer().equals(claims.getIssuer())
					|| !List.of(AUDIENCE).equals(claims.getAudience())
					|| now.isBefore(issuedAt) || !expiresAt.equals(issuedAt.plus(TOKEN_TTL))
					|| !now.isBefore(expiresAt)) {
				throw new InvalidSelectionTokenException();
			}

			SelectionTokenPlace place = new SelectionTokenPlace(
					Long.parseLong(claims.getSubject()),
					claims.getStringClaim("regionId"),
					PlaceRole.valueOf(claims.getStringClaim("placeRole")),
					claims.getStringClaim("kakaoPlaceId"),
					URI.create(claims.getStringClaim("placeUrl")),
					claims.getDoubleClaim("latitude"),
					claims.getDoubleClaim("longitude"));
			regionValidator.validate(new PlaceSearchRegionCriteria(
					place.regionId(), null, place.placeRole()));
			if (place.userId() != authenticatedUserId
					|| !place.regionId().equals(expectedRegionId)
					|| place.placeRole() != expectedRole) {
				throw new InvalidSelectionTokenException();
			}
			return place;
		} catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException
				| ApiException exception) {
			throw new InvalidSelectionTokenException();
		}
	}

	private static Instant requireDate(Date value) {
		if (value == null) {
			throw new InvalidSelectionTokenException();
		}
		return value.toInstant();
	}

	private static Map<String, byte[]> validateAndDecode(SelectionTokenProperties properties) {
		if (properties == null || properties.issuer() == null || properties.issuer().isBlank()) {
			throw new IllegalStateException("selection token issuer must be configured");
		}
		Map<String, byte[]> decodedKeys;
		try {
			decodedKeys = properties.keys().stream().collect(Collectors.toUnmodifiableMap(
					SelectionTokenProperties.SigningKey::id,
					key -> decodeSecret(key.secret())));
		} catch (NullPointerException | IllegalArgumentException exception) {
			throw new IllegalStateException("selection token key IDs must be unique and non-null", exception);
		}
		if (properties.activeKeyId() == null || properties.activeKeyId().isBlank()
				|| !decodedKeys.containsKey(properties.activeKeyId())) {
			throw new IllegalStateException("selection token active key ID must match a configured key");
		}
		if (decodedKeys.keySet().stream().anyMatch(String::isBlank)) {
			throw new IllegalStateException("selection token key IDs must not be blank");
		}
		return decodedKeys;
	}

	private static byte[] decodeSecret(String secret) {
		try {
			byte[] decoded = Base64.getDecoder().decode(secret.getBytes(StandardCharsets.US_ASCII));
			if (decoded.length < 32) {
				throw new IllegalStateException("selection token keys must be at least 256 bits");
			}
			return decoded;
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new IllegalStateException("selection token keys must use Base64", exception);
		}
	}

	private static <T> T jose(JoseOperation<T> operation) {
		try {
			return operation.run();
		} catch (JOSEException exception) {
			throw new IllegalStateException("selection token operation failed", exception);
		}
	}

	@FunctionalInterface
	private interface JoseOperation<T> {
		T run() throws JOSEException;
	}

	public static final class InvalidSelectionTokenException extends RuntimeException {
	}
}
