package com.example.travel.user.controller;

import com.example.travel.user.dto.UserRegistrationRequest;
import com.example.travel.user.service.UserRegistrationService;
import com.example.travel.global.security.ClientIpResolver;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserRegistrationService userRegistrationService;
	private final ClientIpResolver clientIpResolver;

	public UserController(UserRegistrationService userRegistrationService, ClientIpResolver clientIpResolver) {
		this.userRegistrationService = userRegistrationService;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public void register(@Valid @RequestBody UserRegistrationRequest request, HttpServletRequest servletRequest) {
		userRegistrationService.register(request.email(), request.password(), clientIpResolver.resolve(servletRequest));
	}
}
