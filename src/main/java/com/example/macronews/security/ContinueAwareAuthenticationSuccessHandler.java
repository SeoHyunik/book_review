package com.example.macronews.security;

import com.example.macronews.util.RedirectPathUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class ContinueAwareAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    public static final String CONTINUE_URL_SESSION_KEY = "auth.continueUrl";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        String continueUrl = resolveContinueUrl(request);
        clearContinueUrl(request.getSession(false));

        if (StringUtils.hasText(continueUrl)) {
            log.debug("Authentication success, redirecting to continueUrl={}", continueUrl);
            response.sendRedirect(continueUrl);
            return;
        }

        response.sendRedirect("/news");
    }

    private String resolveContinueUrl(HttpServletRequest request) {
        String requestParam = RedirectPathUtils.normalizeSafeRelativePath(request.getParameter("continue"));
        if (StringUtils.hasText(requestParam)) {
            return requestParam;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        Object sessionValue = session.getAttribute(CONTINUE_URL_SESSION_KEY);
        if (sessionValue instanceof String continueUrl) {
            String normalizedSessionContinueUrl = RedirectPathUtils.normalizeSafeRelativePath(continueUrl);
            if (StringUtils.hasText(normalizedSessionContinueUrl)) {
                return normalizedSessionContinueUrl;
            }
        }
        return null;
    }

    private void clearContinueUrl(HttpSession session) {
        if (session != null) {
            session.removeAttribute(CONTINUE_URL_SESSION_KEY);
        }
    }
}
