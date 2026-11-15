package com.braintrustappliedresearch.businessrules;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds two defensive headers to every response from the rules endpoint, including the ones Spring produces before a
 * controller runs (405, 415):
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff}, so a browser never second-guesses the JSON content type;</li>
 *   <li>{@code Cache-Control: no-store}, because a rule result describes one request and must not be kept by a shared
 *       cache or a browser.</li>
 * </ul>
 * A header that is already set (for example by Spring Security) is left alone.
 */
final class ResponseHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        setIfAbsent(response, "X-Content-Type-Options", "nosniff");
        setIfAbsent(response, "Cache-Control", "no-store");
        chain.doFilter(request, response);
    }

    private static void setIfAbsent(HttpServletResponse response, String name, String value) {
        if (!response.containsHeader(name)) {
            response.setHeader(name, value);
        }
    }
}
