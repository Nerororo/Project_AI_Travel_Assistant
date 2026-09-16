package com.example.travel.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordSizeValidator implements ConstraintValidator<PasswordSize, String> {

	private static final int MIN_CODE_POINTS = 8;
	private static final int MAX_CODE_POINTS = 64;
	private static final int MAX_UTF8_BYTES = 72;

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		int codePoints = value.codePointCount(0, value.length());
		return codePoints >= MIN_CODE_POINTS
				&& codePoints <= MAX_CODE_POINTS
				&& value.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES;
	}
}
