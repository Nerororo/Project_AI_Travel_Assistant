package com.example.travel.user.controller;

import com.example.travel.user.dto.LoginRequest;
import com.example.travel.user.dto.LoginResponse;
import com.example.travel.user.service.LoginService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final LoginService loginService;

	public AuthController(LoginService loginService) {
		this.loginService = loginService;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return loginService.login(request.email(), request.password());
	}
}
