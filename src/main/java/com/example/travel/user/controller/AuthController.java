package com.example.travel.user.controller;

import com.example.travel.user.dto.LoginRequest;
import com.example.travel.user.dto.LoginResponse;
import com.example.travel.user.service.LoginService;
import com.example.travel.global.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final LoginService loginService;
	private final ClientIpResolver clientIpResolver;

	public AuthController(LoginService loginService, ClientIpResolver clientIpResolver) {
		this.loginService = loginService;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
		return loginService.login(request.email(), request.password(), clientIpResolver.resolve(servletRequest));
	}
}
