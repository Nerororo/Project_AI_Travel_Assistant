package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.security.JwtService;
import com.example.travel.user.domain.User;
import com.example.travel.user.dto.LoginResponse;
import com.example.travel.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class LoginService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional(readOnly = true)
	public LoginResponse login(String email, String password) {
		User user = userRepository.findByEmail(email.toLowerCase(Locale.ROOT))
				.filter(found -> passwordEncoder.matches(password, found.passwordHash()))
				.orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED));

		String token = jwtService.issue(user.id());
		return new LoginResponse(token, "Bearer", jwtService.expiresInSeconds());
	}

	@Transactional(readOnly = true)
	public boolean userExists(long userId) {
		return userRepository.existsById(userId);
	}
}
