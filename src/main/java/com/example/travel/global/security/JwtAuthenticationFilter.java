package com.example.travel.global.security;

import com.example.travel.user.service.LoginService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final LoginService loginService;
	private final RestAuthenticationEntryPoint authenticationEntryPoint;

	public JwtAuthenticationFilter(JwtService jwtService, LoginService loginService,
			RestAuthenticationEntryPoint authenticationEntryPoint) {
		this.jwtService = jwtService;
		this.loginService = loginService;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return (HttpMethod.POST.matches(request.getMethod()) && path.equals("/api/users"))
				|| (HttpMethod.POST.matches(request.getMethod()) && path.equals("/api/auth/login"))
				|| (HttpMethod.GET.matches(request.getMethod()) && path.startsWith("/api/shared/travel-plans/"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			if (!authorization.startsWith(PREFIX) || authorization.length() == PREFIX.length()) {
				throw new JwtService.InvalidJwtException();
			}
			AuthenticatedUser principal = jwtService.verify(authorization.substring(PREFIX.length()));
			if (!loginService.userExists(principal.userId())) {
				throw new JwtService.InvalidJwtException();
			}
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(principal, null, List.of()));
			filterChain.doFilter(request, response);
		} catch (JwtService.InvalidJwtException exception) {
			SecurityContextHolder.clearContext();
			authenticationEntryPoint.commence(request, response, null);
		}
	}
}
