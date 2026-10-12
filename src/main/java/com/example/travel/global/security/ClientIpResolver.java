package com.example.travel.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class ClientIpResolver {
	private final boolean renderProxy;

	public ClientIpResolver(Environment environment) {
		this.renderProxy = environment.acceptsProfiles(Profiles.of("prod"));
	}

	public String resolve(HttpServletRequest request) {
		if (renderProxy) {
			String forwarded = request.getHeader("X-Forwarded-For");
			if (forwarded != null) {
				String first = forwarded.split(",", 2)[0].trim();
				if (first.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
						|| (first.contains(":") && first.matches("[0-9a-fA-F:.]+"))) {
					try {
						return InetAddress.getByName(first).getHostAddress();
					} catch (UnknownHostException ignored) {
						// Fall back to the socket address if the edge supplies an invalid value.
					}
				}
			}
		}
		return request.getRemoteAddr();
	}
}
