package com.example.travel.global.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

	private static final Instant ISSUED_AT = Instant.parse("2026-09-16T00:00:00Z");

	@Test
	void issuesAndVerifiesTokenWithOneHourLifetime() {
		JwtService service = serviceAt(ISSUED_AT, randomSecret());

		String token = service.issue(42L);

		assertThat(service.verify(token).userId()).isEqualTo(42L);
		assertThat(service.expiresInSeconds()).isEqualTo(3600L);
	}

	@Test
	void rejectsExpiredToken() {
		String secret = randomSecret();
		String token = serviceAt(ISSUED_AT, secret).issue(42L);
		JwtService verifier = serviceAt(ISSUED_AT.plus(Duration.ofHours(1)), secret);

		assertThatThrownBy(() -> verifier.verify(token))
				.isInstanceOf(JwtService.InvalidJwtException.class);
	}

	@Test
	void rejectsTokenWithChangedSignature() {
		JwtService service = serviceAt(ISSUED_AT, randomSecret());
		String token = service.issue(42L);
		String tampered = changeFirstSignatureCharacter(token);

		assertThatThrownBy(() -> service.verify(tampered))
				.isInstanceOf(JwtService.InvalidJwtException.class);
	}

	private static String changeFirstSignatureCharacter(String token) {
		int signatureStart = token.lastIndexOf('.') + 1;
		char original = token.charAt(signatureStart);
		return token.substring(0, signatureStart) + (original == 'A' ? 'B' : 'A')
				+ token.substring(signatureStart + 1);
	}

	@Test
	void rejectsSignatureFromUnknownKey() {
		String token = serviceAt(ISSUED_AT, randomSecret()).issue(42L);

		assertThatThrownBy(() -> serviceAt(ISSUED_AT, randomSecret()).verify(token))
				.isInstanceOf(JwtService.InvalidJwtException.class);
	}

	private static JwtService serviceAt(Instant instant, String secret) {
		JwtProperties properties = new JwtProperties(
				"routy", Duration.ofHours(1), "active", List.of(new JwtProperties.SigningKey("active", secret)));
		return new JwtService(properties, Clock.fixed(instant, ZoneOffset.UTC));
	}

	private static String randomSecret() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
