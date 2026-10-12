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
			String cloudflareIp = request.getHeader("CF-Connecting-IP");
			if (cloudflareIp != null) {
				String address = cloudflareIp.trim();
				if (address.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
						|| (address.contains(":") && address.matches("[0-9a-fA-F:.]+"))) {
					try {
						return InetAddress.getByName(address).getHostAddress();
					} catch (UnknownHostException ignored) {
						// Fall back to the socket address if the edge supplies an invalid value.
					}
				}
			}
		}
		return request.getRemoteAddr();
	}
}
