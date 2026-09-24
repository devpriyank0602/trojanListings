package com.ebay.trojanlistings.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Logs one line per API request to stdout: method, path, query, status, duration.
 *
 * <p>Exists so the backend's own terminal shows every call the frontend makes,
 * without needing a browser devtools tab open. Registered automatically by Spring
 * Boot because {@link Component}-annotated {@code Filter} beans are picked up by the
 * embedded servlet container's auto-configuration.
 */
@Component
public class ApiRequestLoggingFilter extends HttpFilter {

    private static final Logger log = LoggerFactory.getLogger("com.ebay.trojanlistings.api.access");

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        if (!req.getRequestURI().startsWith("/api")) {
            chain.doFilter(req, res);
            return;
        }

        long start = System.currentTimeMillis();
        String query = req.getQueryString();
        String path = query == null ? req.getRequestURI() : req.getRequestURI() + "?" + query;

        try {
            chain.doFilter(req, res);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            int status = res.getStatus();
            // >=400 as a warning so a failing call is visible without reading every line.
            if (status >= 400) {
                log.warn(">> {} {} -> {} ({} ms)", req.getMethod(), path, status, elapsed);
            } else {
                log.info(">> {} {} -> {} ({} ms)", req.getMethod(), path, status, elapsed);
            }
        }
    }
}
