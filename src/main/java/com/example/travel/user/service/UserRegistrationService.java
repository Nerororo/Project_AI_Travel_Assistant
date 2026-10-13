package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.user.domain.User;
import com.example.travel.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Locale;

@Service
public class UserRegistrationService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthAttemptLimitService attemptLimits;

	public UserRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthAttemptLimitService attemptLimits) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.attemptLimits = attemptLimits;
	}

	public void register(String email, String password, String clientIp) {
		String normalizedEmail = email.toLowerCase(Locale.ROOT);
		attemptLimits.acquireRegistration(clientIp, normalizedEmail);
		String encodedPassword = passwordEncoder.encode(password);
		if (userRepository.existsByEmail(normalizedEmail)) {
			throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		try {
			userRepository.saveAndFlush(new User(normalizedEmail, encodedPassword));
		} catch (DataIntegrityViolationException exception) {
			if (isDuplicateKey(exception)) {
				throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
			}
			throw exception;
		}
	}

	private boolean isDuplicateKey(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == 1062) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}
}
