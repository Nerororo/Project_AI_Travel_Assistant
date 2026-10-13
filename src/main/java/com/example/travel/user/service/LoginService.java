package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.security.JwtService;
import com.example.travel.user.domain.User;
import com.example.travel.user.dto.LoginResponse;
import com.example.travel.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class LoginService {
	private static final String DUMMY_PASSWORD_HASH =
			new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthAttemptLimitService attemptLimits;

	public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			AuthAttemptLimitService attemptLimits) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.attemptLimits = attemptLimits;
	}

	public LoginResponse login(String email, String password, String clientIp) {
		attemptLimits.acquireLogin(clientIp, email);
		User user = userRepository.findByEmail(email.toLowerCase(Locale.ROOT)).orElse(null);
		if (!passwordEncoder.matches(password, user == null ? DUMMY_PASSWORD_HASH : user.passwordHash())
				|| user == null) {
			throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
		}

		String token = jwtService.issue(user.id());
		return new LoginResponse(token, "Bearer", jwtService.expiresInSeconds());
	}

	@Transactional(readOnly = true)
	public boolean userExists(long userId) {
		return userRepository.existsById(userId);
	}
}
