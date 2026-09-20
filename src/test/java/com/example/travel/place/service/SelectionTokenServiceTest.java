package com.example.travel.place.service;

import com.example.travel.place.config.SelectionTokenProperties;
import com.example.travel.place.domain.PlaceRole;
import com.example.travel.place.dto.SelectionTokenPlace;
import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.region.dto.PlaceSearchRegion;
import com.example.travel.region.service.PlaceSearchRegionService;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelectionTokenServiceTest {

	private final PlaceSearchRegionValidator regionValidator = mock(PlaceSearchRegionValidator.class);

	private static final Instant ISSUED_AT = Instant.parse("2026-09-18T00:00:00Z");
	private static final SelectionTokenPlace PLACE = new SelectionTokenPlace(
			42L,
			"KR-26",
			PlaceRole.ATTRACTION,
			"26338954",
			URI.create("https://place.map.kakao.com/26338954"),
			35.1587,
			129.1604);

	@Test
	void issuesAndVerifiesThirtyMinuteToken() {
		String secret = randomSecret();
		SelectionTokenService service = serviceAt(ISSUED_AT, "active", keys(key("active", secret)));

		String token = service.issue(PLACE);

		assertThat(service.verify(token, 42L, "KR-26", PlaceRole.ATTRACTION)).isEqualTo(PLACE);
	}

	@Test
	void issuesAndVerifiesTokenForSelectableOpaqueRegionId() {
		PlaceSearchRegionService regionService = mock(PlaceSearchRegionService.class);
		when(regionService.findById("KR-GWANGJU-URBAN")).thenReturn(Optional.of(
				new PlaceSearchRegion("KR-GWANGJU-URBAN", null, true, false)));
		PlaceSearchRegionValidator catalogValidator = new PlaceSearchRegionValidator(regionService);
		SelectionTokenService service = serviceAt(
				ISSUED_AT, "active", keys(key("active", randomSecret())), catalogValidator);
		SelectionTokenPlace gwangjuPlace = new SelectionTokenPlace(
				42L,
				"KR-GWANGJU-URBAN",
				PlaceRole.ATTRACTION,
				"26338954",
				URI.create("https://place.map.kakao.com/26338954"),
				35.1587,
				126.85);

		String token = service.issue(gwangjuPlace);

		assertThat(service.verify(token, 42L, "KR-GWANGJU-URBAN", PlaceRole.ATTRACTION))
				.isEqualTo(gwangjuPlace);
	}

	@Test
	void rejectsUnknownAndNonSelectableRegionBeforeIssuingToken() {
		PlaceSearchRegionService regionService = mock(PlaceSearchRegionService.class);
		when(regionService.findById("KR-UNKNOWN")).thenReturn(Optional.empty());
		when(regionService.findById("KR-51")).thenReturn(Optional.of(
				new PlaceSearchRegion("KR-51", null, false, false)));
		SelectionTokenService service = serviceAt(
				ISSUED_AT,
				"active",
				keys(key("active", randomSecret())),
				new PlaceSearchRegionValidator(regionService));

		assertThatThrownBy(() -> service.issue(placeIn("KR-UNKNOWN")))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		assertThatThrownBy(() -> service.issue(placeIn("KR-51")))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	void rejectsTokenAtExpirationBoundary() {
		String secret = randomSecret();
		String token = serviceAt(ISSUED_AT, "active", keys(key("active", secret))).issue(PLACE);
		SelectionTokenService verifier = serviceAt(
				ISSUED_AT.plus(Duration.ofMinutes(30)), "active", keys(key("active", secret)));

		assertInvalid(() -> verifier.verify(token, 42L, "KR-26", PlaceRole.ATTRACTION));
	}

	@Test
	void rejectsChangedSignature() {
		SelectionTokenService service = serviceAt(
				ISSUED_AT, "active", keys(key("active", randomSecret())));
		String tampered = changeFirstSignatureCharacter(service.issue(PLACE));

		assertInvalid(() -> service.verify(tampered, 42L, "KR-26", PlaceRole.ATTRACTION));
	}

	@Test
	void rejectsAuthenticatedUserRegionAndRoleMismatch() {
		SelectionTokenService service = serviceAt(
				ISSUED_AT, "active", keys(key("active", randomSecret())));
		String token = service.issue(PLACE);

		assertInvalid(() -> service.verify(token, 43L, "KR-26", PlaceRole.ATTRACTION));
		assertInvalid(() -> service.verify(token, 42L, "KR-11", PlaceRole.ATTRACTION));
		assertInvalid(() -> service.verify(token, 42L, "KR-26", PlaceRole.HOTEL));
	}

	@Test
	void rejectsSignedPayloadWithUnexpectedOrInvalidFields() throws Exception {
		String secret = randomSecret();
		SelectionTokenService service = serviceAt(ISSUED_AT, "active", keys(key("active", secret)));

		String unexpectedClaim = signedToken(secret, builderFor(PLACE).claim("providerDisplayName", "not-allowed"));
		String mismatchedUrl = signedToken(secret,
				builderFor(PLACE).claim("placeUrl", "https://place.map.kakao.com/999"));
		String outsideKorea = signedToken(secret, builderFor(PLACE).claim("latitude", 10.0));

		assertInvalid(() -> service.verify(unexpectedClaim, 42L, "KR-26", PlaceRole.ATTRACTION));
		assertInvalid(() -> service.verify(mismatchedUrl, 42L, "KR-26", PlaceRole.ATTRACTION));
		assertInvalid(() -> service.verify(outsideKorea, 42L, "KR-26", PlaceRole.ATTRACTION));
	}

	@Test
	void rejectsSignedTokenWithUnexpectedProtectedHeader() throws Exception {
		String secret = randomSecret();
		SelectionTokenService service = serviceAt(ISSUED_AT, "active", keys(key("active", secret)));
		SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
				.type(new JOSEObjectType("place-selection+jwt"))
				.keyID("active")
				.customParam("unexpected", "not-allowed")
				.build(), builderFor(PLACE).build());
		token.sign(new MACSigner(Base64.getDecoder().decode(secret)));

		assertInvalid(() -> service.verify(
				token.serialize(), 42L, "KR-26", PlaceRole.ATTRACTION));
	}

	@Test
	void verifiesPreviousKeyButIssuesOnlyWithActiveKey() throws Exception {
		String previousSecret = randomSecret();
		String activeSecret = randomSecret();
		SelectionTokenService previousIssuer = serviceAt(
				ISSUED_AT, "previous", keys(key("previous", previousSecret)));
		String previousToken = previousIssuer.issue(PLACE);
		SelectionTokenService rotated = serviceAt(ISSUED_AT, "active", keys(
				key("active", activeSecret), key("previous", previousSecret)));

		assertThat(rotated.verify(previousToken, 42L, "KR-26", PlaceRole.ATTRACTION)).isEqualTo(PLACE);
		assertThat(SignedJWT.parse(rotated.issue(PLACE)).getHeader().getKeyID()).isEqualTo("active");
	}

	@Test
	void rejectsUnknownKeyAndInvalidKeyConfiguration() {
		String token = serviceAt(ISSUED_AT, "retired", keys(key("retired", randomSecret()))).issue(PLACE);
		SelectionTokenService activeOnly = serviceAt(
				ISSUED_AT, "active", keys(key("active", randomSecret())));

		assertInvalid(() -> activeOnly.verify(token, 42L, "KR-26", PlaceRole.ATTRACTION));
		assertThatThrownBy(() -> serviceAt(ISSUED_AT, "missing", keys(key("active", randomSecret()))))
				.isInstanceOf(IllegalStateException.class);
	}

	private static JWTClaimsSet.Builder builderFor(SelectionTokenPlace place) {
		return new JWTClaimsSet.Builder()
				.issuer("routy")
				.audience("place-selection")
				.subject(Long.toString(place.userId()))
				.issueTime(Date.from(ISSUED_AT))
				.expirationTime(Date.from(ISSUED_AT.plus(Duration.ofMinutes(30))))
				.claim("regionId", place.regionId())
				.claim("placeRole", place.placeRole().name())
				.claim("kakaoPlaceId", place.kakaoPlaceId())
				.claim("placeUrl", place.placeUrl().toString())
				.claim("latitude", place.latitude())
				.claim("longitude", place.longitude());
	}

	private static String signedToken(String secret, JWTClaimsSet.Builder claims) throws Exception {
		SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
				.type(new JOSEObjectType("place-selection+jwt"))
				.keyID("active")
				.build(), claims.build());
		token.sign(new MACSigner(Base64.getDecoder().decode(secret)));
		return token.serialize();
	}

	private SelectionTokenService serviceAt(Instant instant, String activeKeyId,
			List<SelectionTokenProperties.SigningKey> keys) {
		return serviceAt(instant, activeKeyId, keys, regionValidator);
	}

	private SelectionTokenService serviceAt(
			Instant instant,
			String activeKeyId,
			List<SelectionTokenProperties.SigningKey> keys,
			PlaceSearchRegionValidator validator
	) {
		SelectionTokenProperties properties = new SelectionTokenProperties("routy", activeKeyId, keys);
		return new SelectionTokenService(properties, Clock.fixed(instant, ZoneOffset.UTC), validator);
	}

	private static SelectionTokenPlace placeIn(String regionId) {
		return new SelectionTokenPlace(
				42L,
				regionId,
				PlaceRole.ATTRACTION,
				"26338954",
				URI.create("https://place.map.kakao.com/26338954"),
				35.1587,
				126.85);
	}

	@SafeVarargs
	private static List<SelectionTokenProperties.SigningKey> keys(
			SelectionTokenProperties.SigningKey... keys) {
		return List.of(keys);
	}

	private static SelectionTokenProperties.SigningKey key(String id, String secret) {
		return new SelectionTokenProperties.SigningKey(id, secret);
	}

	private static String randomSecret() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

	private static String changeFirstSignatureCharacter(String token) {
		int signatureStart = token.lastIndexOf('.') + 1;
		char original = token.charAt(signatureStart);
		return token.substring(0, signatureStart) + (original == 'A' ? 'B' : 'A')
				+ token.substring(signatureStart + 1);
	}

	private static void assertInvalid(ThrowingOperation operation) {
		assertThatThrownBy(operation::run)
				.isExactlyInstanceOf(SelectionTokenService.InvalidSelectionTokenException.class);
	}

	@FunctionalInterface
	private interface ThrowingOperation {
		void run();
	}
}
