package com.example.travel.user.controller;

import com.example.travel.user.dto.UserRegistrationRequest;
import com.example.travel.user.service.UserRegistrationService;
import jakarta.validation.Valid;
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

	public UserController(UserRegistrationService userRegistrationService) {
		this.userRegistrationService = userRegistrationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public void register(@Valid @RequestBody UserRegistrationRequest request) {
		userRegistrationService.register(request.email(), request.password());
	}
}
