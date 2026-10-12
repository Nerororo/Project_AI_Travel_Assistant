package com.example.travel.global.security;

import com.example.travel.global.exception.ErrorCode;
import com.example.travel.global.exception.ErrorResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestSizeFilter extends OncePerRequestFilter {

	private static final int MAX_BODY_BYTES = 64 * 1024;
	private static final JsonFactory REQUEST_JSON = JsonFactory.builder().streamReadConstraints(
			StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(4096).build()).build();
	private final ObjectMapper objectMapper;

	public ApiRequestSizeFilter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > MAX_BODY_BYTES) {
			tooLarge(response);
			return;
		}
		byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
		if (body.length > MAX_BODY_BYTES) {
			tooLarge(response);
			return;
		}
		String contentType = request.getContentType();
		String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
		if (body.length > 0 && (mediaType.equals("application/json") || mediaType.endsWith("+json"))) {
			try (JsonParser parser = REQUEST_JSON.createParser(ObjectReadContext.empty(), body)) {
				while (parser.nextToken() != null) {
					parser.getText();
				}
			} catch (JacksonException exception) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.setContentType("application/json;charset=UTF-8");
				objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(ErrorCode.VALIDATION_FAILED));
				return;
			}
		}
		chain.doFilter(new HttpServletRequestWrapper(request) {
			@Override
			public ServletInputStream getInputStream() {
				ByteArrayInputStream input = new ByteArrayInputStream(body);
				return new ServletInputStream() {
					@Override public boolean isFinished() { return input.available() == 0; }
					@Override public boolean isReady() { return true; }
					@Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
					@Override public int read() { return input.read(); }
				};
			}
		}, response);
	}

	private void tooLarge(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
		response.setContentType("application/json;charset=UTF-8");
		objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(ErrorCode.REQUEST_TOO_LARGE));
	}
}
