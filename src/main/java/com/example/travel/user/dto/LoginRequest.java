package com.example.travel.user.dto;

import com.example.travel.user.validation.PasswordFormat;
import com.example.travel.user.validation.PasswordSize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank @Email @Size(max = 255) String email,
		@NotNull @PasswordSize @PasswordFormat String password
) {
}
