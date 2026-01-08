package com.example.bookreview.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    public LoggingAuthenticationFailureHandler() {
        super("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        String identifier = request.getParameter("username");
        log.warn("Authentication failed for identifier='{}' with reason={} - {}", identifier,
                exception.getClass().getSimpleName(), exception.getMessage());
        super.onAuthenticationFailure(request, response, exception);
    }
}
