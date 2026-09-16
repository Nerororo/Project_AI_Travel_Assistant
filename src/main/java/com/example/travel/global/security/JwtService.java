package com.example.travel.global.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JwtService {

	private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS256;

	private final JwtProperties properties;
	private final Clock clock;
	private final Map<String, byte[]> keys;

	public JwtService(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.keys = properties.keys().stream().collect(Collectors.toUnmodifiableMap(
				JwtProperties.SigningKey::id,
				key -> decodeAndValidate(key.secret())
		));
		if (properties.issuer() == null || properties.issuer().isBlank()) {
			throw new IllegalStateException("JWT issuer must be configured");
		}
		if (properties.accessTokenTtl() == null || properties.accessTokenTtl().isNegative()
				|| properties.accessTokenTtl().isZero()) {
			throw new IllegalStateException("JWT access token TTL must be positive");
		}
		if (!keys.containsKey(properties.activeKeyId())) {
			throw new IllegalStateException("JWT active key ID must match a configured key");
		}
	}

	public String issue(long userId) {
		Instant issuedAt = clock.instant();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(Long.toString(userId))
				.issuer(properties.issuer())
				.issueTime(Date.from(issuedAt))
				.expirationTime(Date.from(issuedAt.plus(properties.accessTokenTtl())))
				.jwtID(UUID.randomUUID().toString())
				.build();
		SignedJWT token = new SignedJWT(
				new JWSHeader.Builder(ALGORITHM).keyID(properties.activeKeyId()).build(), claims);
		return jose(() -> {
			token.sign(new MACSigner(keys.get(properties.activeKeyId())));
			return token.serialize();
		});
	}

	public AuthenticatedUser verify(String serializedToken) {
		try {
			SignedJWT token = SignedJWT.parse(serializedToken);
			JWSHeader header = token.getHeader();
			byte[] key = keys.get(header.getKeyID());
			if (!ALGORITHM.equals(header.getAlgorithm()) || key == null || !token.verify(new MACVerifier(key))) {
				throw new InvalidJwtException();
			}

			JWTClaimsSet claims = token.getJWTClaimsSet();
			Instant now = clock.instant();
			if (!properties.issuer().equals(claims.getIssuer())
					|| claims.getIssueTime() == null || claims.getExpirationTime() == null
					|| claims.getJWTID() == null || claims.getJWTID().isBlank()
					|| !claims.getIssueTime().toInstant().isBefore(claims.getExpirationTime().toInstant())
					|| now.isBefore(claims.getIssueTime().toInstant())
					|| !now.isBefore(claims.getExpirationTime().toInstant())) {
				throw new InvalidJwtException();
			}
			long userId = Long.parseLong(claims.getSubject());
			if (userId <= 0) {
				throw new InvalidJwtException();
			}
			return new AuthenticatedUser(userId);
		} catch (ParseException | JOSEException | NumberFormatException | NullPointerException exception) {
			throw new InvalidJwtException();
		}
	}

	public long expiresInSeconds() {
		return properties.accessTokenTtl().toSeconds();
	}

	private static byte[] decodeAndValidate(String secret) {
		try {
			byte[] decoded = Base64.getDecoder().decode(secret.getBytes(StandardCharsets.US_ASCII));
			if (decoded.length < 32) {
				throw new IllegalStateException("JWT signing keys must be at least 256 bits");
			}
			return decoded;
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("JWT signing keys must use Base64", exception);
		}
	}

	private static <T> T jose(JoseOperation<T> operation) {
		try {
			return operation.run();
		} catch (JOSEException exception) {
			throw new IllegalStateException("JWT operation failed", exception);
		}
	}

	@FunctionalInterface
	private interface JoseOperation<T> {
		T run() throws JOSEException;
	}

	public static final class InvalidJwtException extends RuntimeException {
	}
}
