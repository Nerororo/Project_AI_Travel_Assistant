package com.example.travel.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {
	@Test
	void renderUsesCloudflareAddressAndIgnoresSpoofedForwardedChain() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.2");
		request.addHeader("X-Forwarded-For", "203.0.113.4, 198.51.100.8");
		request.addHeader("CF-Connecting-IP", "192.0.2.9");

		assertThat(new ClientIpResolver(environment).resolve(request)).isEqualTo("192.0.2.9");
	}

	@Test
	void localIgnoresUntrustedForwardedAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("CF-Connecting-IP", "203.0.113.4");

		assertThat(new ClientIpResolver(new MockEnvironment()).resolve(request)).isEqualTo("127.0.0.1");
	}

	@Test
	void renderFallsBackWithoutResolvingForwardedHostnames() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.2");
		request.addHeader("CF-Connecting-IP", "dead.beef");

		assertThat(new ClientIpResolver(environment).resolve(request)).isEqualTo("10.0.0.2");
	}

	@Test
	void renderDoesNotUseForwardedChainWhenCloudflareAddressIsMissing() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.2");
		request.addHeader("X-Forwarded-For", "203.0.113.4");

		assertThat(new ClientIpResolver(environment).resolve(request)).isEqualTo("10.0.0.2");
	}

	@Test
	void renderRejectsAnAddressChainInCloudflareHeader() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.2");
		request.addHeader("CF-Connecting-IP", "203.0.113.4, 198.51.100.8");

		assertThat(new ClientIpResolver(environment).resolve(request)).isEqualTo("10.0.0.2");
	}
}
