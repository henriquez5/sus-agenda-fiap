package br.com.susagenda.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestLogging extends OncePerRequestFilter {
	private static final Logger log = LoggerFactory.getLogger(RequestLogging.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String id = UUID.randomUUID().toString();
		long start = System.nanoTime();
		response.setHeader("X-Request-Id", id);
		MDC.put("requestId", id);
		try {
			chain.doFilter(request, response);
		} finally {
			log.info("request_id={} method={} path={} status={} duration_ms={}", id, request.getMethod(),
					request.getRequestURI(), response.getStatus(), (System.nanoTime() - start) / 1_000_000);
			MDC.remove("requestId");
		}
	}
}
