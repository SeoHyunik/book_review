package com.example.bookreview.filter;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class LogUuidFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String uuid = UUID.randomUUID().toString();
        MDC.put("loguuid", uuid);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("loguuid");
        }
    }
}
