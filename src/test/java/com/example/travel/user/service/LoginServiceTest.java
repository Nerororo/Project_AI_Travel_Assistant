package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.security.JwtService;
import com.example.travel.user.domain.User;
import com.example.travel.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final JwtService jwtService = mock(JwtService.class);
	private final LoginService loginService = new LoginService(userRepository, passwordEncoder, jwtService);

	@Test
	void normalizesEmailAndReturnsBearerTokenForMatchingPassword() throws Exception {
		User user = userWithId(7L);
		when(userRepository.findByEmail("member@example.test")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("submitted-password", user.passwordHash())).thenReturn(true);
		when(jwtService.issue(7L)).thenReturn("issued-token");
		when(jwtService.expiresInSeconds()).thenReturn(3600L);

		var response = loginService.login("MEMBER@EXAMPLE.TEST", "submitted-password");

		assertThat(response.accessToken()).isEqualTo("issued-token");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresInSeconds()).isEqualTo(3600L);
	}

	@Test
	void hidesWhetherEmailOrPasswordWasWrong() {
		when(userRepository.findByEmail("missing@example.test")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> loginService.login("missing@example.test", "submitted-password"))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));
		verify(passwordEncoder, never()).matches(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString());
		verify(jwtService, never()).issue(org.mockito.ArgumentMatchers.anyLong());
	}

	private static User userWithId(long id) throws Exception {
		User user = new User("member@example.test", "stored-password-hash");
		Field field = User.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(user, id);
		return user;
	}
}
