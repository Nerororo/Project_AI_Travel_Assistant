package com.example.travel.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {
	@Test
	void renderUsesFirstProxyAddressAndIgnoresLaterSpoofedAddresses() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.2");
		request.addHeader("X-Forwarded-For", "203.0.113.4, 198.51.100.8");

		assertThat(new ClientIpResolver(environment).resolve(request)).isEqualTo("203.0.113.4");
	}

	@Test
	void localIgnoresUntrustedForwardedAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("X-Forwarded-For", "203.0.113.4");

		assertThat(new ClientIpResolver(new MockEnvironment()).resolve(request)).isEqualTo("127.0.0.1");
	}

	@Test
	void renderFallsBackWithoutResolvingForwardedHostnames() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.2");
		request.addHeader("X-Forwarded-For", "dead.beef, 198.51.100.8");

		assertThat(new ClientIpResolver(environment).resolve(request)).isEqualTo("10.0.0.2");
	}
}
