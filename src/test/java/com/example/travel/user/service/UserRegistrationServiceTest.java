package com.example.travel.user.service;

import com.example.travel.global.exception.ApiException;
import com.example.travel.global.exception.ErrorCode;
import com.example.travel.user.domain.User;
import com.example.travel.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegistrationServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final UserRegistrationService service = new UserRegistrationService(userRepository, passwordEncoder);

	@Test
	void normalizesEmailAndStoresOnlyEncodedPassword() {
		String password = validPassword();
		when(passwordEncoder.encode(password)).thenReturn("encoded-password-value");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.register("MEMBER@EXAMPLE.TEST", password);

		verify(userRepository).existsByEmail("member@example.test");
		verify(userRepository).saveAndFlush(any(User.class));
		verify(passwordEncoder).encode(password);
	}

	@Test
	void rejectsKnownDuplicateBeforeHashing() {
		when(userRepository.existsByEmail("member@example.test")).thenReturn(true);

		assertThatThrownBy(() -> service.register("MEMBER@EXAMPLE.TEST", validPassword()))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));

		verify(passwordEncoder, never()).encode(any());
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void mapsDatabaseDuplicateRaceToSameBusinessError() {
		when(passwordEncoder.encode(any())).thenReturn("encoded-password-value");
		when(userRepository.saveAndFlush(any(User.class)))
				.thenThrow(new DataIntegrityViolationException("database detail must not escape"));

		assertThatThrownBy(() -> service.register("member@example.test", validPassword()))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
	}

	private static String validPassword() {
		return "p".repeat(12);
	}
}
